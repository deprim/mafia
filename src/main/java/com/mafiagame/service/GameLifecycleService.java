package com.mafiagame.service;

import com.mafiagame.model.Game;
import com.mafiagame.model.GamePhase;
import com.mafiagame.model.Player;
import com.mafiagame.repository.GamePhaseRepository;
import com.mafiagame.repository.GameRepository;
import com.mafiagame.repository.PlayerRepository;
import com.mafiagame.utils.enums.*;
import com.mafiagame.websocket.GameBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameLifecycleService {

    private static final int MIN_PLAYERS = 4;

    private final GameRepository games;
    private final PlayerRepository players;
    private final GamePhaseRepository phases;
    private final GameBroadcaster broadcaster;
    // TODO: private final VotingService voting;

    @Value("${game.phase.night:60s}")
    private Duration nightDuration;
    @Value("${game.phase.day:120s}")
    private Duration dayDuration;

    private record Elimination(String nickname) {
    }


    @Transactional
    public void startGame(UUID gameId, UUID requesterId) {
        Game game = lock(gameId);
        Player requester = players.findById(requesterId).orElseThrow();

        if (!requester.isHost() || !requester.getGame().getId().equals(gameId))
            throw new IllegalStateException("Only the host can start the game");
        if (game.getStatus() != GameStatus.WAITING)
            throw new IllegalStateException("Game already started");

        List<Player> list = players.findByGameId(gameId);
        if (list.size() < MIN_PLAYERS)
            throw new IllegalStateException("Need at least " + MIN_PLAYERS + " players");

        assignRoles(list);
        game.setStatus(GameStatus.STARTED);
        games.save(game);

        broadcaster.toGame(gameId, "GAME_STARTED", Map.of());

        enterPhase(game, PhaseType.NIGHT, 1);   // the game always begins with night of round 1
    }

    /**
     * The single transition method. The timer (and later the "everyone voted" check) calls it.
     * Stale calls are ignored, so a double call can't skip a phase.
     */
    @Transactional
    public void advance(UUID gameId, PhaseType expectedType, int expectedRound) {
        Game game = lock(gameId);
        if (game.getStatus() != GameStatus.STARTED) return;   // WAITING or FINISHED: nothing to advance

        GamePhase current = currentPhase(game);
        if (current.getPhaseType() != expectedType || current.getRoundNumber() != expectedRound) {
            log.debug("Ignoring stale advance for game {}", gameId);
            return;
        }

        switch (current.getPhaseType()) {
            case NIGHT -> resolveNight(game, current);
            case DAY -> resolveDay(game, current);
            default -> {
            }
        }
    }


    private void enterPhase(Game game, PhaseType type, int round) {
        Instant endsAt = Instant.now().plus(durationOf(type));

        GamePhase phase = new GamePhase();
        phase.setGame(game);
        phase.setRoundNumber(round);
        phase.setPhaseType(type);
        phase.setEndedAt(Instant.now());
        phases.save(phase);

        broadcaster.toGame(game.getId(), "PHASE_CHANGED", Map.of("phase", type, "round", round, "endsAt", endsAt));

        switch (type) {   // switch on the enum, not on the GamePhase entity
            case NIGHT -> { /* TODO: voting.startVoting(game.getId(), round, VotingType.MAFIA) */ }
            case DAY -> { /* TODO: voting.startVoting(game.getId(), round, VotingType.CIVILIAN) */ }
            default -> {
            }
        }
    }

    /**
     * Resolves the night phase for the specified game by applying the night vote result,
     * broadcasting the elimination outcome, and advancing the lifecycle when appropriate.
     * <p>
     * A null victim means no player is eliminated, such as when there are no votes or
     * the vote ends in a tie. If a victim is present, the player is killed and the
     * eliminated player's nickname is included in the broadcast result. After the
     * result is broadcast, the game is checked for a winning condition. If the game
     * has not finished, the current phase is marked as ended and the game enters the
     * day phase for the same round.
     *
     * @param game    the game whose night phase is being resolved
     * @param current the current night phase being completed
     */
    private void resolveNight(Game game, GamePhase current) {
        // TODO: UUID victimId = voting.closeVoting(game.getId(), current.getRoundNumber(), VotingType.MAFIA)
        //                              .orElse(null);
        UUID victimId = null;   // null = no votes or a tie, nobody dies

        Elimination result = new Elimination(victimId == null ? null : kill(victimId));
        broadcaster.toGame(game.getId(), "VOTE_RESULT", result);

        //set ending of the current phase
        current.setEndedAt(Instant.now());
        phases.save(current);

        //execute validation of the game finishing conditions and do not generate next phase
        if (finishIfWon(game)) return;

        enterPhase(game, PhaseType.DAY, current.getRoundNumber());
    }

    /**
     * Resolves the day phase for the specified game by applying the civilian vote result,
     * broadcasting the elimination outcome, and advancing the lifecycle when appropriate.
     * <p>
     * A null executed player means no player is eliminated, such as when there are no votes
     * or the vote ends in a tie. If a player is selected, the player is killed and the
     * eliminated player's nickname is included in the broadcast result. After the result is
     * broadcast, the game is checked for a winning condition. If the game has not finished,
     * the game enters the night phase for the next round.
     *
     * @param game    the game whose day phase is being resolved
     * @param current the current day phase being completed
     */
    private void resolveDay(Game game, GamePhase current) {
        // TODO: UUID executedId = voting.closeVoting(game.getId(), current.getRoundNumber(), VotingType.CIVILIAN)
        //                                .orElse(null);
        UUID executedId = null;   // null = tie or no votes, nobody is executed

        Elimination result = new Elimination(executedId == null ? null : kill(executedId));
        broadcaster.toGame(game.getId(), "VOTE_RESULT", result);

        //set ending of the current phase
        current.setEndedAt(Instant.now());
        phases.save(current);

        //execute validation of the game finishing conditions and do not generate next phase
        if (finishIfWon(game)) return;

        enterPhase(game, PhaseType.NIGHT, current.getRoundNumber() + 1);   // new round starts with night
    }


    /**
     * Checks whether the specified game has reached a winning condition and finishes it if so.
     * Civilians win when no mafia players remain alive. Mafia wins when the number of alive
     * mafia players is greater than or equal to the number of alive civilian players.
     *
     * @param game the game to check for a winning condition
     * @return true if the game was finished because a winning condition was met, otherwise false
     */
    private boolean finishIfWon(Game game) {
        List<Player> all = players.findByGameId(game.getId());
        long aliveMafia = countAlive(all, PlayerRole.MAFIA);
        long aliveCivilians = countAlive(all, PlayerRole.CIVILIAN);

        if (aliveMafia == 0) {
            endGame(game, PlayerRole.CIVILIAN, all);
            return true;
        }
        if (aliveMafia >= aliveCivilians) {
            endGame(game, PlayerRole.MAFIA, all);
            return true;
        }
        return false;
    }


    private void endGame(Game game, PlayerRole winner, List<Player> all) {
        game.setStatus(GameStatus.FINISHED);
        games.save(game);

        broadcaster.toGame(game.getId(), "GAME_ENDED", Map.of("winner", winner));
        log.info("Game {} finished, winner: {}", game.getId(), winner);
    }

    private long countAlive(List<Player> all, PlayerRole role) {
        return all.stream()
                .filter(p -> p.getRole() == role && p.getStatus() == PlayerStatus.ALIVE)
                .count();
    }


    private String kill(UUID playerId) {
        Player p = players.findById(playerId).orElseThrow();
        p.setStatus(PlayerStatus.DEAD);
        players.save(p);
        return p.getNickname();
    }


    private void assignRoles(List<Player> list) {
        Collections.shuffle(list);
        int mafiaCount = Math.max(1, list.size() / 3);
        for (int i = 0; i < list.size(); i++) {
            list.get(i).setRole(i < mafiaCount ? PlayerRole.MAFIA : PlayerRole.CIVILIAN);
        }
        players.saveAll(list);
    }

    private Duration durationOf(PhaseType type) {
        return switch (type) {
            case NIGHT -> nightDuration;
            case DAY -> dayDuration;
            default -> throw new IllegalArgumentException("No duration for " + type);
        };
    }

    /**
     *
     * @param game - the game to get the current phase of
     * @return - the latest phase of the game, or null if the game has no phases
     */
    private GamePhase currentPhase(Game game) {
        // TODO: add to GamePhaseRepository:
        // (needs a startedAt/createdAt column;
        return new GamePhase();
//        return phases.findFirstByGameIdOrderByStartedAtDesc(game.getId())
//                .orElseThrow(() -> new IllegalStateException("Game has no phase"));
    }

    private Game lock(UUID gameId) {
        return games.findById(gameId)
                .orElseThrow(() -> new IllegalArgumentException("Game not found"));
    }
}
package com.mafiagame.service;

import com.mafiagame.model.Game;
import com.mafiagame.model.GamePhase;
import com.mafiagame.model.Player;
import com.mafiagame.repository.GamePhaseRepository;
import com.mafiagame.repository.GameRepository;
import com.mafiagame.repository.PlayerRepository;
import com.mafiagame.utils.enums.GameStatus;
import com.mafiagame.utils.enums.PhaseType;
import com.mafiagame.utils.enums.PlayerRole;
import com.mafiagame.utils.enums.PlayerStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameLifecycleService {

    private static final int MIN_PLAYERS = 4;

    private final GameRepository games;
    private final PlayerRepository players;
    private final GamePhaseRepository phases;
    // TODO: private final GameBroadcaster broadcaster;
    // TODO: private final VotingService voting;

    // TODO: private final PhaseTimerService timer; (not sure is needed or no)

    @Value("${game.phase.night:60s}")
    private Duration nightDuration;
    @Value("${game.phase.day:120s}")
    private Duration dayDuration;

    private record Elimination(String nickname) {}


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

        // TODO (broadcaster): after commit send
        //   - toPlayer(player.getId(), "ROLE_ASSIGNED", Map.of("role", role)) to every player
        //   - mafia teammates list to each mafia player
        //   - toGame(gameId, "GAME_STARTED", Map.of())

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
            default -> { }
        }
    }

    // TODO (timer): listener that calls advance(e.gameId(), e.phaseType(), e.round())
    // TODO (timer): after a server restart, reschedule timers for games with status STARTED
    //               using the latest phase of each game


    private void enterPhase(Game game, PhaseType type, int round) {
        Instant endsAt = Instant.now().plus(durationOf(type));

        GamePhase phase = new GamePhase();
        phase.setGame(game);
        phase.setRoundNumber(round);
        phase.setPhaseType(type);
        // TODO: phase.setEndsAt(endsAt) if GamePhase gets an end-time column (needed for the client
        //       countdown and for restoring timers after a restart)
        phases.save(phase);

        // TODO (broadcaster, after commit):
        //   toGame(id, "PHASE_CHANGED", Map.of("phase", type, "round", round, "endsAt", endsAt))
        // TODO (timer, after commit): timer.schedule(game.getId(), type, round, endsAt)

        switch (type) {   // switch on the enum, not on the GamePhase entity
            case NIGHT -> { /* TODO: voting.startVoting(game.getId(), round, VotingType.MAFIA) */ }
            case DAY -> { /* TODO: voting.startVoting(game.getId(), round, VotingType.CIVILIAN) */ }
            default -> { }
        }
    }

    private void resolveNight(Game game, GamePhase current) {
        // TODO: UUID victimId = voting.closeVoting(game.getId(), current.getRoundNumber(), VotingType.MAFIA)
        //                              .orElse(null);
        UUID victimId = null;   // null = no votes or a tie, nobody dies

        Elimination result = new Elimination(victimId == null ? null : kill(victimId));
        // TODO (broadcaster, after commit): toGame(game.getId(), "NIGHT_RESULT", result)
        //       announce only who died, never who killed

        if (!finishIfWon(game)) enterPhase(game, PhaseType.DAY, current.getRoundNumber());   // same round
    }

    private void resolveDay(Game game, GamePhase current) {
        // TODO: UUID executedId = voting.closeVoting(game.getId(), current.getRoundNumber(), VotingType.CIVILIAN)
        //                                .orElse(null);
        UUID executedId = null;   // null = tie or no votes, nobody is executed

        Elimination result = new Elimination(executedId == null ? null : kill(executedId));
        // TODO (broadcaster, after commit): toGame(game.getId(), "VOTE_RESULT", result)

        if (finishIfWon(game)) return;

        enterPhase(game, PhaseType.NIGHT, current.getRoundNumber() + 1);   // new round starts with night
    }


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

        // TODO (timer, after commit): timer.cancel(game.getId())
        // TODO (broadcaster, after commit): toGame(game.getId(), "GAME_ENDED",
        //       Map.of("winner", winner, "roles", <nickname -> role map built from `all`>))
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
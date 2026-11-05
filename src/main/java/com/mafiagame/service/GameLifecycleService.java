package com.mafiagame.service;

import com.mafiagame.dto.EliminationResult;
import com.mafiagame.model.Game;
import com.mafiagame.model.GamePhase;
import com.mafiagame.repository.GameRepository;
import com.mafiagame.utils.enums.*;
import com.mafiagame.websocket.GameBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameLifecycleService {

    private static final int MIN_PLAYERS = 4;

    private final GameRepository games;
    private final GameBroadcaster broadcaster;
    private final GamePhaseService gamePhaseService;
    private final PlayerService playerService;
    // TODO: private final VotingService voting;



    @Transactional
    public void startGame(UUID gameId, UUID requesterId) {
        Game game = lock(gameId);

        if (!playerService.isHost(requesterId, gameId))
            throw new IllegalStateException("Only the host can start the game");
        if (game.getStatus() != GameStatus.WAITING)
            throw new IllegalStateException("Game already started");

        if (playerService.isEnoughPlayers(gameId))
            throw new IllegalStateException("Need at least " + MIN_PLAYERS + " players");

        playerService.assignRoles(gameId);
        game.setStatus(GameStatus.STARTED);
        games.save(game);

        broadcaster.toGame(gameId, "GAME_STARTED", Map.of());
        gamePhaseService.enterPhase(game, PhaseType.NIGHT, 1);   // the game always begins with night of round 1
    }

    /**
     * The single transition method. The timer (and later the "everyone voted" check) calls it.
     * Stale calls are ignored, so a double call can't skip a phase.
     */
    @Transactional
    public void advance(UUID gameId, PhaseType expectedType, int expectedRound) {
        Game game = lock(gameId);
        if (game.getStatus() != GameStatus.STARTED) return;   // WAITING or FINISHED: nothing to advance

        GamePhase current = gamePhaseService.currentPhase(game);
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

        EliminationResult result = new EliminationResult(victimId == null ? null : playerService.kill(victimId, current));
        broadcaster.toGame(game.getId(), "VOTE_RESULT", result);

        gamePhaseService.endPhase(current);

        //execute validation of the game finishing conditions and do not generate next phase
        if (finishIfWon(game)) return;

        gamePhaseService.enterPhase(game, PhaseType.DAY, current.getRoundNumber());
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


        EliminationResult result = new EliminationResult(executedId == null ? null : playerService.kill(executedId, current));
        broadcaster.toGame(game.getId(), "VOTE_RESULT", result);

        gamePhaseService.endPhase(current);

        //execute validation of the game finishing conditions and do not generate next phase
        if (finishIfWon(game)) return;

        gamePhaseService.enterPhase(game, PhaseType.NIGHT, current.getRoundNumber() + 1);   // the new round starts with night
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
        long aliveMafia = playerService.countAlive(game.getId(), PlayerRole.MAFIA);
        long aliveCivilians = playerService.countAlive(game.getId(), PlayerRole.CIVILIAN);

        if (aliveMafia == 0) {
            endGame(game, PlayerRole.CIVILIAN);
            return true;
        }
        if (aliveMafia >= aliveCivilians) {
            endGame(game, PlayerRole.MAFIA);
            return true;
        }
        return false;
    }

    private void endGame(Game game, PlayerRole winner) {
        game.setStatus(GameStatus.FINISHED);
        games.save(game);

        broadcaster.toGame(game.getId(), "GAME_ENDED", Map.of("winner", winner));
        log.info("Game {} finished, winner: {}", game.getId(), winner);
    }

    private Game lock(UUID gameId) {
        return games.findById(gameId)
                .orElseThrow(() -> new IllegalArgumentException("Game not found"));
    }
}
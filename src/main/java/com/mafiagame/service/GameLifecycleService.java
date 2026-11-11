package com.mafiagame.service;

import com.mafiagame.dto.EliminationResult;
import com.mafiagame.model.Game;
import com.mafiagame.model.GamePhase;
import com.mafiagame.model.GameResult;
import com.mafiagame.repository.GameRepository;
import com.mafiagame.repository.GameResultRepository;
import com.mafiagame.utils.enums.*;
import com.mafiagame.websocket.GameBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GameLifecycleService {

    private final GameRepository games;
    private final GameBroadcaster broadcaster;
    private final GamePhaseService gamePhaseService;
    private final PlayerService playerService;
    private final VotingService voting;
    private final GameResultRepository gameResultRepository;

    @Transactional
    public void startGame(UUID gameId, String rawSessionToken) {
        Game game = lock(gameId);

        playerService.requireHost(gameId, rawSessionToken);
        if (game.getStatus() != GameStatus.WAITING) throw new ResponseStatusException(HttpStatus.CONFLICT, "Only waiting games can be started");

        playerService.assignRoles(gameId, game.getMafiaCount());
        game.setStatus(GameStatus.STARTED);
        games.save(game);
        log.info("Game {} started", gameId);

        broadcaster.toGameAndChatAfterCommit(gameId, "GAME_STARTED", Map.of(), "The game has started.");
        gamePhaseService.enterPhase(game, PhaseType.NIGHT, 1);   // the game always begins with night of round 1
    }

    /**
     * The single host-authorized transition method. Stale calls are ignored, so a double call
     * can't skip a phase.
     */
    @Transactional
    public void advance(UUID gameId, String rawSessionToken, PhaseType expectedType, int expectedRound) {
        Game game = lock(gameId);
        playerService.requireHost(gameId, rawSessionToken);
        if (game.getStatus() != GameStatus.STARTED) throw new ResponseStatusException(HttpStatus.CONFLICT, "Game has not active status: " + game.getStatus());   // WAITING or FINISHED: nothing to advance

        GamePhase current = gamePhaseService.currentPhase(game);
        if (current.getPhaseType() != expectedType || current.getRoundNumber() != expectedRound) {
            log.debug("Ignoring stale advance for game {}", gameId);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ignoring stale advance for game " + gameId);
        }

        log.info("Advancing game {} to phase {} round {}", gameId, current.getPhaseType(), current.getRoundNumber());
        switch (current.getPhaseType()) {
            case NIGHT -> resolveNight(game, current);
            case DAY -> resolveDay(game, current);
            default -> throw new IllegalStateException("Unexpected value: " + current.getPhaseType());
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
        UUID victimId = voting.closeVoting(current).orElse(null);   // null = no votes or a tie, nobody dies
        log.debug("Phase {} has no elimination with game with ID {}", current.getPhaseType(), game.getId());

        //the priority not found, seems like skip
        EliminationResult result = new EliminationResult(victimId == null ? null : playerService.kill(victimId, current));
        broadcaster.toGameAndChatAfterCommit(
                game.getId(),
                "VOTE_RESULT",
                result,
                voteResultMessage(current, result)
        );

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
        UUID executedId = voting.closeVoting(current).orElse(null);   // null = no votes or a tie, nobody dies
        log.debug("Phase {} hase no elimination in game with ID {}", current.getPhaseType(), game.getId());

        EliminationResult result = new EliminationResult(executedId == null ? null : playerService.kill(executedId, current));
        broadcaster.toGameAndChatAfterCommit(
                game.getId(),
                "VOTE_RESULT",
                result,
                voteResultMessage(current, result)
        );
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
            endGame(game, Team.CIVILIAN);
            return true;
        }
        if (aliveMafia >= aliveCivilians) {
            endGame(game, Team.MAFIA);
            return true;
        }
        return false;
    }

    private void endGame(Game game, Team winner) {
        if (game.getStatus() == GameStatus.FINISHED) return;

        game.setStatus(GameStatus.FINISHED);
        games.save(game);

        gameResultRepository.save(new GameResult(game, winner));

        String winningTeam = winner == Team.MAFIA ? "Mafia" : "Civilian";
        broadcaster.toGameAndChatAfterCommit(
                game.getId(),
                "GAME_ENDED",
                Map.of("winner", winner),
                "The " + winningTeam + " team has won the game."
        );
        log.info("Game {} finished, winner: {}", game.getId(), winner);
    }

    private String voteResultMessage(GamePhase phase, EliminationResult result) {
        String phaseName = phase.getPhaseType() == PhaseType.NIGHT ? "night" : "day";
        String phaseDescription = "the " + phaseName + " phase of round " + phase.getRoundNumber();
        return result.username() == null
                ? "No player was eliminated during " + phaseDescription + "."
                : result.username() + " was eliminated during " + phaseDescription + ".";
    }

    private Game lock(UUID gameId) {
        return games.findByIdForUpdate(gameId)
                .orElseThrow(() -> new IllegalArgumentException("Game not found"));
    }
}
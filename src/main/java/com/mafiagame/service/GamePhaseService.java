package com.mafiagame.service;

import com.mafiagame.model.Game;
import com.mafiagame.model.GamePhase;
import com.mafiagame.repository.GamePhaseRepository;
import com.mafiagame.utils.enums.GamePhaseStatus;
import com.mafiagame.utils.enums.PhaseType;
import com.mafiagame.websocket.GameBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GamePhaseService {
    private final GamePhaseRepository phases;
    private final GameBroadcaster broadcaster;


    private static final String ROUND_TAG = "round";

    /**
     * Starts a new phase for the specified game and notifies all game participants
     * that the phase has changed.
     *
     * @param game the game for which the new phase is being entered
     * @param type the type of phase to enter
     * @param round the round number associated with the new phase
     */
    @Transactional
    public void enterPhase(Game game, PhaseType type, int round) {
        GamePhase phase = new GamePhase(game, round, type);
        phases.save(phase);

        broadcaster.toGameAfterCommit(
                game.getId(),
                "PHASE_CHANGED",
                Map.of("phase", type, ROUND_TAG, round)
        );

        switch (type) {   // switch on the enum, not on the GamePhase entity
            case NIGHT -> {
                //simple send event to the broadcasters with mafia voting. Maybe later will be added new entity for voting save
                broadcaster.toMafiaAfterCommit(game.getId(), "START_NIGHT_VOTING", Map.of(ROUND_TAG, round));
            }
            case DAY -> {
                //simple send event to the broadcasters. Maybe later will be added new entity for voting save
                broadcaster.toGameAfterCommit(game.getId(), "START_DAY_VOTING", Map.of(ROUND_TAG, round));
            }
            default -> {
            }
        }
    }

    /**
     * Retrieves the currently opened phase for the specified game.
     *
     * @param game - the game to get the current phase of
     * @return - the latest phase of the game, or null if the game has no phases
     */
    public GamePhase currentPhase(Game game) {
        return findCurrentPhase(game).orElseThrow(() ->
                new IllegalStateException("Game has no open phase")
        );
    }

    public Optional<GamePhase> findCurrentPhase(Game game) {
        return phases.findByGameIdAndStatus(
                game.getId(),
                GamePhaseStatus.OPENED
        );
    }

    /**
     * Closes the specified game phase and records the time at which it ended.
     *
     * @param phase the opened game phase to close
     * @throws IllegalStateException if the phase is not currently opened
     */
    @Transactional
    public void endPhase(GamePhase phase) {
        if (phase.getStatus() != GamePhaseStatus.OPENED) {
            throw new IllegalStateException("Phase is already closed");
        }

        phase.setStatus(GamePhaseStatus.CLOSED);
        phase.setEndedAt(Instant.now());

        phases.saveAndFlush(phase); // Save and flush to ensure the order of operations, CRUCIAL!
    }


}

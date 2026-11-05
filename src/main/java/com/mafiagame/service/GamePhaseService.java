package com.mafiagame.service;

import com.mafiagame.model.Game;
import com.mafiagame.model.GamePhase;
import com.mafiagame.repository.GamePhaseRepository;
import com.mafiagame.utils.enums.PhaseType;
import com.mafiagame.websocket.GameBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GamePhaseService {
    private final GamePhaseRepository phases;
    private final GameBroadcaster broadcaster;

    private static final String ROUND_TAG = "round";

    @Transactional
    public void enterPhase(Game game, PhaseType type, int round) {
        GamePhase phase = new GamePhase();
        phase.setGame(game);
        phase.setRoundNumber(round);
        phase.setPhaseType(type);
        phases.save(phase);

        broadcaster.toGame(game.getId(), "PHASE_CHANGED", Map.of("phase", type, ROUND_TAG, round, "startedAt", phase.getStartedAt()));

        switch (type) {   // switch on the enum, not on the GamePhase entity
            case NIGHT -> {
                //simple send event to the broadcasters with mafia voting. Maybe later will be added new entity for voting save
                broadcaster.toMafia(game.getId(), "START_NIGHT_VOTING", Map.of(ROUND_TAG, round));
            }
            case DAY -> {
                //simple send event to the broadcasters. Maybe later will be added new entity for voting save
                broadcaster.toGame(game.getId(), "START_DAY_VOTING", Map.of(ROUND_TAG, round));
            }
            default -> {
            }
        }
    }

    /**
     *
     * @param game - the game to get the current phase of
     * @return - the latest phase of the game, or null if the game has no phases
     */
    public GamePhase currentPhase(Game game) {
        return phases.findFirstByGameIdOrderByStartedAtDesc(game.getId());
        //todo: add or lese throw exception
//                .orElseThrow(() -> new IllegalStateException("Game has no phase"));
    }

    @Transactional
    public void endPhase(GamePhase phase) {
        //set ending of the current phase
        phase.setEndedAt(Instant.now());
        phases.save(phase);
    }



}

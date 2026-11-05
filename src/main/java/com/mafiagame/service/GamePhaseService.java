package com.mafiagame.service;

import com.mafiagame.model.Game;
import com.mafiagame.model.GamePhase;
import com.mafiagame.repository.GamePhaseRepository;
import com.mafiagame.repository.GameRepository;
import com.mafiagame.repository.PlayerRepository;
import com.mafiagame.utils.enums.PhaseType;
import com.mafiagame.websocket.GameBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GamePhaseService {
    private final GameRepository games;
    private final PlayerRepository players;
    private final GamePhaseRepository phases;
    private final GameBroadcaster broadcaster;

    public void enterPhase(Game game, PhaseType type, int round) {
        Instant endsAt = Instant.now();

        GamePhase phase = new GamePhase();
        phase.setGame(game);
        phase.setRoundNumber(round);
        phase.setPhaseType(type);
        phase.setEndedAt(endsAt);
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
     *
     * @param game - the game to get the current phase of
     * @return - the latest phase of the game, or null if the game has no phases
     */
    public GamePhase currentPhase(Game game) {
        return phases.findFirstByGameIdOrderByStartedAtDesc(game.getId());
        //todo: add or lese throw exception
//                .orElseThrow(() -> new IllegalStateException("Game has no phase"));
    }

    public void endPhase(GamePhase phase) {
        //set ending of the current phase
        phase.setEndedAt(Instant.now());
        phases.save(phase);
    }



}

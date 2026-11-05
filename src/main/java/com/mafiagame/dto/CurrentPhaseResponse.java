package com.mafiagame.dto;

import com.mafiagame.model.GamePhase;
import com.mafiagame.utils.enums.PhaseType;

import java.util.UUID;

public record CurrentPhaseResponse(
        UUID phaseId,
        PhaseType phase,
        Integer round
) {
    public static CurrentPhaseResponse from(GamePhase gamePhase) {
        return new CurrentPhaseResponse(gamePhase.getId(), gamePhase.getPhaseType(), gamePhase.getRoundNumber());
    }
}

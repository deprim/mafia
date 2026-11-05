package com.mafiagame.dto;

import com.mafiagame.model.GamePhase;
import com.mafiagame.utils.enums.PhaseType;

public record CurrentPhaseResponse(
        PhaseType phase,
        Integer round
) {
    public static CurrentPhaseResponse from(GamePhase gamePhase) {
        return new CurrentPhaseResponse(gamePhase.getPhaseType(), gamePhase.getRoundNumber());
    }
}

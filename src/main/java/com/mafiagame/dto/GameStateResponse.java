package com.mafiagame.dto;

import com.mafiagame.utils.enums.GameStatus;

import java.util.List;
import java.util.UUID;

public record GameStateResponse(
        UUID id,
        String joinCode,
        Integer mafiaCount,
        GameStatus status,
        List<PlayerResponse> players,
        CurrentPhaseResponse currentPhase,
        GameResultResponse result
) {
}

package com.mafiagame.dto;

import com.mafiagame.model.GameResult;
import com.mafiagame.utils.enums.Team;

import java.time.Instant;
import java.util.UUID;

public record GameResultResponse(
        UUID gameId,
        Team winningTeam,
        Instant finishedAt
) {
    public static GameResultResponse from(GameResult gameResult) {
        return new GameResultResponse(gameResult.getGame().getId(),
                gameResult.getWinningTeam(),
                gameResult.getFinishedAt());
    }
}

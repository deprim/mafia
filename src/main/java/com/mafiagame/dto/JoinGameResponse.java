package com.mafiagame.dto;

import java.util.UUID;

public record JoinGameResponse(
        UUID gameId,
        PlayerResponse player
) {
}

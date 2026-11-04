
package com.mafiagame.dto;

import com.mafiagame.utils.enums.PlayerStatus;

import java.util.UUID;

public record PlayerResponse(
        UUID id,
        String nickname,
        boolean host,
        PlayerStatus status
) {
}

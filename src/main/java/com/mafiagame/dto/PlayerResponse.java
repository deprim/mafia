
package com.mafiagame.dto;

import java.util.UUID;

public record PlayerResponse(
        UUID id,
        String nickname,
        boolean host,
        String status
) {
}

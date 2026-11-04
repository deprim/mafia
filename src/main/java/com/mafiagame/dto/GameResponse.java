
package com.mafiagame.dto;

import java.util.UUID;

public record GameResponse(
        UUID id,
        String joinCode,
        Integer mafiaCount,
        String status
) {
}

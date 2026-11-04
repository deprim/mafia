package com.mafiagame.dto;

import java.time.Instant;

public record PersistedChatMessage(
        Long id,
        Instant createdAt,
        String message,
        String username
) {
}

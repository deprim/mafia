package com.mafiagame.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateGameSettingsRequest(
        @NotNull(message = "Mafia count is required")
        @Min(value = 1, message = "Mafia count must be at least 1")
        Integer mafiaCount
) {
}

package com.mafiagame.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UpdateGameSettingsRequest(
        @NotNull(message = "mafiaCount must not be null")
        @Min(value = 1, message = "mafiaCount must be at least 1")
        Integer mafiaCount
) {
}

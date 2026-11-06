package com.mafiagame.dto;

import com.mafiagame.utils.enums.PhaseType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AdvancePhaseRequest(
        @NotNull(message = "expextedType must not be null")
        PhaseType expectedType,

        @NotNull(message = "expectedRound must not be null")
        @Positive(message = "expectedRound must be a positive integer")
        Integer expectedRound

) {
}

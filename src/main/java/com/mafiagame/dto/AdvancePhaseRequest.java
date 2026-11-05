package com.mafiagame.dto;

import com.mafiagame.utils.enums.PhaseType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AdvancePhaseRequest(
        @NotNull
        PhaseType expectedType,

        @NotNull
        @Positive
        Integer expectedRound

) {
}



package com.mafiagame.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateVoteRequest(

        @NotNull(message = "phaseId must not be null")
        UUID phaseId,
        @NotNull(message = "targetId must not be null")
        UUID targetId
) {
}

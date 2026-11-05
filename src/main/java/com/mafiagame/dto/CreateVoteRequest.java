

package com.mafiagame.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateVoteRequest(
        @NotNull
        UUID phaseId,

        @NotNull
        UUID targetId
) {
}

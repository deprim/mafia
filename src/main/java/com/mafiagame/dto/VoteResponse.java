package com.mafiagame.dto;

import java.util.UUID;

public record VoteResponse(
        UUID phaseId,
        UUID targetId
) {
}

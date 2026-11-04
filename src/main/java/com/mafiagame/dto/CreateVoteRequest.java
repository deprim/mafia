

package com.mafiagame.dto;

import java.util.UUID;

public record CreateVoteRequest(
        UUID phaseId,
        UUID targetId
) {
}

package com.mafiagame.dto;

import java.util.List;
import java.util.UUID;

public record VoteStatusResponse(
        UUID phaseId,
        List<UUID> eligibleVoterIds,
        List<UUID> votedPlayerIds,
        boolean statusVisible,
        boolean readyToAdvance
) {
}

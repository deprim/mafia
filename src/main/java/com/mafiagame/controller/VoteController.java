
package com.mafiagame.controller;

import com.mafiagame.dto.CreateVoteRequest;
import com.mafiagame.dto.VoteResponse;
import com.mafiagame.service.VotingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/votes")
@RequiredArgsConstructor
public class VoteController {


    private final VotingService votingService;

    /**
     * Retrieves the authenticated player's vote for the specified phase.
     *
     * @param phaseId the unique identifier of the phase whose vote should be retrieved
     * @param rawSessionToken the session token used to identify the authenticated player
     * @return a response entity containing the player's vote for the specified phase
     */
    @GetMapping("/me")
    public ResponseEntity<VoteResponse> getMyVote(
            @RequestParam UUID phaseId,
            @RequestHeader(value = "X-Session-Token", required = false) String rawSessionToken) {
        return ResponseEntity.ok(votingService.getMyVote(phaseId, rawSessionToken));
    }

    /**
     * Casts or updates the authenticated player's vote for the specified game phase.
     *
     * @param request the vote creation request containing the phase identifier and target player identifier
     * @param rawSessionToken the session token used to identify the authenticated voting player
     * @return a response entity with no content when the vote is successfully cast or updated
     */
    @PostMapping
    public ResponseEntity<Void> createVote(
            @Valid @RequestBody CreateVoteRequest request,
            @RequestHeader(value = "X-Session-Token", required = false) String rawSessionToken) {
        votingService.castVote(request.phaseId(), request.targetId(), rawSessionToken);

        return ResponseEntity.noContent().build();

    }
}

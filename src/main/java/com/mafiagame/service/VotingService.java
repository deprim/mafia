package com.mafiagame.service;

import com.mafiagame.dto.VoteResponse;
import com.mafiagame.dto.VoteStatusResponse;
import com.mafiagame.model.Game;
import com.mafiagame.model.GamePhase;
import com.mafiagame.model.Player;
import com.mafiagame.model.Vote;
import com.mafiagame.repository.GamePhaseRepository;
import com.mafiagame.repository.GameRepository;
import com.mafiagame.repository.PlayerRepository;
import com.mafiagame.repository.VoteRepository;
import com.mafiagame.utils.enums.GamePhaseStatus;
import com.mafiagame.utils.enums.GameStatus;
import com.mafiagame.utils.enums.PhaseType;
import com.mafiagame.utils.enums.PlayerRole;
import com.mafiagame.utils.enums.PlayerStatus;
import com.mafiagame.utils.enums.VotingType;
import com.mafiagame.utils.SessionTokenGenerator;
import com.mafiagame.websocket.GameBroadcaster;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VotingService {

    private final GameRepository games;
    private final GamePhaseRepository phases;
    private final PlayerRepository players;
    private final VoteRepository votes;
    private final GameBroadcaster broadcaster;


    /**
     * Casts or updates the authenticated player's vote for the specified open game phase.
     *
     * The player is resolved from the provided token, verified to belong to the same game as the
     * requested phase, and allowed to vote only while the game is started and the phase is still open.
     * If the player has already voted in the phase, the existing vote is updated to the new target.
     * Otherwise, a new vote is created. A vote notification is published after the transaction commits.
     *
     * @param phaseId the identifier of the game phase in which the vote is being cast
     * @param targetId the identifier of the player selected as the vote target
     * @param rawToken the authentication token used to identify the voting player
     * @throws ResponseStatusException if the phase, game, or target cannot be found, if the voter is
     *         not part of the game, if the game is not running, if the phase is no longer open, or if
     *         the vote is invalid
     */
    @Transactional
    public void castVote(UUID phaseId, UUID targetId, String rawToken) {
        GamePhase requestedPhase = phases.findById(phaseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Phase not found"));

        Game game = games.findByIdForUpdate(requestedPhase.getGame().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found"));

        Player voter = requirePlayer(rawToken);

        if (!voter.getGame().getId().equals(game.getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Player is not part of this room");

        if (game.getStatus() != GameStatus.STARTED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Game is not running");

        GamePhase phase = phases.findByGameIdAndStatus(game.getId(), GamePhaseStatus.OPENED)
                .filter(current -> current.getId().equals(phaseId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "This phase is no longer open"));

        Player target = players.findById(targetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Target not found"));

        validateVote(phase, voter, target);

        Optional<Vote> existingVote = votes.findByPhaseIdAndVoterId(phaseId, voter.getId());
        boolean changedVote = existingVote
                .map(vote -> !vote.getTarget().getId().equals(target.getId()))
                .orElse(false);
        Vote vote = existingVote.orElseGet(() -> new Vote(phase, voter, target));

        vote.setTarget(target);
        votes.saveAndFlush(vote);

        publishVoteAfterCommit(phase, voter, target, changedVote);
    }

    /**
     * Retrieves the authenticated player's vote for the specified phase.
     *
     * @param phaseId the unique identifier of the phase whose vote should be retrieved
     * @param rawToken the authentication token used to identify the player
     * @return the player's vote information for the specified phase
     */
    @Transactional
    public VoteResponse getMyVote(UUID phaseId, String rawToken) {
        Player player = requirePlayer(rawToken);

        Vote vote = votes.findByPhaseIdAndVoterId(phaseId, player.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "You have no vote in this phase"));

        return new VoteResponse(vote.getPhase().getId(), vote.getTarget().getId());
    }

    @Transactional
    public VoteStatusResponse getVoteStatus(UUID phaseId, String rawToken) {
        Player viewer = requirePlayer(rawToken);
        GamePhase phase = phases.findById(phaseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Phase not found"));

        if (!viewer.getGame().getId().equals(phase.getGame().getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Player is not part of this room");
        if (phase.getStatus() != GamePhaseStatus.OPENED || phase.getGame().getStatus() != GameStatus.STARTED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This phase is no longer open");

        List<Player> eligibleVoters = eligibleVoters(phase);
        List<UUID> eligibleVoterIds = eligibleVoters.stream()
                .map(Player::getId)
                .toList();
        Set<UUID> submittedVoteIds = votes.findByPhaseId(phaseId).stream()
                .map(vote -> vote.getVoter().getId())
                .collect(Collectors.toSet());
        boolean readyToAdvance = eligibleVoterIds.stream().allMatch(submittedVoteIds::contains);
        boolean statusVisible = phase.getPhaseType() == PhaseType.DAY
                || (phase.getPhaseType() == PhaseType.NIGHT
                && phase.getRoundNumber() > 1
                && viewer.getRole() == PlayerRole.MAFIA);
        List<UUID> visibleEligibleVoterIds = statusVisible ? eligibleVoterIds : List.of();
        List<UUID> visibleVotedPlayerIds = statusVisible
                ? eligibleVoterIds.stream().filter(submittedVoteIds::contains).toList()
                : List.of();

        return new VoteStatusResponse(
                phaseId,
                visibleEligibleVoterIds,
                visibleVotedPlayerIds,
                statusVisible,
                readyToAdvance
        );
    }

    public boolean allEligibleVotersHaveVoted(GamePhase phase) {
        Set<UUID> submittedVoteIds = votes.findByPhaseId(phase.getId()).stream()
                .map(vote -> vote.getVoter().getId())
                .collect(Collectors.toSet());
        return eligibleVoters(phase).stream()
                .allMatch(player -> submittedVoteIds.contains(player.getId()));
    }


    /**
     * Determines the result of voting for the specified game phase by counting votes
     * per target and identifying whether a single target received the highest number
     * of votes. If no votes were cast or the highest vote count is shared by multiple
     * targets, no target is selected.
     *
     * @param phase the game phase whose votes are to be counted
     * @return an {@link Optional} containing the identifier of the uniquely selected
     * target, or an empty {@link Optional} if there are no votes or the result is tied
     */
    @Transactional
    public Optional<UUID> closeVoting(GamePhase phase) {
        if (phase.getPhaseType() == PhaseType.NIGHT && phase.getRoundNumber() == 1) {
            return Optional.empty();
        }

        Map<UUID, Long> counts = votes.findByPhaseId(phase.getId()).stream()
                .collect(Collectors.groupingBy(v -> v.getTarget().getId(), Collectors.counting()));
        if (counts.isEmpty()) return Optional.empty();

        long max = Collections.max(counts.values());
        List<UUID> leaders = counts.entrySet().stream()
                .filter(e -> e.getValue() == max)
                .map(Map.Entry::getKey)
                .toList();

        return leaders.size() == 1 ? Optional.of(leaders.getFirst()) : Optional.empty();   // tie = nobody
    }

    /**
     * Resolves the player associated with the provided raw session token.
     *
     * @param rawToken the raw session token to validate and look up
     * @return the player associated with the session token
     * @throws ResponseStatusException if the token is null, blank, or does not match an existing player session
     */
    private Player requirePlayer(String rawToken) {
        if (rawToken == null || rawToken.isBlank())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A session token is required");

        return players.findBySessionTokenHash(SessionTokenGenerator.hashToken(rawToken))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid session token"));
    }

    /**
     * Validates whether the specified voter is allowed to vote for the specified target
     * during the given game phase.
     *
     * Ensures that the target belongs to the same game as the phase, both players are
     * alive, the voter is not voting for themselves, voting is available in the current
     * phase, and the voter's role is permitted to vote for that phase's voting type.
     *
     * @param phase the current game phase in which the vote is being cast
     * @param voter the player attempting to cast the vote
     * @param target the player selected as the vote target
     * @throws ResponseStatusException if the vote is invalid for the current game,
     *         player status, target selection, phase, or voter role
     */
    private void validateVote(GamePhase phase, Player voter, Player target) {
        UUID gameId = phase.getGame().getId();

        if (!target.getGame().getId().equals(gameId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Target is not part of this room");

        if (voter.getStatus() != PlayerStatus.ALIVE || target.getStatus() != PlayerStatus.ALIVE)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Voter and target must be alive");

        if (voter.getId().equals(target.getId()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot vote for yourself");

        if (phase.getPhaseType() == PhaseType.NIGHT && phase.getRoundNumber() == 1)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Voting is unavailable on the first night");

        VotingType type = VotingType.forPhase(phase.getPhaseType())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Voting is unavailable in this phase"));

        if (!type.canVote(voter.getRole()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot vote in this phase");
    }

    private List<Player> eligibleVoters(GamePhase phase) {
        if (phase.getPhaseType() == PhaseType.NIGHT && phase.getRoundNumber() == 1) {
            return List.of();
        }

        VotingType type = VotingType.forPhase(phase.getPhaseType())
                .orElseThrow(() -> new IllegalStateException("Voting is unavailable in this phase"));
        return players.findByGameId(phase.getGame().getId()).stream()
                .filter(player -> player.getStatus() == PlayerStatus.ALIVE)
                .filter(player -> type.canVote(player.getRole()))
                .toList();
    }

    /**
     * Registers a transaction synchronization that publishes a vote update event after
     * the current transaction commits successfully.
     *
     * The event is sent to all game participants during the day phase and to mafia
     * participants during the night phase. The published payload includes the phase,
     * voter, and target identifiers.
     *
     * @param phase the game phase in which the vote was cast
     * @param voter the player who cast the vote
     * @param target the player selected as the vote target
     */
    private void publishVoteAfterCommit(GamePhase phase, Player voter, Player target, boolean changedVote) {
        UUID gameId = phase.getGame().getId();
        PhaseType phaseType = phase.getPhaseType();

        Map<String, UUID> data = Map.of(
                "phaseId", phase.getId(),
                "voterId", voter.getId(),
                "targetId", target.getId()
        );

        switch (phaseType) {
            case DAY -> broadcaster.toGameAndChatAfterCommit(
                    gameId,
                    "VOTE_UPDATED",
                    data,
                    voter.getNickname()
                            + (changedVote ? " changed their vote to " : " voted for ")
                            + target.getNickname()
                            + "."
            );
            case NIGHT -> broadcaster.toMafiaAfterCommit(gameId, "VOTE_UPDATED", data);
        }
        broadcaster.toGameAfterCommit(
                gameId,
                "VOTE_PROGRESS",
                Map.of(
                        "phaseId", phase.getId(),
                        "readyToAdvance", allEligibleVotersHaveVoted(phase)
                )
        );
    }
}
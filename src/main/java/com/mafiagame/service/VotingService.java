package com.mafiagame.service;

import com.mafiagame.model.Game;
import com.mafiagame.model.GamePhase;
import com.mafiagame.model.Player;
import com.mafiagame.model.Vote;
import com.mafiagame.repository.GamePhaseRepository;
import com.mafiagame.repository.GameRepository;
import com.mafiagame.repository.PlayerRepository;
import com.mafiagame.repository.VoteRepository;
import com.mafiagame.utils.enums.GameStatus;
import com.mafiagame.utils.enums.PlayerStatus;
import com.mafiagame.utils.enums.VotingType;
import com.mafiagame.websocket.GameBroadcaster;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
     * Records or updates a player's vote in the current phase of a running game.
     * The voter and target must both belong to the specified game, must be alive,
     * and the voter's role must be eligible to vote during the active phase. If the
     * voter has already voted in the phase, the existing vote is updated to the new
     * target. Broadcasts vote updates and announces the end of voting when all
     * eligible players have voted.
     *
     * @param gameId the identifier of the game in which the vote is being cast
     * @param voterId the identifier of the player casting the vote
     * @param targetId the identifier of the player being voted for
     * @throws IllegalArgumentException if the game, voter, or target cannot be found
     * @throws IllegalStateException if the game is not running, the phase does not
     * support voting, the voter or target is not alive, or the voter is not eligible
     * to vote in the current phase
     */
    @Transactional
    public void castVote(UUID gameId, UUID voterId, UUID targetId) {
        Game game = games.findById(gameId)
                .orElseThrow(() -> new IllegalArgumentException("Game not found"));
        if (game.getStatus() != GameStatus.STARTED)
            throw new IllegalStateException("Game is not running");

        GamePhase phase = phases.findFirstByGameIdOrderByStartedAtDesc(gameId)
                .orElseThrow(() -> new IllegalStateException("Game has no phase"));
        VotingType type = VotingType.forPhase(phase.getPhaseType())
                .orElseThrow(() -> new IllegalStateException("the role is not exists"));

        Player voter = loadPlayer(voterId, gameId);
        Player target = loadPlayer(targetId, gameId);

        if (voter.getStatus() != PlayerStatus.ALIVE) throw new IllegalStateException("Dead players can't vote");
        if (target.getStatus() != PlayerStatus.ALIVE) throw new IllegalStateException("Target is not alive");
        if (!type.canVote(voter.getRole())) throw new IllegalStateException("You can't vote now");

        // upsert: re-voting changes the target of the existing row
        Vote vote = votes.findByPhaseIdAndTargetId(phase.getId(), voterId)
                .orElseGet(() -> new Vote(phase, voter, target));
        vote.setTarget(target);
        votes.saveAndFlush(vote);

        broadcaster.toMafia(gameId, "VOTE_UPDATED", Map.of("voter", voterId, "target", targetId));

        if (allEligibleVoted(gameId, phase, type)) {
            broadcaster.toGame(gameId, "VOTE_ENDS", Map.of("phase", phase.getPhaseType(), "round", phase.getRoundNumber()));
        }
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

    private boolean allEligibleVoted(UUID gameId, GamePhase phase, VotingType type) {
        long eligible = players.findByGameId(gameId).stream()
                .filter(p -> p.getStatus() == PlayerStatus.ALIVE && type.canVote(p.getRole()))
                .count();
        return votes.countByPhaseId(phase.getId()) >= eligible;
    }

    private Player loadPlayer(UUID playerId, UUID gameId) {
        Player p = players.findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException("Player not found"));
        if (!p.getGame().getId().equals(gameId))
            throw new IllegalStateException("Player is not in this game");
        return p;
    }
}
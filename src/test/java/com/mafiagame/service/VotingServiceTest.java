package com.mafiagame.service;

import com.mafiagame.dto.VoteResponse;
import com.mafiagame.model.Game;
import com.mafiagame.model.GamePhase;
import com.mafiagame.model.Player;
import com.mafiagame.model.Vote;
import com.mafiagame.repository.GamePhaseRepository;
import com.mafiagame.repository.GameRepository;
import com.mafiagame.repository.PlayerRepository;
import com.mafiagame.repository.VoteRepository;
import com.mafiagame.utils.SessionTokenGenerator;
import com.mafiagame.utils.enums.GamePhaseStatus;
import com.mafiagame.utils.enums.GameStatus;
import com.mafiagame.utils.enums.PhaseType;
import com.mafiagame.utils.enums.PlayerRole;
import com.mafiagame.utils.enums.PlayerStatus;
import com.mafiagame.websocket.GameBroadcaster;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("VotingService")
@ExtendWith(MockitoExtension.class)
class VotingServiceTests {

    private static final String TOKEN = "sessionToken";

    @Mock private GameRepository games;
    @Mock private GamePhaseRepository phases;
    @Mock private PlayerRepository players;
    @Mock private VoteRepository votes;
    @Mock private GameBroadcaster broadcaster;

    @InjectMocks
    private VotingService service;

    private UUID gameId;
    private UUID phaseId;
    private UUID unknownUserId;
    private Game game;
    private GamePhase phase;
    private Player voter;
    private Player target;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();

        gameId = UUID.randomUUID();
        phaseId = UUID.randomUUID();
        unknownUserId = UUID.randomUUID();

        game = new Game(1, "test");
        game.setId(gameId);
        game.setStatus(GameStatus.STARTED);

        voter = givenPlayer(game, PlayerRole.CIVILIAN, PlayerStatus.ALIVE);
        target = givenPlayer(game, PlayerRole.CIVILIAN, PlayerStatus.ALIVE);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /** Lenient mock: not every test touches every getter. */
    private Player givenPlayer(Game playerGame, PlayerRole role, PlayerStatus status) {
        UUID playerId = UUID.randomUUID();

        Player player = new Player(playerGame, "user-" + playerId, false, "hash-" + playerId);
        player.setId(playerId);
        player.setRole(role);
        player.setStatus(status);

        return player;
    }

    /** Stubs the first two lookups of castVote: phase by id and game by phase's game id. */
    private void givenPhaseAndGame(PhaseType type) {
        phase = new GamePhase(game, 1, type);
        phase.setId(phaseId);
        when(phases.findById(phaseId)).thenReturn(Optional.of(phase));
        when(games.findByIdForUpdate(gameId)).thenReturn(Optional.of(game));
    }

    /** Stubs the lookups that come after the voter checks: the open phase and the target. */
    private void givenOpenPhaseAndTarget() {
        when(phases.findByGameIdAndStatus(gameId, GamePhaseStatus.OPENED)).thenReturn(Optional.of(phase));
        when(players.findById(target.getId())).thenReturn(Optional.of(target));
    }

    /** Simulates a successful commit by running the registered afterCommit callbacks. */
    private void runAfterCommit() {
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(TransactionSynchronization::afterCommit);
    }

    @Test
    @DisplayName("should create and save new vote on castVote when voter has not voted yet")
    void shouldCreateNewVoteOnCastVote() {
        givenPhaseAndGame(PhaseType.DAY);
        when(players.findBySessionTokenHash(SessionTokenGenerator.hashToken(TOKEN))).thenReturn(Optional.of(voter));
        givenOpenPhaseAndTarget();
        when(votes.findByPhaseIdAndVoterId(phaseId, voter.getId())).thenReturn(Optional.empty());

        service.castVote(phaseId, target.getId(), TOKEN);

        ArgumentCaptor<Vote> captor = ArgumentCaptor.forClass(Vote.class);
        verify(votes).saveAndFlush(captor.capture());

        Vote saved = captor.getValue();
        assertThat(saved.getPhase()).isSameAs(phase);
        assertThat(saved.getVoter()).isSameAs(voter);
        assertThat(saved.getTarget()).isSameAs(target);
    }

    @Test
    @DisplayName("should update target of existing vote on castVote when voter already voted")
    void shouldUpdateExistingVoteOnCastVote() {
        givenPhaseAndGame(PhaseType.DAY);
        when(players.findBySessionTokenHash(SessionTokenGenerator.hashToken(TOKEN))).thenReturn(Optional.of(voter));
        givenOpenPhaseAndTarget();

        Player oldTarget = givenPlayer(game, PlayerRole.CIVILIAN, PlayerStatus.ALIVE);
        Vote existing = new Vote(phase, voter, oldTarget);
        when(votes.findByPhaseIdAndVoterId(phaseId, voter.getId())).thenReturn(Optional.of(existing));

        service.castVote(phaseId, target.getId(), TOKEN);

        ArgumentCaptor<Vote> captor = ArgumentCaptor.forClass(Vote.class);
        verify(votes).saveAndFlush(captor.capture());

        assertThat(captor.getValue()).isSameAs(existing);
        assertThat(existing.getTarget()).isSameAs(target);
    }

    @Test
    @DisplayName("should not broadcast on castVote until transaction is committed")
    void shouldNotBroadcastOnCastVoteBeforeCommit() {
        givenPhaseAndGame(PhaseType.DAY);
        when(players.findBySessionTokenHash(SessionTokenGenerator.hashToken(TOKEN))).thenReturn(Optional.of(voter));
        givenOpenPhaseAndTarget();
        when(votes.findByPhaseIdAndVoterId(phaseId, voter.getId())).thenReturn(Optional.empty());

        service.castVote(phaseId, target.getId(), TOKEN);

        verify(broadcaster).toGameAfterCommit(eq(gameId), eq("VOTE_UPDATED"), any());
        verify(broadcaster, never()).toGame(any(), any(), any());
        verify(broadcaster, never()).toMafia(any(), any(), any());
    }

    @Test
    @DisplayName("should broadcast vote to whole game after commit when voting during DAY")
    void shouldBroadcastToGameAfterCommitOnDayVote() {
        givenPhaseAndGame(PhaseType.DAY);
        when(players.findBySessionTokenHash(SessionTokenGenerator.hashToken(TOKEN))).thenReturn(Optional.of(voter));
        givenOpenPhaseAndTarget();
        when(votes.findByPhaseIdAndVoterId(phaseId, voter.getId())).thenReturn(Optional.empty());

        service.castVote(phaseId, target.getId(), TOKEN);
        runAfterCommit();

        Map<String, UUID> expected = Map.of(
                "phaseId", phaseId,
                "voterId", voter.getId(),
                "targetId", target.getId()
        );
        verify(broadcaster).toGameAfterCommit(gameId, "VOTE_UPDATED", expected);
        verify(broadcaster, never()).toMafiaAfterCommit(any(), any(), any());
    }

    @Test
    @DisplayName("should broadcast vote to mafia only after commit when voting during NIGHT")
    void shouldBroadcastToMafiaAfterCommitOnNightVote() {
        voter = givenPlayer(game, PlayerRole.MAFIA, PlayerStatus.ALIVE);
        givenPhaseAndGame(PhaseType.NIGHT);
        when(players.findBySessionTokenHash(SessionTokenGenerator.hashToken(TOKEN))).thenReturn(Optional.of(voter));
        givenOpenPhaseAndTarget();
        when(votes.findByPhaseIdAndVoterId(phaseId, voter.getId())).thenReturn(Optional.empty());

        service.castVote(phaseId, target.getId(), TOKEN);
        runAfterCommit();

        Map<String, UUID> expected = Map.of(
                "phaseId", phaseId,
                "voterId", voter.getId(),
                "targetId", target.getId()
        );
        verify(broadcaster).toMafiaAfterCommit(gameId, "VOTE_UPDATED", expected);
        verify(broadcaster, never()).toGameAfterCommit(any(), any(), any());
    }

    @Test
    @DisplayName("should throw NOT_FOUND on castVote when phase not found")
    void shouldThrowNotFoundOnCastVoteWhenPhaseNotFound() {
        when(phases.findById(phaseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.castVote(phaseId, unknownUserId, TOKEN))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);

        verifyNoInteractions(games, players, votes, broadcaster);
    }

    @Test
    @DisplayName("should throw NOT_FOUND on castVote when game not found")
    void shouldThrowNotFoundOnCastVoteWhenGameNotFound() {
        phase = new GamePhase(game, 1, PhaseType.DAY);
        phase.setId(phaseId);
        when(phases.findById(phaseId)).thenReturn(Optional.of(phase));
        when(games.findByIdForUpdate(gameId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.castVote(phaseId, unknownUserId, TOKEN))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);

        verifyNoInteractions(players, votes, broadcaster);
    }

    @Test
    @DisplayName("should throw UNAUTHORIZED on castVote when token is null")
    void shouldThrowUnauthorizedOnCastVoteWhenTokenIsNull() {
        givenPhaseAndGame(PhaseType.DAY);

        assertThatThrownBy(() -> service.castVote(phaseId, unknownUserId, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.UNAUTHORIZED);

        verifyNoInteractions(players, votes);
    }

    @Test
    @DisplayName("should throw UNAUTHORIZED on castVote when token is blank")
    void shouldThrowUnauthorizedOnCastVoteWhenTokenIsBlank() {
        givenPhaseAndGame(PhaseType.DAY);

        assertThatThrownBy(() -> service.castVote(phaseId, unknownUserId, "   "))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.UNAUTHORIZED);

        verifyNoInteractions(players, votes);
    }

    @Test
    @DisplayName("should throw UNAUTHORIZED on castVote when token does not match any player")
    void shouldThrowUnauthorizedOnCastVoteWhenTokenInvalid() {
        givenPhaseAndGame(PhaseType.DAY);
        when(players.findBySessionTokenHash(SessionTokenGenerator.hashToken(TOKEN))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.castVote(phaseId, unknownUserId, TOKEN))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.UNAUTHORIZED);

        verifyNoInteractions(votes);
    }

    @Test
    @DisplayName("should throw FORBIDDEN on castVote when voter belongs to another game")
    void shouldThrowForbiddenOnCastVoteWhenVoterFromAnotherGame() {
        Game otherGame = new Game(1, "other");
        otherGame.setId(UUID.randomUUID());
        Player outsider = givenPlayer(otherGame, PlayerRole.CIVILIAN, PlayerStatus.ALIVE);
        UUID targetId = target.getId();

        givenPhaseAndGame(PhaseType.DAY);
        when(players.findBySessionTokenHash(SessionTokenGenerator.hashToken(TOKEN))).thenReturn(Optional.of(outsider));

        assertThatThrownBy(() -> service.castVote(phaseId, targetId, TOKEN))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.FORBIDDEN);

        verifyNoInteractions(votes);
    }

    @Test
    @DisplayName("should throw CONFLICT on castVote when game is not STARTED")
    void shouldThrowConflictOnCastVoteWhenGameNotStarted() {
        game.setStatus(GameStatus.FINISHED);
        givenPhaseAndGame(PhaseType.DAY);
        when(players.findBySessionTokenHash(SessionTokenGenerator.hashToken(TOKEN))).thenReturn(Optional.of(voter));
        UUID targetId = target.getId();

        assertThatThrownBy(() -> service.castVote(phaseId, targetId, TOKEN))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.CONFLICT);

        verifyNoInteractions(votes);
    }

    @Test
    @DisplayName("should throw CONFLICT on castVote when there is no open phase")
    void shouldThrowConflictOnCastVoteWhenNoOpenPhase() {
        givenPhaseAndGame(PhaseType.DAY);
        when(players.findBySessionTokenHash(SessionTokenGenerator.hashToken(TOKEN))).thenReturn(Optional.of(voter));
        when(phases.findByGameIdAndStatus(gameId, GamePhaseStatus.OPENED)).thenReturn(Optional.empty());
        UUID targetId = target.getId();

        assertThatThrownBy(() -> service.castVote(phaseId, targetId, TOKEN))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.CONFLICT);

        verifyNoInteractions(votes);
    }

    @Test
    @DisplayName("should throw CONFLICT on castVote when requested phase is no longer the open one")
    void shouldThrowConflictOnCastVoteWhenRequestedPhaseIsStale() {
        givenPhaseAndGame(PhaseType.DAY);
        when(players.findBySessionTokenHash(SessionTokenGenerator.hashToken(TOKEN))).thenReturn(Optional.of(voter));
        UUID targetId = target.getId();

        GamePhase newerPhase = new GamePhase(game, 2, PhaseType.NIGHT);
        newerPhase.setId(UUID.randomUUID());
        when(phases.findByGameIdAndStatus(gameId, GamePhaseStatus.OPENED)).thenReturn(Optional.of(newerPhase));

        assertThatThrownBy(() -> service.castVote(phaseId, targetId, TOKEN))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.CONFLICT);

        verifyNoInteractions(votes);
    }

    @Test
    @DisplayName("should throw NOT_FOUND on castVote when target not found")
    void shouldThrowNotFoundOnCastVoteWhenTargetNotFound() {
        UUID unknownTargetId = UUID.randomUUID();
        givenPhaseAndGame(PhaseType.DAY);
        when(players.findBySessionTokenHash(SessionTokenGenerator.hashToken(TOKEN))).thenReturn(Optional.of(voter));
        when(phases.findByGameIdAndStatus(gameId, GamePhaseStatus.OPENED)).thenReturn(Optional.of(phase));
        when(players.findById(unknownTargetId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.castVote(phaseId, unknownTargetId, TOKEN))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);

        verifyNoInteractions(votes);
    }

    @Test
    @DisplayName("should throw FORBIDDEN on castVote when target belongs to another game")
    void shouldThrowForbiddenOnCastVoteWhenTargetFromAnotherGame() {
        Game otherGame = new Game(1, "other");
        otherGame.setId(UUID.randomUUID());
        target = givenPlayer(otherGame, PlayerRole.CIVILIAN, PlayerStatus.ALIVE);
        UUID targetId = target.getId();

        givenPhaseAndGame(PhaseType.DAY);
        when(players.findBySessionTokenHash(SessionTokenGenerator.hashToken(TOKEN))).thenReturn(Optional.of(voter));
        givenOpenPhaseAndTarget();

        assertThatThrownBy(() -> service.castVote(phaseId, targetId, TOKEN))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.FORBIDDEN);

        verifyNoInteractions(votes);
    }

    @Test
    @DisplayName("should throw CONFLICT on castVote when voter is dead")
    void shouldThrowConflictOnCastVoteWhenVoterIsDead() {
        voter = givenPlayer(game, PlayerRole.CIVILIAN, PlayerStatus.DEAD);
        givenPhaseAndGame(PhaseType.DAY);
        when(players.findBySessionTokenHash(SessionTokenGenerator.hashToken(TOKEN))).thenReturn(Optional.of(voter));
        givenOpenPhaseAndTarget();
        UUID targetId = target.getId();

        assertThatThrownBy(() -> service.castVote(phaseId, targetId, TOKEN))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.CONFLICT);

        verifyNoInteractions(votes);
    }

    @Test
    @DisplayName("should throw CONFLICT on castVote when target is dead")
    void shouldThrowConflictOnCastVoteWhenTargetIsDead() {
        target = givenPlayer(game, PlayerRole.CIVILIAN, PlayerStatus.DEAD);
        UUID targetId = target.getId();
        givenPhaseAndGame(PhaseType.DAY);
        when(players.findBySessionTokenHash(SessionTokenGenerator.hashToken(TOKEN))).thenReturn(Optional.of(voter));
        givenOpenPhaseAndTarget();

        assertThatThrownBy(() -> service.castVote(phaseId, targetId, TOKEN))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.CONFLICT);

        verifyNoInteractions(votes);
    }

    @Test
    @DisplayName("should throw BAD_REQUEST on castVote when voter votes for themselves")
    void shouldThrowBadRequestOnCastVoteWhenVotingForSelf() {
        givenPhaseAndGame(PhaseType.DAY);
        UUID voterId = voter.getId();
        when(players.findBySessionTokenHash(SessionTokenGenerator.hashToken(TOKEN))).thenReturn(Optional.of(voter));
        when(phases.findByGameIdAndStatus(gameId, GamePhaseStatus.OPENED)).thenReturn(Optional.of(phase));
        when(players.findById(voter.getId())).thenReturn(Optional.of(voter));

        assertThatThrownBy(() -> service.castVote(phaseId, voterId, TOKEN))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);

        verifyNoInteractions(votes);
    }

    @Test
    @DisplayName("should throw FORBIDDEN on castVote when civilian votes during NIGHT")
    void shouldThrowForbiddenOnCastVoteWhenCivilianVotesAtNight() {
        givenPhaseAndGame(PhaseType.NIGHT);
        UUID targetId = target.getId();
        when(players.findBySessionTokenHash(SessionTokenGenerator.hashToken(TOKEN))).thenReturn(Optional.of(voter));
        givenOpenPhaseAndTarget();

        assertThatThrownBy(() -> service.castVote(phaseId, targetId, TOKEN))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.FORBIDDEN);

        verifyNoInteractions(votes);
    }

    @Test
    @DisplayName("should return phase and target ids on getMyVote")
    void shouldReturnVoteResponseOnGetMyVote() {
        phase = new GamePhase(game, 1, PhaseType.DAY);
        phase.setId(phaseId);
        when(players.findBySessionTokenHash(SessionTokenGenerator.hashToken(TOKEN))).thenReturn(Optional.of(voter));
        when(votes.findByPhaseIdAndVoterId(phaseId, voter.getId()))
                .thenReturn(Optional.of(new Vote(phase, voter, target)));

        VoteResponse response = service.getMyVote(phaseId, TOKEN);

        assertThat(response).usingRecursiveComparison()
                .isEqualTo(new VoteResponse(phaseId, target.getId()));
    }

    @Test
    @DisplayName("should throw NOT_FOUND on getMyVote when player has no vote in phase")
    void shouldThrowNotFoundOnGetMyVoteWhenNoVote() {
        when(players.findBySessionTokenHash(SessionTokenGenerator.hashToken(TOKEN))).thenReturn(Optional.of(voter));
        when(votes.findByPhaseIdAndVoterId(phaseId, voter.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMyVote(phaseId, TOKEN))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("should throw UNAUTHORIZED on getMyVote when token is missing")
    void shouldThrowUnauthorizedOnGetMyVoteWhenTokenMissing() {
        assertThatThrownBy(() -> service.getMyVote(phaseId, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.UNAUTHORIZED);

        verifyNoInteractions(players, votes);
    }

    @Test
    @DisplayName("should throw UNAUTHORIZED on getMyVote when token is invalid")
    void shouldThrowUnauthorizedOnGetMyVoteWhenTokenInvalid() {
        when(players.findBySessionTokenHash(SessionTokenGenerator.hashToken(TOKEN))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMyVote(phaseId, TOKEN))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.UNAUTHORIZED);

        verifyNoInteractions(votes);
    }


    @Test
    @DisplayName("should return empty on closeVoting when there are no votes")
    void shouldReturnEmptyOnCloseVotingWhenNoVotes() {
        phase = new GamePhase(game, 1, PhaseType.DAY);
        phase.setId(phaseId);
        when(votes.findByPhaseId(phaseId)).thenReturn(List.of());

        assertThat(service.closeVoting(phase)).isEmpty();
    }

    @Test
    @DisplayName("should return the only target on closeVoting when a single vote was cast")
    void shouldReturnTargetOnCloseVotingWhenSingleVote() {
        phase = new GamePhase(game, 1, PhaseType.DAY);
        phase.setId(phaseId);
        when(votes.findByPhaseId(phaseId)).thenReturn(List.of(new Vote(phase, voter, target)));

        assertThat(service.closeVoting(phase)).contains(target.getId());
    }

    @Test
    @DisplayName("should return target with most votes on closeVoting")
    void shouldReturnLeaderOnCloseVoting() {
        phase = new GamePhase(game, 1, PhaseType.DAY);
        phase.setId(phaseId);
        Player otherTarget = givenPlayer(game, PlayerRole.CIVILIAN, PlayerStatus.ALIVE);
        Player voter2 = givenPlayer(game, PlayerRole.CIVILIAN, PlayerStatus.ALIVE);
        Player voter3 = givenPlayer(game, PlayerRole.MAFIA, PlayerStatus.ALIVE);

        when(votes.findByPhaseId(phaseId)).thenReturn(List.of(
                new Vote(phase, voter, target),
                new Vote(phase, voter2, target),
                new Vote(phase, voter3, otherTarget)
        ));

        assertThat(service.closeVoting(phase)).contains(target.getId());
    }

    @Test
    @DisplayName("should return empty on closeVoting when top votes are tied")
    void shouldReturnEmptyOnCloseVotingWhenTie() {
        phase = new GamePhase(game, 1, PhaseType.DAY);
        phase.setId(phaseId);
        Player otherTarget = givenPlayer(game, PlayerRole.CIVILIAN, PlayerStatus.ALIVE);
        Player voter2 = givenPlayer(game, PlayerRole.CIVILIAN, PlayerStatus.ALIVE);

        when(votes.findByPhaseId(phaseId)).thenReturn(List.of(
                new Vote(phase, voter, target),
                new Vote(phase, voter2, otherTarget)
        ));

        assertThat(service.closeVoting(phase)).isEmpty();
    }

    @Test
    @DisplayName("should ignore lower-ranked targets on closeVoting when two leaders are tied")
    void shouldReturnEmptyOnCloseVotingWhenLeadersTiedAndThirdTargetBehind() {
        phase = new GamePhase(game, 1, PhaseType.DAY);
        phase.setId(phaseId);
        Player secondTarget = givenPlayer(game, PlayerRole.CIVILIAN, PlayerStatus.ALIVE);
        Player thirdTarget = givenPlayer(game, PlayerRole.CIVILIAN, PlayerStatus.ALIVE);
        Player voter2 = givenPlayer(game, PlayerRole.CIVILIAN, PlayerStatus.ALIVE);
        Player voter3 = givenPlayer(game, PlayerRole.CIVILIAN, PlayerStatus.ALIVE);
        Player voter4 = givenPlayer(game, PlayerRole.CIVILIAN, PlayerStatus.ALIVE);
        Player mafiaVoter = givenPlayer(game, PlayerRole.MAFIA, PlayerStatus.ALIVE);

        when(votes.findByPhaseId(phaseId)).thenReturn(List.of(
                new Vote(phase, voter, target),
                new Vote(phase, voter2, target),
                new Vote(phase, voter3, secondTarget),
                new Vote(phase, voter4, secondTarget),
                new Vote(phase, mafiaVoter, thirdTarget)
        ));

        assertThat(service.closeVoting(phase)).isEmpty();
    }
}
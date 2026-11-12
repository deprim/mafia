package com.mafiagame.service;

import com.mafiagame.dto.EliminationResult;
import com.mafiagame.model.Game;
import com.mafiagame.model.GamePhase;
import com.mafiagame.model.GameResult;
import com.mafiagame.repository.GameRepository;
import com.mafiagame.repository.GameResultRepository;
import com.mafiagame.utils.enums.GameStatus;
import com.mafiagame.utils.enums.PhaseType;
import com.mafiagame.utils.enums.PlayerRole;
import com.mafiagame.utils.enums.Team;
import com.mafiagame.websocket.GameBroadcaster;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("GameLifecycleService")
@ExtendWith(MockitoExtension.class)
class GameLifecycleServiceTests {

    private static final String TOKEN = "sessionToken";

    @Mock private GameRepository gameRepository;
    @Mock private GameResultRepository gameResultRepository;
    @Mock private GameBroadcaster broadcaster;
    @Mock private GamePhaseService gamePhaseService;
    @Mock private PlayerService playerService;
    @Mock private VotingService voting;

    @InjectMocks
    private GameLifecycleService service;

    private UUID gameId;
    private Game game;

    @BeforeEach
    void setUp() {
        gameId = UUID.randomUUID();
        game = new Game(1, "test");
        game.setId(gameId);
    }

    private void givenStartedGame() {
        game.setStatus(GameStatus.STARTED);
        when(gameRepository.findByIdForUpdate(gameId)).thenReturn(Optional.of(game));
    }

    private GamePhase givenCurrentPhase(PhaseType type, int round) {
        GamePhase phase = new GamePhase(game, round, type);
        when(gamePhaseService.currentPhase(game)).thenReturn(phase);
        return phase;
    }

    private GamePhase givenReadyPhase(PhaseType type, int round) {
        GamePhase phase = givenCurrentPhase(type, round);
        when(voting.allEligibleVotersHaveVoted(phase)).thenReturn(true);
        return phase;
    }

    private void givenAlive(long mafia, long civilians) {
        when(playerService.countAlive(gameId, PlayerRole.MAFIA)).thenReturn(mafia);
        when(playerService.countAlive(gameId, PlayerRole.CIVILIAN)).thenReturn(civilians);
    }

    private void assertGameFinishedWith(Team winner) {
        assertThat(game.getStatus()).isEqualTo(GameStatus.FINISHED);
        verify(gameRepository).save(game);

        ArgumentCaptor<GameResult> captor = ArgumentCaptor.forClass(GameResult.class);
        verify(gameResultRepository).save(captor.capture());
        assertThat(captor.getValue().getWinningTeam()).isEqualTo(winner);   // adjust getter name if needed

        String winningTeam = winner == Team.MAFIA ? "Mafia" : "Civilian";
        verify(broadcaster).toGameAndChatAfterCommit(
                gameId,
                "GAME_ENDED",
                Map.of("winner", winner),
                "The " + winningTeam + " team has won the game."
        );
    }

    @Test
    @DisplayName("should start game, assign roles and enter first night")
    void shouldStartGameAssignRolesAndEnterFirstNight() {
        game.setStatus(GameStatus.WAITING);
        when(gameRepository.findByIdForUpdate(gameId)).thenReturn(Optional.of(game));

        service.startGame(gameId, TOKEN);

        assertThat(game.getStatus()).isEqualTo(GameStatus.STARTED);
        verify(playerService).requireHost(gameId, TOKEN);
        verify(playerService).assignRoles(gameId, game.getMafiaCount());
        verify(gameRepository).save(game);
        verify(broadcaster).toGameAndChatAfterCommit(
                gameId,
                "GAME_STARTED",
                Map.of(),
                "The game has started."
        );
        verify(gamePhaseService).enterPhase(game, PhaseType.NIGHT, 1);
    }

    @Test
    @DisplayName("should throw CONFLICT on game start if game is not WAITING")
    void shouldThrowConflictOnStartWhenGameNotWaiting() {
        game.setStatus(GameStatus.STARTED);
        when(gameRepository.findByIdForUpdate(gameId)).thenReturn(Optional.of(game));

        assertThatThrownBy(() -> service.startGame(gameId, TOKEN))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.CONFLICT);

        verify(playerService, never()).assignRoles(any(), anyInt());
        verify(gamePhaseService, never()).enterPhase(any(), any(), anyInt());
        verifyNoInteractions(broadcaster);
    }

    @Test
    @DisplayName("should propagate exception on game start if caller is not host")
    void shouldPropagateExceptionOnStartWhenCallerIsNotHost() {
        when(gameRepository.findByIdForUpdate(gameId)).thenReturn(Optional.of(game));
        when(playerService.requireHost(gameId, TOKEN))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> service.startGame(gameId, TOKEN))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.FORBIDDEN);

        verify(playerService, never()).assignRoles(any(), anyInt());
    }

    @Test
    @DisplayName("should throw on game start if game not found")
    void shouldThrowOnStartWhenGameNotFound() {
        when(gameRepository.findByIdForUpdate(gameId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.startGame(gameId, TOKEN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Game not found");
    }

    @Test
    @DisplayName("should throw CONFLICT on advance if game is not STARTED")
    void shouldThrowConflictOnAdvanceWhenGameNotStarted() {
        game.setStatus(GameStatus.WAITING);
        when(gameRepository.findByIdForUpdate(gameId)).thenReturn(Optional.of(game));

        assertThatThrownBy(() -> service.advance(gameId, TOKEN, PhaseType.NIGHT, 1))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.CONFLICT);

        verifyNoInteractions(gamePhaseService, voting);
    }

    @Test
    @DisplayName("should reject stale advance when phase type differs")
    void shouldRejectStaleAdvanceWhenPhaseTypeDiffers() {
        givenStartedGame();
        givenCurrentPhase(PhaseType.DAY, 1);

        assertThatThrownBy(() -> service.advance(gameId, TOKEN, PhaseType.NIGHT, 1))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.CONFLICT);

        verifyNoInteractions(voting);
        verify(gamePhaseService, never()).endPhase(any());
    }

    @Test
    @DisplayName("should reject stale advance when round differs")
    void shouldRejectStaleAdvanceWhenRoundDiffers() {
        givenStartedGame();
        givenCurrentPhase(PhaseType.NIGHT, 2);

        assertThatThrownBy(() -> service.advance(gameId, TOKEN, PhaseType.NIGHT, 1))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.CONFLICT);

        verifyNoInteractions(voting);
    }

    @Test
    @DisplayName("should kill victim, broadcast result and enter DAY of same round after night")
    void shouldKillVictimAndEnterDayAfterNight() {
        givenStartedGame();
        UUID victimId = UUID.randomUUID();
        GamePhase night = givenReadyPhase(PhaseType.NIGHT, 1);
        when(voting.closeVoting(night)).thenReturn(Optional.of(victimId));
        givenAlive(1, 3);

        service.advance(gameId, TOKEN, PhaseType.NIGHT, 1);

        verify(playerService).kill(victimId, night);
        verify(broadcaster).toGameAndChatAfterCommit(
                eq(gameId),
                eq("VOTE_RESULT"),
                any(EliminationResult.class),
                anyString()
        );

        InOrder order = inOrder(gamePhaseService);
        order.verify(gamePhaseService).endPhase(night);
        order.verify(gamePhaseService).enterPhase(game, PhaseType.DAY, 1);

        assertThat(game.getStatus()).isEqualTo(GameStatus.STARTED);
        verifyNoInteractions(gameResultRepository);
    }

    @Test
    @DisplayName("should not kill anyone after night on tie or no votes")
    void shouldNotKillAnyoneAfterNightWhenNoVictim() {
        givenStartedGame();
        GamePhase night = givenReadyPhase(PhaseType.NIGHT, 1);
        when(voting.closeVoting(night)).thenReturn(Optional.empty());
        givenAlive(1, 3);

        service.advance(gameId, TOKEN, PhaseType.NIGHT, 1);

        verify(playerService, never()).kill(any(), any());
        verify(broadcaster).toGameAndChatAfterCommit(
                eq(gameId),
                eq("VOTE_RESULT"),
                any(EliminationResult.class),
                anyString()
        );
        verify(gamePhaseService).enterPhase(game, PhaseType.DAY, 1);
    }

    @Test
    @DisplayName("should finish game with MAFIA win after night when mafia >= civilians")
    void shouldFinishGameWithMafiaWinAfterNight() {
        givenStartedGame();
        UUID victimId = UUID.randomUUID();
        GamePhase night = givenReadyPhase(PhaseType.NIGHT, 1);
        when(voting.closeVoting(night)).thenReturn(Optional.of(victimId));
        givenAlive(2, 2);

        service.advance(gameId, TOKEN, PhaseType.NIGHT, 1);

        assertGameFinishedWith(Team.MAFIA);
        verify(gamePhaseService).endPhase(night);
        verify(gamePhaseService, never()).enterPhase(any(), any(), anyInt());
    }

    @Test
    @DisplayName("should execute player and enter NIGHT of next round after day")
    void shouldExecutePlayerAndEnterNextNightAfterDay() {
        givenStartedGame();
        UUID executedId = UUID.randomUUID();
        GamePhase day = givenReadyPhase(PhaseType.DAY, 2);
        when(voting.closeVoting(day)).thenReturn(Optional.of(executedId));
        givenAlive(1, 3);

        service.advance(gameId, TOKEN, PhaseType.DAY, 2);

        verify(playerService).kill(executedId, day);
        verify(broadcaster).toGameAndChatAfterCommit(
                eq(gameId),
                eq("VOTE_RESULT"),
                any(EliminationResult.class),
                anyString()
        );

        InOrder order = inOrder(gamePhaseService);
        order.verify(gamePhaseService).endPhase(day);
        order.verify(gamePhaseService).enterPhase(game, PhaseType.NIGHT, 3);
    }

    @Test
    @DisplayName("should not execute anyone after day on tie or no votes")
    void shouldNotExecuteAnyoneAfterDayWhenNoVotes() {
        givenStartedGame();
        GamePhase day = givenReadyPhase(PhaseType.DAY, 1);
        when(voting.closeVoting(day)).thenReturn(Optional.empty());
        givenAlive(1, 3);

        service.advance(gameId, TOKEN, PhaseType.DAY, 1);

        verify(playerService, never()).kill(any(), any());
        verify(gamePhaseService).enterPhase(game, PhaseType.NIGHT, 2);
    }

    @Test
    @DisplayName("should finish game with CIVILIAN win after day when no mafia left")
    void shouldFinishGameWithCivilianWinAfterDay() {
        givenStartedGame();
        UUID executedId = UUID.randomUUID();
        GamePhase day = givenReadyPhase(PhaseType.DAY, 1);
        when(voting.closeVoting(day)).thenReturn(Optional.of(executedId));
        givenAlive(0, 3);

        service.advance(gameId, TOKEN, PhaseType.DAY, 1);

        assertGameFinishedWith(Team.CIVILIAN);
        verify(gamePhaseService, never()).enterPhase(any(), any(), anyInt());
    }

    @Test
    @DisplayName("should finish game with MAFIA win after day when mafia >= civilians")
    void shouldFinishGameWithMafiaWinAfterDay() {
        givenStartedGame();
        GamePhase day = givenReadyPhase(PhaseType.DAY, 1);
        when(voting.closeVoting(day)).thenReturn(Optional.empty());
        givenAlive(2, 2);

        service.advance(gameId, TOKEN, PhaseType.DAY, 1);

        assertGameFinishedWith(Team.MAFIA);
    }
}
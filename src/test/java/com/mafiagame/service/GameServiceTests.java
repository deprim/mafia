package com.mafiagame.service;

import com.mafiagame.dto.CreateGameRequest;
import com.mafiagame.dto.CurrentPhaseResponse;
import com.mafiagame.dto.GameCreationResult;
import com.mafiagame.dto.GameResultResponse;
import com.mafiagame.dto.GameStateResponse;
import com.mafiagame.dto.JoinGameRequest;
import com.mafiagame.dto.PlayerJoinResult;
import com.mafiagame.dto.PlayerResponse;
import com.mafiagame.exception.GameAccessDeniedException;
import com.mafiagame.exception.GameConflictException;
import com.mafiagame.exception.InvalidSessionException;
import com.mafiagame.exception.ResourceNotFoundException;
import com.mafiagame.model.Game;
import com.mafiagame.model.GamePhase;
import com.mafiagame.model.GameResult;
import com.mafiagame.model.Player;
import com.mafiagame.repository.GameRepository;
import com.mafiagame.repository.GameResultRepository;
import com.mafiagame.repository.PlayerRepository;
import com.mafiagame.utils.SessionTokenGenerator;
import com.mafiagame.utils.enums.GameStatus;
import com.mafiagame.utils.enums.PhaseType;
import com.mafiagame.utils.enums.PlayerStatus;
import com.mafiagame.utils.enums.Team;
import com.mafiagame.websocket.GameBroadcaster;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@DisplayName("GameService")
@ExtendWith(MockitoExtension.class)
class GameServiceTests {

    private static final String TOKEN = "sessionToken";

    @Mock private GameRepository gameRepository;
    @Mock private PlayerRepository playerRepository;
    @Mock private GameResultRepository gameResultRepository;
    @Mock private GamePhaseService gamePhaseService;
    @Mock private GameBroadcaster broadcaster;
    @Mock private ChatHistoryService chatHistory;

    @InjectMocks
    private GameService service;

    private UUID gameId;
    private Game game;

    @BeforeEach
    void setUp() {
        gameId = UUID.randomUUID();
        game = new Game(1, "ABCDEFGH");
        game.setId(gameId);
    }

    private Player newPlayer(Game playerGame, String nickname, boolean host) {
        Player player = new Player(playerGame, nickname, host, SessionTokenGenerator.hashToken(TOKEN));
        player.setId(UUID.randomUUID());
        return player;
    }

    private void givenHost() {
        Player host = newPlayer(game, "host", true);
        when(playerRepository.findBySessionTokenHash(SessionTokenGenerator.hashToken(TOKEN)))
                .thenReturn(Optional.of(host));
    }

    @Test
    @DisplayName("should create waiting game and save host")
    void shouldCreateGameAndHostOnCreateGame() {
        GameCreationResult result = service.createGame(new CreateGameRequest("host"));

        assertThat(result.game().status()).isEqualTo(GameStatus.WAITING);
        assertThat(result.game().mafiaCount()).isEqualTo(1);
        assertThat(result.game().joinCode()).isNotBlank();
        assertThat(result.rawToken()).isNotBlank();
        verify(gameRepository).save(any());
        verify(playerRepository).save(any());
        verify(chatHistory).saveSystemMessage(result.game().id(), "host created the room.");
    }

    @Test
    @DisplayName("should save player and broadcast join on joinGame")
    void shouldSavePlayerAndBroadcastOnJoinGame() {
        when(gameRepository.findByJoinCode(game.getJoinCode())).thenReturn(Optional.of(game));
        when(playerRepository.findByGameId(gameId)).thenReturn(List.of());

        PlayerJoinResult result = service.joinGame(new JoinGameRequest(game.getJoinCode(), "newcomer"));

        assertThat(result.gameId()).isEqualTo(gameId);
        assertThat(result.player().nickname()).isEqualTo("newcomer");
        assertThat(result.player().host()).isFalse();
        assertThat(result.player().status()).isEqualTo(PlayerStatus.ALIVE);
        assertThat(result.rawToken()).isNotBlank();
        verify(playerRepository).save(any());
        verify(broadcaster).toGameAndChatAfterCommit(
                gameId, "PLAYER_JOINED", result.player(), "newcomer joined the room."
        );
    }

    @Test
    @DisplayName("should throw NOT_FOUND on joinGame when room does not exist")
    void shouldThrowNotFoundOnJoinGameWhenRoomNotFound() {
        when(gameRepository.findByJoinCode(game.getJoinCode())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.joinGame(new JoinGameRequest(game.getJoinCode(), "player")))
                .matches(error -> error instanceof ResourceNotFoundException);

        verifyNoInteractions(playerRepository, broadcaster);
    }

    @Test
    @DisplayName("should reject joining a game that is no longer waiting")
    void shouldRejectJoinGameWhenNotWaiting() {
        when(gameRepository.findByJoinCode(game.getJoinCode())).thenReturn(Optional.of(game));

        game.setStatus(GameStatus.STARTED);
        assertThatThrownBy(() -> service.joinGame(new JoinGameRequest(game.getJoinCode(), "player")))
                .matches(error -> error instanceof GameConflictException);

        game.setStatus(GameStatus.FINISHED);
        assertThatThrownBy(() -> service.joinGame(new JoinGameRequest(game.getJoinCode(), "player")))
                .matches(error -> error instanceof GameConflictException);

        verifyNoInteractions(playerRepository, broadcaster);
    }

    @Test
    @DisplayName("should reject duplicate nickname regardless of case on joinGame")
    void shouldRejectDuplicateNicknameOnJoinGame() {
        when(gameRepository.findByJoinCode(game.getJoinCode())).thenReturn(Optional.of(game));
        when(playerRepository.findByGameId(gameId)).thenReturn(List.of(newPlayer(game, "Player", false)));

        assertThatThrownBy(() -> service.joinGame(new JoinGameRequest(game.getJoinCode(), "Player")))
                .matches(error -> error instanceof GameConflictException);
        assertThatThrownBy(() -> service.joinGame(new JoinGameRequest(game.getJoinCode(), "pLaYeR")))
                .matches(error -> error instanceof GameConflictException);

        verify(playerRepository, never()).save(any());
        verifyNoInteractions(broadcaster);
    }

    @Test
    @DisplayName("should return waiting room details and players without phase or result on getRoomState")
    void shouldReturnWaitingRoomStateOnGetRoomState() {
        Player host = newPlayer(game, "host", true);
        Player player = newPlayer(game, "player", false);
        player.setStatus(PlayerStatus.DEAD);
        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));
        when(playerRepository.findByGameId(gameId)).thenReturn(List.of(host, player));
        when(gamePhaseService.findCurrentPhase(game)).thenReturn(Optional.empty());
        when(gameResultRepository.findByGameId(gameId)).thenReturn(Optional.empty());

        GameStateResponse result = service.getRoomState(gameId);

        assertThat(result).isEqualTo(new GameStateResponse(
                gameId, game.getJoinCode(), 1, GameStatus.WAITING,
                List.of(
                        new PlayerResponse(host.getId(), "host", true, PlayerStatus.ALIVE),
                        new PlayerResponse(player.getId(), "player", false, PlayerStatus.DEAD)
                ),
                null, null
        ));
    }

    @Test
    @DisplayName("should include current phase on getRoomState when game has started")
    void shouldIncludeCurrentPhaseOnGetRoomState() {
        game.setStatus(GameStatus.STARTED);
        GamePhase phase = new GamePhase(game, 2, PhaseType.DAY);
        phase.setId(UUID.randomUUID());
        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));
        when(playerRepository.findByGameId(gameId)).thenReturn(List.of());
        when(gamePhaseService.findCurrentPhase(game)).thenReturn(Optional.of(phase));
        when(gameResultRepository.findByGameId(gameId)).thenReturn(Optional.empty());

        GameStateResponse result = service.getRoomState(gameId);

        assertThat(result.status()).isEqualTo(GameStatus.STARTED);
        assertThat(result.players()).isEmpty();
        assertThat(result.currentPhase()).isEqualTo(new CurrentPhaseResponse(phase.getId(), PhaseType.DAY, 2));
        assertThat(result.result()).isNull();
    }

    @Test
    @DisplayName("should include game result on getRoomState when game has finished")
    void shouldIncludeResultOnGetRoomState() {
        game.setStatus(GameStatus.FINISHED);
        GameResult gameResult = new GameResult(game, Team.CIVILIAN);
        gameResult.setFinishedAt(Instant.parse("2026-01-01T12:00:00Z"));
        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));
        when(playerRepository.findByGameId(gameId)).thenReturn(List.of());
        when(gamePhaseService.findCurrentPhase(game)).thenReturn(Optional.empty());
        when(gameResultRepository.findByGameId(gameId)).thenReturn(Optional.of(gameResult));

        GameStateResponse result = service.getRoomState(gameId);

        assertThat(result.status()).isEqualTo(GameStatus.FINISHED);
        assertThat(result.currentPhase()).isNull();
        assertThat(result.result()).isEqualTo(new GameResultResponse(
                gameId, Team.CIVILIAN, gameResult.getFinishedAt()
        ));
    }

    @Test
    @DisplayName("should throw NOT_FOUND on getRoomState when room does not exist")
    void shouldThrowNotFoundOnGetRoomStateWhenRoomNotFound() {
        when(gameRepository.findById(gameId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRoomState(gameId))
                .matches(error -> error instanceof ResourceNotFoundException);

        verifyNoInteractions(playerRepository, gamePhaseService, gameResultRepository);
    }

    @Test
    @DisplayName("should return winning team and finish time on getResult")
    void shouldReturnGameResultOnGetResult() {
        GameResult gameResult = new GameResult(game, Team.MAFIA);
        gameResult.setFinishedAt(Instant.parse("2026-01-01T12:00:00Z"));
        when(gameResultRepository.findByGameId(gameId)).thenReturn(Optional.of(gameResult));

        assertThat(service.getResult(gameId)).isEqualTo(new GameResultResponse(
                gameId, Team.MAFIA, gameResult.getFinishedAt()
        ));
    }

    @Test
    @DisplayName("should throw NOT_FOUND on getResult when result does not exist")
    void shouldThrowNotFoundOnGetResultWhenResultNotFound() {
        when(gameResultRepository.findByGameId(gameId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getResult(gameId))
                .matches(error -> error instanceof ResourceNotFoundException);
    }

    @Test
    @DisplayName("should delete players and waiting game and broadcast room closure on deleteGame")
    void shouldDeletePlayersAndGameAndBroadcastOnDeleteGame() {
        Player host = newPlayer(game, "host", true);
        List<Player> players = List.of(host, newPlayer(game, "player", false));
        when(playerRepository.findBySessionTokenHash(SessionTokenGenerator.hashToken(TOKEN)))
                .thenReturn(Optional.of(host));
        when(playerRepository.findByGameId(gameId)).thenReturn(players);

        service.deleteGame(gameId, TOKEN);

        verify(playerRepository).deleteAll(players);
        verify(gameRepository).delete(game);
        verify(broadcaster).toGameAfterCommit(gameId, "ROOM_CLOSED", Map.of("gameId", gameId));
    }

    @Test
    @DisplayName("should reject missing or blank token on deleteGame")
    void shouldRejectMissingTokenOnDeleteGame() {
        assertThatThrownBy(() -> service.deleteGame(gameId, null))
                .matches(error -> error instanceof InvalidSessionException);
        assertThatThrownBy(() -> service.deleteGame(gameId, ""))
                .matches(error -> error instanceof InvalidSessionException);
        assertThatThrownBy(() -> service.deleteGame(gameId, " "))
                .matches(error -> error instanceof InvalidSessionException);

        verifyNoInteractions(playerRepository, gameRepository, broadcaster);
    }

    @Test
    @DisplayName("should throw UNAUTHORIZED on deleteGame when token does not match any player")
    void shouldThrowUnauthorizedOnDeleteGameWhenTokenInvalid() {
        when(playerRepository.findBySessionTokenHash(SessionTokenGenerator.hashToken(TOKEN)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteGame(gameId, TOKEN))
                .matches(error -> error instanceof InvalidSessionException);

        verify(playerRepository, never()).deleteAll(any());
        verifyNoInteractions(gameRepository, broadcaster);
    }

    @Test
    @DisplayName("should throw FORBIDDEN on deleteGame when player is not host")
    void shouldThrowForbiddenOnDeleteGameWhenPlayerNotHost() {
        Player player = newPlayer(game, "player", false);
        when(playerRepository.findBySessionTokenHash(SessionTokenGenerator.hashToken(TOKEN)))
                .thenReturn(Optional.of(player));

        assertThatThrownBy(() -> service.deleteGame(gameId, TOKEN))
                .matches(error -> error instanceof GameAccessDeniedException);

        verify(playerRepository, never()).deleteAll(any());
        verifyNoInteractions(gameRepository, broadcaster);
    }

    @Test
    @DisplayName("should throw FORBIDDEN on deleteGame when host belongs to another game")
    void shouldThrowForbiddenOnDeleteGameWhenHostFromAnotherGame() {
        Game otherGame = new Game(1, "other");
        otherGame.setId(UUID.randomUUID());
        Player outsider = newPlayer(otherGame, "host", true);
        when(playerRepository.findBySessionTokenHash(SessionTokenGenerator.hashToken(TOKEN)))
                .thenReturn(Optional.of(outsider));

        assertThatThrownBy(() -> service.deleteGame(gameId, TOKEN))
                .matches(error -> error instanceof GameAccessDeniedException);

        verify(playerRepository, never()).deleteAll(any());
        verifyNoInteractions(gameRepository, broadcaster);
    }

    @Test
    @DisplayName("should reject deleting a game that is no longer waiting")
    void shouldRejectDeleteGameWhenNotWaiting() {
        givenHost();

        game.setStatus(GameStatus.STARTED);
        assertThatThrownBy(() -> service.deleteGame(gameId, TOKEN))
                .matches(error -> error instanceof GameConflictException);

        game.setStatus(GameStatus.FINISHED);
        assertThatThrownBy(() -> service.deleteGame(gameId, TOKEN))
                .matches(error -> error instanceof GameConflictException);

        verify(playerRepository, never()).deleteAll(any());
        verifyNoInteractions(gameRepository, broadcaster);
    }
}

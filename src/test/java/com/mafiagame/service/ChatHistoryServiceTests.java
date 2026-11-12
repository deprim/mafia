package com.mafiagame.service;

import com.mafiagame.dto.ChatMessage;
import com.mafiagame.dto.PersistedChatMessage;
import com.mafiagame.exception.GameAccessDeniedException;
import com.mafiagame.exception.InvalidSessionException;
import com.mafiagame.model.ChatMessageRecord;
import com.mafiagame.model.Game;
import com.mafiagame.model.Player;
import com.mafiagame.repository.ChatMessageRepository;
import com.mafiagame.repository.GameRepository;
import com.mafiagame.repository.PlayerRepository;
import com.mafiagame.utils.SessionTokenGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@DisplayName("ChatHistoryService")
@ExtendWith(MockitoExtension.class)
class ChatHistoryServiceTests {

    private static final String TOKEN = "sessionToken";

    @Mock private ChatMessageRepository messageRepository;
    @Mock private GameRepository gameRepository;
    @Mock private PlayerRepository playerRepository;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ChatHistoryService service;

    private UUID gameId;
    private Game game;

    @BeforeEach
    void setUp() {
        gameId = UUID.randomUUID();
        game = new Game(1, "test");
        game.setId(gameId);
    }

    private void givenRoomPlayer() {
        Player player = new Player(game, "player", false, SessionTokenGenerator.hashToken(TOKEN));
        when(playerRepository.findBySessionTokenHash(SessionTokenGenerator.hashToken(TOKEN)))
                .thenReturn(Optional.of(player));
    }

    @Test
    @DisplayName("should serialize and persist player message with database id and time on save")
    void shouldPersistPlayerMessageOnSave(@Mock ChatMessageRecord saved) {
        Instant createdAt = Instant.parse("2026-01-01T12:00:00Z");
        when(saved.getId()).thenReturn(1L);
        when(saved.getCreatedAt()).thenReturn(createdAt);
        when(gameRepository.getReferenceById(gameId)).thenReturn(game);
        when(messageRepository.save(any())).thenReturn(saved);

        PersistedChatMessage result = service.save(gameId, new ChatMessage("hello", "player"));

        assertThat(result).isEqualTo(new PersistedChatMessage(1L, createdAt, "hello", "player", false));
        verify(messageRepository).save(any());
    }

    @Test
    @DisplayName("should persist system message with Game username and system flag on saveSystemMessage")
    void shouldPersistSystemMessageOnSaveSystemMessage(@Mock ChatMessageRecord saved) {
        Instant createdAt = Instant.parse("2026-01-01T12:00:00Z");
        when(saved.getId()).thenReturn(2L);
        when(saved.getCreatedAt()).thenReturn(createdAt);
        when(gameRepository.getReferenceById(gameId)).thenReturn(game);
        when(messageRepository.save(any())).thenReturn(saved);

        PersistedChatMessage result = service.saveSystemMessage(gameId, "Game started");

        assertThat(result).isEqualTo(new PersistedChatMessage(2L, createdAt, "Game started", "Game", true));
        verify(messageRepository).save(any());
    }

    @Test
    @DisplayName("should throw on save when message cannot be serialized")
    void shouldThrowOnSaveWhenSerializationFails(@Mock JacksonException cause) {
        doThrow(cause).when(objectMapper).writeValueAsString(any());

        assertThatThrownBy(() -> service.save(gameId, new ChatMessage("hello", "player")))
                .matches(error -> error instanceof IllegalStateException)
                .hasMessage("Could not serialize chat message for game " + gameId)
                .hasCause(cause);

        verifyNoInteractions(messageRepository, gameRepository);
    }

    @Test
    @DisplayName("should throw on saveSystemMessage when message cannot be serialized")
    void shouldThrowOnSaveSystemMessageWhenSerializationFails(@Mock JacksonException cause) {
        doThrow(cause).when(objectMapper).writeValueAsString(any());

        assertThatThrownBy(() -> service.saveSystemMessage(gameId, "Game started"))
                .matches(error -> error instanceof IllegalStateException)
                .hasMessage("Could not serialize chat message for game " + gameId)
                .hasCause(cause);

        verifyNoInteractions(messageRepository, gameRepository);
    }

    @Test
    @DisplayName("should return recent player and system messages in chronological order")
    void shouldReturnMessagesInChronologicalOrderOnRecent(
            @Mock ChatMessageRecord first, @Mock ChatMessageRecord second) {
        givenRoomPlayer();
        Instant firstTime = Instant.parse("2026-01-01T12:00:00Z");
        Instant secondTime = firstTime.plusSeconds(1);
        when(first.getId()).thenReturn(1L);
        when(first.getCreatedAt()).thenReturn(firstTime);
        when(second.getId()).thenReturn(2L);
        when(second.getCreatedAt()).thenReturn(secondTime);
        when(first.getPayload()).thenReturn("{\"message\":\"hello\",\"username\":\"player\",\"system\":false}");
        when(second.getPayload()).thenReturn("{\"message\":\"Game started\",\"username\":\"Game\",\"system\":true}");
        List<ChatMessageRecord> storedMessages = List.of(second, first);
        when(messageRepository.findTop100ByGameIdOrderByIdDesc(gameId)).thenReturn(storedMessages);

        List<PersistedChatMessage> result = service.recent(gameId, TOKEN);

        assertThat(result).containsExactly(
                new PersistedChatMessage(1L, firstTime, "hello", "player", false),
                new PersistedChatMessage(2L, secondTime, "Game started", "Game", true)
        );
        assertThat(storedMessages).containsExactly(second, first);
    }

    @Test
    @DisplayName("should return empty history on recent when room has no messages")
    void shouldReturnEmptyOnRecentWhenNoMessages() {
        givenRoomPlayer();
        when(messageRepository.findTop100ByGameIdOrderByIdDesc(gameId)).thenReturn(List.of());

        assertThat(service.recent(gameId, TOKEN)).isEmpty();
    }

    @Test
    @DisplayName("should reject missing or blank token on recent")
    void shouldRejectMissingTokenOnRecent() {
        assertThatThrownBy(() -> service.recent(gameId, null))
                .matches(error -> error instanceof InvalidSessionException);
        assertThatThrownBy(() -> service.recent(gameId, ""))
                .matches(error -> error instanceof InvalidSessionException);
        assertThatThrownBy(() -> service.recent(gameId, " "))
                .matches(error -> error instanceof InvalidSessionException);

        verifyNoInteractions(playerRepository, messageRepository);
    }

    @Test
    @DisplayName("should throw UNAUTHORIZED on recent when token does not match any player")
    void shouldThrowUnauthorizedOnRecentWhenTokenInvalid() {
        when(playerRepository.findBySessionTokenHash(SessionTokenGenerator.hashToken(TOKEN)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recent(gameId, TOKEN))
                .matches(error -> error instanceof InvalidSessionException);

        verifyNoInteractions(messageRepository);
    }

    @Test
    @DisplayName("should throw FORBIDDEN on recent when player belongs to another room")
    void shouldThrowForbiddenOnRecentWhenPlayerFromAnotherRoom() {
        Game otherGame = new Game(1, "other");
        otherGame.setId(UUID.randomUUID());
        Player outsider = new Player(otherGame, "player", false, SessionTokenGenerator.hashToken(TOKEN));
        when(playerRepository.findBySessionTokenHash(SessionTokenGenerator.hashToken(TOKEN)))
                .thenReturn(Optional.of(outsider));

        assertThatThrownBy(() -> service.recent(gameId, TOKEN))
                .matches(error -> error instanceof GameAccessDeniedException);

        verifyNoInteractions(messageRepository);
    }

    @Test
    @DisplayName("should throw on recent when stored message cannot be deserialized")
    void shouldThrowOnRecentWhenStoredMessageInvalid() {
        givenRoomPlayer();
        ChatMessageRecord stored = new ChatMessageRecord(game, "invalid-payload");
        when(messageRepository.findTop100ByGameIdOrderByIdDesc(gameId)).thenReturn(List.of(stored));

        assertThatThrownBy(() -> service.recent(gameId, TOKEN))
                .matches(error -> error instanceof IllegalStateException)
                .hasMessage("Stored chat message is invalid");
    }
}

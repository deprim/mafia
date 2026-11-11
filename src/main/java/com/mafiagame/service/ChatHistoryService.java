package com.mafiagame.service;

import com.mafiagame.dto.ChatMessage;
import com.mafiagame.dto.ChatMessagePayload;
import com.mafiagame.dto.PersistedChatMessage;
import com.mafiagame.model.ChatMessageRecord;
import com.mafiagame.model.Player;
import com.mafiagame.repository.ChatMessageRepository;
import com.mafiagame.repository.GameRepository;
import com.mafiagame.repository.PlayerRepository;
import com.mafiagame.utils.SessionTokenGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Transactional(readOnly = true)
public class ChatHistoryService {
    private final ChatMessageRepository messageRepository;
    private final GameRepository gameRepository;
    private final PlayerRepository playerRepository;
    private final ObjectMapper objectMapper;

    public ChatHistoryService(
            ChatMessageRepository messageRepository,
            GameRepository gameRepository,
            PlayerRepository playerRepository,
            ObjectMapper objectMapper
    ) {
        this.messageRepository = messageRepository;
        this.gameRepository = gameRepository;
        this.playerRepository = playerRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PersistedChatMessage save(UUID gameId, ChatMessage message) {
        return save(gameId, new ChatMessagePayload(message.message(), message.username(), false));
    }

    @Transactional
    public PersistedChatMessage saveSystemMessage(UUID gameId, String message) {
        return save(gameId, new ChatMessagePayload(message, "Game", true));
    }

    private PersistedChatMessage save(UUID gameId, ChatMessagePayload message) {
        try {
            String payload = objectMapper.writeValueAsString(message);
            ChatMessageRecord saved = messageRepository.save(new ChatMessageRecord(
                    gameRepository.getReferenceById(gameId),
                    payload
            ));
            return toResponse(saved, message);
        } catch (JacksonException e) {
            throw new IllegalStateException("Could not serialize chat message for game " + gameId, e);
        }
    }

    public List<PersistedChatMessage> recent(UUID gameId, String rawToken) {
        requireRoomPlayer(gameId, rawToken);
        List<ChatMessageRecord> messages = new ArrayList<>(
                messageRepository.findTop100ByGameIdOrderByIdDesc(gameId)
        );
        Collections.reverse(messages);
        log.debug("Chat history for game {} loaded: {}", gameId, messages.size() + " messages");
        return messages.stream()
                .map(this::toResponse)
                .toList();
    }

    private Player requireRoomPlayer(UUID gameId, String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A session token is required");
        }
        Player player = playerRepository.findBySessionTokenHash(SessionTokenGenerator.hashToken(rawToken))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid session token"));
        if (!player.getGame().getId().equals(gameId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Player is not part of this room");
        }
        return player;
    }

    private PersistedChatMessage toResponse(ChatMessageRecord saved) {
        try {
            ChatMessagePayload message = objectMapper.readValue(saved.getPayload(), ChatMessagePayload.class);
            return toResponse(saved, message);
        } catch (JacksonException e) {
            throw new IllegalStateException("Stored chat message is invalid", e);
        }
    }

    private PersistedChatMessage toResponse(ChatMessageRecord saved, ChatMessagePayload message) {
        return new PersistedChatMessage(
                saved.getId(),
                saved.getCreatedAt(),
                message.getMessage(),
                message.getUsername(),
                message.isSystem()
        );
    }
}

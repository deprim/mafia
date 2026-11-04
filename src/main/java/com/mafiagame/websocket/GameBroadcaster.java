package com.mafiagame.websocket;

import com.mafiagame.dto.GameEvent;
import com.mafiagame.dto.PersistedChatMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;


@Component
@RequiredArgsConstructor
public class GameBroadcaster {
    private final SimpMessagingTemplate messaging;

    /** Everyone in the game */
    public void toGame(UUID gameId, String type, Object data) {
        messaging.convertAndSend("/game-session/game/" + gameId, new GameEvent(type, data));
    }

    public void toChat(UUID gameId, PersistedChatMessage message) {
        messaging.convertAndSend(
                "/game-session/game/" + gameId,
                new GameEvent("CHAT", message)
        );
    }

    /** Mafia team only */
    public void toMafia(UUID gameId, String type, Object data) {
        messaging.convertAndSend("/game-session/game/" + gameId + "/mafia", new GameEvent(type, data));
    }
}

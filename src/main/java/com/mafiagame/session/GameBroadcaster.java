package com.mafiagame.session;

import com.mafiagame.dto.GameEvent;
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

    /** Mafia team only */
    public void toMafia(UUID gameId, String type, Object data) {
        messaging.convertAndSend("/game-session/game/" + gameId + "/mafia", new GameEvent(type, data));
    }
}

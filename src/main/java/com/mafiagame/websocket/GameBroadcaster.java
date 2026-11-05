package com.mafiagame.websocket;

import com.mafiagame.dto.GameEvent;
import com.mafiagame.dto.PersistedChatMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;


@Component
@RequiredArgsConstructor
public class GameBroadcaster {
    private final SimpMessagingTemplate messaging;

    /** Everyone in the game */
    public void toGame(UUID gameId, String type, Object data) {
        messaging.convertAndSend("/game-session/game/" + gameId, new GameEvent(type, data));
    }

    public void toGameAfterCommit(UUID gameId, String type, Object data) {
        afterCommit(() -> toGame(gameId, type, data));
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

    public void toMafiaAfterCommit(UUID gameId, String type, Object data) {
        afterCommit(() -> toMafia(gameId, type, data));
    }

    private void afterCommit(Runnable notification) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Room notifications require an active transaction");
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notification.run();
            }
        });
    }
}

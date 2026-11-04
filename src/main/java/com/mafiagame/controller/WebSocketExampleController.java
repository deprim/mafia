package com.mafiagame.controller;

import com.mafiagame.dto.ChatMessage;
import com.mafiagame.model.Player;
import com.mafiagame.repository.PlayerRepository;
import com.mafiagame.websocket.PlayerPrincipal;
import com.mafiagame.websocket.GameBroadcaster;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class WebSocketExampleController {
    private final PlayerRepository players;
    private final GameBroadcaster broadcaster;

    @MessageMapping("/{gameId}/send-message")
    public void sendMessage(
            @DestinationVariable UUID gameId,
            ChatMessage message,
            Principal principal
    ) {
        if (!(principal instanceof PlayerPrincipal player) || !player.gameId().equals(gameId)) {
            throw new MessagingException("Player is not part of this room");
        }

        String text = message.message() == null ? "" : message.message().trim();
        if (text.isEmpty() || text.length() > 300) {
            throw new MessagingException("Message must be between 1 and 300 characters");
        }

        String nickname = players.findById(player.playerId())
                .map(Player::getNickname)
                .orElseThrow(() -> new MessagingException("Player session is invalid"));
        broadcaster.toGame(gameId, "CHAT", new ChatMessage(text, nickname));
    }
}

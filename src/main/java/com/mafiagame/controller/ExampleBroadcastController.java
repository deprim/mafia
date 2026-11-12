package com.mafiagame.controller;

import com.mafiagame.exception.GameAccessDeniedException;
import com.mafiagame.exception.InvalidRequestException;
import com.mafiagame.exception.InvalidSessionException;
import com.mafiagame.model.Player;
import com.mafiagame.repository.PlayerRepository;
import com.mafiagame.utils.SessionTokenGenerator;
import com.mafiagame.utils.enums.PlayerRole;
import com.mafiagame.websocket.GameBroadcaster;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/example")
@RequiredArgsConstructor
public class ExampleBroadcastController {

    private final GameBroadcaster broadcaster;
    private final PlayerRepository players;

    @PostMapping("/{gameId}/all")
    public void toAll(
            @PathVariable UUID gameId,
            @RequestHeader(value = "X-Session-Token", required = false) String rawToken,
            @RequestBody Map<String, String> body
    ) {
        requirePlayer(gameId, rawToken, false);
        broadcaster.toGame(gameId, "TEST", requireText(body));
    }

    @PostMapping("/{gameId}/mafia")
    public void toMafia(
            @PathVariable UUID gameId,
            @RequestHeader(value = "X-Session-Token", required = false) String rawToken,
            @RequestBody Map<String, String> body
    ) {
        requirePlayer(gameId, rawToken, true);
        broadcaster.toMafia(gameId, "TEST_MAFIA", requireText(body));
    }

    private Player requirePlayer(UUID gameId, String rawToken, boolean mafiaOnly) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidSessionException("A session token is required");
        }
        Player player = players.findBySessionTokenHash(SessionTokenGenerator.hashToken(rawToken))
                .orElseThrow(() -> new InvalidSessionException("Invalid session token"));
        if (!player.getGame().getId().equals(gameId)) {
            throw new GameAccessDeniedException("Player is not part of this room");
        }
        if (mafiaOnly && player.getRole() != PlayerRole.MAFIA) {
            throw new GameAccessDeniedException("Mafia access is restricted");
        }
        return player;
    }

    private String requireText(Map<String, String> body) {
        String text = body == null ? null : body.get("text");
        if (text == null || text.isBlank() || text.length() > 300) {
            throw new InvalidRequestException("Text must be between 1 and 300 characters");
        }
        return text.trim();
    }
}

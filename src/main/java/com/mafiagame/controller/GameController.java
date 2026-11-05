
package com.mafiagame.controller;

import com.mafiagame.dto.CreateGameRequest;
import com.mafiagame.dto.GameResponse;
import com.mafiagame.dto.PersistedChatMessage;
import com.mafiagame.service.ChatHistoryService;
import com.mafiagame.service.GameLifecycleService;
import com.mafiagame.service.GameService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
public class GameController {
    private final GameService gameService;
    private final GameLifecycleService gameLifecycleService;
    private final ChatHistoryService chatHistoryService;

    @GetMapping("/{id}")
    public ResponseEntity<GameResponse> getGame(
            @PathVariable UUID id) {

        return ResponseEntity.ok(gameService.getGame(id));
    }

    @GetMapping("/{id}/chat")
    public ResponseEntity<java.util.List<PersistedChatMessage>> getRecentChatMessages(
            @PathVariable UUID id,
            @RequestHeader("X-Session-Token") String rawSessionToken
    ) {
        return ResponseEntity.ok(chatHistoryService.recent(id, rawSessionToken));
    }

    @PostMapping
    public ResponseEntity<GameResponse> createGame(
            @RequestBody CreateGameRequest request) {
        var result = gameService.createGame(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("X-Session-Token", result.rawToken())
                .body(result.game());
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<Void> startGame(
            @PathVariable UUID id,
            @RequestHeader("X-Session-Token") String rawSessionToken
    ) {
        gameLifecycleService.startGame(id, rawSessionToken);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGame(
            @PathVariable UUID id,
            @RequestHeader("X-Session-Token") String rawSessionToken
    ) {
        gameService.deleteGame(id, rawSessionToken);
        return ResponseEntity.noContent().build();
    }
}

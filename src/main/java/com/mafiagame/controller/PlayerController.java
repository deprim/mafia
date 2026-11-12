
package com.mafiagame.controller;

import com.mafiagame.dto.JoinGameRequest;
import com.mafiagame.dto.JoinGameResponse;
import com.mafiagame.dto.PlayerDetailsResponse;
import com.mafiagame.service.GameService;
import com.mafiagame.service.PlayerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/players")
@RequiredArgsConstructor
public class PlayerController {
    private final GameService gameService;
    private final PlayerService playerService;

    @GetMapping("/{id}")
    public ResponseEntity<PlayerDetailsResponse> getPlayer(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Session-Token", required = false) String rawSessionToken) {
        return ResponseEntity.ok(playerService.getOwnDetails(id, rawSessionToken));
    }

    @PostMapping
    public ResponseEntity<JoinGameResponse> addPlayer(
            @Valid @RequestBody JoinGameRequest request) {
        var result = gameService.joinGame(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("X-Session-Token", result.rawToken())
                .body(new JoinGameResponse(result.gameId(), result.player()));
    }
}

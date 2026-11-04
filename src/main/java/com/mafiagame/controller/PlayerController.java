
package com.mafiagame.controller;

import com.mafiagame.dto.JoinGameRequest;
import com.mafiagame.dto.JoinGameResponse;
import com.mafiagame.dto.PlayerResponse;
import com.mafiagame.service.GameService;
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

    @GetMapping("/{id}")
    public ResponseEntity<PlayerResponse> getPlayer(
            @PathVariable UUID id) {

        return ResponseEntity.status(
                HttpStatus.NOT_IMPLEMENTED).build();
    }

    @PostMapping
    public ResponseEntity<JoinGameResponse> addPlayer(
            @RequestBody JoinGameRequest request) {
        var result = gameService.joinGame(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("X-Session-Token", result.rawToken())
                .body(new JoinGameResponse(result.gameId(), result.player()));
    }
}

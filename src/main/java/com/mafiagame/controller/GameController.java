
package com.mafiagame.controller;

import com.mafiagame.dto.CreateGameRequest;
import com.mafiagame.dto.GameResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/games")
public class GameController {

    @GetMapping("/{id}")
    public ResponseEntity<GameResponse> getGame(
            @PathVariable UUID id) {

        return ResponseEntity.status(
                HttpStatus.NOT_IMPLEMENTED).build();
    }

    @PostMapping
    public ResponseEntity<GameResponse> createGame(
            @RequestBody CreateGameRequest request) {

        return ResponseEntity.status(
                HttpStatus.NOT_IMPLEMENTED).build();
    }
}


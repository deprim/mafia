
package com.mafiagame.controller;

import com.mafiagame.dto.JoinGameRequest;
import com.mafiagame.dto.PlayerResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/players")
public class PlayerController {

    @GetMapping("/{id}")
    public ResponseEntity<PlayerResponse> getPlayer(
            @PathVariable UUID id) {

        return ResponseEntity.status(
                HttpStatus.NOT_IMPLEMENTED).build();
    }

    @PostMapping
    public ResponseEntity<PlayerResponse> addPlayer(
            @RequestBody JoinGameRequest request) {

        return ResponseEntity.status(
                HttpStatus.NOT_IMPLEMENTED).build();
    }
}

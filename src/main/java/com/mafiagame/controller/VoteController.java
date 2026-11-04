
package com.mafiagame.controller;

import com.mafiagame.dto.CreateVoteRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/votes")
public class VoteController {

    @GetMapping("/{id}")
    public ResponseEntity<Void> getVote(
            @PathVariable UUID id) {

        return ResponseEntity.status(
                HttpStatus.NOT_IMPLEMENTED).build();
    }

    @PostMapping
    public ResponseEntity<Void> createVote(
            @RequestBody CreateVoteRequest request) {

        return ResponseEntity.status(
                HttpStatus.NOT_IMPLEMENTED).build();
    }
}

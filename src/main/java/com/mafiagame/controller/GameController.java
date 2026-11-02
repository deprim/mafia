
package com.mafiagame.controller;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/games")
public class GameController {

    @GetMapping("/{id}")
    public String getGame(@PathVariable Long id) {
        return "Game ID: " + id;
    }

    @PostMapping
    public String createGame() {
        return "Game created successfully";
    }
}

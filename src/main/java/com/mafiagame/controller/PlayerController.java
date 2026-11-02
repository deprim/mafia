
package com.mafiagame.controller;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/players")
public class PlayerController {

    @GetMapping("/{id}")
    public String getPlayer(@PathVariable Long id) {
        return "Player ID: " + id;
    }

    @PostMapping
    public String addPlayer() {
        return "Player added successfully";
    }
}

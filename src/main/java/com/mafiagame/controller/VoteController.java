
package com.mafiagame.controller;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/votes")
public class VoteController {

    @GetMapping("/{id}")
    public String getVote(@PathVariable Long id) {
        return "Vote ID: " + id;
    }

    @PostMapping
    public String createVote() {
        return "Vote submitted successfully";
    }
}

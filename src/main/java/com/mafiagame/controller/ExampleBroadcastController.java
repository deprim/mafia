package com.mafiagame.controller;

import com.mafiagame.session.GameBroadcaster;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/example")
@RequiredArgsConstructor
public class ExampleBroadcastController {

    private final GameBroadcaster broadcaster;

    @PostMapping("/{gameId}/all")
    public void toAll(@PathVariable UUID gameId, @RequestBody Map<String, String> body) {
        broadcaster.toGame(gameId, "TEST", body.get("text"));
    }

    @PostMapping("/{gameId}/mafia")
    public void toMafia(@PathVariable UUID gameId, @RequestBody Map<String, String> body) {
        broadcaster.toMafia(gameId, "TEST_MAFIA", body.get("text"));
    }
}

package com.mafiagame.controller;

import com.mafiagame.dto.JoinGameRequest;
import com.mafiagame.dto.PlayerDetailsResponse;
import com.mafiagame.dto.PlayerJoinResult;
import com.mafiagame.dto.PlayerResponse;
import com.mafiagame.service.GameService;
import com.mafiagame.service.PlayerService;
import com.mafiagame.utils.enums.PlayerStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PlayerController.class)
class PlayerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GameService gameService;

    @MockitoBean
    private PlayerService playerService;

    @Test
    void joinGameReturns201AndSessionToken() throws Exception {
        UUID gameId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        PlayerResponse player = new PlayerResponse(playerId, "Anna", false, PlayerStatus.ALIVE);
        when(gameService.joinGame(any(JoinGameRequest.class)))
                .thenReturn(new PlayerJoinResult(gameId, player, "player-session-token"));

        mockMvc.perform(post("/api/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"joinCode\":\"ABCDEFGH\",\"nickname\":\"Anna\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Session-Token", "player-session-token"))
                .andExpect(jsonPath("$.gameId").value(gameId.toString()))
                .andExpect(jsonPath("$.player.nickname").value("Anna"))
                .andExpect(jsonPath("$.player.status").value("ALIVE"));
    }

    @Test
    void joinGameRejectsInvalidInput() throws Exception {
        mockMvc.perform(post("/api/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"joinCode\":\"BAD\",\"nickname\":\"A\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.joinCode").exists())
                .andExpect(jsonPath("$.errors.nickname").exists());

        verifyNoInteractions(gameService);
    }

    @Test
    void getPlayerPassesSessionTokenAndReturnsDetails() throws Exception {
        UUID playerId = UUID.randomUUID();
        PlayerDetailsResponse response = new PlayerDetailsResponse(
                playerId, "Anna", false, PlayerStatus.ALIVE, null, List.of());
        when(playerService.getOwnDetails(playerId, "player-token"))
                .thenReturn(response);

        mockMvc.perform(get("/api/players/{id}", playerId)
                        .header("X-Session-Token", "player-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(playerId.toString()))
                .andExpect(jsonPath("$.nickname").value("Anna"));

        verify(playerService).getOwnDetails(playerId, "player-token");
    }
}

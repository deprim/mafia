package com.mafiagame.controller;

import com.mafiagame.dto.CreateGameRequest;
import com.mafiagame.dto.GameCreationResult;
import com.mafiagame.dto.GameResponse;
import com.mafiagame.exception.ResourceNotFoundException;
import com.mafiagame.service.ChatHistoryService;
import com.mafiagame.service.GameLifecycleService;
import com.mafiagame.service.GameService;
import com.mafiagame.utils.enums.GameStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GameController.class)
class GameControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GameService gameService;

    @MockitoBean
    private GameLifecycleService gameLifecycleService;

    @MockitoBean
    private ChatHistoryService chatHistoryService;

    @Test
    void createGameReturns201AndSessionToken() throws Exception {
        UUID gameId = UUID.randomUUID();
        GameResponse response = new GameResponse(gameId, "ABCDEFGH", 1, GameStatus.WAITING);
        when(gameService.createGame(any(CreateGameRequest.class)))
                .thenReturn(new GameCreationResult(response, "test-session-token"));

        mockMvc.perform(post("/api/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"Anna\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Session-Token", "test-session-token"))
                .andExpect(jsonPath("$.id").value(gameId.toString()))
                .andExpect(jsonPath("$.joinCode").value("ABCDEFGH"))
                .andExpect(jsonPath("$.status").value("WAITING"));
    }

    @Test
    void createGameRejectsBlankNickname() throws Exception {
        mockMvc.perform(post("/api/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation error"))
                .andExpect(jsonPath("$.errors.nickname").exists());

        verifyNoInteractions(gameService);
    }

    @Test
    void getGameReturns404WhenMissing() throws Exception {
        UUID gameId = UUID.randomUUID();
        when(gameService.getRoomState(gameId))
                .thenThrow(new ResourceNotFoundException("Game not found"));

        mockMvc.perform(get("/api/games/{id}", gameId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void startGameReturns204AndPassesSessionToken() throws Exception {
        UUID gameId = UUID.randomUUID();

        mockMvc.perform(post("/api/games/{id}/start", gameId)
                        .header("X-Session-Token", "host-token"))
                .andExpect(status().isNoContent());

        verify(gameLifecycleService).startGame(gameId, "host-token");
    }

    @Test
    void updateSettingsRejectsZeroMafiaCount() throws Exception {
        UUID gameId = UUID.randomUUID();

        mockMvc.perform(put("/api/games/{id}/settings", gameId)
                        .header("X-Session-Token", "host-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mafiaCount\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.mafiaCount").exists());

        verifyNoInteractions(gameLifecycleService);
    }

    @Test
    void deleteGameReturns204AndPassesSessionToken() throws Exception {
        UUID gameId = UUID.randomUUID();

        mockMvc.perform(delete("/api/games/{id}", gameId)
                        .header("X-Session-Token", "host-token"))
                .andExpect(status().isNoContent());

        verify(gameService).deleteGame(gameId, "host-token");
    }

    @Test
    void advancePhaseRejectsNonPositiveRound() throws Exception {
        UUID gameId = UUID.randomUUID();

        mockMvc.perform(post("/api/games/{id}/advance", gameId)
                        .header("X-Session-Token", "host-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedType\":\"DAY\",\"expectedRound\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.expectedRound").exists());

        verifyNoInteractions(gameLifecycleService);
    }
}

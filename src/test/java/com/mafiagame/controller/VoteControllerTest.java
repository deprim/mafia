package com.mafiagame.controller;

import com.mafiagame.dto.VoteResponse;
import com.mafiagame.dto.VoteStatusResponse;
import com.mafiagame.service.VotingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VoteController.class)
class VoteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VotingService votingService;

    @Test
    void castVoteReturns204() throws Exception {
        UUID phaseId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        String body = "{\"phaseId\":\"" + phaseId + "\",\"targetId\":\"" + targetId + "\"}";

        mockMvc.perform(post("/api/votes")
                        .header("X-Session-Token", "voter-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNoContent());

        verify(votingService).castVote(phaseId, targetId, "voter-token");
    }

    @Test
    void castVoteRejectsMissingTarget() throws Exception {
        UUID phaseId = UUID.randomUUID();

        mockMvc.perform(post("/api/votes")
                        .header("X-Session-Token", "voter-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phaseId\":\"" + phaseId + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.targetId").exists());

        verifyNoInteractions(votingService);
    }

    @Test
    void getMyVoteReturns200() throws Exception {
        UUID phaseId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        when(votingService.getMyVote(phaseId, "voter-token"))
                .thenReturn(new VoteResponse(phaseId, targetId));

        mockMvc.perform(get("/api/votes/me")
                        .param("phaseId", phaseId.toString())
                        .header("X-Session-Token", "voter-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phaseId").value(phaseId.toString()))
                .andExpect(jsonPath("$.targetId").value(targetId.toString()));
    }

    @Test
    void getVoteStatusReturns200() throws Exception {
        UUID phaseId = UUID.randomUUID();
        UUID voterId = UUID.randomUUID();
        when(votingService.getVoteStatus(phaseId, "voter-token"))
                .thenReturn(new VoteStatusResponse(
                        phaseId, List.of(voterId), List.of(voterId), true, false));

        mockMvc.perform(get("/api/votes/status")
                        .param("phaseId", phaseId.toString())
                        .header("X-Session-Token", "voter-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phaseId").value(phaseId.toString()))
                .andExpect(jsonPath("$.statusVisible").value(true))
                .andExpect(jsonPath("$.readyToAdvance").value(false));
    }
}

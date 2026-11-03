package com.mafiagame.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(HomeController.class)
class HomeControllerTest {

    private static final String BOOTSTRAP_CSS =
            "/vendor/bootstrap/5.3.8/css/bootstrap.min.css";
    private static final String BOOTSTRAP_JS =
            "/vendor/bootstrap/5.3.8/js/bootstrap.bundle.min.js";
    private static final String STOMP_JS =
            "/vendor/stomp/7.5.0/stomp.umd.min.js";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rendersTheThymeleafPageWithLocalAssetPaths() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString(BOOTSTRAP_CSS)))
                .andExpect(content().string(containsString(BOOTSTRAP_JS)))
                .andExpect(content().string(containsString(STOMP_JS)));
    }

    @Test
    void servesLocalVendorAssets() throws Exception {
        mockMvc.perform(get(BOOTSTRAP_CSS))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("text/css")))
                .andExpect(content().string(not(emptyString())));

        mockMvc.perform(get(BOOTSTRAP_JS))
                .andExpect(status().isOk())
                .andExpect(content().string(not(emptyString())));

        mockMvc.perform(get(STOMP_JS))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("StompJs")));
    }
}

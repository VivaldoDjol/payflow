package com.gozzerks.payflow.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HomeController.class)
@DisplayName("HomeController Tests")
class HomeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Should return 200 with welcome message and API links")
    void shouldReturnWelcomeMessage() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Welcome to PayFlow API"))
                .andExpect(jsonPath("$.documentation").value("/swagger-ui/index.html"))
                .andExpect(jsonPath("$.['api-docs']").value("/v3/api-docs"))
                .andExpect(jsonPath("$.health").value("/actuator/health"));
    }
}
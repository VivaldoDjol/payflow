package com.gozzerks.payflow.controller;

import com.gozzerks.payflow.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HomeController.class)
@Import(SecurityConfig.class)
@DisplayName("HomeController Tests")
class HomeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("Should return 200 with welcome message and API links")
    void shouldReturnWelcomeMessage() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Welcome to PayFlow API"))
                .andExpect(jsonPath("$.documentation").value("/swagger-ui/index.html"))
                .andExpect(jsonPath("$.['api-docs']").value("/v3/api-docs"))
                .andExpect(jsonPath("$.health").value("/actuator/health"))
                .andExpect(jsonPath("$.metrics").value("/actuator/metrics"))
                .andExpect(jsonPath("$.rabbitmq").value("http://localhost:15672"))
                .andExpect(jsonPath("$.zipkin").value("http://localhost:9411"));
    }
}
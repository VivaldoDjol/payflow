package com.gozzerks.payflow.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Tag(name = "Home", description = "Application root endpoint")
public class HomeController {

    @GetMapping("/")
    @Operation(summary = "Application root endpoint", description = "Returns welcome message and API information")
    @ApiResponse(responseCode = "200", description = "Successful response")
    public Map<String, String> home() {
        return Map.of(
                "message", "Welcome to PayFlow API",
                "documentation", "/swagger-ui/index.html",
                "api-docs", "/v3/api-docs",
                "health", "/actuator/health"
        );
    }
}
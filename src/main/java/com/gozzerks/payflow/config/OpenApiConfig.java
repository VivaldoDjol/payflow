package com.gozzerks.payflow.config;

import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("PayFlow API")
                        .description("Async Payment Processing System")
                        .version("0.0.1-SNAPSHOT")
                        .contact(new io.swagger.v3.oas.models.info.Contact()
                                .name("Vivaldo Djol")
                                .url("https://github.com/VivaldoDjol/payflow"))
                );
    }
}
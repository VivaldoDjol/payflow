package com.gozzerks.payflow.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
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
                )
                .addSecurityItem(new SecurityRequirement().addList("keycloak"))
                .components(new Components()
                        .addSecuritySchemes("keycloak", new SecurityScheme()
                                .type(SecurityScheme.Type.OAUTH2)
                                .flows(new OAuthFlows()
                                        .password(new OAuthFlow()
                                                .tokenUrl("http://localhost:8180/realms/payflow/protocol/openid-connect/token")
                                                .scopes(new Scopes()
                                                        .addString("orders:read", "Read access to orders")
                                                        .addString("orders:write", "Write access to orders")
                                                )
                                        )
                                )
                        )
                );
    }
}
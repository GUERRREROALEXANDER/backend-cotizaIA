package com.cotizaia.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documents the authenticated HTTP contract (project.txt section 2).
 * A shared bearer scheme lets Swagger exercise the same agency boundary as other clients.
 */
@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI cotizaIaOpenApi() {
        return new OpenAPI().info(new Info().title("CotizaIA API").version("1.0"))
                .components(new Components().addSecuritySchemes("bearerJwt",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerJwt"));
    }
}

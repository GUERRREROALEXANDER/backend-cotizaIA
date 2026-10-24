package com.cotizaia.config;

import com.cotizaia.api.CurrentUser;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documents the authenticated HTTP contract (project.txt section 2).
 * A shared bearer scheme lets Swagger exercise the same agency boundary as other clients.
 *
 * <p>{@link CurrentUser} is resolved from the verified JWT, never from the request, so it is
 * hidden from every operation instead of being documented as bogus query parameters.
 */
@Configuration
public class OpenApiConfiguration {

    static {
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(CurrentUser.class);
    }

    @Bean
    public OpenAPI cotizaIaOpenApi() {
        return new OpenAPI().info(new Info().title("CotizaIA API").version("1.0"))
                .components(new Components().addSecuritySchemes("bearerJwt",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerJwt"));
    }
}

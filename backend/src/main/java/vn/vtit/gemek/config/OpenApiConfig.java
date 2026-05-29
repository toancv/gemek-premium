/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SpringDoc OpenAPI 3 global configuration.
 *
 * <p>Configures API metadata and the global Bearer JWT security scheme so
 * that Swagger UI shows an "Authorize" button for authenticated endpoints.
 */
@Configuration
public class OpenApiConfig {

    /** Security scheme identifier used across all secured endpoints. */
    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    /**
     * Configures the {@link OpenAPI} bean with project metadata and the JWT Bearer security scheme.
     *
     * @return configured {@link OpenAPI} instance.
     */
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Gemek Premium — Apartment Management API")
                        .description("Backend REST API for the Gemek Premium Apartment Management System. " +
                                "All authenticated endpoints require a Bearer JWT in the Authorization header.")
                        .version("2.0"))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }
}

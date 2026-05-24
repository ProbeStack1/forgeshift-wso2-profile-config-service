package com.forgeshift.profile.config.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI apiDocs() {
        return new OpenAPI().info(new Info()
                .title("Forgeshift WSO2 / Kong Konnect / GCS Profile Config Service")
                .version("0.1.0")
                .description("Centralized vault for connection profiles used by the WSO2-to-Kong migrator."));
    }
}

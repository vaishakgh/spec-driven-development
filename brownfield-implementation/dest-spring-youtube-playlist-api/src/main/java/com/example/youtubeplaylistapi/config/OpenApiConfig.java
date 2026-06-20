package com.example.youtubeplaylistapi.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("YouTube Playlist API")
                        .version("v1")
                        .description("Spring Boot service proxying the YouTube Data API v3 — MuleSoft migration"));
    }

}

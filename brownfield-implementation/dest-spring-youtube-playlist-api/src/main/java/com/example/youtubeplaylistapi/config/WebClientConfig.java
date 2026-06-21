package com.example.youtubeplaylistapi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    @Value("${youtube.api.base-url}")
    private String baseUrl;

    // Injected to enforce startup validation — if YOUTUBE_API_KEY env var is missing,
    // Spring fails here with a clear property resolution error.
    // Service layer classes (PlaylistService, VideoService) inject this directly
    // via @Value("${youtube.api.key}") in their own Stories (2.x, 3.x).
    @Value("${youtube.api.key}")
    private String apiKey;

    @Bean
    public WebClient webClient() {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(10));
        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

}

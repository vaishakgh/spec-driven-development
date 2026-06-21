package com.example.youtubeplaylistapi.service;

import com.example.youtubeplaylistapi.dto.PlaylistResponse;
import com.example.youtubeplaylistapi.dto.youtube.YTPlaylistItemsResponse;
import com.example.youtubeplaylistapi.exception.UpstreamUnauthorizedException;
import com.example.youtubeplaylistapi.mapper.PlaylistMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class PlaylistService {

    private final WebClient webClient;
    private final PlaylistMapper playlistMapper;
    private final String apiKey;
    private final int maxResults;

    public PlaylistService(WebClient webClient,
                           PlaylistMapper playlistMapper,
                           @Value("${youtube.api.key}") String apiKey,
                           @Value("${youtube.api.max-results}") int maxResults) {
        this.webClient = webClient;
        this.playlistMapper = playlistMapper;
        this.apiKey = apiKey;
        this.maxResults = maxResults;
    }

    public PlaylistResponse getPlaylist(String playlistId, String pageToken) {
        YTPlaylistItemsResponse ytResponse = webClient.get()
            .uri(uriBuilder -> {
                var builder = uriBuilder
                    .path("/playlistItems")
                    .queryParam("part", "snippet")
                    .queryParam("playlistId", playlistId)
                    .queryParam("maxResults", maxResults)
                    .queryParam("key", apiKey);
                if (pageToken != null) {
                    builder = builder.queryParam("pageToken", pageToken);
                }
                return builder.build();
            })
            .retrieve()
            .onStatus(status -> status.value() == 401,
                resp -> Mono.error(new UpstreamUnauthorizedException()))
            .bodyToMono(YTPlaylistItemsResponse.class)
            .block();
        if (ytResponse == null) {
            ytResponse = new YTPlaylistItemsResponse();
        }
        return playlistMapper.toPlaylistResponse(ytResponse);
    }
}

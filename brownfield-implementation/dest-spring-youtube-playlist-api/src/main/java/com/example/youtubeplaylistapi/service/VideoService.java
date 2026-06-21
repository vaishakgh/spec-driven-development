package com.example.youtubeplaylistapi.service;

import com.example.youtubeplaylistapi.dto.SongDetail;
import com.example.youtubeplaylistapi.dto.youtube.YTVideoDetailsResponse;
import com.example.youtubeplaylistapi.exception.UpstreamUnauthorizedException;
import com.example.youtubeplaylistapi.exception.VideoNotFoundException;
import com.example.youtubeplaylistapi.mapper.VideoMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class VideoService {

    private final WebClient webClient;
    private final VideoMapper videoMapper;
    private final String apiKey;

    public VideoService(WebClient webClient,
                        VideoMapper videoMapper,
                        @Value("${youtube.api.key}") String apiKey) {
        this.webClient = webClient;
        this.videoMapper = videoMapper;
        this.apiKey = apiKey;
    }

    public SongDetail getVideo(String videoId) {
        YTVideoDetailsResponse ytResponse = webClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/videos")
                .queryParam("part", "snippet,statistics,contentDetails")
                .queryParam("id", videoId)
                .queryParam("key", apiKey)
                .build())
            .retrieve()
            .onStatus(status -> status.value() == 401,
                resp -> Mono.error(new UpstreamUnauthorizedException()))
            .bodyToMono(YTVideoDetailsResponse.class)
            .block();

        SongDetail songDetail = videoMapper.toSongDetail(ytResponse);
        if (songDetail == null) {
            throw new VideoNotFoundException();
        }
        return songDetail;
    }
}

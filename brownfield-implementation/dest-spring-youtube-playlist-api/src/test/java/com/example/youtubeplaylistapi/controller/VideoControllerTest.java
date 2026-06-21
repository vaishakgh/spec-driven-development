package com.example.youtubeplaylistapi.controller;

import com.example.youtubeplaylistapi.dto.SongDetail;
import com.example.youtubeplaylistapi.exception.GlobalExceptionHandler;
import com.example.youtubeplaylistapi.exception.UpstreamUnauthorizedException;
import com.example.youtubeplaylistapi.exception.VideoNotFoundException;
import com.example.youtubeplaylistapi.service.VideoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import java.net.URI;

import static org.mockito.Mockito.*;
import static org.springframework.http.HttpHeaders.EMPTY;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class VideoControllerTest {

    @Mock
    private VideoService videoService;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        VideoController controller = new VideoController(videoService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void should_return_200_when_valid_video_id() throws Exception {
        SongDetail detail = buildSongDetail("dQw4w9WgXcQ");
        when(videoService.getVideo("dQw4w9WgXcQ")).thenReturn(detail);

        mockMvc.perform(get("/api/youtube/song/dQw4w9WgXcQ"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.videoId").value("dQw4w9WgXcQ"))
            .andExpect(jsonPath("$.channelName").value("RickAstleyVEVO"))
            .andExpect(jsonPath("$.duration").value("PT3M33S"));
    }

    @Test
    void should_return_404_when_video_not_found() throws Exception {
        when(videoService.getVideo("nonexistent")).thenThrow(new VideoNotFoundException());

        mockMvc.perform(get("/api/youtube/song/nonexistent"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(404))
            .andExpect(jsonPath("$.error").value("Not Found"))
            .andExpect(jsonPath("$.message").value("Video not found for the given videoId."));
    }

    @Test
    void should_return_401_when_youtube_unauthorized() throws Exception {
        when(videoService.getVideo("dQw4w9WgXcQ")).thenThrow(new UpstreamUnauthorizedException());

        mockMvc.perform(get("/api/youtube/song/dQw4w9WgXcQ"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void should_return_503_when_youtube_unreachable() throws Exception {
        var ex = new WebClientRequestException(new RuntimeException("connection refused"),
                GET, URI.create("https://googleapis.com"), EMPTY);
        when(videoService.getVideo("dQw4w9WgXcQ")).thenThrow(ex);

        mockMvc.perform(get("/api/youtube/song/dQw4w9WgXcQ"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value(503));
    }

    private SongDetail buildSongDetail(String videoId) {
        SongDetail d = new SongDetail();
        d.setVideoId(videoId);
        d.setTitle("Never Gonna Give You Up");
        d.setDescription("The official music video.");
        d.setChannelName("RickAstleyVEVO");
        d.setPublishedAt("2009-10-25T06:57:33Z");
        d.setDuration("PT3M33S");
        d.setViewCount("1400000000");
        d.setLikeCount("15000000");
        d.setVideoUrl("https://www.youtube.com/watch?v=" + videoId);
        return d;
    }
}

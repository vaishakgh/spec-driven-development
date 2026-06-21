package com.example.youtubeplaylistapi.service;

import com.example.youtubeplaylistapi.dto.SongDetail;
import com.example.youtubeplaylistapi.exception.UpstreamUnauthorizedException;
import com.example.youtubeplaylistapi.exception.VideoNotFoundException;
import com.example.youtubeplaylistapi.mapper.VideoMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@WireMockTest
class VideoServiceTest {

    private static final String VIDEO_SUCCESS_BODY = """
            {
              "items": [
                {
                  "id": "dQw4w9WgXcQ",
                  "snippet": {
                    "publishedAt": "2009-10-25T06:57:33Z",
                    "title": "Never Gonna Give You Up",
                    "description": "The official music video.",
                    "channelTitle": "RickAstleyVEVO",
                    "thumbnails": {
                      "high": { "url": "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg" }
                    }
                  },
                  "contentDetails": { "duration": "PT3M33S" },
                  "statistics": { "viewCount": "1400000000", "likeCount": "15000000" }
                }
              ]
            }
            """;

    private static final String VIDEO_EMPTY_BODY = """
            { "items": [] }
            """;

    private VideoService buildService(WireMockRuntimeInfo wmInfo) {
        WebClient webClient = WebClient.builder()
            .baseUrl(wmInfo.getHttpBaseUrl())
            .build();
        return new VideoService(webClient, new VideoMapper(), "test-key");
    }

    @Test
    void should_call_youtube_with_correct_params_and_return_song_detail(WireMockRuntimeInfo wmInfo) {
        stubFor(get(urlPathEqualTo("/videos"))
            .withQueryParam("part", equalTo("snippet,statistics,contentDetails"))
            .withQueryParam("id", equalTo("dQw4w9WgXcQ"))
            .withQueryParam("key", equalTo("test-key"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(VIDEO_SUCCESS_BODY)));

        SongDetail result = buildService(wmInfo).getVideo("dQw4w9WgXcQ");

        assertThat(result.getVideoId()).isEqualTo("dQw4w9WgXcQ");
        assertThat(result.getChannelName()).isEqualTo("RickAstleyVEVO");
        assertThat(result.getDuration()).isEqualTo("PT3M33S");
    }

    @Test
    void should_throw_upstream_unauthorized_when_youtube_returns_401(WireMockRuntimeInfo wmInfo) {
        stubFor(get(urlPathEqualTo("/videos"))
            .willReturn(aResponse().withStatus(401)));

        assertThatThrownBy(() -> buildService(wmInfo).getVideo("dQw4w9WgXcQ"))
            .isInstanceOf(UpstreamUnauthorizedException.class);
    }

    @Test
    void should_throw_video_not_found_when_items_array_is_empty(WireMockRuntimeInfo wmInfo) {
        stubFor(get(urlPathEqualTo("/videos"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(VIDEO_EMPTY_BODY)));

        assertThatThrownBy(() -> buildService(wmInfo).getVideo("nonexistent"))
            .isInstanceOf(VideoNotFoundException.class);
    }
}

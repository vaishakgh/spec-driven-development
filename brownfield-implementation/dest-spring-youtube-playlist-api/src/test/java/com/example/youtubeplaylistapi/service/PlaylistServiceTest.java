package com.example.youtubeplaylistapi.service;

import com.example.youtubeplaylistapi.dto.PlaylistResponse;
import com.example.youtubeplaylistapi.exception.UpstreamUnauthorizedException;
import com.example.youtubeplaylistapi.mapper.PlaylistMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@WireMockTest
class PlaylistServiceTest {

    private static final String PLAYLIST_SUCCESS_BODY = """
            {
              "nextPageToken": "EAAelgEKADiD",
              "pageInfo": { "totalResults": 50, "resultsPerPage": 25 },
              "items": [
                {
                  "snippet": {
                    "publishedAt": "2009-10-25T06:57:33Z",
                    "title": "Never Gonna Give You Up",
                    "description": "The official music video.",
                    "thumbnails": { "medium": { "url": "https://img.youtube.com/vi/dQw4w9WgXcQ/mqdefault.jpg" } },
                    "position": 0,
                    "resourceId": { "videoId": "dQw4w9WgXcQ" }
                  }
                }
              ]
            }
            """;

    private PlaylistService buildService(WireMockRuntimeInfo wmInfo) {
        WebClient webClient = WebClient.builder()
            .baseUrl(wmInfo.getHttpBaseUrl())
            .build();
        return new PlaylistService(webClient, new PlaylistMapper(), "test-key", 25);
    }

    @Test
    void should_call_youtube_with_correct_params_and_return_playlist_response(WireMockRuntimeInfo wmInfo) {
        stubFor(get(urlPathEqualTo("/playlistItems"))
            .withQueryParam("part", equalTo("snippet"))
            .withQueryParam("playlistId", equalTo("PLxxx"))
            .withQueryParam("maxResults", equalTo("25"))
            .withQueryParam("key", equalTo("test-key"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(PLAYLIST_SUCCESS_BODY)));

        PlaylistResponse result = buildService(wmInfo).getPlaylist("PLxxx", null);

        assertThat(result.getTotalResults()).isEqualTo(50);
        assertThat(result.getNextPageToken()).isEqualTo("EAAelgEKADiD");
        assertThat(result.getPlaylist()).hasSize(1);
    }

    @Test
    void should_forward_page_token_when_provided(WireMockRuntimeInfo wmInfo) {
        stubFor(get(urlPathEqualTo("/playlistItems"))
            .withQueryParam("pageToken", equalTo("EAAelgEKADiD"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(PLAYLIST_SUCCESS_BODY)));

        PlaylistResponse result = buildService(wmInfo).getPlaylist("PLxxx", "EAAelgEKADiD");

        assertThat(result).isNotNull();
        verify(getRequestedFor(urlPathEqualTo("/playlistItems"))
            .withQueryParam("pageToken", equalTo("EAAelgEKADiD")));
    }

    @Test
    void should_throw_upstream_unauthorized_when_youtube_returns_401(WireMockRuntimeInfo wmInfo) {
        stubFor(get(urlPathEqualTo("/playlistItems"))
            .willReturn(aResponse().withStatus(401)));

        assertThatThrownBy(() -> buildService(wmInfo).getPlaylist("PLxxx", null))
            .isInstanceOf(UpstreamUnauthorizedException.class);
    }
}

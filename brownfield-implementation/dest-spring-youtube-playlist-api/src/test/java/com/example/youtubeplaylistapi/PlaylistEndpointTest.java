package com.example.youtubeplaylistapi;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest(httpPort = 8089)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureWebTestClient
class PlaylistEndpointTest {

    @Autowired
    private WebTestClient webTestClient;

    // ── Test Data ────────────────────────────────────────────────────────────
    // NOTE: Uses snippet.resourceId.videoId (Spring Boot model) — NOT contentDetails.videoId (Mule)

    private static final String PLAYLIST_SUCCESS_JSON = """
            {
              "pageInfo": { "totalResults": 2, "resultsPerPage": 25 },
              "nextPageToken": "token123",
              "items": [
                {
                  "snippet": {
                    "position": 0,
                    "title": "Song One",
                    "description": "First song description",
                    "publishedAt": "2024-01-01T00:00:00Z",
                    "thumbnails": { "medium": { "url": "https://img.youtube.com/vi/abc123/mqdefault.jpg" } },
                    "resourceId": { "videoId": "abc123" }
                  }
                },
                {
                  "snippet": {
                    "position": 1,
                    "title": "Song Two",
                    "description": "Second song description",
                    "publishedAt": "2024-02-01T00:00:00Z",
                    "thumbnails": { "medium": { "url": "https://img.youtube.com/vi/def456/mqdefault.jpg" } },
                    "resourceId": { "videoId": "def456" }
                  }
                }
              ]
            }
            """;

    private static final String PLAYLIST_EMPTY_JSON = """
            {
              "pageInfo": { "totalResults": 0, "resultsPerPage": 25 },
              "items": []
            }
            """;

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test
    void should_return_200_with_all_fields_when_valid_playlist_id() {
        stubFor(get(urlPathEqualTo("/playlistItems"))
            .withQueryParam("playlistId", equalTo("PLtest12345"))
            .withQueryParam("part", equalTo("snippet"))
            .withQueryParam("maxResults", equalTo("25"))
            .withQueryParam("key", equalTo("test-api-key-placeholder"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(PLAYLIST_SUCCESS_JSON)));

        webTestClient.get()
            .uri("/api/youtube/playlists/PLtest12345")
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class)
            .consumeWith(result -> {
                String body = result.getResponseBody();
                assertThat(body).isNotNull();
                assertThat(body).contains("\"totalResults\":2");
                assertThat(body).contains("\"resultsPerPage\":25");
                assertThat(body).contains("\"nextPageToken\":\"token123\"");
                assertThat(body).contains("\"videoId\":\"abc123\"");
                assertThat(body).contains("\"title\":\"Song One\"");
                assertThat(body).contains("\"videoUrl\":\"https://www.youtube.com/watch?v=abc123\"");
                assertThat(body).contains("\"thumbnail\":\"https://img.youtube.com/vi/abc123/mqdefault.jpg\"");
            });
    }

    @Test
    void should_return_200_with_empty_playlist_when_items_array_is_empty() {
        stubFor(get(urlPathEqualTo("/playlistItems"))
            .withQueryParam("playlistId", equalTo("PLempty"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(PLAYLIST_EMPTY_JSON)));

        webTestClient.get()
            .uri("/api/youtube/playlists/PLempty")
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class)
            .consumeWith(result -> {
                assertThat(result.getResponseBody()).contains("\"totalResults\":0");
                assertThat(result.getResponseBody()).contains("\"playlist\":[]");
            });
    }

    @Test
    void should_return_401_when_youtube_returns_401() {
        stubFor(get(urlPathEqualTo("/playlistItems"))
            .willReturn(aResponse().withStatus(401)));

        webTestClient.get()
            .uri("/api/youtube/playlists/PLbadkey")
            .exchange()
            .expectStatus().isUnauthorized()
            .expectBody(String.class)
            .consumeWith(result -> {
                assertThat(result.getResponseBody()).contains("\"code\":401");
                assertThat(result.getResponseBody()).contains("\"error\":\"Unauthorized\"");
                assertThat(result.getResponseBody()).contains("\"message\":\"Invalid or missing YouTube API Key.\"");
            });
    }

    @Test
    void should_return_503_when_youtube_connection_reset() {
        stubFor(get(urlPathEqualTo("/playlistItems"))
            .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        webTestClient.get()
            .uri("/api/youtube/playlists/PLoffline")
            .exchange()
            .expectStatus().isEqualTo(503)
            .expectBody(String.class)
            .consumeWith(result -> {
                assertThat(result.getResponseBody()).contains("\"code\":503");
                assertThat(result.getResponseBody()).contains("\"error\":\"Connection Error\"");
                assertThat(result.getResponseBody()).contains("\"message\":\"Unable to connect to YouTube API.\"");
            });
    }

    @Test
    void should_forward_page_token_to_upstream_youtube_call() {
        stubFor(get(urlPathEqualTo("/playlistItems"))
            .withQueryParam("pageToken", equalTo("EAAelgEKADiD"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(PLAYLIST_SUCCESS_JSON)));

        webTestClient.get()
            .uri("/api/youtube/playlists/PLtest12345?pageToken=EAAelgEKADiD")
            .exchange()
            .expectStatus().isOk();

        verify(getRequestedFor(urlPathEqualTo("/playlistItems"))
            .withQueryParam("pageToken", equalTo("EAAelgEKADiD")));
    }
}

package com.example.youtubeplaylistapi;

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
class VideoEndpointTest {

    @Autowired
    private WebTestClient webTestClient;

    // ── Test Data ────────────────────────────────────────────────────────────
    // Uses item.id as videoId (NOT snippet.resourceId) — VideoMapper uses item.id

    private static final String VIDEO_SUCCESS_JSON = """
            {
              "items": [
                {
                  "id": "dQw4w9WgXcQ",
                  "snippet": {
                    "title": "Never Gonna Give You Up",
                    "description": "The classic Rick Astley hit.",
                    "channelTitle": "RickAstleyVEVO",
                    "publishedAt": "2009-10-25T06:57:33Z",
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

    private static final String VIDEO_EMPTY_JSON = """
            {
              "items": []
            }
            """;

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test
    void should_return_200_with_all_fields_when_valid_video_id() {
        stubFor(get(urlPathEqualTo("/videos"))
            .withQueryParam("id", equalTo("dQw4w9WgXcQ"))
            .withQueryParam("part", equalTo("snippet,statistics,contentDetails"))
            .withQueryParam("key", equalTo("test-api-key-placeholder"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(VIDEO_SUCCESS_JSON)));

        webTestClient.get()
            .uri("/api/youtube/song/dQw4w9WgXcQ")
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class)
            .consumeWith(result -> {
                String body = result.getResponseBody();
                assertThat(body).isNotNull();
                assertThat(body).contains("\"videoId\":\"dQw4w9WgXcQ\"");
                assertThat(body).contains("\"title\":\"Never Gonna Give You Up\"");
                assertThat(body).contains("\"channelName\":\"RickAstleyVEVO\"");    // channelTitle → channelName
                assertThat(body).contains("\"duration\":\"PT3M33S\"");
                assertThat(body).contains("\"viewCount\":\"1400000000\"");
                assertThat(body).contains("\"likeCount\":\"15000000\"");
                assertThat(body).contains("\"thumbnail\":\"https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg\"");
                assertThat(body).contains("\"videoUrl\":\"https://www.youtube.com/watch?v=dQw4w9WgXcQ\"");
            });
    }

    @Test
    void should_return_404_when_video_not_found_in_youtube_response() {
        stubFor(get(urlPathEqualTo("/videos"))
            .withQueryParam("id", equalTo("INVALID_ID"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(VIDEO_EMPTY_JSON)));

        // FR-7: Returns 404, NOT 200 with null fields (breaking change from Mule)
        webTestClient.get()
            .uri("/api/youtube/song/INVALID_ID")
            .exchange()
            .expectStatus().isNotFound()
            .expectBody(String.class)
            .consumeWith(result -> {
                assertThat(result.getResponseBody()).contains("\"code\":404");
                assertThat(result.getResponseBody()).contains("\"error\":\"Not Found\"");
                assertThat(result.getResponseBody()).contains("\"message\":\"Video not found for the given videoId.\"");
            });
    }

    @Test
    void should_return_500_when_youtube_returns_5xx() {
        stubFor(get(urlPathEqualTo("/videos"))
            .withQueryParam("id", equalTo("errorId"))
            .willReturn(aResponse().withStatus(500)));

        webTestClient.get()
            .uri("/api/youtube/song/errorId")
            .exchange()
            .expectStatus().is5xxServerError()
            .expectBody(String.class)
            .consumeWith(result -> {
                assertThat(result.getResponseBody()).contains("\"code\":500");
                assertThat(result.getResponseBody()).contains("\"error\":\"Internal Server Error\"");
                assertThat(result.getResponseBody()).contains("\"message\":\"An unexpected error occurred.\"");
            });
    }
}

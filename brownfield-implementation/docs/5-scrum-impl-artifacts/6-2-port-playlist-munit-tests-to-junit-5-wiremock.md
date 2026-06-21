# Story 6.2: Port Playlist MUnit Tests to JUnit 5 + WireMock

Status: ready-for-dev

## Story

As an **internal developer**,
I want the four playlist MUnit test scenarios ported to JUnit 5 + WireMock equivalents,
so that the playlist endpoint's happy path, empty response, auth failure, and connectivity failure are all automatically verified.

## Acceptance Criteria

1. **Given** a WireMock stub returning a valid `/playlistItems` YouTube response **When** `GET /api/youtube/playlists/{playlistId}` is called **Then** HTTP 200 is returned and all `PlaylistResponse` fields (`totalResults`, `resultsPerPage`, `nextPageToken`, `playlist[]` with all item fields) match the stubbed data (`test-get-youtube-playlists-success`)
2. **Given** a WireMock stub returning an empty `items[]` from `/playlistItems` **When** the endpoint is called **Then** HTTP 200 is returned with `totalResults: 0` and an empty `playlist` array (`test-get-youtube-playlists-empty`)
3. **Given** a WireMock stub returning HTTP 401 from `/playlistItems` **When** the endpoint is called **Then** HTTP 401 is returned with a valid `ErrorResponse` body (`test-get-youtube-playlists-unauthorized`)
4. **Given** a WireMock fault simulating `CONNECTION_RESET` on `/playlistItems` **When** the endpoint is called **Then** HTTP 503 is returned with a valid `ErrorResponse` body (`test-get-youtube-playlists-connectivity-error`)
5. **Given** a WireMock stub returning a valid response with `pageToken` in the request query **When** `GET /api/youtube/playlists/{playlistId}?pageToken={token}` is called **Then** WireMock verifies the `pageToken` parameter was forwarded to the upstream YouTube call (additional test beyond MUnit parity)

## Tasks / Subtasks

- [ ] Read `PlaylistEndpointTest.java` shell created in Story 6.1 (AC: all)
- [ ] Add `PLAYLIST_SUCCESS_JSON` static constant with correct test data (AC: 1)
  - [ ] Use `snippet.resourceId.videoId` format — NOT `contentDetails.videoId` (critical!)
- [ ] Implement `should_return_200_with_all_fields_when_valid_playlist_id` (AC: 1)
  - [ ] Assert `totalResults`, `resultsPerPage`, `nextPageToken`
  - [ ] Assert `playlist[0].videoId`, `playlist[0].title`, `playlist[0].videoUrl`, `playlist[0].thumbnail`
- [ ] Implement `should_return_200_with_empty_playlist_when_items_array_is_empty` (AC: 2)
  - [ ] Assert `totalResults: 0`, empty `playlist` array
- [ ] Implement `should_return_401_when_youtube_returns_401` (AC: 3)
  - [ ] Assert `ErrorResponse { code: 401, error: "Unauthorized", message: "Invalid or missing YouTube API Key." }`
- [ ] Implement `should_return_503_when_youtube_connection_reset` (AC: 4)
  - [ ] Use `aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)`
  - [ ] Assert `ErrorResponse { code: 503, error: "Connection Error" }`
- [ ] Implement `should_forward_page_token_to_upstream_youtube_call` (AC: 5)
  - [ ] Stub requires `pageToken` query param present
  - [ ] Verify with `WireMockRuntimeInfo`
- [ ] Run `./mvnw test` to confirm all 5 new tests pass

## Dev Notes

### Prerequisite: Story 6.1 Must Be Done

`PlaylistEndpointTest.java` must already exist (created in Story 6.1) with the class annotations in place. Read the existing file before adding test methods.

### CRITICAL: Spring Boot Playlist Model Differs from Mule

The MUnit test data uses `item.contentDetails.videoId` to identify video ID. **The Spring Boot `PlaylistMapper` does NOT use `contentDetails`** — it reads from `snippet.resourceId.videoId`.

```java
// PlaylistMapper.toPlaylistItem() — the actual implementation:
item.setVideoId(snippet.getResourceId().getVideoId());  // ← snippet.resourceId.videoId
```

**Test stubs MUST use `snippet.resourceId.videoId` format:**

```json
// ✅ CORRECT — matches PlaylistMapper expectations
{
  "snippet": {
    "position": 0,
    "title": "Song One",
    "publishedAt": "2024-01-01T00:00:00Z",
    "thumbnails": { "medium": { "url": "https://img.youtube.com/vi/abc123/mqdefault.jpg" } },
    "resourceId": { "videoId": "abc123" }
  }
}

// ❌ WRONG — Mule format, Spring Boot PlaylistMapper will fail to map
{
  "contentDetails": { "videoId": "abc123" },
  "snippet": { ... }
}
```

This is a known migration difference between the Mule flow (`playlist.xml` uses `contentDetails.videoId`) and the Spring Boot implementation.

### Complete Test Class Implementation

```java
package com.example.youtubeplaylistapi;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest(httpPort = 8089)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PlaylistEndpointTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

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

        ResponseEntity<String> response = restTemplate.getForEntity(
            "/api/youtube/playlists/PLtest12345", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body).contains("\"totalResults\":2");
        assertThat(body).contains("\"resultsPerPage\":25");
        assertThat(body).contains("\"nextPageToken\":\"token123\"");
        assertThat(body).contains("\"videoId\":\"abc123\"");
        assertThat(body).contains("\"title\":\"Song One\"");
        assertThat(body).contains("\"videoUrl\":\"https://www.youtube.com/watch?v=abc123\"");
        assertThat(body).contains("\"thumbnail\":\"https://img.youtube.com/vi/abc123/mqdefault.jpg\"");
    }

    @Test
    void should_return_200_with_empty_playlist_when_items_array_is_empty() {
        stubFor(get(urlPathEqualTo("/playlistItems"))
            .withQueryParam("playlistId", equalTo("PLempty"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(PLAYLIST_EMPTY_JSON)));

        ResponseEntity<String> response = restTemplate.getForEntity(
            "/api/youtube/playlists/PLempty", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"totalResults\":0");
        assertThat(response.getBody()).contains("\"playlist\":[]");
    }

    @Test
    void should_return_401_when_youtube_returns_401() {
        stubFor(get(urlPathEqualTo("/playlistItems"))
            .willReturn(aResponse().withStatus(401)));

        ResponseEntity<String> response = restTemplate.getForEntity(
            "/api/youtube/playlists/PLbadkey", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("\"code\":401");
        assertThat(response.getBody()).contains("\"error\":\"Unauthorized\"");
        assertThat(response.getBody()).contains("\"message\":\"Invalid or missing YouTube API Key.\"");
    }

    @Test
    void should_return_503_when_youtube_connection_reset() {
        stubFor(get(urlPathEqualTo("/playlistItems"))
            .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        ResponseEntity<String> response = restTemplate.getForEntity(
            "/api/youtube/playlists/PLoffline", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).contains("\"code\":503");
        assertThat(response.getBody()).contains("\"error\":\"Connection Error\"");
        assertThat(response.getBody()).contains("\"message\":\"Unable to connect to YouTube API.\"");
    }

    @Test
    void should_forward_page_token_to_upstream_youtube_call(WireMockRuntimeInfo wmInfo) {
        stubFor(get(urlPathEqualTo("/playlistItems"))
            .withQueryParam("pageToken", equalTo("EAAelgEKADiD"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(PLAYLIST_SUCCESS_JSON)));

        ResponseEntity<String> response = restTemplate.getForEntity(
            "/api/youtube/playlists/PLtest12345?pageToken=EAAelgEKADiD", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(getRequestedFor(urlPathEqualTo("/playlistItems"))
            .withQueryParam("pageToken", equalTo("EAAelgEKADiD")));
    }
}
```

### Key Implementation Notes

**TestRestTemplate relative URL calls:**
When `@Autowired TestRestTemplate restTemplate` in a `@SpringBootTest(RANDOM_PORT)` test, Spring Boot pre-configures the base URL as `http://localhost:{port}`. Calls use relative paths starting with `/`.

**WireMock `Fault.CONNECTION_RESET_BY_PEER`:**
This simulates the TCP connection being reset mid-connection. Spring's WebClient throws `WebClientRequestException`, which `GlobalExceptionHandler` catches and maps to HTTP 503. This is the correct port of MUnit's `HTTP:CONNECTIVITY` error.

**String assertions over JSON parsing:**
The tests use `contains(...)` string assertions for simplicity. This is acceptable for integration tests at this level. The field values are string-matched exactly so false positives are not a risk.

**Existing `PlaylistServiceTest` already tests 401 and pageToken at unit level:**
The integration tests in this story test the SAME scenarios end-to-end through the full stack (servlet → controller → service → WebClient → WireMock → back). They are NOT duplicate tests — they verify the full pipeline.

### Architecture Compliance

- WireMock stubs defined inline in each test method — NO external JSON stub files [Source: docs/4-architect-artifacts/architecture/04-implementation-patterns-consistency-rules.md#Structure Patterns]
- Test methods named `should_return_{status}_when_{condition}()` — snake_case pattern [Source: docs/4-architect-artifacts/architecture/04-implementation-patterns-consistency-rules.md#Naming Patterns]
- No try/catch in test methods — let AssertionErrors propagate
- `@ActiveProfiles("test")` ensures `YOUTUBE_API_KEY` env var is not required

### Project Structure Notes

- UPDATE: `src/test/java/com/example/youtubeplaylistapi/PlaylistEndpointTest.java` — add 5 @Test methods
- No new files created
- No production code changes

### References

- [Source: docs/3-product-manager-artifacts/epics-and-stories/epic-6-test-suite-quality-gate.md#Story 6.2]
- [Source: docs/4-architect-artifacts/architecture/04-implementation-patterns-consistency-rules.md]
- [Source: docs/4-architect-artifacts/architecture/05-project-structure-boundaries.md#Test Organisation]
- [Source: source-mule-youtube-playlist-api/src/test/munit/youtube-playlist-test.xml] — original MUnit tests (reference only; data format differs)
- [Source: dest-spring-youtube-playlist-api/src/main/java/com/example/youtubeplaylistapi/mapper/PlaylistMapper.java] — confirms `snippet.resourceId.videoId` field mapping
- wiremock-standalone 3.10.0: `Fault.CONNECTION_RESET_BY_PEER` simulates TCP reset; maps to `WebClientRequestException` in Spring's WebClient

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

### Completion Notes List

### File List

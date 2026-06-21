---
baseline_commit: b1318e8096366d5200c2744cb42f5ae8816d8e20
---

# Story 2.2: Implement PlaylistService with YouTube API Integration

Status: done

## Story

As an **internal developer**,
I want a `PlaylistService` that calls the YouTube Data API v3 `/playlistItems` endpoint using WebClient,
so that real playlist data is retrieved from YouTube and made available to the controller.

## Acceptance Criteria

1. **Given** a valid `playlistId` and no `pageToken`
   **When** `PlaylistService.getPlaylist()` is called
   **Then** a WebClient GET request is made to the configured base URL at path `/playlistItems` with `part=snippet`, `playlistId={playlistId}`, `maxResults={configuredPageSize}`, and `key={YOUTUBE_API_KEY}`
   **And** the raw YouTube API response body is deserialized into `YTPlaylistItemsResponse`, mapped via `PlaylistMapper`, and returned as `PlaylistResponse`

2. **Given** a valid `playlistId` and a non-null `pageToken`
   **When** `PlaylistService.getPlaylist()` is called
   **Then** the `pageToken` parameter is forwarded to the upstream YouTube API as `pageToken={token}`

3. **Given** the YouTube API returns HTTP 401
   **When** `PlaylistService.getPlaylist()` is called
   **Then** `UpstreamUnauthorizedException` is thrown (caught by `GlobalExceptionHandler` → HTTP 401)

4. **Given** the YouTube API is unreachable (connection timeout or reset)
   **When** `PlaylistService.getPlaylist()` is called
   **Then** `WebClientRequestException` propagates naturally to `GlobalExceptionHandler` → HTTP 503 (do NOT catch in service)

## Tasks / Subtasks

- [x] Task 1 — Create `PlaylistService` (AC: 1–4)
  - [x] Create `src/main/java/com/example/youtubeplaylistapi/service/PlaylistService.java`
  - [x] Annotate with `@Service`
  - [x] Constructor-inject: `WebClient webClient`, `PlaylistMapper playlistMapper`, `@Value("${youtube.api.key}") String apiKey`, `@Value("${youtube.api.max-results}") int maxResults`
  - [x] Public method: `PlaylistResponse getPlaylist(String playlistId, String pageToken)`
  - [x] Build URI using `uriBuilder.path("/playlistItems").queryParam("part","snippet").queryParam("playlistId", playlistId).queryParam("maxResults", maxResults).queryParam("key", apiKey)` — conditionally add `.queryParam("pageToken", pageToken)` when `pageToken != null`
  - [x] Chain `.retrieve().onStatus(status -> status.value() == 401, resp -> Mono.error(new UpstreamUnauthorizedException())).bodyToMono(YTPlaylistItemsResponse.class).block()`
  - [x] Call `playlistMapper.toPlaylistResponse(ytResponse)` and return the result
  - [x] Do NOT catch `WebClientRequestException` — let it propagate

- [x] Task 2 — Write unit tests (AC: 1–4)
  - [x] Create `src/test/java/com/example/youtubeplaylistapi/service/PlaylistServiceTest.java`
  - [x] Use `@WireMockTest` (JUnit 5 extension, dynamic port) — no Spring context, no `@SpringBootTest`
  - [x] Build `WebClient` and `PlaylistService` inline using `wmInfo.getHttpBaseUrl()` from `WireMockRuntimeInfo`
  - [x] `should_call_youtube_with_correct_params_and_return_playlist_response()` — stub GET `/playlistItems` with correct query params, assert `PlaylistResponse` is returned and `totalResults` matches (AC 1)
  - [x] `should_forward_page_token_when_provided()` — stub with `pageToken` query param present, verify forwarded (AC 2)
  - [x] `should_throw_upstream_unauthorized_when_youtube_returns_401()` — stub 401 response, assert `UpstreamUnauthorizedException` thrown (AC 3)
  - [x] Run `mvn test` — all tests pass; total: 15 (12 existing + 3 new service tests)

- [x] Task 3 — Compile and verify (AC: all)
  - [x] Run `mvn generate-sources compile` — no errors
  - [x] Run `mvn test` — all 15 tests pass (12 existing + 3 new service tests)
  - [x] Confirm `WebClientRequestException` is NOT caught in `PlaylistService`

### Review Findings

- [x] [Review][Patch] WebClient.block() has no timeout — add responseTimeout(Duration.ofSeconds(10)) on WebClient.Builder in WebClientConfig (decision: WebClient-level timeout) [WebClientConfig.java]
- [x] [Review][Patch] Null ytResponse NPE — .block() returns null on 204/empty body; next line playlistMapper.toPlaylistResponse(ytResponse) throws NPE [PlaylistService.java:120]
- [x] [Review][Defer] Virtual thread blocking concern — .block() on virtual threads is speculative low-risk; no virtual thread executor configured in this project [PlaylistService.java:119] — deferred, pre-existing
- [x] [Review][Defer] Non-401 YouTube errors (400, 403, 429) fall through to 500 catch-all — by design per Epic 4 architecture; not Epic 2 scope [PlaylistService.java:116] — deferred, pre-existing
- [x] [Review][Defer] API key visible in URI debug logs — architectural concern, standard for YouTube API integrations; suppressing keys from logs is a separate security hardening story [PlaylistService.java] — deferred, pre-existing

## Dev Notes

### CRITICAL: Dependency on Story 2.1

`PlaylistService` depends on `YTPlaylistItemsResponse` and `PlaylistMapper` which are created in Story 2.1. Story 2.1 MUST be implemented and compiled before this story.

- `YTPlaylistItemsResponse`: `src/main/java/com/example/youtubeplaylistapi/dto/youtube/YTPlaylistItemsResponse.java`
- `PlaylistMapper`: `src/main/java/com/example/youtubeplaylistapi/mapper/PlaylistMapper.java`

### PlaylistService Implementation

```java
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
        return playlistMapper.toPlaylistResponse(ytResponse);
    }
}
```

### WebClient Configuration

The `WebClient` bean is created in `WebClientConfig` with `baseUrl` set to `${youtube.api.base-url}` (value: `https://www.googleapis.com/youtube/v3`). The service uses path `/playlistItems` — do NOT include the base URL again in the path.

```java
// WebClientConfig (already implemented — do not modify)
@Bean
public WebClient webClient() {
    return WebClient.builder()
            .baseUrl(baseUrl)  // "https://www.googleapis.com/youtube/v3"
            .build();
}
```

The full URL called: `https://www.googleapis.com/youtube/v3/playlistItems?part=snippet&playlistId={id}&maxResults=25&key={apiKey}`.

### Error Handling Architecture

```
HTTP 401 from YouTube → .onStatus(401) → throw UpstreamUnauthorizedException → GlobalExceptionHandler → HTTP 401
WebClientRequestException (network) → propagates naturally → GlobalExceptionHandler → HTTP 503
HTTP 500/other from YouTube → not caught in service → GlobalExceptionHandler catch-all → HTTP 500
```

**DO NOT:**
```java
// ❌ Catch WebClientRequestException in service — bypasses GlobalExceptionHandler
try {
    return webClient.get()...block();
} catch (WebClientRequestException e) {
    throw new UpstreamConnectivityException(); // WRONG — propagate naturally
}

// ❌ Catch generic Exception in service
try { ... } catch (Exception e) { ... }  // WRONG
```

**DO:**
```java
// ✅ Only detect HTTP 401 via onStatus(); all other errors propagate naturally
.onStatus(status -> status.value() == 401,
    resp -> Mono.error(new UpstreamUnauthorizedException()))
```

### PlaylistServiceTest Implementation

```java
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
```

### Imports Needed

```java
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import com.example.youtubeplaylistapi.dto.youtube.YTPlaylistItemsResponse;
import com.example.youtubeplaylistapi.dto.PlaylistResponse;
import com.example.youtubeplaylistapi.exception.UpstreamUnauthorizedException;
import com.example.youtubeplaylistapi.mapper.PlaylistMapper;

// Test imports
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
```

### Generated DTOs

`PlaylistResponse` and `PlaylistItem` are generated from the OAS spec. Import from `com.example.youtubeplaylistapi.dto`. Run `mvn generate-sources` first if missing.

```java
// Correct imports
import com.example.youtubeplaylistapi.dto.PlaylistResponse;
// Generated location: target/generated-sources/openapi/com/example/youtubeplaylistapi/dto/PlaylistResponse.java
```

### Java & Maven Environment

```bash
export JAVA_HOME=/Users/admin/java/jdk-21.0.5+11/Contents/Home
export PATH=$JAVA_HOME/bin:/usr/bin:/bin
mvn generate-sources compile   # generate-sources first — PlaylistResponse comes from target/
mvn test                        # run full suite (expect 12 tests: 9 existing + 3 new)
```

### What This Story Does NOT Include

- **No `PlaylistController.java`** — that is Story 2.3
- **No pageToken format validation** — that is Story 2.3's responsibility
- **No WireMock `@SpringBootTest` integration tests** — those are Epic 6 (T01–T03)
- **No modification to `GlobalExceptionHandler`** — already handles all exceptions correctly
- **No modification to `WebClientConfig`** — bean is already properly configured

### Files to Create in This Story

| File | Type | Notes |
|------|------|-------|
| `src/main/java/com/example/youtubeplaylistapi/service/PlaylistService.java` | NEW | `@Service`, constructor-injected WebClient + PlaylistMapper |
| `src/test/java/com/example/youtubeplaylistapi/service/PlaylistServiceTest.java` | NEW | `@WireMockTest`, 3 test methods, no Spring context |

All paths are relative to `dest-spring-youtube-playlist-api/`.

### Anti-Patterns to Avoid

```java
// ❌ Hardcoding the full YouTube URL (base URL is set in WebClientConfig)
.uri("https://www.googleapis.com/youtube/v3/playlistItems?...")  // WRONG

// ❌ Catching WebClientRequestException in the service
try {
    return webClient.get()...block();
} catch (WebClientRequestException e) { ... }  // WRONG

// ❌ Returning YTPlaylistItemsResponse from getPlaylist() — controller expects PlaylistResponse
public YTPlaylistItemsResponse getPlaylist(...)  // WRONG — return PlaylistResponse

// ❌ Always adding pageToken even when null — causes "pageToken=null" in URL
builder.queryParam("pageToken", pageToken)  // WRONG when pageToken is null; use conditional
```

### Cross-Story Impact

- **Story 2.3 (PlaylistController)** will inject `PlaylistService` and call `getPlaylist(playlistId, pageToken)`. Method signature must remain stable.
- **Epic 6 (PlaylistEndpointTest)** will test the full stack through `PlaylistController → PlaylistService → YouTube API` (via WireMock). This service's error handling must be compatible with the handler established in Epic 4.

### References

- Epic 2 story definitions: [Source: docs/3-product-manager-artifacts/epics-and-stories/epic-2-playlist-retrieval-end-to-end.md#Story 2.2]
- WebClient `.onStatus()` pattern + blocking style: [Source: docs/4-architect-artifacts/architecture/04-implementation-patterns-consistency-rules.md#Process Patterns]
- Exception hierarchy (UpstreamUnauthorizedException): [Source: docs/4-architect-artifacts/architecture/04-implementation-patterns-consistency-rules.md#Process Patterns]
- Service layer responsibility: [Source: docs/4-architect-artifacts/architecture/05-project-structure-boundaries.md#Middle Boundary]
- WebClientConfig bean wiring: [Source: docs/4-architect-artifacts/architecture/05-project-structure-boundaries.md#South Boundary]
- `@Value("${youtube.api.key}")` pattern: [Source: docs/4-architect-artifacts/architecture/03-core-architectural-decisions.md#Authentication & Security]
- JAVA_HOME macOS path: [Source: docs/5-scrum-impl-artifacts/4-2-implement-upstream-and-unexpected-error-handling.md#Java & Maven Environment]

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6 (bmad-create-story 2026-06-21)
claude-sonnet-4-6 (bmad-dev-story 2026-06-21)

### Debug Log References

### Completion Notes List

- Created `PlaylistService` as `@Service` with constructor injection of `WebClient`, `PlaylistMapper`, `youtube.api.key`, and `youtube.api.max-results`. Builds URI conditionally (pageToken only appended when non-null). Handles HTTP 401 from YouTube via `.onStatus()` → `UpstreamUnauthorizedException`. `WebClientRequestException` is NOT caught — propagates naturally to `GlobalExceptionHandler`.
- Created `PlaylistServiceTest` using `@WireMockTest` (no Spring context). 3 tests: correct query params + return mapping (AC 1), pageToken forwarding (AC 2), HTTP 401 → `UpstreamUnauthorizedException` (AC 3).
- Final `mvn test`: 15 tests total (12 existing + 3 new), 0 failures, BUILD SUCCESS.

### File List

- `dest-spring-youtube-playlist-api/src/main/java/com/example/youtubeplaylistapi/service/PlaylistService.java` (NEW)
- `dest-spring-youtube-playlist-api/src/test/java/com/example/youtubeplaylistapi/service/PlaylistServiceTest.java` (NEW)

## Change Log

- 2026-06-21: Implemented Story 2.2 — created `PlaylistService` with YouTube API WebClient integration and `PlaylistServiceTest` with 3 WireMock tests. All 15 tests pass. BUILD SUCCESS.
- 2026-06-21: Code review patches applied — null `ytResponse` guard after `.block()`, 10-second `responseTimeout` added to `WebClientConfig` via `ReactorClientHttpConnector`. All 19 tests pass. Story done.

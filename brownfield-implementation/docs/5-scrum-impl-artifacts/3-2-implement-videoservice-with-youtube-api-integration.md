---
baseline_commit: b1318e8096366d5200c2744cb42f5ae8816d8e20
---

# Story 3.2: Implement VideoService with YouTube API Integration

Status: done

## Story

As an **internal developer**,
I want a `VideoService` that calls the YouTube Data API v3 `/videos` endpoint using WebClient,
so that real video metadata is retrieved from YouTube and made available to the controller.

## Acceptance Criteria

1. **Given** a valid `videoId`
   **When** `VideoService.getVideo()` is called
   **Then** a WebClient GET request is made to the configured base URL at path `/videos` with `part=snippet,statistics,contentDetails`, `id={videoId}`, and `key={YOUTUBE_API_KEY}`
   **And** the response is deserialized into `YTVideoDetailsResponse`, mapped via `VideoMapper`, and returned as `SongDetail`

2. **Given** the YouTube API returns HTTP 401
   **When** `VideoService.getVideo()` is called
   **Then** `UpstreamUnauthorizedException` is thrown (caught by `GlobalExceptionHandler` → HTTP 401)

3. **Given** the YouTube API is unreachable (connection timeout or reset)
   **When** `VideoService.getVideo()` is called
   **Then** `WebClientRequestException` propagates naturally to `GlobalExceptionHandler` → HTTP 503 (do NOT catch in service)

4. **Given** the YouTube API returns an empty `items[]` array (video does not exist)
   **When** `VideoService.getVideo()` is called
   **Then** `VideoNotFoundException` is thrown (caught by `GlobalExceptionHandler` → HTTP 404)

## Tasks / Subtasks

- [x] Task 1 — Create `VideoService` (AC: 1–4)
  - [x] Create `src/main/java/com/example/youtubeplaylistapi/service/VideoService.java`
  - [x] Annotate with `@Service`
  - [x] Constructor-inject: `WebClient webClient`, `VideoMapper videoMapper`, `@Value("${youtube.api.key}") String apiKey`
  - [x] Note: NO `maxResults` injection (unlike PlaylistService — `/videos` has no pagination)
  - [x] Public method: `SongDetail getVideo(String videoId)`
  - [x] Build URI: `uriBuilder.path("/videos").queryParam("part", "snippet,statistics,contentDetails").queryParam("id", videoId).queryParam("key", apiKey).build()`
  - [x] Chain `.retrieve().onStatus(status -> status.value() == 401, resp -> Mono.error(new UpstreamUnauthorizedException())).bodyToMono(YTVideoDetailsResponse.class).block()`
  - [x] Call `videoMapper.toSongDetail(ytResponse)` → store in local variable
  - [x] If mapper returns `null` → throw `new VideoNotFoundException()`
  - [x] Otherwise return the `SongDetail`
  - [x] Do NOT catch `WebClientRequestException` — let it propagate

- [x] Task 2 — Write unit tests (AC: 1–4)
  - [x] Create `src/test/java/com/example/youtubeplaylistapi/service/VideoServiceTest.java`
  - [x] Use `@WireMockTest` (JUnit 5 extension, dynamic port) — no Spring context, no `@SpringBootTest`
  - [x] Build `WebClient` and `VideoService` inline using `wmInfo.getHttpBaseUrl()` from `WireMockRuntimeInfo`
  - [x] `should_call_youtube_with_correct_params_and_return_song_detail()` — stub GET `/videos` with correct params, assert `SongDetail` returned with correct `videoId` and `channelName` (AC 1)
  - [x] `should_throw_upstream_unauthorized_when_youtube_returns_401()` — stub 401 response, assert `UpstreamUnauthorizedException` thrown (AC 2)
  - [x] `should_throw_video_not_found_when_items_array_is_empty()` — stub 200 with empty items[], assert `VideoNotFoundException` thrown (AC 4)
  - [x] Run `mvn test` — all tests pass

- [x] Task 3 — Compile and verify (AC: all)
  - [x] Run `mvn generate-sources compile` — no errors
  - [x] Run `mvn test` — all tests pass (no regressions)
  - [x] Confirm `WebClientRequestException` is NOT caught in `VideoService`
  - [x] Confirm `VideoNotFoundException` is thrown when `videoMapper.toSongDetail()` returns `null`

## Dev Notes

### CRITICAL: Dependency on Story 3.1

`VideoService` depends on `YTVideoDetailsResponse` and `VideoMapper` which are created in Story 3.1. Story 3.1 MUST be implemented and compiled before this story.

- `YTVideoDetailsResponse`: `src/main/java/com/example/youtubeplaylistapi/dto/youtube/YTVideoDetailsResponse.java`
- `VideoMapper`: `src/main/java/com/example/youtubeplaylistapi/mapper/VideoMapper.java`

### VideoService Implementation

```java
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
```

### Key Differences from PlaylistService (Story 2.2)

| | PlaylistService | VideoService |
|---|---|---|
| Path | `/playlistItems` | `/videos` |
| ID param | `playlistId` | `id` |
| Parts | `part=snippet` | `part=snippet,statistics,contentDetails` |
| maxResults | yes (`${youtube.api.max-results}`) | **no** (single video, no pagination) |
| pageToken | yes (conditional) | **no** |
| Null check | none (mapper never returns null) | **yes** → throw `VideoNotFoundException` |
| Return type | `PlaylistResponse` | `SongDetail` |

**The `part` parameter value is a single comma-separated string:**
```java
.queryParam("part", "snippet,statistics,contentDetails")  // CORRECT — single string
// NOT:
.queryParam("part", "snippet").queryParam("part", "statistics")  // WRONG — multiple params
```

### WebClient Configuration

The `WebClient` bean is created in `WebClientConfig` with `baseUrl = https://www.googleapis.com/youtube/v3`. Path `/videos` appends to this base.

Full URL called: `https://www.googleapis.com/youtube/v3/videos?part=snippet,statistics,contentDetails&id={videoId}&key={apiKey}`

### VideoNotFoundException — Already Exists

`VideoNotFoundException` was created in Epic 4 (Story 4.1) and already has a handler in `GlobalExceptionHandler`. Do NOT create or modify it.

```java
// Already exists at:
// src/main/java/com/example/youtubeplaylistapi/exception/VideoNotFoundException.java
// Already handled in GlobalExceptionHandler → HTTP 404
```

### Error Handling Flow

```
HTTP 401 from YouTube → .onStatus(401) → throw UpstreamUnauthorizedException → GlobalExceptionHandler → HTTP 401
WebClientRequestException (network) → propagates naturally → GlobalExceptionHandler → HTTP 503
Empty items[] from YouTube → videoMapper.toSongDetail() returns null → throw VideoNotFoundException → GlobalExceptionHandler → HTTP 404
```

**DO NOT:**
```java
// ❌ Catching WebClientRequestException in service
try { return webClient.get()...block(); } catch (WebClientRequestException e) { ... }  // WRONG

// ❌ Checking for null before calling mapper
if (ytResponse == null) throw new VideoNotFoundException();  // WRONG — let mapper handle it

// ❌ Not checking mapper result for null
SongDetail songDetail = videoMapper.toSongDetail(ytResponse);
return songDetail;  // WRONG — null would reach controller, causing NPE or wrong response
```

### VideoServiceTest Implementation

```java
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
```

### Java & Maven Environment

```bash
export JAVA_HOME=/Users/admin/java/jdk-21.0.5+11/Contents/Home
export PATH=$JAVA_HOME/bin:/usr/bin:/bin
mvn generate-sources compile   # generate-sources first — SongDetail comes from target/
mvn test                        # run full suite
```

### Files to Create in This Story

| File | Type | Notes |
|------|------|-------|
| `src/main/java/com/example/youtubeplaylistapi/service/VideoService.java` | NEW | `@Service`, constructor-injected WebClient + VideoMapper, 404 null check |
| `src/test/java/com/example/youtubeplaylistapi/service/VideoServiceTest.java` | NEW | `@WireMockTest`, 3 test methods, no Spring context |

All paths are relative to `dest-spring-youtube-playlist-api/`.

### What This Story Does NOT Include

- **No `VideoController.java`** — that is Story 3.3
- **No pageToken handling** — the `/videos` endpoint has no pagination
- **No `maxResults` configuration** — single video lookup, no page size
- **No modification to `GlobalExceptionHandler`** — `VideoNotFoundException` handler already exists from Epic 4
- **No modification to `WebClientConfig`** — same bean used by PlaylistService and VideoService
- **No WireMock `@SpringBootTest` integration tests** — those are Epic 6 (T04–T05)

### Anti-Patterns to Avoid

```java
// ❌ Using playlistId parameter name (wrong YouTube param for /videos endpoint)
.queryParam("playlistId", videoId)  // WRONG
.queryParam("id", videoId)          // CORRECT

// ❌ Injecting maxResults (not needed for /videos)
@Value("${youtube.api.max-results}") int maxResults  // WRONG for VideoService

// ❌ Not checking mapper null return — propagates null to controller
SongDetail songDetail = videoMapper.toSongDetail(ytResponse);
return songDetail;  // WRONG if null

// ❌ Throwing VideoNotFoundException before calling mapper — mapper may have valid data
if (ytResponse.getItems().isEmpty()) throw new VideoNotFoundException();
// WRONG — call mapper first, check its return value

// ❌ Multiple part params instead of comma-separated string
.queryParam("part", "snippet").queryParam("part", "statistics")  // WRONG
.queryParam("part", "snippet,statistics,contentDetails")          // CORRECT
```

### Cross-Story Impact

- **Story 3.3 (VideoController)** will inject `VideoService` and call `getVideo(videoId)`. The method will either return `SongDetail` or throw `VideoNotFoundException`. The controller does NOT need to handle null.
- **Epic 6 (VideoEndpointTest T04–T05)** will test: T04 happy path (200 with SongDetail), T05 video not found (404 ErrorResponse).

### References

- Epic 3 story definitions: [Source: docs/3-product-manager-artifacts/epics-and-stories/epic-3-video-detail-retrieval-end-to-end.md#Story 3.2]
- WebClient `.onStatus()` pattern: [Source: docs/4-architect-artifacts/architecture/04-implementation-patterns-consistency-rules.md#Process Patterns]
- Exception hierarchy: [Source: docs/4-architect-artifacts/architecture/04-implementation-patterns-consistency-rules.md#Process Patterns]
- Service layer responsibility: [Source: docs/4-architect-artifacts/architecture/05-project-structure-boundaries.md#Middle Boundary]
- `VideoNotFoundException` already implemented: [Source: docs/5-scrum-impl-artifacts/4-1-implement-errorresponse-dto-and-globalexceptionhandler-for-request-level-errors.md]
- Story 2.2 as reference pattern: [Source: docs/5-scrum-impl-artifacts/2-2-implement-playlistservice-with-youtube-api-integration.md]
- JAVA_HOME macOS path: [Source: docs/5-scrum-impl-artifacts/4-2-implement-upstream-and-unexpected-error-handling.md#Java & Maven Environment]

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6 (bmad-create-story 2026-06-21)

### Debug Log References

None.

### Completion Notes List

- Task 1: `VideoService` created as `@Service`; constructor-injects `WebClient`, `VideoMapper`, `@Value("${youtube.api.key}")`. Builds `/videos` URI with `part=snippet,statistics,contentDetails`, `id`, `key`. `.onStatus(401)` → `UpstreamUnauthorizedException`. Mapper null-check → `VideoNotFoundException`. `WebClientRequestException` propagates naturally.
- Task 2: `VideoServiceTest` with `@WireMockTest` — 3 tests: correct params + SongDetail (AC 1), 401 → `UpstreamUnauthorizedException` (AC 2), empty items[] → `VideoNotFoundException` (AC 4). All pass.
- Task 3: `mvn test` — 25 tests run, 0 failures, 0 errors. No regressions.

### File List

- `dest-spring-youtube-playlist-api/src/main/java/com/example/youtubeplaylistapi/service/VideoService.java` (NEW)
- `dest-spring-youtube-playlist-api/src/test/java/com/example/youtubeplaylistapi/service/VideoServiceTest.java` (NEW)

### Change Log

- 2026-06-21: Story 3.2 complete — `VideoService` and `VideoServiceTest` implemented; 3 new WireMock tests pass; full suite 25/25 green.

## Senior Developer Review (AI)

**Review date:** 2026-06-21
**Reviewer layers:** Blind Hunter, Edge Case Hunter, Acceptance Auditor
**Scope:** Epic 3 (Stories 3.1–3.3) + Epic 5 (Stories 5.1–5.2)

### Review Findings

- [x] [Review][Defer] `.block()` called on potentially reactive thread [`VideoService.java:115`] — deferred, pre-existing pattern identical to `PlaylistService`; project uses synchronous WebClient throughout; no evidence of reactive dispatch in this application
- [x] [Review][Defer] YouTube non-401 error statuses (403, 429, 500) not intercepted [`VideoService.java:112–113`] — deferred, consistent with PlaylistService pattern; GlobalExceptionHandler catches remaining WebClientResponseException as HTTP 500; expanding scope is out of story spec

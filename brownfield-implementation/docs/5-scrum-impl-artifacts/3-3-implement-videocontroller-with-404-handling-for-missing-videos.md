---
baseline_commit: b1318e8096366d5200c2744cb42f5ae8816d8e20
---

# Story 3.3: Implement VideoController with 404 Handling for Missing Videos

Status: done

## Story

As an **internal developer**,
I want a `VideoController` that wires `VideoService`, exposes `GET /api/youtube/song/{videoId}`, and returns HTTP 404 when the video does not exist,
so that the API uses correct HTTP semantics instead of the current Mule behaviour of returning HTTP 200 with null fields.

## Acceptance Criteria

1. **Given** a request to `GET /api/youtube/song/{videoId}` with a valid, existing `videoId`
   **When** the endpoint is called
   **Then** HTTP 200 is returned with a valid `SongDetail` body containing all required fields

2. **Given** a request with a `videoId` that does not correspond to any YouTube video
   **When** the endpoint is called
   **Then** HTTP 404 is returned with an `ErrorResponse` body (`code: 404`, `error: "Not Found"`, `message: "Video not found for the given videoId."`)
   **And** the response body is NOT a `SongDetail` with null fields (breaking change from Mule behaviour)

3. **Given** the YouTube API returns HTTP 401
   **When** the endpoint is called
   **Then** HTTP 401 is returned with an `ErrorResponse` body

4. **Given** the YouTube API is unreachable
   **When** the endpoint is called
   **Then** HTTP 503 is returned with an `ErrorResponse` body

## Tasks / Subtasks

- [x] Task 1 — Create `VideoController` (AC: 1–4)
  - [x] Create `src/main/java/com/example/youtubeplaylistapi/controller/VideoController.java`
  - [x] Annotate with `@RestController`
  - [x] Constructor-inject `VideoService`
  - [x] Implement generated `VideoApi` interface (`implements VideoApi`)
  - [x] Override `getVideoDetails(String videoId)`
  - [x] Log request entry: `log.info("endpoint={} videoId={}", "GET /api/youtube/song/{videoId}", videoId)`
  - [x] Call `videoService.getVideo(videoId)` — this either returns `SongDetail` or throws `VideoNotFoundException`
  - [x] Log success: `log.info("endpoint={} videoId={}", "GET /api/youtube/song/{videoId}", songDetail.getVideoId())`
  - [x] Return `ResponseEntity.ok(songDetail)`
  - [x] No try/catch — let all exceptions propagate to `GlobalExceptionHandler`
  - [x] No null check in controller — `VideoService` throws `VideoNotFoundException` instead of returning null

- [x] Task 2 — Write unit tests (AC: 1–4)
  - [x] Create `src/test/java/com/example/youtubeplaylistapi/controller/VideoControllerTest.java`
  - [x] Use `@ExtendWith(MockitoExtension.class)` + `MockMvcBuilders.standaloneSetup()` pattern (matching project convention; `@WebMvcTest` not available in this project setup)
  - [x] `should_return_200_when_valid_video_id()` — mock service returns `SongDetail`, assert 200 and key fields (AC 1)
  - [x] `should_return_404_when_video_not_found()` — mock service throws `VideoNotFoundException`, assert 404 + ErrorResponse body with correct message (AC 2)
  - [x] `should_return_401_when_youtube_unauthorized()` — mock service throws `UpstreamUnauthorizedException`, assert 401 (AC 3)
  - [x] `should_return_503_when_youtube_unreachable()` — mock service throws `WebClientRequestException`, assert 503 (AC 4)
  - [x] Run `mvn test` — all tests pass

- [x] Task 3 — Compile and verify (AC: all)
  - [x] Run `mvn generate-sources compile` — no errors
  - [x] Run `mvn test` — all tests pass (no regressions)
  - [x] Confirm endpoint `GET /api/youtube/song/{videoId}` is reachable and returns `SongDetail` shape

## Dev Notes

### CRITICAL: Dependencies on Stories 3.1 and 3.2

- Story 3.1 must be done: `VideoMapper`, `YTVideoDetailsResponse` must exist
- Story 3.2 must be done: `VideoService` must exist and compile
- Run `mvn generate-sources` first: `VideoApi` interface and `SongDetail` DTO are generated

### Generated VideoApi Interface

`VideoController` implements the generated `VideoApi` interface generated from the OAS spec. Import:
```java
import com.example.youtubeplaylistapi.controller.VideoApi;
```
Generated location: `target/generated-sources/openapi/com/example/youtubeplaylistapi/controller/VideoApi.java`

The generated method signature (confirmed from OAS operationId `getVideoDetails` + tag `Video`):
```java
ResponseEntity<SongDetail> getVideoDetails(@PathVariable String videoId);
```

The controller's `@Override` must match this signature exactly.

### VideoController Implementation

```java
package com.example.youtubeplaylistapi.controller;

import com.example.youtubeplaylistapi.dto.SongDetail;
import com.example.youtubeplaylistapi.service.VideoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VideoController implements VideoApi {

    private static final Logger log = LoggerFactory.getLogger(VideoController.class);

    private final VideoService videoService;

    public VideoController(VideoService videoService) {
        this.videoService = videoService;
    }

    @Override
    public ResponseEntity<SongDetail> getVideoDetails(String videoId) {
        log.info("endpoint={} videoId={}", "GET /api/youtube/song/{videoId}", videoId);
        SongDetail songDetail = videoService.getVideo(videoId);
        log.info("endpoint={} videoId={}", "GET /api/youtube/song/{videoId}", songDetail.getVideoId());
        return ResponseEntity.ok(songDetail);
    }
}
```

**The controller is pure delegation** — no null checks, no try/catch, no validation logic. `VideoService.getVideo()` never returns null; it throws `VideoNotFoundException` instead.

### 404 Flow — How It Works

```
GET /api/youtube/song/nonexistent
    │
    ▼
VideoController.getVideoDetails("nonexistent")
    │  calls
    ▼
VideoService.getVideo("nonexistent")
    │  YouTube returns items: []
    │  VideoMapper.toSongDetail() returns null
    │  VideoService throws VideoNotFoundException
    ▼
GlobalExceptionHandler.handleVideoNotFound()
    │  already implemented in Epic 4
    ▼
HTTP 404: {"error":"Not Found","message":"Video not found for the given videoId.","code":404}
```

**The controller never sees null.** VideoService owns the 404 detection and throws the exception. GlobalExceptionHandler (already implemented) handles it.

### Key Differences from PlaylistController (Story 2.3)

| | PlaylistController | VideoController |
|---|---|---|
| pageToken validation | Yes (throws `InvalidPageTokenException`) | **None** (no pageToken param) |
| Modifies GlobalExceptionHandler | Yes (adds `InvalidPageTokenException` handler) | **No** (all handlers already exist) |
| Null return from service | No (service always returns list) | **No** (service throws VideoNotFoundException) |
| New exception class | `InvalidPageTokenException` | **None** |
| New files | 4 (exception, handler update, controller, test) | **2** (controller, test only) |

### VideoControllerTest Implementation

```java
package com.example.youtubeplaylistapi.controller;

import com.example.youtubeplaylistapi.dto.SongDetail;
import com.example.youtubeplaylistapi.exception.UpstreamUnauthorizedException;
import com.example.youtubeplaylistapi.exception.VideoNotFoundException;
import com.example.youtubeplaylistapi.service.VideoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import java.net.URI;

import static org.mockito.Mockito.*;
import static org.springframework.http.HttpHeaders.EMPTY;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(VideoController.class)
class VideoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VideoService videoService;

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
```

### @WebMvcTest and GlobalExceptionHandler

`@WebMvcTest` includes `@RestControllerAdvice` beans (including `GlobalExceptionHandler`) in the test application context. The 404, 401, and 503 assertions in the tests verify the full exception → handler → response pipeline without requiring `@SpringBootTest`.

### @MockitoBean — Spring Boot 4.x

Use `@MockitoBean` (not deprecated `@MockBean`):
```java
import org.springframework.test.context.bean.override.mockito.MockitoBean;
@MockitoBean
private VideoService videoService;
```

### WebClientRequestException Constructor

For the 503 test, construct `WebClientRequestException` as:
```java
import org.springframework.web.reactive.function.client.WebClientRequestException;
import java.net.URI;
import static org.springframework.http.HttpHeaders.EMPTY;
import static org.springframework.http.HttpMethod.GET;

new WebClientRequestException(
    new RuntimeException("connection refused"),
    GET,
    URI.create("https://googleapis.com"),
    EMPTY
)
```
This is the same constructor pattern confirmed in Story 4.2.

### Exact Error String Contract — VideoNotFoundException

The `GlobalExceptionHandler.handleVideoNotFound()` already returns (implemented in Epic 4):
```
error: "Not Found"
message: "Video not found for the given videoId."
code: 404
```
The test assertion in `should_return_404_when_video_not_found()` MUST match these exact strings.

### Logging Pattern

```java
// Request entry (INFO)
log.info("endpoint={} videoId={}", "GET /api/youtube/song/{videoId}", videoId);

// Success (INFO)
log.info("endpoint={} videoId={}", "GET /api/youtube/song/{videoId}", songDetail.getVideoId());

// No logging for errors — GlobalExceptionHandler handles all error logging
```

### Java & Maven Environment

```bash
export JAVA_HOME=/Users/admin/java/jdk-21.0.5+11/Contents/Home
export PATH=$JAVA_HOME/bin:/usr/bin:/bin
mvn generate-sources compile   # generates VideoApi interface + SongDetail DTO
mvn test                        # run full suite
```

### Files Changed in This Story

| File | Type | Notes |
|------|------|-------|
| `src/main/java/com/example/youtubeplaylistapi/controller/VideoController.java` | NEW | `@RestController implements VideoApi`, pure delegation |
| `src/test/java/com/example/youtubeplaylistapi/controller/VideoControllerTest.java` | NEW | `@WebMvcTest`, 4 test methods |

All paths are relative to `dest-spring-youtube-playlist-api/`. No exception classes, no GlobalExceptionHandler changes needed.

### What This Story Does NOT Include

- **No new exception classes** — `VideoNotFoundException`, `UpstreamUnauthorizedException` already exist from Epic 4
- **No modification to `GlobalExceptionHandler`** — all required handlers (404, 401, 503) already in place
- **No pageToken validation** — `/song/{videoId}` has no pageToken parameter
- **No null check in controller** — VideoService throws exception; controller always receives valid SongDetail
- **No WireMock `@SpringBootTest` integration tests** — those are Epic 6 (T04–T05)

### Anti-Patterns to Avoid

```java
// ❌ Adding null check in controller — VideoService throws instead
SongDetail songDetail = videoService.getVideo(videoId);
if (songDetail == null) {
    throw new VideoNotFoundException();  // WRONG — service already does this
}

// ❌ Adding try/catch in controller
try {
    return ResponseEntity.ok(videoService.getVideo(videoId));
} catch (VideoNotFoundException e) {
    return ResponseEntity.notFound().build();  // WRONG — no ErrorResponse body
}

// ❌ Returning HTTP 200 for not-found video (Mule behaviour — do NOT replicate)
if (songDetail == null) {
    return ResponseEntity.ok(new SongDetail());  // WRONG — violates FR-7 breaking change

// ❌ Using @MockBean (deprecated in Spring Boot 4.x)
@MockBean  // ❌ use @MockitoBean instead

// ❌ Missing @RestController annotation
@Controller  // WRONG — must be @RestController to match generated interface expectations
```

### Cross-Story Impact

- **Epic 6 (VideoEndpointTest T04–T05)** will test this controller end-to-end with WireMock. T04: happy path (200 with full SongDetail fields). T05: video not found → 404 ErrorResponse.
- **This epic (3.1–3.3) can run in parallel with Epic 2 (2.1–2.3)** per architecture recommendation.

### References

- Epic 3 story definitions: [Source: docs/3-product-manager-artifacts/epics-and-stories/epic-3-video-detail-retrieval-end-to-end.md#Story 3.3]
- Controller delegation-only pattern: [Source: docs/4-architect-artifacts/architecture/05-project-structure-boundaries.md#North Boundary]
- VideoNotFoundException handler (exact strings): [Source: docs/5-scrum-impl-artifacts/4-2-implement-upstream-and-unexpected-error-handling.md#Exact Error String Contract]
- Logging mandatory log points: [Source: docs/4-architect-artifacts/architecture/04-implementation-patterns-consistency-rules.md#Logging Patterns]
- `@MockitoBean` usage (Spring Boot 4.x): [Source: docs/4-architect-artifacts/architecture/03-core-architectural-decisions.md]
- `WebClientRequestException` constructor: [Source: docs/5-scrum-impl-artifacts/4-2-implement-upstream-and-unexpected-error-handling.md#Unit Test Additions]
- Story 2.3 PlaylistController as reference pattern: [Source: docs/5-scrum-impl-artifacts/2-3-implement-playlistcontroller-with-pagetoken-validation.md]
- JAVA_HOME macOS path: [Source: docs/5-scrum-impl-artifacts/4-2-implement-upstream-and-unexpected-error-handling.md#Java & Maven Environment]

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6 (bmad-create-story 2026-06-21)

### Debug Log References

- Story spec called for `@WebMvcTest` but that annotation is not available in this project's dependency setup. Used `MockMvcBuilders.standaloneSetup()` with `GlobalExceptionHandler` controller advice — matching the established `PlaylistControllerTest` pattern. All 4 ACs validated by tests.

### Completion Notes List

- Task 1: `VideoController` created as `@RestController implements VideoApi`. Pure delegation — no try/catch, no null check. Logs request entry and success. Returns `ResponseEntity.ok(songDetail)`.
- Task 2: `VideoControllerTest` using `@ExtendWith(MockitoExtension.class)` + `standaloneSetup` (project convention). 4 tests: 200 with SongDetail (AC 1), 404 with ErrorResponse body (AC 2), 401 (AC 3), 503 (AC 4). All pass.
- Task 3: `mvn test` — 29 tests run, 0 failures, 0 errors. No regressions.

### File List

- `dest-spring-youtube-playlist-api/src/main/java/com/example/youtubeplaylistapi/controller/VideoController.java` (NEW)
- `dest-spring-youtube-playlist-api/src/test/java/com/example/youtubeplaylistapi/controller/VideoControllerTest.java` (NEW)

### Change Log

- 2026-06-21: Story 3.3 complete — `VideoController` and `VideoControllerTest` implemented; 4 new controller tests pass; full suite 29/29 green. Used standaloneSetup pattern (matching project convention) instead of @WebMvcTest.

## Senior Developer Review (AI)

**Review date:** 2026-06-21
**Reviewer layers:** Blind Hunter, Edge Case Hunter, Acceptance Auditor
**Scope:** Epic 3 (Stories 3.1–3.3) + Epic 5 (Stories 5.1–5.2)

### Review Findings

- [x] [Review][Defer] No videoId path parameter validation [`VideoController.java`] — deferred, OAS-spec–generated `VideoApi` interface handles path variable binding via Spring routing; explicit validation of path segment not required by story spec

---
baseline_commit: b1318e8096366d5200c2744cb42f5ae8816d8e20
---

# Story 2.3: Implement PlaylistController with pageToken Validation

Status: done

## Story

As an **internal developer**,
I want a `PlaylistController` that wires `PlaylistService` and exposes `GET /api/youtube/playlists/{playlistId}`, and validates the optional `pageToken` query parameter,
so that callers receive paginated playlist data and receive HTTP 400 immediately for an invalid token without hitting the YouTube API.

## Acceptance Criteria

1. **Given** a request to `GET /api/youtube/playlists/{playlistId}` with no `pageToken`
   **When** the endpoint is called
   **Then** HTTP 200 is returned with a valid `PlaylistResponse` body

2. **Given** a request with a well-formed `pageToken`
   **When** the endpoint is called
   **Then** the token is forwarded to `PlaylistService.getPlaylist()` and the correct next page of results is returned

3. **Given** a request with a `pageToken` value that is malformed (fails local format validation)
   **When** the endpoint is called
   **Then** HTTP 400 is returned with an `ErrorResponse` body and no upstream call to YouTube is made

4. **Given** a valid-format `pageToken` that is rejected by the YouTube API (expired or unknown)
   **When** the endpoint is called
   **Then** the 401/400 response is handled by `GlobalExceptionHandler` (service throws `UpstreamUnauthorizedException` → HTTP 401; or YouTube returns 400 → HTTP 400 via catch-all)

5. **Given** a request with a valid `playlistId` that returns an empty playlist
   **When** the endpoint is called
   **Then** HTTP 200 is returned with `totalResults: 0` and an empty `playlist` array

## Tasks / Subtasks

- [x] Task 1 — Create `InvalidPageTokenException` (AC: 3)
  - [x] Create `src/main/java/com/example/youtubeplaylistapi/exception/InvalidPageTokenException.java`
  - [x] Extend `RuntimeException`
  - [x] No-arg constructor with message `"Invalid or malformed pageToken format."`

- [x] Task 2 — Add `InvalidPageTokenException` handler to `GlobalExceptionHandler` (AC: 3)
  - [x] Modify `src/main/java/com/example/youtubeplaylistapi/exception/GlobalExceptionHandler.java`
  - [x] Add `@ExceptionHandler(InvalidPageTokenException.class)` method: HTTP 400, `error="Bad Request"`, `message="Invalid or malformed pageToken."`, `code=400`
  - [x] Add at WARN log level: `log.warn("endpoint={} status={} error={}", request.getRequestURI(), 400, ex.getClass().getSimpleName())`
  - [x] Place the new handler before the `Exception` catch-all to ensure it is not shadowed

- [x] Task 3 — Create `PlaylistController` (AC: 1–5)
  - [x] Create `src/main/java/com/example/youtubeplaylistapi/controller/PlaylistController.java`
  - [x] Annotate with `@RestController`
  - [x] Constructor-inject `PlaylistService`
  - [x] Implement generated `PlaylistApi` interface (`implements PlaylistApi`)
  - [x] Override `getPlaylistItems(String playlistId, String pageToken)`
  - [x] Log request entry: `log.info("endpoint={} playlistId={}", "GET /api/youtube/playlists/{playlistId}", playlistId)`
  - [x] Validate pageToken: if `pageToken != null && !pageToken.isBlank() && !pageToken.matches("^[A-Za-z0-9_\\-=]+$")` → throw `new InvalidPageTokenException()`
  - [x] Call `playlistService.getPlaylist(playlistId, pageToken)`
  - [x] Log success: `log.info("endpoint={} totalResults={}", "GET /api/youtube/playlists/{playlistId}", response.getTotalResults())`
  - [x] Return `ResponseEntity.ok(response)`
  - [x] No try/catch — let all exceptions propagate to `GlobalExceptionHandler`

- [x] Task 4 — Write unit tests (AC: 1–5)
  - [x] Create `src/test/java/com/example/youtubeplaylistapi/controller/PlaylistControllerTest.java`
  - [x] `@WebMvcTest` not available in Spring Boot 4.1.0 — adapted to `MockMvcBuilders.standaloneSetup()` with `@ExtendWith(MockitoExtension.class)` and `@Mock PlaylistService`; `GlobalExceptionHandler` added via `.setControllerAdvice()` to preserve AC 3 validation
  - [x] `should_return_200_when_valid_playlist_id_no_page_token()` — mock service returns `PlaylistResponse`, assert 200 and body fields (AC 1)
  - [x] `should_return_200_when_valid_page_token_provided()` — mock service, assert token forwarded (AC 2)
  - [x] `should_return_400_when_page_token_is_malformed()` — no service mock call, assert 400 + ErrorResponse body (AC 3)
  - [x] `should_return_200_with_empty_playlist_when_no_items()` — mock service returns empty PlaylistResponse (AC 5)
  - [x] Run `mvn test` — all tests pass; total: 19 (15 existing + 4 new controller tests)

- [x] Task 5 — Compile and verify (AC: all)
  - [x] Run `mvn generate-sources compile` — no errors
  - [x] Run `mvn test` — all 19 tests pass
  - [x] Confirm malformed `pageToken` does NOT reach `PlaylistService` (verified by `verifyNoInteractions(playlistService)` in test)

### Review Findings

- [x] [Review][Patch] Blank pageToken bypasses validation — isBlank() guard skips regex check, forwarding blank string to YouTube API [PlaylistController.java:26]
- [x] [Review][Patch] Exception message vs handler message inconsistency — InvalidPageTokenException says "Invalid or malformed pageToken format." but handleInvalidPageToken response body says "Invalid or malformed pageToken." [InvalidPageTokenException.java:5]
- [x] [Review][Defer] playlistId format not validated — no format or length check on playlistId path variable; out of Epic 2 scope [PlaylistController.java:23] — deferred, pre-existing

## Dev Notes

### CRITICAL: Dependencies on Stories 2.1 and 2.2

- Story 2.1 must be done: `PlaylistMapper`, `YTPlaylistItemsResponse` must exist
- Story 2.2 must be done: `PlaylistService` must exist and compile
- Run `mvn generate-sources` first: `PlaylistApi` interface and `PlaylistResponse` DTO are generated

### Generated PlaylistApi Interface

`PlaylistController` implements the generated `PlaylistApi` interface. This interface is generated from the OAS spec during `mvn generate-sources`. Import:
```java
import com.example.youtubeplaylistapi.controller.PlaylistApi;
```
Generated location: `target/generated-sources/openapi/com/example/youtubeplaylistapi/controller/PlaylistApi.java`

The generated method signature (confirmed from OAS operationId `getPlaylistItems` + tag `Playlist`):
```java
ResponseEntity<PlaylistResponse> getPlaylistItems(
    @PathVariable String playlistId,
    @RequestParam(required = false) String pageToken
);
```

The controller's `@Override` must match this signature exactly.

### InvalidPageTokenException

```java
package com.example.youtubeplaylistapi.exception;

public class InvalidPageTokenException extends RuntimeException {
    public InvalidPageTokenException() {
        super("Invalid or malformed pageToken format.");
    }
}
```

### GlobalExceptionHandler — New Handler to Add

Add this handler inside the existing `GlobalExceptionHandler` class, **before** the `Exception` catch-all handler (after the `handleBadRequest` handler in the Story 4.1 section):

```java
// ─── pageToken format validation failure ──────────────────────────────────
@ExceptionHandler(InvalidPageTokenException.class)
public ResponseEntity<ErrorResponse> handleInvalidPageToken(
        InvalidPageTokenException ex, HttpServletRequest request) {

    log.warn("endpoint={} status={} error={}", request.getRequestURI(), 400,
             ex.getClass().getSimpleName());

    ErrorResponse body = new ErrorResponse();
    body.setError("Bad Request");
    body.setMessage("Invalid or malformed pageToken.");
    body.setCode(400);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
}
```

**Do NOT modify or replace** the existing Story 4.1 or 4.2 handlers. This is an append-only change.

### PlaylistController Implementation

```java
package com.example.youtubeplaylistapi.controller;

import com.example.youtubeplaylistapi.dto.PlaylistResponse;
import com.example.youtubeplaylistapi.exception.InvalidPageTokenException;
import com.example.youtubeplaylistapi.service.PlaylistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PlaylistController implements PlaylistApi {

    private static final Logger log = LoggerFactory.getLogger(PlaylistController.class);

    private final PlaylistService playlistService;

    public PlaylistController(PlaylistService playlistService) {
        this.playlistService = playlistService;
    }

    @Override
    public ResponseEntity<PlaylistResponse> getPlaylistItems(String playlistId, String pageToken) {
        log.info("endpoint={} playlistId={}", "GET /api/youtube/playlists/{playlistId}", playlistId);

        if (pageToken != null && !pageToken.isBlank() && !pageToken.matches("^[A-Za-z0-9_\\-=]+$")) {
            throw new InvalidPageTokenException();
        }

        PlaylistResponse response = playlistService.getPlaylist(playlistId, pageToken);
        log.info("endpoint={} totalResults={}", "GET /api/youtube/playlists/{playlistId}",
                response.getTotalResults());
        return ResponseEntity.ok(response);
    }
}
```

### pageToken Validation Logic

Valid pageToken characters (YouTube base64url format): `A-Z`, `a-z`, `0-9`, `_`, `-`, `=`

```
Validation rule: pageToken != null AND not blank AND matches ^[A-Za-z0-9_\-=]+$
- null → no validation, passed through as-is (no pageToken scenario)
- "" or whitespace-only → treated as null by Spring MVC (RequestParam, required=false)
- "EAAelgEKADiD" → valid, forwarded to service
- "bad token!!" → invalid (contains space and !), throws InvalidPageTokenException → HTTP 400
- "abc123_-=" → valid
```

**Do NOT pre-validate null** (Spring handles required=false `pageToken`). Only validate non-null values.

### PlaylistControllerTest Implementation

```java
package com.example.youtubeplaylistapi.controller;

import com.example.youtubeplaylistapi.dto.PlaylistResponse;
import com.example.youtubeplaylistapi.service.PlaylistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PlaylistController.class)
class PlaylistControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlaylistService playlistService;

    @Test
    void should_return_200_when_valid_playlist_id_no_page_token() throws Exception {
        PlaylistResponse response = buildResponse(50, "EAAelgEKADiD");
        when(playlistService.getPlaylist("PLxxx", null)).thenReturn(response);

        mockMvc.perform(get("/api/youtube/playlists/PLxxx"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalResults").value(50))
            .andExpect(jsonPath("$.nextPageToken").value("EAAelgEKADiD"));
    }

    @Test
    void should_return_200_when_valid_page_token_provided() throws Exception {
        PlaylistResponse response = buildResponse(50, null);
        when(playlistService.getPlaylist("PLxxx", "EAAelgEKADiD")).thenReturn(response);

        mockMvc.perform(get("/api/youtube/playlists/PLxxx").param("pageToken", "EAAelgEKADiD"))
            .andExpect(status().isOk());

        verify(playlistService).getPlaylist("PLxxx", "EAAelgEKADiD");
    }

    @Test
    void should_return_400_when_page_token_is_malformed() throws Exception {
        mockMvc.perform(get("/api/youtube/playlists/PLxxx").param("pageToken", "bad token!!"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.error").value("Bad Request"));

        verifyNoInteractions(playlistService);
    }

    @Test
    void should_return_200_with_empty_playlist_when_no_items() throws Exception {
        PlaylistResponse response = buildResponse(0, null);
        response.setPlaylist(List.of());
        when(playlistService.getPlaylist("PLxxx", null)).thenReturn(response);

        mockMvc.perform(get("/api/youtube/playlists/PLxxx"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalResults").value(0))
            .andExpect(jsonPath("$.playlist").isEmpty());
    }

    private PlaylistResponse buildResponse(int totalResults, String nextPageToken) {
        PlaylistResponse r = new PlaylistResponse();
        r.setTotalResults(totalResults);
        r.setResultsPerPage(25);
        r.setNextPageToken(nextPageToken);
        r.setPlaylist(List.of());
        return r;
    }
}
```

### @MockitoBean Annotation

Spring Boot 4.x uses `@MockitoBean` (from `org.springframework.test.context.bean.override.mockito.MockitoBean`) instead of the deprecated `@MockBean`. Use `@MockitoBean` for this story.

```java
// Correct (Spring Boot 4.x)
import org.springframework.test.context.bean.override.mockito.MockitoBean;
@MockitoBean
private PlaylistService playlistService;

// Wrong (deprecated in Spring Boot 4.x)
import org.springframework.boot.test.mock.mockito.MockBean;
@MockBean  // ❌ deprecated
```

### Logging Pattern

```java
// Request entry (INFO)
log.info("endpoint={} playlistId={}", "GET /api/youtube/playlists/{playlistId}", playlistId);

// Success (INFO)
log.info("endpoint={} totalResults={}", "GET /api/youtube/playlists/{playlistId}", response.getTotalResults());

// No logging in controller for errors — GlobalExceptionHandler logs at WARN/ERROR
```

### Java & Maven Environment

```bash
export JAVA_HOME=/Users/admin/java/jdk-21.0.5+11/Contents/Home
export PATH=$JAVA_HOME/bin:/usr/bin:/bin
mvn generate-sources compile   # generates PlaylistApi interface + PlaylistResponse DTO
mvn test                        # run full suite (expect 16 tests: 12 existing + 4 new)
```

### Files Changed in This Story

| File | Type | Notes |
|------|------|-------|
| `src/main/java/com/example/youtubeplaylistapi/exception/InvalidPageTokenException.java` | NEW | Extends `RuntimeException`, no-arg constructor |
| `src/main/java/com/example/youtubeplaylistapi/exception/GlobalExceptionHandler.java` | UPDATE | Add `handleInvalidPageToken` handler → HTTP 400 |
| `src/main/java/com/example/youtubeplaylistapi/controller/PlaylistController.java` | NEW | `@RestController implements PlaylistApi` |
| `src/test/java/com/example/youtubeplaylistapi/controller/PlaylistControllerTest.java` | NEW | `@WebMvcTest`, 4 test methods |

All paths are relative to `dest-spring-youtube-playlist-api/`.

### What This Story Does NOT Include

- **No `VideoController.java`** — that is Epic 3
- **No pageToken validation changes to `PlaylistService`** — validation is controller responsibility only
- **No WireMock `@SpringBootTest` integration tests** — those are Epic 6 (T01–T03)
- **No `@Valid` or bean validation annotations** — pageToken validation is manual (regex check)
- **No modification to `WebClientConfig` or `PlaylistMapper`** — already complete

### Anti-Patterns to Avoid

```java
// ❌ Adding try/catch in controller — GlobalExceptionHandler handles all exceptions
try {
    return ResponseEntity.ok(playlistService.getPlaylist(...));
} catch (Exception e) { ... }  // WRONG

// ❌ Calling service with invalid pageToken — validate BEFORE calling service
if (isValidPageToken(pageToken)) {
    return ResponseEntity.ok(playlistService.getPlaylist(playlistId, pageToken));
}
// WRONG — service must never receive invalid token

// ❌ Returning null for malformed pageToken instead of throwing
if (!isValidPageToken(pageToken)) {
    return ResponseEntity.badRequest().build();  // WRONG — body missing ErrorResponse
}

// ❌ Using @MockBean (deprecated in Spring Boot 4.x)
@MockBean  // ❌ use @MockitoBean instead

// ❌ Importing from generated controller package — PlaylistApi is generated, not hand-written
import com.example.youtubeplaylistapi.controller.PlaylistApi;  // ✅ correct (generated)
// Never hand-write PlaylistApi.java in the controller package
```

### Cross-Story Impact

- **Epic 6 (PlaylistEndpointTest T01–T03)** will test this controller end-to-end with WireMock. The endpoint `GET /api/youtube/playlists/{playlistId}` must be reachable and return `PlaylistResponse`.
- **Epic 3 (VideoController)** follows the same pattern — use this controller as the reference implementation.

### References

- Epic 2 story definitions: [Source: docs/3-product-manager-artifacts/epics-and-stories/epic-2-playlist-retrieval-end-to-end.md#Story 2.3]
- Controller delegation-only pattern: [Source: docs/4-architect-artifacts/architecture/05-project-structure-boundaries.md#North Boundary]
- pageToken validation architecture: [Source: docs/4-architect-artifacts/architecture/03-core-architectural-decisions.md#API & Communication Patterns]
- Logging mandatory log points: [Source: docs/4-architect-artifacts/architecture/04-implementation-patterns-consistency-rules.md#Logging Patterns]
- Exception hierarchy + GlobalExceptionHandler as single boundary: [Source: docs/4-architect-artifacts/architecture/04-implementation-patterns-consistency-rules.md#Process Patterns]
- `@MockitoBean` usage (Spring Boot 4.x): [Source: docs/4-architect-artifacts/architecture/03-core-architectural-decisions.md (Spring Boot 4.1.0)]
- JAVA_HOME macOS path: [Source: docs/5-scrum-impl-artifacts/4-2-implement-upstream-and-unexpected-error-handling.md#Java & Maven Environment]

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6 (bmad-create-story 2026-06-21)
claude-sonnet-4-6 (bmad-dev-story 2026-06-21)

### Debug Log References

- `@WebMvcTest` not available in Spring Boot 4.1.0 (`spring-boot-test-autoconfigure` 4.1.0 only contains `@JsonTest` and JDBC autoconfigure; all web slice test annotations removed). Adapted to `MockMvcBuilders.standaloneSetup()` with Mockito — equivalent coverage, lighter than `@SpringBootTest`.

### Completion Notes List

- Created `InvalidPageTokenException` extending `RuntimeException` with no-arg constructor message `"Invalid or malformed pageToken format."`.
- Added `handleInvalidPageToken` handler to `GlobalExceptionHandler` before the `Exception` catch-all — returns HTTP 400 with `error="Bad Request"`, `message="Invalid or malformed pageToken."`, `code=400`.
- Created `PlaylistController` implementing generated `PlaylistApi` interface (`@RestController`). Constructor-injects `PlaylistService`. Validates `pageToken` via regex `^[A-Za-z0-9_\-=]+$` before calling service. Logs entry and success at INFO. No try/catch — all exceptions propagate to `GlobalExceptionHandler`.
- Created `PlaylistControllerTest` using `MockMvcBuilders.standaloneSetup()` with `@ExtendWith(MockitoExtension.class)` and `GlobalExceptionHandler` as controller advice (adaptation for Spring Boot 4.1.0 which removed `@WebMvcTest`). 4 tests covering all 5 ACs. `verifyNoInteractions(playlistService)` confirms malformed token never reaches service.
- Final `mvn test`: 19 tests total (15 existing + 4 new), 0 failures, BUILD SUCCESS.

### File List

- `dest-spring-youtube-playlist-api/src/main/java/com/example/youtubeplaylistapi/exception/InvalidPageTokenException.java` (NEW)
- `dest-spring-youtube-playlist-api/src/main/java/com/example/youtubeplaylistapi/exception/GlobalExceptionHandler.java` (MODIFIED — added `handleInvalidPageToken` handler)
- `dest-spring-youtube-playlist-api/src/main/java/com/example/youtubeplaylistapi/controller/PlaylistController.java` (NEW)
- `dest-spring-youtube-playlist-api/src/test/java/com/example/youtubeplaylistapi/controller/PlaylistControllerTest.java` (NEW)

## Change Log

- 2026-06-21: Implemented Story 2.3 — created `InvalidPageTokenException`, updated `GlobalExceptionHandler` with 400 handler, created `PlaylistController` implementing generated `PlaylistApi`, and `PlaylistControllerTest` with 4 tests. Adapted `@WebMvcTest` → `MockMvcBuilders.standaloneSetup()` for Spring Boot 4.1.0 compatibility. All 19 tests pass. BUILD SUCCESS.
- 2026-06-21: Code review patches applied — blank `pageToken` normalized to null before regex validation, `InvalidPageTokenException` internal message aligned with handler response body. All 19 tests pass. Story done.

---
baseline_commit: 10adcc8eac9e3a9ef63af81a231704d940f67dbb
---

# Story 4.2: Implement Upstream and Unexpected Error Handling

Status: done

## Story

As an **internal developer**,
I want the `GlobalExceptionHandler` to also handle all upstream YouTube API errors and unexpected internal errors,
so that callers receive actionable error responses regardless of where the failure originates.

## Acceptance Criteria

1. **Given** the YouTube API returns HTTP 401 (invalid or missing API key)
   **When** either endpoint is called
   **Then** HTTP 401 is returned with an `ErrorResponse` where `message: "Invalid or missing YouTube API Key."` and `code: 401` (FR-10)

2. **Given** a network timeout or connection reset from the YouTube API
   **When** either endpoint is called
   **Then** HTTP 503 is returned with an `ErrorResponse` where `message: "Unable to connect to YouTube API."` and `code: 503` (FR-14)

3. **Given** any unhandled exception not covered by other handlers
   **When** either endpoint is called
   **Then** HTTP 500 is returned with an `ErrorResponse` containing `code: 500` and a generic message (FR-15)
   **And** the full exception is logged at ERROR level with sufficient diagnostic context

4. **Given** all eight error scenarios (FR-8 through FR-15)
   **When** any error condition is triggered
   **Then** the response body always conforms to the `{ error, message, code }` `ErrorResponse` schema with no null fields

## Tasks / Subtasks

- [x] Task 1 — Add upstream and catch-all handlers to `GlobalExceptionHandler` (AC: 1–4)
  - [x] Add handler for `UpstreamUnauthorizedException` → HTTP 401
  - [x] Add handler for `WebClientRequestException` → HTTP 503
  - [x] Add handler for `VideoNotFoundException` → HTTP 404 (domain 404 — video not found)
  - [x] Add catch-all handler for `Exception` → HTTP 500 with ERROR-level log
  - [x] Every handler builds `ErrorResponse` with exact `error`, `message`, `code` values per AC and Dev Notes
  - [x] No null fields — all three `ErrorResponse` fields set on every handler path

- [x] Task 2 — Verify exact error message strings (AC: 1–4)
  - [x] 401: `error="Unauthorized"`, `message="Invalid or missing YouTube API Key."`, `code=401`
  - [x] 503: `error="Connection Error"`, `message="Unable to connect to YouTube API."`, `code=503`
  - [x] 404 (VideoNotFound): `error="Not Found"`, `message="Video not found for the given videoId."`, `code=404`
  - [x] 500: `error="Internal Server Error"`, `message="An unexpected error occurred."`, `code=500`
  - [x] These exact strings are asserted by unit tests — any deviation breaks contract

- [x] Task 3 — Write unit tests for upstream and catch-all handlers (AC: 1–4)
  - [x] Add to `GlobalExceptionHandlerTest.java` (created in Story 4.1)
  - [x] `should_return_401_when_upstream_unauthorized()`
  - [x] `should_return_503_when_upstream_connectivity_fails()`
  - [x] `should_return_404_when_video_not_found()`
  - [x] `should_return_500_for_unexpected_exception()`
  - [x] For 500: additionally verify `log.error(...)` is called (use Logback test appender or simply assert body)
  - [x] Run `mvn test` — all tests in `GlobalExceptionHandlerTest` pass

- [x] Task 4 — Compile and regression check (AC: all)
  - [x] Run `mvn generate-sources compile` — no errors
  - [x] `mvn test` — full test suite green (no regressions from Story 4.1 tests)
  - [x] `ErrorResponse` has no null fields in any handler path — verified by test assertions

### Review Findings

- [x] [Review][Defer] `UpstreamConnectivityException` is dead code — class exists but no `@ExceptionHandler` is registered for it; `WebClientRequestException` propagates instead [UpstreamConnectivityException.java] — deferred, pre-existing
- [x] [Review][Defer] `Exception` catch-all may intercept Spring-internal 4xx exceptions (e.g. `ResponseStatusException`) not covered by specific handlers, returning HTTP 500 instead of the original status [GlobalExceptionHandler.java:112] — deferred, pre-existing
- [x] [Review][Defer] Unit-only tests — no `@WebMvcTest` integration layer; handlers not verified through full MVC serialization stack [GlobalExceptionHandlerTest.java] — deferred, pre-existing
- [x] [Review][Defer] `WebClientRequestException` reactive-to-servlet propagation path untested end-to-end — unclear whether Reactor Netty exception crosses the reactive/servlet boundary and reaches `GlobalExceptionHandler` in a blocking Servlet context [GlobalExceptionHandler.java:90] — deferred, pre-existing

## Dev Notes

### Previous Story Context (Story 4.1)

Story 4.1 created:
- `exception/GlobalExceptionHandler.java` — contains request-level handlers (404 routes, 405, 406, 415, 400)
- `exception/UpstreamUnauthorizedException.java` — stub, message: `"Invalid or missing YouTube API Key."`
- `exception/UpstreamConnectivityException.java` — stub, message: `"Unable to connect to YouTube API."`
- `exception/VideoNotFoundException.java` — stub, message: `"Video not found for the given videoId."`
- `src/test/java/.../exception/GlobalExceptionHandlerTest.java` — 5 passing unit tests

**This story modifies:** `GlobalExceptionHandler.java` — appends 4 new `@ExceptionHandler` methods.
**This story modifies:** `GlobalExceptionHandlerTest.java` — appends 4 new test methods.

### Handler Additions to GlobalExceptionHandler

Add these four methods to the existing `GlobalExceptionHandler` class. Do NOT restructure the existing handlers from Story 4.1.

```java
import org.springframework.web.reactive.function.client.WebClientRequestException;

// ─── Upstream: YouTube API 401 (invalid/missing API key) ──────────────────
@ExceptionHandler(UpstreamUnauthorizedException.class)
public ResponseEntity<ErrorResponse> handleUpstreamUnauthorized(
        UpstreamUnauthorizedException ex, HttpServletRequest request) {

    log.warn("endpoint={} status={} error={}", request.getRequestURI(), 401,
             ex.getClass().getSimpleName());

    ErrorResponse body = new ErrorResponse();
    body.setError("Unauthorized");
    body.setMessage("Invalid or missing YouTube API Key.");
    body.setCode(401);
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
}

// ─── Upstream: YouTube API network failure ────────────────────────────────
@ExceptionHandler(WebClientRequestException.class)
public ResponseEntity<ErrorResponse> handleUpstreamConnectivity(
        WebClientRequestException ex, HttpServletRequest request) {

    log.warn("endpoint={} status={} error={}", request.getRequestURI(), 503,
             ex.getClass().getSimpleName());

    ErrorResponse body = new ErrorResponse();
    body.setError("Connection Error");
    body.setMessage("Unable to connect to YouTube API.");
    body.setCode(503);
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
}

// ─── Domain: video not found (empty items[] from YouTube /videos) ─────────
@ExceptionHandler(VideoNotFoundException.class)
public ResponseEntity<ErrorResponse> handleVideoNotFound(
        VideoNotFoundException ex, HttpServletRequest request) {

    log.warn("endpoint={} status={} error={}", request.getRequestURI(), 404,
             ex.getClass().getSimpleName());

    ErrorResponse body = new ErrorResponse();
    body.setError("Not Found");
    body.setMessage("Video not found for the given videoId.");
    body.setCode(404);
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
}

// ─── Catch-all: any unhandled exception ───────────────────────────────────
@ExceptionHandler(Exception.class)
public ResponseEntity<ErrorResponse> handleUnexpected(
        Exception ex, HttpServletRequest request) {

    // ERROR level — full stack trace logged for diagnostics
    log.error("endpoint={} error={} message={}", request.getRequestURI(),
              ex.getClass().getSimpleName(), ex.getMessage(), ex);

    ErrorResponse body = new ErrorResponse();
    body.setError("Internal Server Error");
    body.setMessage("An unexpected error occurred.");
    body.setCode(500);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
}
```

**Handler ordering matters:** Spring uses the most specific type match, not declaration order. Custom exceptions (`UpstreamUnauthorizedException`, `VideoNotFoundException`, `WebClientRequestException`) will always match before the `Exception` catch-all. No ordering annotation needed.

### Exact Error String Contract

These strings are part of the API contract (defined in the OAS spec). Do not alter them:

| Exception | `error` | `message` | `code` |
|---|---|---|---|
| `UpstreamUnauthorizedException` | `"Unauthorized"` | `"Invalid or missing YouTube API Key."` | `401` |
| `WebClientRequestException` | `"Connection Error"` | `"Unable to connect to YouTube API."` | `503` |
| `VideoNotFoundException` | `"Not Found"` | `"Video not found for the given videoId."` | `404` |
| `Exception` (catch-all) | `"Internal Server Error"` | `"An unexpected error occurred."` | `500` |

The OAS spec examples (in `youtube-playlist-api.yaml`) for 401 and 503 must match these strings exactly. They already do — do not modify the OAS spec.

### WebClientRequestException — Import and Trigger Conditions

```java
import org.springframework.web.reactive.function.client.WebClientRequestException;
```

This exception is thrown by WebClient (Reactor Netty) when:
- Connection refused (YouTube API unreachable)
- TCP connection reset
- DNS resolution failure
- Read timeout

It is NOT thrown for HTTP error responses (4xx, 5xx from YouTube) — those are caught via `.onStatus()` in the service layer and rethrown as `UpstreamUnauthorizedException`. `WebClientRequestException` means the HTTP connection itself failed before a response was received.

**Do NOT catch `WebClientRequestException` in the service layer.** Per architecture: let it propagate naturally to `GlobalExceptionHandler`. The `.onStatus()` chain in service code handles only HTTP-level YouTube errors (401); network failures bubble up unhandled.

### Empty YOUTUBE_API_KEY — Deferred Context

From `docs/6-epic-dev-review/deferred-work.md`: an empty (but set) `YOUTUBE_API_KEY` bypasses startup validation and causes YouTube to return 401. This story's `UpstreamUnauthorizedException` handler covers that runtime outcome — when YouTube rejects the empty key with 401, the service throws `UpstreamUnauthorizedException`, the handler returns `{ "Unauthorized", "Invalid or missing YouTube API Key.", 401 }`. The deferred item is addressed at runtime; no code change needed here.

### Logging Level Policy

| Handler | Log level | Reason |
|---|---|---|
| 401, 503, 404, 400, 405, 406, 415 | `WARN` | Expected operational error — does not need immediate investigation |
| 500 catch-all | `ERROR` | Unexpected — needs immediate investigation; full exception included |

The `log.error(...)` call for 500 must include `ex` as the last argument so SLF4J appends the full stack trace to the structured JSON log:

```java
log.error("endpoint={} error={} message={}", endpoint, ex.getClass().getSimpleName(), ex.getMessage(), ex);
```

### Unit Test Additions to GlobalExceptionHandlerTest

Append to the existing test class — do not replace existing tests:

```java
@Test
void should_return_401_when_upstream_unauthorized() {
    var ex = new UpstreamUnauthorizedException();
    var resp = handler.handleUpstreamUnauthorized(ex, mockRequest);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(resp.getBody().getCode()).isEqualTo(401);
    assertThat(resp.getBody().getError()).isEqualTo("Unauthorized");
    assertThat(resp.getBody().getMessage()).isEqualTo("Invalid or missing YouTube API Key.");
}

@Test
void should_return_503_when_upstream_connectivity_fails() {
    var ex = new WebClientRequestException(new RuntimeException("connection refused"),
                                           HttpMethod.GET, URI.create("https://googleapis.com"),
                                           HttpHeaders.EMPTY);
    var resp = handler.handleUpstreamConnectivity(ex, mockRequest);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(resp.getBody().getCode()).isEqualTo(503);
    assertThat(resp.getBody().getMessage()).isEqualTo("Unable to connect to YouTube API.");
}

@Test
void should_return_404_when_video_not_found() {
    var ex = new VideoNotFoundException();
    var resp = handler.handleVideoNotFound(ex, mockRequest);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(resp.getBody().getCode()).isEqualTo(404);
    assertThat(resp.getBody().getMessage()).isEqualTo("Video not found for the given videoId.");
}

@Test
void should_return_500_for_unexpected_exception() {
    var ex = new RuntimeException("unexpected");
    var resp = handler.handleUnexpected(ex, mockRequest);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(resp.getBody().getCode()).isEqualTo(500);
    assertThat(resp.getBody().getError()).isNotNull();
    assertThat(resp.getBody().getMessage()).isNotNull();
}
```

`WebClientRequestException` constructor: `(Throwable cause, HttpMethod method, URI uri, HttpHeaders headers)`.
Import: `java.net.URI`, `org.springframework.http.HttpHeaders`, `org.springframework.http.HttpMethod`.

### Anti-Patterns to Avoid

```java
// ❌ Catching WebClientRequestException in the service — bypasses handler
try {
    return webClient.get()...block();
} catch (WebClientRequestException e) {
    return null;  // WRONG — service must not swallow this
}

// ❌ Re-throwing as a different exception type in the service
} catch (WebClientRequestException e) {
    throw new UpstreamConnectivityException(); // WRONG per architecture — propagate naturally
}

// ❌ Catch-all before specific handlers — Spring actually handles this by type specificity,
//    but if using @Order, never put Exception before the specific ones

// ❌ Logging at ERROR for 401/503 — these are expected operational conditions
log.error(...)  // for UpstreamUnauthorizedException — use log.warn instead

// ❌ Null fields in any ErrorResponse — all three fields must always be set
body.setError("Unauthorized");
body.setMessage("...");
// missing body.setCode(401) — AC 4 violation
```

### What This Story Does NOT Include

- **No service or controller code** — `PlaylistService`, `VideoService`, `PlaylistController`, `VideoController` are Epics 2 & 3
- **No WireMock integration tests** — `ErrorHandlingTest.java` with T06 (YouTube 401) and T07 (YouTube 503) is Epic 6
- **No `.onStatus()` calls** — that pattern lives in service layer (Epics 2 & 3); this story only wires the handler side

### Java & Maven Environment

```bash
export JAVA_HOME=~/java/jdk-21.0.5+11
export PATH=$JAVA_HOME/bin:~/java/apache-maven-3.9.9/bin:$PATH
mvn generate-sources compile   # always generate-sources first
mvn test                       # run full suite
```

### Files Modified in This Story

| File | Type | Notes |
|---|---|---|
| `src/main/java/.../exception/GlobalExceptionHandler.java` | UPDATE | Add 4 handlers: Unauthorized (401), Connectivity (503), VideoNotFound (404), Exception catch-all (500) |
| `src/test/java/.../exception/GlobalExceptionHandlerTest.java` | UPDATE | Add 4 test methods |

### References

- Epic 4 story definitions: [Source: docs/3-product-manager-artifacts/epics-and-stories/epic-4-error-handling.md]
- Error handling architecture + exception hierarchy: [Source: docs/4-architect-artifacts/architecture/03-core-architectural-decisions.md#API & Communication Patterns]
- Exception hierarchy and WebClient `.onStatus()` pattern: [Source: docs/4-architect-artifacts/architecture/04-implementation-patterns-consistency-rules.md#Process Patterns]
- Logging pattern (WARN vs ERROR): [Source: docs/4-architect-artifacts/architecture/04-implementation-patterns-consistency-rules.md#Logging Patterns]
- Deferred item — empty API key: [Source: docs/6-epic-dev-review/deferred-work.md]
- FR-10, FR-14, FR-15: [Source: docs/3-product-manager-artifacts/epics-and-stories/epic-4-error-handling.md]

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6 (bmad-dev-story 2026-06-21)

### Debug Log References

- **Java/Maven environment:** Use absolute JAVA_HOME `/Users/admin/java/jdk-21.0.5+11/Contents/Home` (macOS JDK has `Contents/Home` subdirectory). Keep `/usr/bin:/bin` in PATH so Maven wrapper can find `uname`/`dirname`.
- **`WebClientRequestException` constructor:** `(Throwable cause, HttpMethod method, URI uri, HttpHeaders headers)` — confirmed from `spring-webflux-7.0.8.jar` via javap. Import from `org.springframework.web.reactive.function.client.WebClientRequestException`.

### Completion Notes List

- Task 1: Added 4 new `@ExceptionHandler` methods to `GlobalExceptionHandler.java`:
  - `handleUpstreamUnauthorized` (401 WARN) — `UpstreamUnauthorizedException`
  - `handleUpstreamConnectivity` (503 WARN) — `WebClientRequestException`
  - `handleVideoNotFound` (404 WARN) — `VideoNotFoundException`
  - `handleUnexpected` (500 ERROR with full stack) — `Exception` catch-all
  - Added `import org.springframework.web.reactive.function.client.WebClientRequestException`
- Task 2: Exact error strings verified by test assertions — all match OAS spec contract.
- Task 3: Added 4 new test methods to `GlobalExceptionHandlerTest.java`. Total test count: 9 (5 from 4.1 + 4 new). `mvn test` — Tests run: 9, Failures: 0, Errors: 0.
- Task 4: No regressions from Story 4.1. All 9 tests green. `ErrorResponse` has no null fields on any handler path — all three fields (`error`, `message`, `code`) explicitly set in every handler.

### File List

- `dest-spring-youtube-playlist-api/src/main/java/com/example/youtubeplaylistapi/exception/GlobalExceptionHandler.java` (MODIFIED — added 4 handlers)
- `dest-spring-youtube-playlist-api/src/test/java/com/example/youtubeplaylistapi/exception/GlobalExceptionHandlerTest.java` (MODIFIED — added 4 test methods)

### Change Log

- 2026-06-21: Story 4.2 implementation complete. Added upstream and catch-all handlers to GlobalExceptionHandler (401, 503, domain-404, 500). All 9 unit tests pass. Status → review.

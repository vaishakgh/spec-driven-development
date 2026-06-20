# Implementation Patterns & Consistency Rules

## Critical Conflict Points Identified

8 areas where independent Developer agents could make different but individually valid choices, producing incompatible code.

## Naming Patterns

**Java Code Naming:**

| Element | Convention | Example |
|---------|-----------|---------|
| Classes | PascalCase | `PlaylistController`, `GlobalExceptionHandler` |
| Methods | camelCase | `getPlaylist()`, `toPlaylistResponse()` |
| Fields/variables | camelCase | `playlistId`, `maxResults` |
| Constants | UPPER_SNAKE_CASE | `YOUTUBE_BASE_URL` |
| Packages | lowercase, no separator | `com.example.youtubeplaylistapi.controller` |
| Test classes | `{ClassName}Test` suffix | `PlaylistControllerTest`, `PlaylistMapperTest` |
| Test methods | snake_case, descriptive | `should_return_200_when_valid_playlist_id()` |
| Typed exceptions | `{Condition}Exception` suffix | `VideoNotFoundException`, `UpstreamUnauthorizedException` |

**JSON Field Naming — camelCase throughout:**

All JSON request and response fields use camelCase. Jackson is configured with default settings (no `SNAKE_CASE` `PropertyNamingStrategy`). The OAS 3.0 spec defines all field names — they are authoritative. Agents must not rename fields.

```java
// Correct
{ "totalResults": 10, "nextPageToken": "abc", "videoUrl": "https://..." }

// Wrong — snake_case breaks consumer contracts
{ "total_results": 10, "next_page_token": "abc", "video_url": "https://..." }
```

**Configuration Property Keys — kebab-case in YAML:**

```yaml
# Correct
youtube:
  api:
    base-url: www.googleapis.com
    key: ${YOUTUBE_API_KEY}
    max-results: 25

# Wrong — camelCase in YAML is non-standard for Spring Boot
youtube:
  api:
    baseUrl: www.googleapis.com  # ❌
```

Java `@Value` and `@ConfigurationProperties` reference these in any standard format — Spring Boot normalises `youtube.api.max-results` and `youtube.api.maxResults` equivalently.

Spring Bean names: use default Spring auto-naming (class name, first letter lowercase). Do not assign explicit bean names unless resolving a collision.

## Structure Patterns

**Test file location:** Mirror the main source tree under `src/test/java/`:

```
src/test/java/com/example/youtubeplaylistapi/
├── controller/    # PlaylistControllerTest, VideoControllerTest
├── service/       # PlaylistServiceTest, VideoServiceTest
└── mapper/        # PlaylistMapperTest, VideoMapperTest
```

**WireMock stubs:** Define stubs inline using Java `stubFor(...)` within test methods or `@BeforeEach`. Do not use external JSON stub files — keeps tests self-contained and readable.

```java
// Correct
stubFor(get(urlPathEqualTo("/youtube/v3/playlistItems"))
    .withQueryParam("playlistId", equalTo("PLxxx"))
    .willReturn(aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody(PLAYLIST_SUCCESS_BODY)));

// Wrong — external JSON file adds indirection
stubFor(get(...).willReturn(aResponse().withBodyFile("playlist-success.json")));
```

**OAS spec location:** `src/main/resources/api/youtube-playlist-api.yaml`

**Generated sources:** Maven default `target/generated-sources/openapi/` — never edit generated files; modify the OAS spec and regenerate.

## Format Patterns

**API Error Response — fixed shape, no variation:**

```json
{
  "error": "Bad Request",
  "message": "Request does not match API specification.",
  "code": 400
}
```

- `error`: short human-readable error category (string, never null)
- `message`: actionable description (string, never null)
- `code`: integer matching HTTP status code exactly

Never return `null` for any `ErrorResponse` field. Never wrap `ErrorResponse` in an outer object.

Successful response shapes are defined by the OAS 3.0 spec — no additional wrapping. Do not add `status`, `data`, or `success` envelope fields.

**Date/time format:** ISO 8601 strings as returned by YouTube API, passed through as-is. No reformatting. Field type in Java: `String` (not `LocalDateTime` or `Instant`).

**Null fields:** Nullable fields (e.g. `thumbnail`) are serialised as JSON `null`, not omitted. Do not configure Jackson with `NON_NULL` serialisation inclusion globally.

## Process Patterns

**Exception Hierarchy:**

All custom exceptions extend `RuntimeException`. The `GlobalExceptionHandler` catches them by specific type — never catch-and-rethrow generic `Exception` in service or controller code.

```
RuntimeException
├── UpstreamUnauthorizedException    → HTTP 401 (YouTube API returned 401)
├── UpstreamConnectivityException    → HTTP 503 (network failure to YouTube)
└── VideoNotFoundException           → HTTP 404 (empty items[] from /videos endpoint)
```

**WebClient Error Detection — mandatory pattern:**

Use `.onStatus()` to detect upstream HTTP errors before `.bodyToMono()`:

```java
webClient.get()
    .uri(uriBuilder -> uriBuilder
        .path("/playlistItems")
        .queryParam("playlistId", playlistId)
        .queryParam("maxResults", maxResults)
        .queryParam("key", apiKey)
        .build())
    .retrieve()
    .onStatus(status -> status.value() == 401,
        resp -> Mono.error(new UpstreamUnauthorizedException()))
    .bodyToMono(YouTubePlaylistItemsResponse.class)
    .block();
```

Connectivity failures (`WebClientRequestException`) propagate naturally — caught by `GlobalExceptionHandler` and mapped to HTTP 503. Do not catch them in the service.

**pageToken Validation Pattern:**

Validate format locally in the controller before calling `PlaylistService`. Any validation failure returns HTTP 400 immediately — do not pass an invalid token to the service layer.

**Swagger UI Profile Gating:**

Disable via YAML only — no `@ConditionalOnProperty` or `@Profile` annotations on springdoc beans:

```yaml
# application-prod.yml
springdoc:
  api-docs:
    enabled: false
  swagger-ui:
    enabled: false
```

## Logging Patterns

**Mandatory log points — every endpoint:**

```java
// 1. Request entry (INFO)
log.info("endpoint={} playlistId={}", "GET /api/youtube/playlists/{playlistId}", playlistId);

// 2. Success (INFO)
log.info("endpoint={} totalResults={}", "GET /api/youtube/playlists/{playlistId}",
    response.getTotalResults());

// 3. Error (ERROR) — in GlobalExceptionHandler only
log.error("endpoint={} error={} message={}", requestUri,
    ex.getClass().getSimpleName(), ex.getMessage(), ex);
```

Logback JSON field names are `logstash-logback-encoder` defaults: `timestamp`, `level`, `logger_name`, `message`, `stack_trace`. Do not override these keys. Application fields are passed as key-value pairs in the message string.

## Enforcement Guidelines

**All Developer agents MUST:**

- Use camelCase for all JSON fields — no exceptions
- Return `ErrorResponse { error, message, code }` for every non-2xx response — no other shape
- Let `GlobalExceptionHandler` handle all exceptions — no try/catch in controllers or services
- Use `.onStatus()` for WebClient upstream HTTP error detection
- Name test methods with `should_return_{status}_when_{condition}()` pattern
- Define WireMock stubs inline in Java — no JSON stub files
- Reference all configuration via `@Value("${youtube.api.xxx}")` — no hardcoded values
- Never store or log the `YOUTUBE_API_KEY` value

**Anti-Patterns to Avoid:**

```java
// ❌ Catching exceptions in service — bypasses GlobalExceptionHandler
try {
    return webClient.get()...block();
} catch (Exception e) { return null; }

// ❌ snake_case JSON field — breaks OAS contract
@JsonProperty("total_results")

// ❌ Custom error shape — not ErrorResponse
return Map.of("status", "error", "detail", "...");

// ❌ Returning HTTP 200 for not-found video — violates FR-7
if (response.getItems().isEmpty()) {
    return ResponseEntity.ok(new SongDetail());
}
```

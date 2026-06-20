# Connector Mapping — MuleSoft to Spring Boot

**Date:** 2026-06-19
**Scope:** All connectors and components used in `youtube-playlist-api`

---

## Complete Connector Map

### 1. `mule-http-connector` (v1.11.3) — Inbound Listener

| Mule Attribute | Mule Value | Spring Boot Equivalent | Spring Config |
|---|---|---|---|
| `http:listener-config` | `HTTP_Listener_Config` | Spring Boot embedded server | Built-in (no config needed) |
| `host` | `0.0.0.0` | `server.address=0.0.0.0` | `application.yml` |
| `port` | `8081` | `server.port=8081` | `application.yml` |
| `path` | `/api/*` | `@RequestMapping("/api")` | `@RestController` |
| Protocol | HTTP (plain) | HTTP (Spring Boot default) | TLS optional via `server.ssl.*` |

**Migration action:** No explicit HTTP server configuration needed — Spring Boot auto-configures Tomcat on `server.port`. Add `server.port=8081` to `application.yml` to match current behaviour.

---

### 2. `mule-apikit-module` (v1.11.8) — RAML Router + Validation

| Mule Capability | Spring Boot Equivalent | Notes |
|---|---|---|
| RAML-driven request routing | `openapi-generator-maven-plugin` → `@RestController` stubs | Convert RAML → OAS 3.0 first |
| Automatic RAML validation (schema, params) | `spring-boot-starter-validation` + Bean Validation annotations | Add `@Valid` on controller params |
| `APIKIT:BAD_REQUEST` (400) | `MethodArgumentNotValidException` → `@ExceptionHandler` | Spring throws this on validation failure |
| `APIKIT:NOT_FOUND` (404) | `NoHandlerFoundException` / `@ResponseStatus(NOT_FOUND)` | Spring default behaviour |
| `APIKIT:METHOD_NOT_ALLOWED` (405) | `HttpRequestMethodNotSupportedException` | Spring default behaviour |
| `APIKIT:UNSUPPORTED_MEDIA_TYPE` (415) | `HttpMediaTypeNotSupportedException` | Spring default behaviour |
| `APIKIT:NOT_ACCEPTABLE` (406) | `HttpMediaTypeNotAcceptableException` | Spring default behaviour |
| APIKit Console (`/api/console/*`) | Springdoc Swagger UI (`/swagger-ui.html`) | Enable only in non-prod profiles |

**Migration action:** Implement a `GlobalExceptionHandler` class with `@ControllerAdvice` that catches all the above Spring exceptions and returns the same `{ error, message, code }` JSON shape as the current Mule transforms.

---

### 3. `mule-http-connector` (v1.11.3) — Outbound HTTP Request

| Mule Attribute | Mule Value | Spring Boot Equivalent |
|---|---|---|
| `http:request-config` | `YouTube_Request_Config` | `WebClient` bean |
| `protocol` | `HTTPS` | `WebClient.builder().baseUrl("https://...")` |
| `host` | `${youtube.api.baseUrl}` | `https://${youtube.api.base-url}` in config |
| `port` | `443` | Implicit in `https://` |
| `basePath` | `/youtube/v3` | Part of base URL or per-request URI |
| `http:request method="GET" path="/playlistItems"` | `webClient.get().uri("/playlistItems", params -> ...)` | Reactive call |
| `http:request method="GET" path="/videos"` | `webClient.get().uri("/videos", params -> ...)` | Reactive call |
| `http:query-params` DataWeave expression | `.queryParam("part", "snippet,contentDetails")` etc. | Fluent WebClient builder |

**WebClient configuration example:**
```java
@Bean
public WebClient youTubeWebClient(@Value("${youtube.api.base-url}") String baseUrl) {
    return WebClient.builder()
        .baseUrl("https://" + baseUrl + "/youtube/v3")
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .build();
}
```

**Playlist items call:**
```java
YouTubePlaylistItemsResponse response = youTubeWebClient.get()
    .uri(uriBuilder -> uriBuilder
        .path("/playlistItems")
        .queryParam("part", "snippet,contentDetails")
        .queryParam("playlistId", playlistId)
        .queryParam("maxResults", maxResults)
        .queryParam("key", apiKey)
        .build())
    .retrieve()
    .bodyToMono(YouTubePlaylistItemsResponse.class)
    .block();
```

**Error handling mapping (outbound):**

| Mule Error Type | Trigger | Spring WebClient Equivalent | Spring Exception |
|---|---|---|---|
| `HTTP:UNAUTHORIZED` | YouTube returns 401 | `WebClientResponseException.Unauthorized` | Catch in `@ExceptionHandler` |
| `HTTP:CONNECTIVITY` | Network failure / timeout | `WebClientRequestException` or `ConnectException` | Catch in `@ExceptionHandler` |
| `ANY` | Any other error | Generic `Exception` | Catch `Throwable` as last resort |

---

### 4. `mule-apikit-module` — APIKit Config

| Mule Config | Migration Action |
|---|---|
| `apikit:config name="APIKit_Config" raml="api/youtube-playlist-api.raml"` | Convert RAML to OAS, reference OAS in `springdoc.api-docs.path` |
| `outboundHeadersMapName="outboundHeaders"` | Handled by Spring Boot response headers automatically |
| `httpStatusVarName="httpStatus"` | Spring Boot sets HTTP status via `@ResponseStatus` or `ResponseEntity<>` |

---

## Global Error Handler Implementation

Replace all 11 DataWeave error transforms with one `@ControllerAdvice` class:

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    // APIKit:BAD_REQUEST equivalent
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBadRequest(MethodArgumentNotValidException ex) {
        return new ErrorResponse("Bad Request",
            "Request does not match API specification.", 400);
    }

    // HTTP:UNAUTHORIZED equivalent
    @ExceptionHandler(WebClientResponseException.Unauthorized.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleUnauthorized(WebClientResponseException.Unauthorized ex) {
        return new ErrorResponse("Unauthorized",
            "Invalid or missing YouTube API Key.", 401);
    }

    // HTTP:CONNECTIVITY equivalent
    @ExceptionHandler({ConnectException.class, WebClientRequestException.class})
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorResponse handleConnectivity(Exception ex) {
        return new ErrorResponse("Connection Error",
            "Unable to connect to YouTube API.", 503);
    }

    // ANY (generic 500) equivalent
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGeneric(Exception ex) {
        return new ErrorResponse("Internal Server Error",
            ex.getMessage() != null ? ex.getMessage() : "Unexpected error.", 500);
    }
}
```

---

## Configuration Properties Migration

| Mule Property | Config File | Spring Boot Equivalent | Spring Config Key |
|---|---|---|---|
| `youtube.api.baseUrl` | config-${env}.yaml | `youtube.api.base-url` | `application.yml` |
| `youtube.api.key` | `${YOUTUBE_API_KEY}` env var | `${YOUTUBE_API_KEY}` env var | `youtube.api.key: ${YOUTUBE_API_KEY}` |
| `youtube.api.maxResults` | config-${env}.yaml | `youtube.api.max-results` | `application.yml` per profile |
| `env` selector | runtime property | `SPRING_PROFILES_ACTIVE` env var | Kubernetes pod env |
| HTTP listener port | config-${env}.yaml | `server.port` | `application.yml` |

**Note:** The `youtube.api.playlistId` CloudHub property (in `pom.xml`) is passed as a default playlist ID. This is not used in the flow logic (the actual `playlistId` comes from the URI path parameter). This property can be removed unless there's a use case for it.

---

## Logger Processor Migration

| Mule Logger | Spring Boot Equivalent |
|---|---|
| `<logger level="INFO" message="#['[get-playlists] Fetching playlist for ID: ' ++ attributes.uriParams.playlistId]"/>` | `log.info("[get-playlists] Fetching playlist for ID: {}", playlistId)` |
| `<logger level="INFO" message="#['[get-playlists] Total songs found: ' ++ (payload.totalResults as String)]"/>` | `log.info("[get-playlists] Total songs found: {}", response.getTotalResults())` |
| `<logger level="INFO" message="#['[get-song] Fetching details for video ID: ' ++ attributes.uriParams.videoId]"/>` | `log.info("[get-song] Fetching details for video ID: {}", videoId)` |

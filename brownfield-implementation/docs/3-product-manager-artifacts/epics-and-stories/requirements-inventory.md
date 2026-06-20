# Requirements Inventory

## Functional Requirements

FR-1: The internal team can retrieve a page of PlaylistItems for a given PlaylistId by calling `GET /api/youtube/playlists/{playlistId}`. Returns HTTP 200 and a PlaylistResponse body with totalResults, resultsPerPage, playlist array, and nextPageToken.

FR-2: Each PlaylistItem contains: `position` (integer, zero-based), `videoId`, `title`, `description`, `publishedAt` (ISO 8601), `thumbnail` (URL or null), `videoUrl` (https://www.youtube.com/watch?v={videoId}).

FR-3: The internal team can retrieve the next page of results by supplying the `pageToken` query parameter. Invalid/malformed pageToken returns HTTP 400 before any upstream call. Upstream-rejected token also returns HTTP 400.

FR-4: Page size is environment-configurable — 25 for local/dev/test, 50 for production. Passed as `maxResults` to YouTube API. Callers cannot override.

FR-5: The internal team can retrieve metadata for a specific YouTube video by calling `GET /api/youtube/song/{videoId}`. Valid existing VideoId returns HTTP 200 and a SongDetail body.

FR-6: SongDetail contains: `videoId`, `title`, `description`, `channelName` (mapped from YouTube's `channelTitle`), `publishedAt` (ISO 8601), `duration` (ISO 8601 e.g. PT3M33S), `viewCount` (string), `likeCount` (string), `thumbnail` (URL or null), `videoUrl`.

FR-7: When the requested VideoId does not correspond to any YouTube video (empty `items[]` from upstream), the service returns HTTP 404 and an ErrorResponse with `code: 404`. Breaking change from current Mule 200-with-nulls behaviour.

FR-8: All non-2xx responses use a consistent ErrorResponse shape: `error` (string), `message` (string), `code` (integer matching HTTP status).

FR-9: Requests that do not conform to the API spec (missing required path params, non-conforming media types) return HTTP 400 and an ErrorResponse.

FR-10: When YouTube API responds with 401, the service returns HTTP 401 and ErrorResponse with `message: "Invalid or missing YouTube API Key."`.

FR-11: A GET request to any path other than the two defined endpoints returns HTTP 404 and an ErrorResponse.

FR-12: A POST, PUT, PATCH, or DELETE to either defined endpoint path returns HTTP 405 and an ErrorResponse.

FR-13: A request with `Accept: text/html` (or non-`application/json`) returns HTTP 406. A request with `Content-Type: application/xml` returns HTTP 415.

FR-14: Network timeouts and connection refusals from upstream YouTube API return HTTP 503 and ErrorResponse with `message: "Unable to connect to YouTube API."`.

FR-15: Any unhandled error returns HTTP 500 and an ErrorResponse.

FR-16: The migrated service is governed by an OAS 3.0 spec covering all endpoints, request parameters, response types, and error shapes. Spec includes nullable `thumbnail` fields, `pageToken` query param, and HTTP 404 response for video endpoint.

FR-17: Both endpoints remain accessible at `/api/youtube/playlists/{playlistId}` and `/api/youtube/song/{videoId}` (base path parity — no URL changes required by consumers).

FR-18: An OpenAPI-based UI (Swagger UI) is reachable in local and dev profiles. Not accessible in production.

FR-19: The service has distinct configuration profiles for local, dev, production, and test environments.

FR-20: The YouTube API key is injected via the `YOUTUBE_API_KEY` environment variable. No API key value appears in any source-controlled configuration file.

FR-21: `GET /actuator/health/liveness` and `GET /actuator/health/readiness` both return HTTP 200 when healthy. Available without authentication.

FR-22: Each inbound request logs endpoint + key input param at INFO. Each successful response logs key result metric at INFO. Error conditions log at ERROR with diagnostic context.

## NonFunctional Requirements

NFR-1: Response latency of the Spring Boot service must not regress more than 20% against the Mule/CloudHub baseline at equivalent request volume, as measured in shadow mode.

NFR-2: All 7 MUnit test scenarios must have functional equivalents in the Spring Boot test suite before cutover is permitted. No regression in test coverage is acceptable.

NFR-3: Traffic must not be fully switched to the Spring Boot service until shadow mode validation has confirmed response parity for all endpoints and all error paths.

NFR-4: The production container must operate stably within 512 MB of heap memory.

NFR-5: No API keys, passwords, or other credentials may appear in any source-controlled file. All secrets are injected via environment variables at runtime.

## Additional Requirements

From PRD Addendum (technical implementation):

- **Stack:** Java 21 + Spring Boot 3.3.x + Spring WebClient. No Apache Camel.
- **API contract:** RAML 1.0 → OAS 3.0 conversion. New additions: `pageToken` query param, HTTP 404 on video endpoint.
- **Code generation:** `openapi-generator-maven-plugin` to generate controller stubs from OAS 3.0 spec.
- **Outbound HTTP:** `WebClient` bean configured with base URL `https://www.googleapis.com/youtube/v3`.
- **Response mapping:** Java mapper methods + Jackson DTOs for both DataWeave transforms (Simple complexity — no MapStruct, no JOLT).
  - Transform 1 (Playlist): `map` + `default` null-coalescing + string concatenation for videoUrl (~25 lines Java)
  - Transform 2 (Video): `items[0]` extraction + field rename `channelTitle` → `channelName` (~20 lines Java)
- **Error handling:** `@ControllerAdvice` + `@ExceptionHandler` replacing all 11 Mule `on-error-propagate` + DataWeave error response constants.
- **Config:** Spring Boot `application-{profile}.yml` profiles (local/dev/prod/test). `YOUTUBE_API_KEY` env var.
- **Testing:** JUnit 5 + WireMock. `mock-when http:request` → `stubFor(...)`. 7 MUnit parity tests + 2 additional:
  - `pageToken` forwarding to upstream (assert token passed through)
  - Video not-found → assert HTTP 404 body contains `code: 404`
- **Deployment target:** Dockerfile + Kubernetes. Pod: 100m–250m CPU, 256Mi–512Mi RAM, 1–2 replicas. Kubernetes Secret → `YOUTUBE_API_KEY` env var. K8s ConfigMap + `application-{profile}.yml`.
- **Shadow mode:** Request-replay harness comparing Mule vs Spring Boot responses field-by-field. Hard gate before cutover.
- **Cutover:** Team lead sends 3-working-days notice to consumer team. Consumer confirmation required before traffic switch.
- **CloudHub decommission:** After shadow mode stability confirmed.

## UX Design Requirements

Not applicable — this is an internal REST API with no user interface component.

## FR Coverage Map

FR-1: Epic 2 — Playlist retrieval via WebClient
FR-2: Epic 2 — PlaylistItem field mapping (DW-transform-1 → Java mapper)
FR-3: Epic 2 — pageToken pagination + HTTP 400 validation
FR-4: Epic 2 — Environment-configurable maxResults (25/50)
FR-5: Epic 3 — Video retrieval via WebClient
FR-6: Epic 3 — SongDetail field mapping (DW-transform-2 → Java mapper)
FR-7: Epic 3 — HTTP 404 for missing video (breaking change)
FR-8: Epic 4 — ErrorResponse body shape ({error, message, code})
FR-9: Epic 4 — HTTP 400 for malformed requests
FR-10: Epic 4 — HTTP 401 for invalid YouTube API key
FR-11: Epic 4 — HTTP 404 for unknown routes
FR-12: Epic 4 — HTTP 405 for unsupported HTTP methods
FR-13: Epic 4 — HTTP 406/415 for unsupported media types
FR-14: Epic 4 — HTTP 503 for upstream connectivity failures
FR-15: Epic 4 — HTTP 500 for unexpected errors
FR-16: Epic 1 — OAS 3.0 spec (RAML → OAS conversion, code-gen)
FR-17: Epic 1 — Base path /api/* parity
FR-18: Epic 5 — Swagger UI in non-prod environments
FR-19: Epic 1 — Multi-environment configuration profiles
FR-20: Epic 1 — YOUTUBE_API_KEY environment variable
FR-21: Epic 5 — Kubernetes liveness + readiness health probes
FR-22: Epic 5 — Structured JSON application logging
NFR-1: Epic 6 — Latency baseline measured during shadow mode
NFR-2: Epic 6 — All 7 MUnit scenarios ported (cutover gate)
NFR-3: Epic 7 — Zero-downtime cutover gated on shadow mode parity
NFR-4: Epic 7 — Production container ≤512MB heap
NFR-5: Epic 1 + Epic 7 — No secrets in source control (config + K8s Secret)

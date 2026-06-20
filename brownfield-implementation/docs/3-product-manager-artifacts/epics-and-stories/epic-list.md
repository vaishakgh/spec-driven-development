# Epic List

## Epic 1: Project Foundation & API Contract
The internal team can run the Spring Boot service locally, with the correct API paths recognised and the OAS 3.0 spec established as the contract source of truth. Environment configuration and secret injection work across all profiles.
**FRs covered:** FR-16, FR-17, FR-19, FR-20
**NFRs covered:** NFR-5 (partial — no secrets in source-controlled config files)
**Technical:** Maven scaffold (Java 21, Spring Boot 3.3.x), OAS 3.0 spec from RAML 1.0, `openapi-generator-maven-plugin`, `application-{profile}.yml` (local/dev/prod/test), WebClient bean config

## Epic 2: Playlist Retrieval — End to End
The internal team can call `GET /api/youtube/playlists/{playlistId}` against the Spring Boot service and receive correctly shaped, paginated playlist data. The `pageToken` parameter enables navigation to subsequent pages; invalid tokens are rejected with HTTP 400.
**FRs covered:** FR-1, FR-2, FR-3, FR-4
**Technical:** `PlaylistController`, `PlaylistService`, `PlaylistMapper` (DW-transform-1 → Java), WebClient `/playlistItems` call

## Epic 3: Video Detail Retrieval — End to End
The internal team can call `GET /api/youtube/song/{videoId}` and receive correct video metadata, or a proper HTTP 404 when the video does not exist (corrects the Mule 200-with-nulls bug — breaking change).
**FRs covered:** FR-5, FR-6, FR-7
**Technical:** `VideoController`, `VideoService`, `VideoMapper` (DW-transform-2 → Java), WebClient `/videos` call

## Epic 4: Error Handling
All error conditions across both endpoints return a consistent `{ error, message, code }` ErrorResponse body with semantically correct HTTP status codes, replacing all 11 Mule on-error-propagate scopes.
**FRs covered:** FR-8, FR-9, FR-10, FR-11, FR-12, FR-13, FR-14, FR-15
**Technical:** `GlobalExceptionHandler` (`@ControllerAdvice` + `@ExceptionHandler`)

## Epic 5: Observability & API Explorer
Operations can monitor service health for Kubernetes probes; developers can browse the API in Swagger UI in non-prod environments and diagnose issues from structured JSON logs.
**FRs covered:** FR-18, FR-21, FR-22
**Technical:** `springdoc-openapi`, Spring Actuator, Logback JSON config

## Epic 6: Test Suite & Quality Gate
The team has a complete automated test suite (7 MUnit-parity scenarios + 2 additional) that confirms migration correctness and satisfies the mandatory cutover gate.
**NFRs covered:** NFR-2 (test parity cutover gate), NFR-1 (latency baseline measurable)
**Technical:** JUnit 5 + WireMock — 9 test scenarios: 7 parity + pageToken forwarding + 404 body validation

## Epic 7: Deployment, Shadow Mode & Cutover
The service runs in Kubernetes, shadow mode confirms response parity with Mule, the internal team is notified of the breaking change, traffic switches with zero downtime, and CloudHub is decommissioned.
**NFRs covered:** NFR-3 (zero-downtime cutover), NFR-4 (512MB heap), NFR-5 (K8s Secret for YOUTUBE_API_KEY)
**Technical:** Dockerfile, K8s Deployment/Service/ConfigMap/Secret manifests, request-replay shadow harness, cutover sequence, CloudHub decommission

---

# Epic Coverage Validation

## Coverage Matrix

| FR Number | PRD Requirement (summary) | Epic Coverage | Status |
|-----------|--------------------------|---------------|--------|
| FR-1 | GET /api/youtube/playlists/{playlistId} → HTTP 200 PlaylistResponse | Epic 2 (PlaylistController + WebClient) | ✓ Covered |
| FR-2 | PlaylistItem fields: position, videoId, title, description, publishedAt, thumbnail, videoUrl | Epic 2 (PlaylistMapper — DW-transform-1 → Java) | ✓ Covered |
| FR-3 | pageToken query param; invalid/malformed → HTTP 400 before upstream call | Epic 2 (PlaylistController pageToken validation) | ✓ Covered |
| FR-4 | maxResults env-configurable: 25 local/dev/test, 50 prod; callers cannot override | Epic 2 (PlaylistService + application-{profile}.yml) | ✓ Covered |
| FR-5 | GET /api/youtube/song/{videoId} → HTTP 200 SongDetail | Epic 3 (VideoController + WebClient) | ✓ Covered |
| FR-6 | SongDetail fields: videoId, title, description, channelName, publishedAt, duration, viewCount, likeCount, thumbnail, videoUrl | Epic 3 (VideoMapper — DW-transform-2 → Java) | ✓ Covered |
| FR-7 | Non-existent videoId → HTTP 404 + ErrorResponse code:404 (breaking change from Mule 200-with-nulls) | Epic 3 (VideoController + VideoService empty-items check) | ✓ Covered |
| FR-8 | All non-2xx → ErrorResponse {error, message, code} shape | Epic 4 (GlobalExceptionHandler @ControllerAdvice) | ✓ Covered |
| FR-9 | Malformed requests → HTTP 400 + ErrorResponse | Epic 4 (GlobalExceptionHandler request-level handler) | ✓ Covered |
| FR-10 | YouTube API 401 → HTTP 401 + message "Invalid or missing YouTube API Key." | Epic 4 (GlobalExceptionHandler upstream handler) | ✓ Covered |
| FR-11 | Unknown routes → HTTP 404 + ErrorResponse | Epic 4 (GlobalExceptionHandler NoHandlerFoundException) | ✓ Covered |
| FR-12 | Unsupported HTTP methods → HTTP 405 + ErrorResponse | Epic 4 (GlobalExceptionHandler MethodNotAllowedException) | ✓ Covered |
| FR-13 | Accept: non-JSON → HTTP 406; Content-Type: application/xml → HTTP 415 | Epic 4 (GlobalExceptionHandler media-type handler) | ✓ Covered |
| FR-14 | Upstream connectivity failure → HTTP 503 + message "Unable to connect to YouTube API." | Epic 4 (GlobalExceptionHandler upstream connectivity handler) | ✓ Covered |
| FR-15 | Unhandled errors → HTTP 500 + ErrorResponse | Epic 4 (GlobalExceptionHandler catch-all) | ✓ Covered |
| FR-16 | OAS 3.0 spec governs all endpoints, params, responses, errors; includes pageToken, HTTP 404, nullable thumbnail | Epic 1 (RAML → OAS 3.0 conversion + openapi-generator-maven-plugin) | ✓ Covered |
| FR-17 | Base path /api/* preserved; no consumer URL changes | Epic 1 (Spring Boot server.servlet.context-path=/api) | ✓ Covered |
| FR-18 | Swagger UI at /swagger-ui.html in local+dev; not in production | Epic 5 (springdoc-openapi, profile-gated) | ✓ Covered |
| FR-19 | Distinct config profiles: local, dev, production, test | Epic 1 (application-{profile}.yml for all 4 environments) | ✓ Covered |
| FR-20 | YOUTUBE_API_KEY via env var only; no key in source-controlled files | Epic 1 (application-{profile}.yml placeholder + K8s Secret) | ✓ Covered |
| FR-21 | GET /actuator/health/liveness + /readiness → HTTP 200; no auth required | Epic 5 (Spring Actuator health probes) | ✓ Covered |
| FR-22 | INFO on request entry + success; ERROR on error with context | Epic 5 (Logback JSON structured logging config) | ✓ Covered |

## NFR Coverage Matrix

| NFR Number | PRD Requirement (summary) | Epic Coverage | Status |
|------------|--------------------------|---------------|--------|
| NFR-1 | Latency must not regress >20% vs Mule/CloudHub baseline in shadow mode | Epic 6 (shadow mode harness captures latency baseline) | ✓ Covered |
| NFR-2 | All 7 MUnit scenarios must have JUnit 5 equivalents before cutover | Epic 6 (7 parity tests + 2 additional = 9 total test scenarios) | ✓ Covered |
| NFR-3 | Traffic switch only after shadow mode confirms parity for all endpoints and error paths | Epic 7 (shadow mode gate hard prerequisite before cutover) | ✓ Covered |
| NFR-4 | Production container stable within 512 MB heap | Epic 7 (K8s Deployment manifest: 256Mi–512Mi RAM limit) | ✓ Covered |
| NFR-5 | No credentials in source-controlled files; all secrets via env vars | Epic 1 + Epic 7 (profile YAMLs use placeholders; K8s Secret injects YOUTUBE_API_KEY) | ✓ Covered |

## Missing Requirements

**None.** All PRD requirements are accounted for in the epic and story structure.

## Coverage Statistics

- Total PRD FRs: 22
- FRs covered in epics: 22
- FR coverage: **100%**
- Total PRD NFRs: 5
- NFRs covered in epics: 5
- NFR coverage: **100%**
- Orphaned epic FRs (in epics but not in PRD): 0

---

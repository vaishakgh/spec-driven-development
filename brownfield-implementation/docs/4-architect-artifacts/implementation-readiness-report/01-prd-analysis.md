# PRD Analysis

## Functional Requirements

FR-1: Retrieve a page of PlaylistItems for a given PlaylistId via `GET /api/youtube/playlists/{playlistId}`. Returns HTTP 200 with PlaylistResponse (totalResults, resultsPerPage, playlist[], nextPageToken).
FR-2: Each PlaylistItem contains: position (integer, zero-based), videoId, title, description, publishedAt (ISO 8601), thumbnail (URL or null), videoUrl (https://www.youtube.com/watch?v={videoId}).
FR-3: Retrieve next page via `pageToken` query parameter. Invalid/malformed token returns HTTP 400 before any upstream call. Upstream-rejected token also returns HTTP 400.
FR-4: Page size is environment-configurable — 25 for local/dev/test, 50 for production. Passed as maxResults. Callers cannot override.
FR-5: Retrieve video metadata via `GET /api/youtube/song/{videoId}`. Valid existing VideoId returns HTTP 200 with SongDetail.
FR-6: SongDetail contains: videoId, title, description, channelName (mapped from channelTitle), publishedAt (ISO 8601), duration (ISO 8601), viewCount (string), likeCount (string), thumbnail (URL or null), videoUrl.
FR-7: Non-existent VideoId (empty items[] from upstream) returns HTTP 404 and ErrorResponse with code:404. Breaking change from Mule 200-with-nulls.
FR-8: All non-2xx responses use ErrorResponse shape: error (string), message (string), code (integer matching HTTP status).
FR-9: Malformed requests (missing required params, non-conforming media types) return HTTP 400 and ErrorResponse.
FR-10: YouTube API 401 response propagated as HTTP 401 with message "Invalid or missing YouTube API Key."
FR-11: Unknown routes return HTTP 404 and ErrorResponse.
FR-12: Unsupported HTTP methods on valid paths return HTTP 405 and ErrorResponse.
FR-13: Accept: non-application/json returns HTTP 406. Content-Type: application/xml returns HTTP 415.
FR-14: Upstream connectivity failures return HTTP 503 with message "Unable to connect to YouTube API."
FR-15: Unhandled errors return HTTP 500 and ErrorResponse.
FR-16: OAS 3.0 spec governs all endpoints, params, response types, and error shapes. Includes pageToken param, HTTP 404 on video endpoint, nullable thumbnail.
FR-17: Base path preserved at /api/* — both endpoints accessible at existing URLs, no consumer URL changes required.
FR-18: Swagger UI accessible at /swagger-ui.html in local and dev profiles. Not accessible in production.
FR-19: Distinct configuration profiles for local, dev, production, and test environments.
FR-20: YouTube API key injected via YOUTUBE_API_KEY environment variable. No key in any source-controlled file.
FR-21: GET /actuator/health/liveness and GET /actuator/health/readiness return HTTP 200 when healthy. No authentication required.
FR-22: Structured logging — INFO on request entry (endpoint + key param), INFO on success (key result metric), ERROR on error (exception + context).

**Total FRs: 22**

## Non-Functional Requirements

NFR-1: API response latency must not regress more than 20% against Mule/CloudHub baseline at equivalent request volume, measured in shadow mode.
NFR-2: All 7 MUnit test scenarios must have JUnit 5 equivalents before cutover. No regression in test coverage permitted.
NFR-3: Traffic must not switch to Spring Boot until shadow mode validation confirms response parity for all endpoints and error paths.
NFR-4: Production container must operate stably within 512 MB heap memory.
NFR-5: No API keys, passwords, or credentials in any source-controlled file. All secrets via environment variables.

**Total NFRs: 5**

## Additional Requirements / Constraints

- **Non-goals (explicit):** No auth, no Camel, no caching, no rate limiting, no write operations, no new endpoints, no response shape changes (except pageToken addition and FR-7 404 fix).
- **Breaking change:** FR-7 requires consumer team notification ≥3 working days before cutover with written confirmation.
- **Shadow mode gate:** Hard prerequisite before any traffic cutover (NFR-3).
- **CloudHub decommission:** Follows stability confirmation post-cutover.

## PRD Completeness Assessment

The PRD is well-structured and complete. All 22 FRs have testable consequences. All 5 NFRs have measurable criteria. Non-goals are explicitly documented. The one gap noted in the PRD itself is the architecture document — technical implementation decisions are deferred to the addendum.

---

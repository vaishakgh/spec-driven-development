# 4. Features

## 4.1 Playlist Retrieval

**Description:** The service retrieves playlist items from the YouTube Data API v3 (`/playlistItems`) for a caller-supplied PlaylistId and returns a transformed PlaylistResponse. Results are paginated; the caller controls page navigation by supplying an optional `pageToken`. The YouTube API key and base URL are never visible to the caller. This feature realises UJ-1.

**Functional Requirements:**

### FR-1: Retrieve playlist items by PlaylistId

The internal team can retrieve a page of PlaylistItems for a given PlaylistId by calling `GET /api/youtube/playlists/{playlistId}`.

**Consequences (testable):**
- A request with a valid PlaylistId returns HTTP 200 and a PlaylistResponse body.
- The response `totalResults` matches the total count returned by the upstream YouTube API.
- The response `resultsPerPage` reflects the configured page size for the active environment.
- The response `playlist` array contains one PlaylistItem per video in the current page.
- The response `nextPageToken` is `null` when no further pages exist, and a non-null string when more pages are available.

### FR-2: PlaylistItem field contract

Each PlaylistItem in a PlaylistResponse contains all required fields.

**Consequences (testable):**
- Each PlaylistItem contains: `position` (integer, zero-based), `videoId` (string), `title` (string), `description` (string), `publishedAt` (ISO 8601 datetime string), `thumbnail` (string URL or null), `videoUrl` (string — format: `https://www.youtube.com/watch?v={videoId}`).
- `thumbnail` is `null` when the upstream YouTube API does not provide a medium-quality thumbnail.
- `videoUrl` is always a well-formed YouTube watch URL containing the `videoId`.

### FR-3: Pagination — retrieve subsequent pages

The internal team can retrieve the next page of results by supplying the `pageToken` query parameter.

**Consequences (testable):**
- A request to `GET /api/youtube/playlists/{playlistId}?pageToken={token}` passes the token to the upstream YouTube API `pageToken` parameter.
- The response reflects the correct next page of results.
- A `pageToken` value that is malformed or does not conform to the expected cursor format returns HTTP 400 and an ErrorResponse before any upstream call is made.
- An expired or otherwise rejected `pageToken` returned as an upstream error is propagated as HTTP 400.

### FR-4: Environment-configurable page size

The number of results returned per page is configurable per deployment environment.

**Consequences (testable):**
- Default page size is 25 for local, dev, and test environments.
- Production page size is 50.
- The configured page size is passed as `maxResults` to the upstream YouTube API.

**Out of Scope:**
- Callers cannot override the page size per request (no `maxResults` query parameter exposed).

---

## 4.2 Video Detail Retrieval

**Description:** The service retrieves metadata for a single YouTube video from the YouTube Data API v3 (`/videos`) for a caller-supplied VideoId and returns a SongDetail. Statistics (viewCount, likeCount) and content details (duration) are included alongside snippet data.

**Functional Requirements:**

### FR-5: Retrieve video details by VideoId

The internal team can retrieve metadata for a specific YouTube video by calling `GET /api/youtube/song/{videoId}`.

**Consequences (testable):**
- A request with a valid, existing VideoId returns HTTP 200 and a SongDetail body.

### FR-6: SongDetail field contract

A SongDetail response contains all required fields.

**Consequences (testable):**
- SongDetail contains: `videoId` (string), `title` (string), `description` (string), `channelName` (string — mapped from YouTube's `channelTitle` field), `publishedAt` (ISO 8601 datetime string), `duration` (ISO 8601 duration string, e.g. `PT3M33S`), `viewCount` (string), `likeCount` (string), `thumbnail` (string URL or null), `videoUrl` (string — format: `https://www.youtube.com/watch?v={videoId}`).
- `thumbnail` is `null` when the upstream YouTube API does not provide a high-quality thumbnail.
- `viewCount` and `likeCount` are returned as strings, consistent with the upstream YouTube API representation.

### FR-7: Non-existent VideoId returns HTTP 404

When the requested VideoId does not correspond to any YouTube video, the service returns HTTP 404 — not HTTP 200 with null fields.

**Consequences (testable):**
- A request with a VideoId that returns an empty `items[]` from the upstream YouTube API yields HTTP 404 and an ErrorResponse body with `code: 404`.
- The ErrorResponse `message` clearly indicates the video was not found.

**Note:** This corrects the current Mule behaviour (HTTP 200 with all-null fields). The internal team must be notified of this change prior to cutover — see §9.

---

## 4.3 Error Handling

**Description:** All error conditions — whether from invalid request parameters, upstream YouTube API failures, or unexpected internal errors — return an ErrorResponse body with a consistent `{ error, message, code }` structure. HTTP status codes are semantically correct and match the current Mule implementation (except for FR-7 above).

**Functional Requirements:**

### FR-8: Standardised ErrorResponse body

All error responses use the ErrorResponse shape.

**Consequences (testable):**
- Every non-2xx response body contains `error` (string), `message` (string), and `code` (integer matching the HTTP status).

### FR-9: HTTP 400 for malformed requests

A request that does not conform to the API specification returns HTTP 400.

**Consequences (testable):**
- Requests with missing required path parameters or non-conforming media types return HTTP 400 and an ErrorResponse.

### FR-10: HTTP 401 for invalid YouTube API key

An invalid or missing YouTube API key returns HTTP 401.

**Consequences (testable):**
- When the YouTube API responds with 401, the service returns HTTP 401 and an ErrorResponse with `message: "Invalid or missing YouTube API Key."`.

### FR-11: HTTP 404 for unknown routes

A request path that does not match any defined endpoint returns HTTP 404.

**Consequences (testable):**
- A GET request to any path other than `/api/youtube/playlists/{playlistId}` and `/api/youtube/song/{videoId}` returns HTTP 404 and an ErrorResponse body.

### FR-12: HTTP 405 for unsupported HTTP methods

A valid path called with an unsupported HTTP method returns HTTP 405.

**Consequences (testable):**
- A POST, PUT, PATCH, or DELETE request to either defined endpoint path returns HTTP 405 and an ErrorResponse body.

### FR-13: HTTP 406 / 415 for unsupported media types

Requests with unsupported `Accept` or `Content-Type` headers return HTTP 406 or 415 respectively.

**Consequences (testable):**
- A request with `Accept: text/html` (or any non-`application/json` Accept value) returns HTTP 406 and an ErrorResponse body.
- A request with `Content-Type: application/xml` returns HTTP 415 and an ErrorResponse body.

### FR-14: HTTP 503 for upstream connectivity failures

When the service cannot connect to the YouTube API, it returns HTTP 503.

**Consequences (testable):**
- Network timeouts and connection refusals from the upstream YouTube API return HTTP 503 and an ErrorResponse with `message: "Unable to connect to YouTube API."`.

### FR-15: HTTP 500 for unexpected errors

Any unhandled error returns HTTP 500 and an ErrorResponse.

---

## 4.4 API Contract and Documentation

**Description:** The API is contract-first. The existing RAML 1.0 specification is converted to OpenAPI 3.0 as the authoritative API definition for the Spring Boot service. An interactive API explorer is available in non-production environments.

**Functional Requirements:**

### FR-16: OpenAPI 3.0 specification

The migrated service is governed by an OpenAPI 3.0 specification that fully represents all endpoints, request parameters, response types, and error shapes.

**Consequences (testable):**
- The OAS 3.0 spec is generated from the existing RAML 1.0 spec and validated to cover all endpoints, response types, and error responses.
- The spec correctly marks `thumbnail` fields as nullable.
- The spec includes the new `pageToken` query parameter on the Playlist endpoint.
- The spec reflects the corrected HTTP 404 response for the Video endpoint when the video does not exist.

### FR-17: Backward-compatible base path

The API base path remains `/api/*` so that existing internal callers require no URL changes.

**Consequences (testable):**
- Both endpoints are accessible at `/api/youtube/playlists/{playlistId}` and `/api/youtube/song/{videoId}`.

### FR-18: Interactive API explorer in non-production environments

A browsable API explorer is accessible in local and dev environments.

**Consequences (testable):**
- An OpenAPI-based UI (e.g. Swagger UI) is reachable in local and dev profiles.
- The explorer is not accessible in the production profile.

---

## 4.5 Configuration and Secrets Management

**Description:** The service supports multiple deployment environments via profile-based configuration. The YouTube API key is sourced from the environment at runtime and is never present in source-controlled files.

**Functional Requirements:**

### FR-19: Multi-environment configuration profiles

The service has distinct configuration profiles for local, dev, production, and test environments.

**Consequences (testable):**
- Selecting the `local` profile targets `www.googleapis.com` with `maxResults=25`.
- Selecting the `prod` profile targets `www.googleapis.com` with `maxResults=50`.
- Selecting the `test` profile uses a static test API key and does not require a real YouTube connection.

### FR-20: YouTube API key sourced from environment variable

The YouTube API key is injected via the `YOUTUBE_API_KEY` environment variable.

**Consequences (testable):**
- The application starts and operates correctly when `YOUTUBE_API_KEY` is set in the environment.
- No API key value appears in any source-controlled configuration file.
- In the test profile, a static placeholder value is used so that tests run without a real key.

---

## 4.6 Observability

**Description:** The migrated service exposes health check endpoints and structured logging to support operational visibility in the Kubernetes deployment. This capability is absent in the current Mule/CloudHub deployment and is added as part of the migration.

**Functional Requirements:**

### FR-21: Health endpoints for Kubernetes probes

The service exposes liveness and readiness health endpoints.

**Consequences (testable):**
- `GET /actuator/health/liveness` returns HTTP 200 when the application is running.
- `GET /actuator/health/readiness` returns HTTP 200 when the application is ready to receive traffic.
- Both endpoints are available without authentication.

### FR-22: Structured application logging

The service produces structured logs at appropriate severity levels.

**Consequences (testable):**
- Each inbound request logs the endpoint called and the key input parameter (PlaylistId or VideoId) at INFO level.
- Each successful response logs the key result metric (total results count for playlist, or video found/not-found for video) at INFO level.
- Error conditions log at ERROR level with sufficient context to diagnose the failure.

---

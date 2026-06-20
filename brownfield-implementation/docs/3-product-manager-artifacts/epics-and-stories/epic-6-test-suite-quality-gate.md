# Epic 6: Test Suite & Quality Gate

The team has a complete automated test suite (7 MUnit-parity scenarios + 2 additional) that confirms migration correctness and satisfies the mandatory cutover gate.

## Story 6.1: Set Up JUnit 5 and WireMock Test Infrastructure

As an **internal developer**,
I want a JUnit 5 + WireMock test infrastructure configured for the `test` Spring profile,
So that all subsequent test stories have a working harness that intercepts outbound YouTube API calls without requiring a real network connection.

**Acceptance Criteria:**

**Given** `wiremock-spring-boot` (or equivalent) is added to `pom.xml` test scope
**When** `mvn test` is executed with the `test` profile
**Then** the WireMock server starts on a random port and the WebClient base URL is pointed at it automatically

**Given** the `test` profile is active
**When** any test runs
**Then** no real outbound HTTP calls are made to `googleapis.com`
**And** the static placeholder `YOUTUBE_API_KEY` from `application-test.yml` is used

**Given** the test infrastructure is in place
**When** `mvn test` is run with no test methods yet written
**Then** the build completes successfully with 0 failures

## Story 6.2: Port Playlist MUnit Tests to JUnit 5 + WireMock

As an **internal developer**,
I want the four playlist MUnit test scenarios ported to JUnit 5 + WireMock equivalents,
So that the playlist endpoint's happy path, empty response, auth failure, and connectivity failure are all automatically verified.

**Acceptance Criteria:**

**Given** a WireMock stub returning a valid `/playlistItems` YouTube response
**When** `GET /api/youtube/playlists/{playlistId}` is called in the test
**Then** HTTP 200 is returned and all `PlaylistResponse` fields (`totalResults`, `resultsPerPage`, `nextPageToken`, `playlist[]` with all item fields) match the stubbed data (`test-get-youtube-playlists-success`)

**Given** a WireMock stub returning an empty `items[]` from `/playlistItems`
**When** the endpoint is called
**Then** HTTP 200 is returned with `totalResults: 0` and an empty `playlist` array (`test-get-youtube-playlists-empty`)

**Given** a WireMock stub returning HTTP 401 from `/playlistItems`
**When** the endpoint is called
**Then** HTTP 401 is returned with a valid `ErrorResponse` body (`test-get-youtube-playlists-unauthorized`)

**Given** a WireMock fault simulating `CONNECTION_RESET` on `/playlistItems`
**When** the endpoint is called
**Then** HTTP 503 is returned with a valid `ErrorResponse` body (`test-get-youtube-playlists-connectivity-error`)

**Given** a WireMock stub returning a valid response with `pageToken` in the request query
**When** `GET /api/youtube/playlists/{playlistId}?pageToken={token}` is called
**Then** the WireMock stub verifies the `pageToken` parameter was forwarded to the upstream call (additional test beyond MUnit parity)

## Story 6.3: Port Video MUnit Tests and Add 404 Body Validation Test

As an **internal developer**,
I want the three video MUnit test scenarios ported to JUnit 5 + WireMock equivalents plus a test validating the 404 response body,
So that the video endpoint's full behaviour — including the corrected not-found handling — is automatically verified before cutover.

**Acceptance Criteria:**

**Given** a WireMock stub returning a valid `/videos` YouTube response with one item
**When** `GET /api/youtube/song/{videoId}` is called
**Then** HTTP 200 is returned and all `SongDetail` fields match the stubbed data, including `channelName` mapped from `channelTitle` (`test-get-youtube-song-by-id-success`)

**Given** a WireMock stub returning an empty `items[]` from `/videos`
**When** the endpoint is called
**Then** HTTP 404 is returned — not HTTP 200 with null fields (`test-get-youtube-song-not-found`)
**And** the `ErrorResponse` body contains `code: 404` (additional test beyond MUnit parity)

**Given** a WireMock stub returning HTTP 500 from `/videos`
**When** the endpoint is called
**Then** HTTP 500 is returned with a valid `ErrorResponse` body (`test-get-youtube-song-generic-error`)

**Given** all 9 test scenarios pass
**When** `mvn test` completes
**Then** the build is green with 0 failures, satisfying NFR-2 and unblocking the cutover gate

---

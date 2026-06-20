# Epic 2: Playlist Retrieval — End to End

The internal team can call `GET /api/youtube/playlists/{playlistId}` against the Spring Boot service and receive correctly shaped, paginated playlist data. The `pageToken` parameter enables navigation to subsequent pages; invalid tokens are rejected with HTTP 400.

## Story 2.1: Implement Playlist Response DTOs and Mapper

As an **internal developer**,
I want `PlaylistResponse`, `PlaylistItem` DTOs and a `PlaylistMapper` that transforms the raw YouTube API response into the contract-defined shape,
So that the playlist endpoint returns correctly structured data with all required fields.

**Acceptance Criteria:**

**Given** a raw YouTube `/playlistItems` API response with one or more items
**When** `PlaylistMapper.toPlaylistResponse()` is called
**Then** the returned `PlaylistResponse` contains `totalResults`, `resultsPerPage`, `nextPageToken` (null when no further pages exist), and a `playlist` array
**And** each `PlaylistItem` in `playlist` contains: `position` (integer, zero-based), `videoId`, `title`, `description`, `publishedAt` (ISO 8601), `thumbnail` (string URL or null), `videoUrl` formatted as `https://www.youtube.com/watch?v={videoId}`

**Given** a raw YouTube response where a video has no medium-quality thumbnail
**When** `PlaylistMapper.toPlaylistResponse()` is called
**Then** the `thumbnail` field for that item is `null`

**Given** a raw YouTube response with an empty `items[]` array
**When** `PlaylistMapper.toPlaylistResponse()` is called
**Then** `totalResults` is 0, `playlist` is an empty array, and `nextPageToken` is null

## Story 2.2: Implement PlaylistService with YouTube API Integration

As an **internal developer**,
I want a `PlaylistService` that calls the YouTube Data API v3 `/playlistItems` endpoint using WebClient,
So that real playlist data is retrieved from YouTube and made available to the controller.

**Acceptance Criteria:**

**Given** a valid `playlistId` and no `pageToken`
**When** `PlaylistService.getPlaylist()` is called
**Then** a WebClient GET request is made to `https://www.googleapis.com/youtube/v3/playlistItems` with `part=snippet`, `playlistId={playlistId}`, `maxResults={configuredPageSize}`, and `key={YOUTUBE_API_KEY}`
**And** the raw YouTube API response body is returned for mapping

**Given** a valid `playlistId` and a non-null `pageToken`
**When** `PlaylistService.getPlaylist()` is called
**Then** the `pageToken` parameter is forwarded to the upstream YouTube API as `pageToken={token}`

**Given** the YouTube API returns HTTP 401
**When** `PlaylistService.getPlaylist()` is called
**Then** a typed exception is thrown that the error handler can map to HTTP 401

**Given** the YouTube API is unreachable (connection timeout or reset)
**When** `PlaylistService.getPlaylist()` is called
**Then** a typed exception is thrown that the error handler can map to HTTP 503

## Story 2.3: Implement PlaylistController with pageToken Validation

As an **internal developer**,
I want a `PlaylistController` that wires `PlaylistService` and `PlaylistMapper`, exposes `GET /api/youtube/playlists/{playlistId}`, and validates the optional `pageToken` query parameter,
So that callers receive paginated playlist data and receive HTTP 400 immediately for an invalid token without hitting the YouTube API.

**Acceptance Criteria:**

**Given** a request to `GET /api/youtube/playlists/{playlistId}` with no `pageToken`
**When** the endpoint is called
**Then** HTTP 200 is returned with a valid `PlaylistResponse` body

**Given** a request with a well-formed `pageToken`
**When** the endpoint is called
**Then** the token is forwarded to `PlaylistService` and the correct next page of results is returned

**Given** a request with a `pageToken` value that is malformed (fails local format validation)
**When** the endpoint is called
**Then** HTTP 400 is returned with an `ErrorResponse` body and no upstream call to YouTube is made

**Given** a valid-format `pageToken` that is rejected by the YouTube API (expired or unknown)
**When** the endpoint is called
**Then** HTTP 400 is returned with an `ErrorResponse` body

**Given** a request with a valid `playlistId` that returns an empty playlist
**When** the endpoint is called
**Then** HTTP 200 is returned with `totalResults: 0` and an empty `playlist` array

---

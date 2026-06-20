# Epic 3: Video Detail Retrieval — End to End

The internal team can call `GET /api/youtube/song/{videoId}` and receive correct video metadata, or a proper HTTP 404 when the video does not exist (corrects the Mule 200-with-nulls bug — breaking change).

## Story 3.1: Implement SongDetail DTO and VideoMapper

As an **internal developer**,
I want a `SongDetail` DTO and a `VideoMapper` that transforms the raw YouTube API response into the contract-defined shape,
So that the video endpoint returns correctly structured data with all required fields including the `channelTitle` → `channelName` rename.

**Acceptance Criteria:**

**Given** a raw YouTube `/videos` API response with one item
**When** `VideoMapper.toSongDetail()` is called
**Then** the returned `SongDetail` contains: `videoId`, `title`, `description`, `channelName` (mapped from YouTube's `channelTitle` field), `publishedAt` (ISO 8601), `duration` (ISO 8601 duration e.g. `PT3M33S`), `viewCount` (string), `likeCount` (string), `thumbnail` (string URL or null), `videoUrl` formatted as `https://www.youtube.com/watch?v={videoId}`

**Given** a raw YouTube response where the video has no high-quality thumbnail
**When** `VideoMapper.toSongDetail()` is called
**Then** the `thumbnail` field is `null`

**Given** a raw YouTube response with an empty `items[]` array
**When** `VideoMapper.toSongDetail()` is called
**Then** `null` is returned (signal to the controller to issue HTTP 404)

## Story 3.2: Implement VideoService with YouTube API Integration

As an **internal developer**,
I want a `VideoService` that calls the YouTube Data API v3 `/videos` endpoint using WebClient,
So that real video metadata is retrieved from YouTube and made available to the controller.

**Acceptance Criteria:**

**Given** a valid `videoId`
**When** `VideoService.getVideo()` is called
**Then** a WebClient GET request is made to `https://www.googleapis.com/youtube/v3/videos` with `part=snippet,statistics,contentDetails`, `id={videoId}`, and `key={YOUTUBE_API_KEY}`
**And** the raw YouTube API response body is returned for mapping

**Given** the YouTube API returns HTTP 401
**When** `VideoService.getVideo()` is called
**Then** a typed exception is thrown that the error handler can map to HTTP 401

**Given** the YouTube API is unreachable (connection timeout or reset)
**When** `VideoService.getVideo()` is called
**Then** a typed exception is thrown that the error handler can map to HTTP 503

## Story 3.3: Implement VideoController with 404 Handling for Missing Videos

As an **internal developer**,
I want a `VideoController` that wires `VideoService` and `VideoMapper`, exposes `GET /api/youtube/song/{videoId}`, and returns HTTP 404 when the video does not exist,
So that the API uses correct HTTP semantics instead of the current Mule behaviour of returning HTTP 200 with null fields.

**Acceptance Criteria:**

**Given** a request to `GET /api/youtube/song/{videoId}` with a valid, existing `videoId`
**When** the endpoint is called
**Then** HTTP 200 is returned with a valid `SongDetail` body containing all required fields

**Given** a request with a `videoId` that does not correspond to any YouTube video (upstream returns empty `items[]`)
**When** the endpoint is called
**Then** HTTP 404 is returned with an `ErrorResponse` body where `code: 404` and `message` clearly indicates the video was not found
**And** the response body is NOT a `SongDetail` with null fields (breaking change from Mule behaviour)

**Given** the YouTube API returns HTTP 401
**When** the endpoint is called
**Then** HTTP 401 is returned with an `ErrorResponse` body

**Given** the YouTube API is unreachable
**When** the endpoint is called
**Then** HTTP 503 is returned with an `ErrorResponse` body

---

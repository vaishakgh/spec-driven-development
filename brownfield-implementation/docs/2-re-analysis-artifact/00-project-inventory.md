# youtube-playlist-api — Mule 4.6.0 Project Inventory

**Date:** 2026-06-19
**Project:** `com.example:youtube-playlist-api:1.0.0-SNAPSHOT`
**Runtime:** Mule 4.6.0 (`mule-application`)
**Java Compatibility:** 8, 11, 17 (per `mule-artifact.json`)
**Deployment:** CloudHub, MICRO worker (0.1 vCore), 1 instance, `us-east-1`

---

## 1. Source File Inventory

```
source-mule-youtube-playlist-api/
├── pom.xml                                      # Maven build descriptor
├── mule-artifact.json                           # Runtime + Java compat declaration
├── settings.xml                                 # Maven repo settings (Anypoint Exchange)
├── exchange-docs/
│   └── home.md                                  # Anypoint Exchange description
├── src/
│   ├── main/
│   │   ├── mule/
│   │   │   ├── youtube-playlist.xml             # ENTRY FLOW — HTTP Listener + APIKit Router + error handlers
│   │   │   ├── playlist.xml                     # IMPL FLOW 1 — GET /youtube/playlists/{playlistId}
│   │   │   └── playlist-video.xml               # IMPL FLOW 2 — GET /youtube/song/{videoId}
│   │   └── resources/
│   │       ├── api/
│   │       │   └── youtube-playlist-api.raml    # RAML 1.0 API specification
│   │       ├── log4j2.xml                       # Logging configuration
│   │       └── properties/
│   │           ├── config.yaml.template         # Property template (no secrets)
│   │           ├── config-local.yaml            # Local dev — API key via env var
│   │           ├── config-dev.yaml              # Dev CloudHub — API key via env var
│   │           └── config-prod.yaml             # Prod CloudHub — maxResults=50
│   └── test/
│       ├── munit/
│       │   └── youtube-playlist-test.xml        # MUnit 3.7.0 test suite (7 tests)
│       └── resources/
│           └── properties/
│               └── config-test.yaml             # Test config — static TEST_API_KEY
```

---

## 2. Mule Flow Inventory

### Flow 1: `youtube-playlist-api-main` (youtube-playlist.xml)

**Role:** Main HTTP entry point + APIKit Router + APIKit error handlers

| Component | Details |
|---|---|
| Trigger | `http:listener` on `HTTP_Listener_Config` — `path="/api/*"`, port 8081 |
| Router | `apikit:router` pointing to `APIKit_Config` (RAML: `api/youtube-playlist-api.raml`) |
| Error handlers | 5 APIKit error types (see table below) |

**APIKit Error Handlers in main flow:**

| Error Type | HTTP Status | Response |
|---|---|---|
| `APIKIT:BAD_REQUEST` | 400 | `{ error, message, code }` JSON |
| `APIKIT:NOT_FOUND` | 404 | `{ error, message, code }` JSON |
| `APIKIT:METHOD_NOT_ALLOWED` | 405 | `{ error, message, code }` JSON |
| `APIKIT:UNSUPPORTED_MEDIA_TYPE` | 415 | `{ error, message, code }` JSON |
| `APIKIT:NOT_ACCEPTABLE` | 406 | `{ error, message, code }` JSON |

---

### Flow 2: `get:\youtube\playlists\(playlistId):APIKit_Config` (playlist.xml)

**Role:** Implementation for `GET /youtube/playlists/{playlistId}`

| Step | Component | Details |
|---|---|---|
| 1 | Logger | INFO — logs incoming `playlistId` |
| 2 | `http:request` | `GET /playlistItems` against `YouTube_Request_Config` with query params: `part=snippet,contentDetails`, `playlistId` (from URI), `maxResults` (from config), `key` (from config) |
| 3 | `ee:transform` (DataWeave) | Maps YouTube API response → custom `PlaylistResponse` shape |
| 4 | Logger | INFO — logs `totalResults` |
| — | Error handlers | 3 types: `HTTP:UNAUTHORIZED` (401), `HTTP:CONNECTIVITY` (503), `ANY` (500) |

**DataWeave Transform 1 — Playlist Response:**
```dataweave
%dw 2.0
output application/json
var items = payload.items default []
---
{
    totalResults   : payload.pageInfo.totalResults default 0,
    resultsPerPage : payload.pageInfo.resultsPerPage default 0,
    nextPageToken  : payload.nextPageToken default null,
    playlist: items map (item, index) -> {
        position    : item.snippet.position,
        videoId     : item.contentDetails.videoId,
        title       : item.snippet.title,
        description : item.snippet.description,
        publishedAt : item.snippet.publishedAt,
        thumbnail   : item.snippet.thumbnails.medium.url default null,
        videoUrl    : "https://www.youtube.com/watch?v=" ++ item.contentDetails.videoId
    }
}
```

---

### Flow 3: `get:\youtube\song\(videoId):APIKit_Config` (playlist-video.xml)

**Role:** Implementation for `GET /youtube/song/{videoId}`

| Step | Component | Details |
|---|---|---|
| 1 | Logger | INFO — logs incoming `videoId` |
| 2 | `http:request` | `GET /videos` against `YouTube_Request_Config` with query params: `part=snippet,contentDetails,statistics`, `id` (from URI), `key` (from config) |
| 3 | `ee:transform` (DataWeave) | Maps YouTube API response → custom `SongDetail` shape |
| — | Error handlers | 3 types: `HTTP:UNAUTHORIZED` (401), `HTTP:CONNECTIVITY` (503), `ANY` (500) |

**DataWeave Transform 2 — Song Detail:**
```dataweave
%dw 2.0
output application/json
var video = payload.items[0] default {}
---
{
    videoId     : video.id,
    title       : video.snippet.title,
    description : video.snippet.description,
    channelName : video.snippet.channelTitle,
    publishedAt : video.snippet.publishedAt,
    duration    : video.contentDetails.duration,
    viewCount   : video.statistics.viewCount,
    likeCount   : video.statistics.likeCount,
    thumbnail   : video.snippet.thumbnails.high.url default null,
    videoUrl    : "https://www.youtube.com/watch?v=" ++ (video.id default "")
}
```

---

### Flow 4: `youtube-playlist-api-console` (youtube-playlist.xml)

**Role:** APIKit Console (interactive API explorer, dev only)

| Component | Details |
|---|---|
| Trigger | `http:listener` on `/api/console/*` |
| Content | `apikit:console` — serves Mule's built-in API Console UI |
| Note | Should be disabled/removed before production deployment |

---

## 3. Connector Inventory

| Connector | Version | Used For |
|---|---|---|
| `mule-http-connector` | 1.11.3 | Inbound HTTP listener (port 8081) + outbound HTTP requests to YouTube API |
| `mule-apikit-module` | 1.11.8 | RAML-driven routing, request validation, API console |
| `munit-runner` | 3.7.0 | MUnit test runner (test scope only) |
| `munit-tools` | 3.7.0 | MUnit mock and assertion utilities (test scope only) |
| `weave:assertions` | 1.2.1 | DataWeave test assertion utilities (test scope only) |

**No DB connectors, no messaging connectors, no scheduler, no file/FTP, no Salesforce/SAP.**

---

## 4. HTTP Configuration

### Inbound (HTTP Listener)
```
Host: 0.0.0.0
Port: 8081
Base path: /api/*
```

### Outbound (YouTube API)
```
Protocol: HTTPS
Host: ${youtube.api.baseUrl}  (= www.googleapis.com)
Port: 443
Base path: /youtube/v3
Endpoint 1: GET /playlistItems
Endpoint 2: GET /videos
```

---

## 5. Configuration Properties

| Property | Description | Source |
|---|---|---|
| `youtube.api.baseUrl` | YouTube API hostname | Config YAML — always `www.googleapis.com` |
| `youtube.api.key` | YouTube Data API v3 key | `${YOUTUBE_API_KEY}` env var — NOT in config files |
| `youtube.api.maxResults` | Page size | Config YAML — 25 (dev/local/test), 50 (prod) |
| `youtube.api.playlistId` | Default playlist | CloudHub property (pom.xml) |
| `env` | Environment selector | Set at runtime; resolves `config-${env}.yaml` |

**Environment profiles:**

| Profile | File | `maxResults` | API Key Source |
|---|---|---|---|
| `local` | config-local.yaml | 25 | `${YOUTUBE_API_KEY}` env var |
| `dev` | config-dev.yaml | 25 | `${YOUTUBE_API_KEY}` env var |
| `prod` | config-prod.yaml | 50 | `${YOUTUBE_API_KEY}` env var |
| `test` | config-test.yaml | 25 | `TEST_API_KEY_NOT_REAL` (static) |

---

## 6. API Contract (from RAML 1.0 spec)

**Base URI:** `https://youtube-playlist-api.cloudhub.io`
**Media type:** `application/json`
**Auth model:** Internal only — YouTube API key in backend config; no consumer-facing auth

### Endpoints

| Method | Path | Description | YouTube API Call |
|---|---|---|---|
| GET | `/youtube/playlists/{playlistId}` | Returns paginated playlist items | `GET /youtube/v3/playlistItems` |
| GET | `/youtube/song/{videoId}` | Returns video metadata | `GET /youtube/v3/videos` |

### Response Types

| Type | Fields |
|---|---|
| `PlaylistResponse` | `totalResults`, `resultsPerPage`, `nextPageToken`, `playlist[]` |
| `PlaylistItem` | `position`, `videoId`, `title`, `description`, `publishedAt`, `thumbnail`, `videoUrl` |
| `SongDetail` | `videoId`, `title`, `description`, `channelName`, `publishedAt`, `duration`, `viewCount`, `likeCount`, `thumbnail`, `videoUrl` |
| `ErrorResponse` | `error`, `message`, `code` |

### Standard Error Responses

| Status | Trigger | Description |
|---|---|---|
| 400 | `APIKIT:BAD_REQUEST` | Request doesn't match RAML spec |
| 401 | `HTTP:UNAUTHORIZED` | Invalid/missing YouTube API key |
| 404 | `APIKIT:NOT_FOUND` | URI doesn't match any RAML resource |
| 405 | `APIKIT:METHOD_NOT_ALLOWED` | HTTP method not supported |
| 406 | `APIKIT:NOT_ACCEPTABLE` | Response media type not supported |
| 415 | `APIKIT:UNSUPPORTED_MEDIA_TYPE` | Request media type not supported |
| 500 | `ANY` | Unexpected internal error |
| 503 | `HTTP:CONNECTIVITY` | Cannot connect to YouTube API |

---

## 7. Test Inventory (MUnit 3.7.0)

| # | Test Name | Flow Under Test | Scenario | Mock Target |
|---|---|---|---|---|
| 1 | `test-get-youtube-playlists-success` | playlist flow | 200 — 2-item playlist | `http:request` → `/playlistItems` |
| 2 | `test-get-youtube-playlists-empty` | playlist flow | 200 — empty playlist | `http:request` → `/playlistItems` |
| 3 | `test-get-youtube-playlists-unauthorized` | playlist flow | 401 — invalid API key | `http:request` throws `HTTP:UNAUTHORIZED` |
| 4 | `test-get-youtube-playlists-connectivity-error` | playlist flow | 503 — connection failure | `http:request` throws `HTTP:CONNECTIVITY` |
| 5 | `test-get-youtube-song-by-id-success` | song flow | 200 — valid video | `http:request` → `/videos` |
| 6 | `test-get-youtube-song-not-found` | song flow | 200 — empty items (not found) | `http:request` → `/videos` (empty) |
| 7 | `test-get-youtube-song-generic-error` | song flow | 500 — generic error | `http:request` throws `MULE:UNKNOWN` |

**Test strategy:** All tests use `munit-tools:mock-when` to intercept `http:request` processors and inject mock responses or errors. No real HTTP calls are made. Tests directly invoke implementation flows via `flow-ref`.

---

## 8. Deployment Configuration

| Parameter | Value |
|---|---|
| Target | CloudHub 1.0 (Anypoint Platform) |
| Worker type | MICRO (0.1 vCore, ~500MB RAM) |
| Worker count | 1 |
| Region | `us-east-1` |
| App name | `youtube-playlist-api` |
| Mule runtime | 4.6.0 |
| Anypoint URI | `https://anypoint.mulesoft.com` |

**CloudHub properties passed at deploy time:**
- `youtube.api.key`
- `youtube.api.playlistId`
- `youtube.api.maxResults` (default: 25)
- `youtube.api.baseUrl` (default: googleapis.com)

---

## 9. Logging Configuration

- **Framework:** log4j2 (log4j2.xml in resources)
- **Format:** Standard log4j2 pattern (no structured/JSON logging configured)
- **Level:** INFO for application flows; standard Mule framework log levels

---

## 10. What This Project Does NOT Have

- No database connectors (no JPA, JDBC, MongoDB)
- No messaging (no Anypoint MQ, no Kafka, no RabbitMQ)
- No scheduler or batch processing
- No caching layer
- No custom Java classes (no src/main/java)
- No Salesforce/SAP/ERP connectors
- No file/FTP/SFTP operations
- No security policies (no OAuth2, no JWT, no rate limiting — auth is internal)
- No Mule 3.x constructs (pure Mule 4.x throughout)
- No scatter-gather, aggregators, or complex EIP patterns
- No sub-flows or private flows

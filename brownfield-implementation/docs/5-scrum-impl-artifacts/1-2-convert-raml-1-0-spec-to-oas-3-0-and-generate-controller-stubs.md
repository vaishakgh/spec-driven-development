---
baseline_commit: d39500e7c2b6be612c060ad33070eb0f26c9a323
---

# Story 1.2: Convert RAML 1.0 Spec to OAS 3.0 and Generate Controller Stubs

Status: done

## Story

As an **internal developer**,
I want the API contract defined as an OAS 3.0 specification with `openapi-generator-maven-plugin` generating controller stubs,
so that the implementation is contract-first and the base path is preserved for existing callers.

## Acceptance Criteria

1. **Given** the existing RAML 1.0 spec as input
   **When** the OAS 3.0 spec is produced at `src/main/resources/api/youtube-playlist-api.yaml`
   **Then** it defines both endpoints at `/api/youtube/playlists/{playlistId}` and `/api/youtube/song/{videoId}` (FR-17)
   **And** it includes the `pageToken` query parameter (optional) on the Playlist endpoint (FR-3)
   **And** it includes an HTTP 404 response on the Video endpoint for non-existent videos (FR-7)
   **And** `thumbnail` fields in `PlaylistItem` and `SongDetail` are marked `nullable: true`
   **And** `nextPageToken` in `PlaylistResponse` is marked `nullable: true`
   **And** all error response shapes use the `{ error, message, code }` ErrorResponse schema (FR-16)

2. **Given** the OAS 3.0 spec is present and `pom.xml` has the generator plugin configured
   **When** `mvn generate-sources` is executed
   **Then** `openapi-generator-maven-plugin` generates compilable `PlaylistApi` and `VideoApi` Java interfaces
   **And** the generated interfaces return HTTP 501 Not Implemented by default
   **And** `mvn compile` succeeds with no errors

## Tasks / Subtasks

- [x] Task 1 — Create OAS 3.0 spec (AC: 1)
  - [x] Create `src/main/resources/api/` directory
  - [x] Write `youtube-playlist-api.yaml` using the complete spec from Dev Notes
  - [x] Verify: both paths use `/api/youtube/...` prefix (not just `/youtube/...` as in RAML)
  - [x] Verify: `pageToken` query param is present on playlist endpoint
  - [x] Verify: `404` response is present on video endpoint
  - [x] Verify: `thumbnail` and `nextPageToken` have `nullable: true`
  - [x] Verify: `publishedAt` and `duration` are `type: string` WITHOUT `format: date-time`

- [x] Task 2 — Configure openapi-generator plugin in `pom.xml` (AC: 2)
  - [x] Add `<executions>` block and full `<configuration>` to the existing plugin declaration (Story 1.1 added the plugin without config)
  - [x] Use the exact XML configuration block from Dev Notes
  - [x] Run `mvn generate-sources` and confirm output in `target/generated-sources/openapi/`

- [x] Task 3 — Verify generated interfaces (AC: 2)
  - [x] Confirm `PlaylistApi.java` generated in `target/.../controller/` (from tag `Playlist`)
  - [x] Confirm `VideoApi.java` generated in `target/.../controller/` (from tag `Video`)
  - [x] Confirm DTOs generated: `PlaylistResponse`, `PlaylistItem`, `SongDetail`, `ErrorResponse` in `target/.../dto/`
  - [x] Run `mvn compile` — must succeed with no errors

- [x] Task 4 — Create `OpenApiConfig.java` (springdoc metadata)
  - [x] Create `src/main/java/.../config/OpenApiConfig.java` per Dev Notes
  - [x] Provides title, version, description for Swagger UI (active in local/dev profiles)

- [x] Task 5 — Regression check
  - [x] `mvn spring-boot:run -Dspring-boot.run.profiles=local` still starts on port 8081
  - [x] Add `target/generated-sources/` to `.gitignore` if not already excluded (typically `target/` covers it)

## Dev Notes

### What This Story Does NOT Include

The OAS spec and generated interfaces are foundational for the entire project. However:
- **No controller implementations yet** — `PlaylistController` and `VideoController` are created in Epics 2 and 3
- **No service or mapper code** — those are later stories
- `OpenApiConfig.java` does NOT gate Swagger UI by profile — profile gating via YAML is Story 1.3's concern

### Previous Story Context (Story 1.1)

Story 1.1 created:
- `pom.xml` with `openapi-generator-maven-plugin` declared (version only, NO configuration block yet)
- `src/main/resources/application.yml` and `application-local.yml`
- `src/main/java/.../config/WebClientConfig.java`
- `YoutubePlaylistApiApplication.java`

**This story modifies:** `pom.xml` (add execution config), creates `src/main/resources/api/youtube-playlist-api.yaml` and `OpenApiConfig.java`.

### CRITICAL: RAML vs OAS Path Differences

The source RAML spec (`source-mule-youtube-playlist-api/src/main/resources/api/youtube-playlist-api.raml`) defines paths as:
```
/youtube:
  /playlists:
    /{playlistId}:   → /youtube/playlists/{playlistId}
  /song:
    /{videoId}:      → /youtube/song/{videoId}
```

The Mule HTTP listener config adds the `/api` prefix. The **OAS spec must include the full path**:
```
/api/youtube/playlists/{playlistId}   ← correct for Spring Boot
/api/youtube/song/{videoId}           ← correct for Spring Boot
```

The generated `@GetMapping` annotations on the interfaces will use these full paths. If you omit `/api`, all endpoints will be wrong and break FR-17.

### CRITICAL: Date Fields Must Be `type: string` (No `format: date-time`)

In the OAS spec, `publishedAt` and `duration` must be:
```yaml
publishedAt:
  type: string        # ← correct
  description: ISO 8601 timestamp ...
```

**NOT:**
```yaml
publishedAt:
  type: string
  format: date-time   # ← WRONG — generator produces OffsetDateTime, breaking mappers
```

With `format: date-time`, the openapi-generator produces `OffsetDateTime` in Java. This breaks the mapper because YouTube API returns raw ISO 8601 strings and we pass them through as-is (per architecture). `duration` (ISO 8601 period like `PT3M33S`) also has no standard OAS format — just use `type: string`.

### Complete OAS 3.0 Spec (`src/main/resources/api/youtube-playlist-api.yaml`)

```yaml
openapi: "3.0.3"
info:
  title: YouTube Playlist API
  description: |
    Spring Boot service proxying the YouTube Data API v3.
    Internal API — consumers do not supply API keys.
  version: "v1"

servers:
  - url: http://localhost:8081
    description: Local development server

paths:

  /api/youtube/playlists/{playlistId}:
    get:
      tags:
        - Playlist
      summary: Get Playlist Items
      operationId: getPlaylistItems
      description: |
        Fetches items from the specified YouTube playlist, paginated.
        Use nextPageToken from the response to retrieve subsequent pages.
      parameters:
        - name: playlistId
          in: path
          required: true
          description: Unique identifier of the YouTube playlist (e.g. PLxxxxxx)
          schema:
            type: string
          example: "PLbpi6ZahtOH6Ar_3GPy3workP3aUa5QGD"
        - name: pageToken
          in: query
          required: false
          description: Cursor token for pagination. Must match the cursor format from a prior response.
          schema:
            type: string
      responses:
        "200":
          description: Successfully retrieved playlist items
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/PlaylistResponse"
        "400":
          description: Invalid or malformed pageToken parameter
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ErrorResponse"
        "401":
          description: The YouTube API key is invalid or missing
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ErrorResponse"
              example:
                error: "Unauthorized"
                message: "Invalid or missing YouTube API Key."
                code: 401
        "503":
          description: Could not connect to the YouTube API
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ErrorResponse"
              example:
                error: "Connection Error"
                message: "Unable to connect to YouTube API."
                code: 503
        "500":
          description: Unexpected internal server error
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ErrorResponse"

  /api/youtube/song/{videoId}:
    get:
      tags:
        - Video
      summary: Get Song Details by Video ID
      operationId: getVideoDetails
      description: |
        Fetches detailed metadata for a single YouTube video including snippet,
        content details (duration), and statistics (views, likes).
        Returns HTTP 404 if the videoId does not exist — this is a breaking change
        from the previous Mule behaviour (which returned HTTP 200 with null fields).
      parameters:
        - name: videoId
          in: path
          required: true
          description: Unique YouTube video identifier (11-character string)
          schema:
            type: string
          example: "dQw4w9WgXcQ"
      responses:
        "200":
          description: Successfully retrieved video details
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/SongDetail"
        "404":
          description: The requested videoId does not correspond to any YouTube video
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ErrorResponse"
              example:
                error: "Not Found"
                message: "Video not found for the given videoId."
                code: 404
        "401":
          description: The YouTube API key is invalid or missing
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ErrorResponse"
        "503":
          description: Could not connect to the YouTube API
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ErrorResponse"
        "500":
          description: Unexpected internal server error
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ErrorResponse"

components:
  schemas:

    PlaylistItem:
      description: A single item (song/video) within a YouTube playlist
      type: object
      required:
        - position
        - videoId
        - title
        - description
        - publishedAt
        - videoUrl
      properties:
        position:
          type: integer
          description: Zero-based position of this item in the playlist
          example: 0
        videoId:
          type: string
          description: YouTube video identifier
          example: "dQw4w9WgXcQ"
        title:
          type: string
          description: Title of the video
          example: "Never Gonna Give You Up"
        description:
          type: string
          description: Video description text
          example: "The official music video."
        publishedAt:
          type: string
          description: ISO 8601 timestamp when the video was published
          example: "2009-10-25T06:57:33Z"
        thumbnail:
          type: string
          nullable: true
          description: URL of the medium-quality thumbnail, or null if unavailable
          example: "https://img.youtube.com/vi/dQw4w9WgXcQ/mqdefault.jpg"
        videoUrl:
          type: string
          description: "Full YouTube watch URL. Format: https://www.youtube.com/watch?v={videoId}"
          example: "https://www.youtube.com/watch?v=dQw4w9WgXcQ"

    PlaylistResponse:
      description: Response returned by GET /api/youtube/playlists/{playlistId}
      type: object
      required:
        - totalResults
        - resultsPerPage
        - playlist
      properties:
        totalResults:
          type: integer
          description: Total number of items in the playlist as reported by YouTube
          example: 50
        resultsPerPage:
          type: integer
          description: Maximum number of results returned per page
          example: 25
        nextPageToken:
          type: string
          nullable: true
          description: Token to retrieve the next page. Null when no further pages exist.
          example: "EAAelgEKADiD..."
        playlist:
          type: array
          description: Array of playlist items for the current page
          items:
            $ref: "#/components/schemas/PlaylistItem"

    SongDetail:
      description: Detailed metadata for a single YouTube video
      type: object
      required:
        - videoId
        - title
        - description
        - channelName
        - publishedAt
        - duration
        - viewCount
        - likeCount
        - videoUrl
      properties:
        videoId:
          type: string
          description: YouTube video identifier
          example: "dQw4w9WgXcQ"
        title:
          type: string
          description: Title of the video
          example: "Never Gonna Give You Up"
        description:
          type: string
          description: Full description of the video
          example: "The official music video."
        channelName:
          type: string
          description: Channel name (mapped from YouTube's channelTitle field)
          example: "RickAstleyVEVO"
        publishedAt:
          type: string
          description: ISO 8601 timestamp when the video was published
          example: "2009-10-25T06:57:33Z"
        duration:
          type: string
          description: "ISO 8601 duration string, e.g. PT3M33S"
          example: "PT3M33S"
        viewCount:
          type: string
          description: Total views as string (YouTube API returns string)
          example: "1400000000"
        likeCount:
          type: string
          description: Total likes as string (YouTube API returns string)
          example: "15000000"
        thumbnail:
          type: string
          nullable: true
          description: URL of the high-quality thumbnail, or null if unavailable
          example: "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg"
        videoUrl:
          type: string
          description: "Full YouTube watch URL. Format: https://www.youtube.com/watch?v={videoId}"
          example: "https://www.youtube.com/watch?v=dQw4w9WgXcQ"

    ErrorResponse:
      description: Standard error response returned on all error conditions
      type: object
      required:
        - error
        - message
        - code
      properties:
        error:
          type: string
          description: Short label describing the error category
          example: "Unauthorized"
        message:
          type: string
          description: Human-readable description of what went wrong
          example: "Invalid or missing YouTube API Key."
        code:
          type: integer
          description: HTTP status code matching the response status
          example: 401
```

### `pom.xml` Plugin Configuration Update

Story 1.1 declared the plugin with version only. Story 1.2 adds the execution:

```xml
<plugin>
    <groupId>org.openapitools</groupId>
    <artifactId>openapi-generator-maven-plugin</artifactId>
    <version><!-- version from Story 1.1 --></version>
    <executions>
        <execution>
            <goals>
                <goal>generate</goal>
            </goals>
            <configuration>
                <inputSpec>${project.basedir}/src/main/resources/api/youtube-playlist-api.yaml</inputSpec>
                <generatorName>spring</generatorName>
                <configOptions>
                    <interfaceOnly>true</interfaceOnly>
                    <useSpringBoot3>true</useSpringBoot3>
                    <useJakartaEe>true</useJakartaEe>
                    <useTags>true</useTags>
                </configOptions>
                <modelPackage>com.example.youtubeplaylistapi.dto</modelPackage>
                <apiPackage>com.example.youtubeplaylistapi.controller</apiPackage>
            </configuration>
        </execution>
    </executions>
</plugin>
```

**Key config option explanations:**
| Option | Effect |
|---|---|
| `interfaceOnly=true` | Generates Java interfaces, not implementation classes. Controllers implement these interfaces. |
| `useSpringBoot3=true` | Generates Spring Boot 3+ compatible code (also works for 4.x) |
| `useJakartaEe=true` | Generates `jakarta.*` imports (not `javax.*`) — required for Spring Boot 4.x |
| `useTags=true` | Groups operations by OAS tag to name the interfaces: `Playlist` → `PlaylistApi`, `Video` → `VideoApi` |
| `modelPackage` | Generated DTOs land in `dto` package — **do not hand-write these** |
| `apiPackage` | Generated interfaces land in `controller` package — **do not hand-write these** |

### `OpenApiConfig.java` (complete)

```java
package com.example.youtubeplaylistapi.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("YouTube Playlist API")
                        .version("v1")
                        .description("Spring Boot service proxying the YouTube Data API v3 — MuleSoft migration"));
    }
}
```

This registers the API metadata for Swagger UI. Profile gating (hiding Swagger UI in prod) is done via YAML in Story 1.3 — not here.

### What Gets Generated by `mvn generate-sources`

Output location: `target/generated-sources/openapi/com/example/youtubeplaylistapi/`

| Generated File | Package | Purpose |
|---|---|---|
| `PlaylistApi.java` | `controller` | Interface with `@GetMapping("/api/youtube/playlists/{playlistId}")` + default 501 body |
| `VideoApi.java` | `controller` | Interface with `@GetMapping("/api/youtube/song/{videoId}")` + default 501 body |
| `PlaylistResponse.java` | `dto` | Contract DTO matching OAS schema |
| `PlaylistItem.java` | `dto` | Contract DTO matching OAS schema |
| `SongDetail.java` | `dto` | Contract DTO matching OAS schema |
| `ErrorResponse.java` | `dto` | Contract DTO matching OAS schema |

**Do NOT commit any of these.** They must stay in `target/`. `target/` must be in `.gitignore`.

**Do NOT edit generated files.** If a field name or type is wrong, edit the OAS spec and regenerate.

### Architecture Guardrails for This Story

**Never hand-write DTOs for OAS contract types.** `PlaylistResponse`, `PlaylistItem`, `SongDetail`, and `ErrorResponse` are generated from the spec. Hand-writing them creates drift. The mappers in later stories reference the generated types — they must come from the generator.

**The controller interfaces are not implemented in this story.** Story 1.2 delivers the interfaces only. No `PlaylistController.java` or `VideoController.java` yet — those are created in Stories 2.3 and 3.3. After this story, a request to `/api/youtube/playlists/{id}` will return 501 (Not Implemented) at most, but likely 404 because no controller exists yet.

**Tags drive interface names.** The `Playlist` tag → `PlaylistApi` and `Video` tag → `VideoApi`. If you rename the tags in the OAS spec, you rename the generated interfaces — all future controller implements clauses must match.

**OAS 3.0.x not 3.1.x.** The Spring generator targets OAS 3.0.x. Use `openapi: "3.0.3"`. OpenAPI 3.1 has breaking changes in the generator — do not use it.

### RAML Source for Reference

Original RAML spec at: `source-mule-youtube-playlist-api/src/main/resources/api/youtube-playlist-api.raml`

Key differences between RAML source and the OAS spec this story produces:

| Aspect | RAML Source | OAS 3.0 (this story) |
|---|---|---|
| Base path | `/youtube/...` (listener adds `/api`) | Full `/api/youtube/...` |
| `pageToken` param | **Absent** | **Added** (FR-3) |
| Video endpoint 404 | **Absent** (returns 200) | **Added** (FR-7 — breaking change) |
| `thumbnail` nullable | `string \| nil` | `nullable: true` |
| `publishedAt` type | `datetime (rfc3339)` | `string` (no format) |
| Common errors | RAML trait `commonErrors` | Inline 401/503/500 per endpoint |

### Files Created/Modified in This Story

| File | Type | Notes |
|---|---|---|
| `src/main/resources/api/youtube-playlist-api.yaml` | NEW | OAS 3.0 spec — single source of truth |
| `pom.xml` | UPDATE | Add `<executions>` and `<configuration>` to existing openapi-generator plugin |
| `src/main/java/.../config/OpenApiConfig.java` | NEW | springdoc title/version metadata |
| `target/generated-sources/openapi/...` | GENERATED | Do not commit; auto-added to classpath |

### Anti-Patterns to Avoid

```yaml
# ❌ Wrong: missing /api prefix — endpoints will be unreachable
paths:
  /youtube/playlists/{playlistId}:   # must be /api/youtube/playlists/{playlistId}

# ❌ Wrong: date-time format generates OffsetDateTime — breaks mappers
publishedAt:
  type: string
  format: date-time    # remove format

# ❌ Wrong: OpenAPI 3.1 syntax
openapi: "3.1.0"   # use 3.0.3

# ❌ Wrong: thumbnail not nullable — breaks consumer contract
thumbnail:
  type: string    # must add: nullable: true
```

```java
// ❌ Wrong: hand-writing generated DTOs
// Don't create PlaylistResponse.java in src/main/java/
// It belongs in target/ — generated by plugin

// ❌ Wrong: editing generated files
// If you need to change PlaylistResponse, edit the OAS spec
// and run mvn generate-sources again
```

### References

- OAS spec location: [Source: docs/4-architect-artifacts/architecture/04-implementation-patterns-consistency-rules.md#Structure Patterns]
- openapi-generator configuration: [Source: docs/4-architect-artifacts/architecture/03-core-architectural-decisions.md#API & Communication Patterns]
- Generated file locations and what's committed vs generated: [Source: docs/4-architect-artifacts/architecture/05-project-structure-boundaries.md#Complete Project Directory Structure]
- FR-3 (pageToken), FR-7 (404 not-found), FR-16 (ErrorResponse), FR-17 (base path): [Source: docs/3-product-manager-artifacts/prd/4-features.md]
- Source RAML for reference: `source-mule-youtube-playlist-api/src/main/resources/api/youtube-playlist-api.raml`

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6 (bmad-create-story 2026-06-20)
claude-sonnet-4-6 (bmad-dev-story 2026-06-20)

### Debug Log References

- First `mvn compile` failed: generated DTOs imported `org.openapitools.jackson.nullable.JsonNullable` because `nullable: true` fields trigger `JsonNullable<T>` wrapping by default. Fix: added `<openApiNullable>false</openApiNullable>` to generator configOptions — nullable fields now use plain `@Nullable String` without the extra wrapper, which is correct for this pass-through mapper architecture.
- Generated correctly: `PlaylistApi.java` with `@RequestMapping(value = "/api/youtube/playlists/{playlistId}", ...)` and `VideoApi.java` with `@RequestMapping(value = "/api/youtube/song/{videoId}", ...)` — paths confirmed correct.
- Regression check: app starts cleanly on port 8081; `/actuator/health` returns `{"status":"UP"}`.

### Completion Notes List

- Added `<openApiNullable>false</openApiNullable>` to the generator configOptions (not in the story spec — discovered at compile time). This is the correct setting for this architecture: nullable fields in generated DTOs are plain `@Nullable` Java types, not `JsonNullable<T>` wrappers. Mappers in later stories reference plain types.
- Both ACs verified: OAS spec contains all required elements ✓; `mvn compile` BUILD SUCCESS with 10 source files ✓
- `target/` in `.gitignore` already covers `target/generated-sources/` — no change needed.
- AC 2 ("generated interfaces return HTTP 501 Not Implemented by default") — the generated default interface methods call `getRequest()` and return `501 Not Implemented` via `ApiUtil`. Confirmed in generated `PlaylistApi.java` and `VideoApi.java`.

### File List

- `dest-spring-youtube-playlist-api/src/main/resources/api/youtube-playlist-api.yaml` (NEW)
- `dest-spring-youtube-playlist-api/src/main/java/com/example/youtubeplaylistapi/config/OpenApiConfig.java` (NEW)
- `dest-spring-youtube-playlist-api/pom.xml` (UPDATE — openapi-generator executions + configuration block)

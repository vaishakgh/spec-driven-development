---
baseline_commit: 10adcc8eac9e3a9ef63af81a231704d940f67dbb
---

# Story 2.1: Implement Playlist Response DTOs and Mapper

Status: done

## Story

As an **internal developer**,
I want `PlaylistResponse`, `PlaylistItem` DTOs and a `PlaylistMapper` that transforms the raw YouTube API response into the contract-defined shape,
so that the playlist endpoint returns correctly structured data with all required fields.

## Acceptance Criteria

1. **Given** a raw YouTube `/playlistItems` API response with one or more items
   **When** `PlaylistMapper.toPlaylistResponse()` is called
   **Then** the returned `PlaylistResponse` contains `totalResults`, `resultsPerPage`, `nextPageToken` (null when no further pages exist), and a `playlist` array
   **And** each `PlaylistItem` in `playlist` contains: `position` (integer, zero-based), `videoId`, `title`, `description`, `publishedAt` (ISO 8601), `thumbnail` (string URL or null), `videoUrl` formatted as `https://www.youtube.com/watch?v={videoId}`

2. **Given** a raw YouTube response where a video has no medium-quality thumbnail
   **When** `PlaylistMapper.toPlaylistResponse()` is called
   **Then** the `thumbnail` field for that item is `null`

3. **Given** a raw YouTube response with an empty `items[]` array
   **When** `PlaylistMapper.toPlaylistResponse()` is called
   **Then** `totalResults` is 0, `playlist` is an empty array, and `nextPageToken` is null

## Tasks / Subtasks

- [x] Task 1 — Create `YTPlaylistItemsResponse` upstream model (AC: 1–3)
  - [x] Create `src/main/java/com/example/youtubeplaylistapi/dto/youtube/YTPlaylistItemsResponse.java`
  - [x] Annotate class and all static inner classes with `@JsonIgnoreProperties(ignoreUnknown = true)`
  - [x] Fields: `String nextPageToken`, `PageInfo pageInfo`, `List<Item> items`
  - [x] Inner class `PageInfo`: `Integer totalResults`, `Integer resultsPerPage`
  - [x] Inner class `Item` → inner class `Snippet`: `String title`, `String description`, `String publishedAt`, `Integer position`, `Thumbnails thumbnails`, `ResourceId resourceId`
  - [x] Inner class `Thumbnails`: only `Thumbnail medium` (no `default`, `high`, etc. — ignore others via `@JsonIgnoreProperties`)
  - [x] Inner class `Thumbnail`: `String url`
  - [x] Inner class `ResourceId`: `String videoId`
  - [x] All fields use standard Java camelCase — matches YouTube API's camelCase JSON exactly; no `@JsonProperty` annotations needed

- [x] Task 2 — Create `PlaylistMapper` (AC: 1–3)
  - [x] Create `src/main/java/com/example/youtubeplaylistapi/mapper/PlaylistMapper.java`
  - [x] Annotate with `@Component`
  - [x] Public method: `PlaylistResponse toPlaylistResponse(YTPlaylistItemsResponse ytResponse)`
  - [x] Maps `pageInfo.totalResults` → `totalResults` (not `items.size()`)
  - [x] Maps `pageInfo.resultsPerPage` → `resultsPerPage`
  - [x] Passes `nextPageToken` through directly (null when not in YouTube response)
  - [x] Maps each `item.snippet` to a `PlaylistItem` using a private `toPlaylistItem()` helper
  - [x] `thumbnail`: set to `null` when `snippet.thumbnails` is null OR `snippet.thumbnails.medium` is null
  - [x] `videoUrl`: always constructed as `"https://www.youtube.com/watch?v=" + snippet.resourceId.videoId`
  - [x] Uses setters on `PlaylistResponse` and `PlaylistItem` — NOT constructors (generated DTOs have setters; constructor signature may change on regen)
  - [x] Handles null or empty `items` list — returns `playlist = new ArrayList<>()`
  - [x] Imports `PlaylistResponse` and `PlaylistItem` from `com.example.youtubeplaylistapi.dto` (generated package)

- [x] Task 3 — Write unit tests (AC: 1–3)
  - [x] Create `src/test/java/com/example/youtubeplaylistapi/mapper/PlaylistMapperTest.java`
  - [x] Plain JUnit 5, no `@SpringBootTest` — instantiate `PlaylistMapper` directly: `new PlaylistMapper()`
  - [x] `should_map_all_fields_when_full_youtube_response()` — verifies AC 1: all `PlaylistItem` fields, `totalResults`, `resultsPerPage`, `nextPageToken`
  - [x] `should_return_null_thumbnail_when_medium_thumbnail_missing()` — verifies AC 2
  - [x] `should_return_empty_playlist_when_items_array_is_empty()` — verifies AC 3: `totalResults=0`, `playlist.isEmpty()`, `nextPageToken==null`
  - [x] Assert `videoUrl` equals `"https://www.youtube.com/watch?v=" + videoId`
  - [x] Assert `publishedAt` is passed through as-is (no reformatting)
  - [x] Run `mvn test` — all 3 tests pass

- [x] Task 4 — Compile and verify (AC: all)
  - [x] Run `mvn generate-sources compile` — must succeed (generated DTOs required in classpath)
  - [x] Run `mvn test` — all tests pass (9 existing from Epic 4 + 3 new mapper tests = 12 total)
  - [x] Confirm no new files created under `src/main/java/.../dto/` root (no hand-written `PlaylistResponse.java` or `PlaylistItem.java`)

### Review Findings

- [x] [Review][Patch] Null snippet NPE in toPlaylistItem — ytItem.getSnippet() called without null guard; NPE if YouTube returns item with no snippet field [PlaylistMapper.java:242]
- [x] [Review][Patch] Null resourceId NPE — snippet.getResourceId() called twice without null check; NPE if resourceId absent [PlaylistMapper.java:245]
- [x] [Review][Patch] Silent null videoId — getVideoId() not guarded; produces videoUrl "https://www.youtube.com/watch?v=null" silently [PlaylistMapper.java:257]
- [x] [Review][Defer] Null Integer in pageInfo — YouTube rarely returns null totalResults/resultsPerPage but possible; setTotalResults(null) would serialize as null in response [PlaylistMapper.java:221] — deferred, pre-existing

## Dev Notes

### CRITICAL: PlaylistResponse and PlaylistItem Are GENERATED — Do NOT Hand-Write

`PlaylistResponse` and `PlaylistItem` are generated by `openapi-generator-maven-plugin` from the OAS 3.0 spec.

**Correct imports:**
```java
import com.example.youtubeplaylistapi.dto.PlaylistResponse;
import com.example.youtubeplaylistapi.dto.PlaylistItem;
```

**Generated file locations (never edit):**
```
target/generated-sources/openapi/com/example/youtubeplaylistapi/dto/PlaylistResponse.java
target/generated-sources/openapi/com/example/youtubeplaylistapi/dto/PlaylistItem.java
```

Run `mvn generate-sources` first if the generated files are not present.

### Generated DTO API (confirmed from source)

**`PlaylistResponse`** fields (all use setters):
- `setTotalResults(Integer)` / `getTotalResults()`
- `setResultsPerPage(Integer)` / `getResultsPerPage()`
- `setNextPageToken(String)` / `getNextPageToken()` — `null` by default
- `setPlaylist(List<PlaylistItem>)` / `getPlaylist()`

**`PlaylistItem`** fields (all use setters):
- `setPosition(Integer)` / `getPosition()`
- `setVideoId(String)` / `getVideoId()`
- `setTitle(String)` / `getTitle()`
- `setDescription(String)` / `getDescription()`
- `setPublishedAt(String)` / `getPublishedAt()` — passes through ISO 8601 string as-is
- `setThumbnail(String)` / `getThumbnail()` — nullable, `null` by default
- `setVideoUrl(String)` / `getVideoUrl()`

**Use setters — not constructors.** The required-param constructor exists but will change if the OAS spec changes and is regenerated.

### YTPlaylistItemsResponse Structure

Create this class at `src/main/java/com/example/youtubeplaylistapi/dto/youtube/YTPlaylistItemsResponse.java`.

The YouTube Data API v3 `/playlistItems` response shape (only fields we need):

```json
{
  "nextPageToken": "EAAelgEKADiD...",
  "pageInfo": {
    "totalResults": 50,
    "resultsPerPage": 25
  },
  "items": [
    {
      "snippet": {
        "publishedAt": "2009-10-25T06:57:33Z",
        "title": "Never Gonna Give You Up",
        "description": "The official music video.",
        "thumbnails": {
          "medium": {
            "url": "https://img.youtube.com/vi/dQw4w9WgXcQ/mqdefault.jpg"
          }
        },
        "position": 0,
        "resourceId": {
          "videoId": "dQw4w9WgXcQ"
        }
      }
    }
  ]
}
```

**Mandatory:** All classes need `@JsonIgnoreProperties(ignoreUnknown = true)` — YouTube returns many extra fields (`kind`, `etag`, `channelId`, `channelTitle`, `playlistId`, etc.) that Jackson must ignore without failing.

**Java structure:**

```java
@JsonIgnoreProperties(ignoreUnknown = true)
public class YTPlaylistItemsResponse {
    private String nextPageToken;
    private PageInfo pageInfo;
    private List<Item> items;
    // getters/setters

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PageInfo {
        private Integer totalResults;
        private Integer resultsPerPage;
        // getters/setters
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        private Snippet snippet;
        // getter/setter

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Snippet {
            private String title;
            private String description;
            private String publishedAt;
            private Integer position;
            private Thumbnails thumbnails;  // null when video has no thumbnails
            private ResourceId resourceId;
            // getters/setters

            @JsonIgnoreProperties(ignoreUnknown = true)
            public static class Thumbnails {
                private Thumbnail medium;   // null when no medium-quality thumbnail
                // getter/setter

                @JsonIgnoreProperties(ignoreUnknown = true)
                public static class Thumbnail {
                    private String url;
                    // getter/setter
                }
            }

            @JsonIgnoreProperties(ignoreUnknown = true)
            public static class ResourceId {
                private String videoId;
                // getter/setter
            }
        }
    }
}
```

### PlaylistMapper Implementation

```java
package com.example.youtubeplaylistapi.mapper;

import com.example.youtubeplaylistapi.dto.PlaylistItem;
import com.example.youtubeplaylistapi.dto.PlaylistResponse;
import com.example.youtubeplaylistapi.dto.youtube.YTPlaylistItemsResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PlaylistMapper {

    public PlaylistResponse toPlaylistResponse(YTPlaylistItemsResponse ytResponse) {
        PlaylistResponse response = new PlaylistResponse();

        if (ytResponse.getPageInfo() != null) {
            response.setTotalResults(ytResponse.getPageInfo().getTotalResults());
            response.setResultsPerPage(ytResponse.getPageInfo().getResultsPerPage());
        } else {
            response.setTotalResults(0);
            response.setResultsPerPage(0);
        }

        response.setNextPageToken(ytResponse.getNextPageToken());  // null when absent

        List<PlaylistItem> items = new ArrayList<>();
        if (ytResponse.getItems() != null) {
            for (YTPlaylistItemsResponse.Item ytItem : ytResponse.getItems()) {
                items.add(toPlaylistItem(ytItem));
            }
        }
        response.setPlaylist(items);

        return response;
    }

    private PlaylistItem toPlaylistItem(YTPlaylistItemsResponse.Item ytItem) {
        var snippet = ytItem.getSnippet();
        PlaylistItem item = new PlaylistItem();
        item.setPosition(snippet.getPosition());
        item.setVideoId(snippet.getResourceId().getVideoId());
        item.setTitle(snippet.getTitle());
        item.setDescription(snippet.getDescription());
        item.setPublishedAt(snippet.getPublishedAt());  // pass-through, no reformatting

        // thumbnail is null when no medium-quality thumbnail available (AC 2)
        String thumbnail = null;
        if (snippet.getThumbnails() != null && snippet.getThumbnails().getMedium() != null) {
            thumbnail = snippet.getThumbnails().getMedium().getUrl();
        }
        item.setThumbnail(thumbnail);

        item.setVideoUrl("https://www.youtube.com/watch?v=" + snippet.getResourceId().getVideoId());

        return item;
    }
}
```

### PlaylistMapperTest Implementation

```java
package com.example.youtubeplaylistapi.mapper;

import com.example.youtubeplaylistapi.dto.PlaylistResponse;
import com.example.youtubeplaylistapi.dto.youtube.YTPlaylistItemsResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlaylistMapperTest {

    private final PlaylistMapper mapper = new PlaylistMapper();

    @Test
    void should_map_all_fields_when_full_youtube_response() {
        YTPlaylistItemsResponse yt = buildFullResponse();

        PlaylistResponse result = mapper.toPlaylistResponse(yt);

        assertThat(result.getTotalResults()).isEqualTo(50);
        assertThat(result.getResultsPerPage()).isEqualTo(25);
        assertThat(result.getNextPageToken()).isEqualTo("EAAelgEKADiD");
        assertThat(result.getPlaylist()).hasSize(1);

        var item = result.getPlaylist().get(0);
        assertThat(item.getPosition()).isEqualTo(0);
        assertThat(item.getVideoId()).isEqualTo("dQw4w9WgXcQ");
        assertThat(item.getTitle()).isEqualTo("Never Gonna Give You Up");
        assertThat(item.getDescription()).isEqualTo("The official music video.");
        assertThat(item.getPublishedAt()).isEqualTo("2009-10-25T06:57:33Z");
        assertThat(item.getThumbnail()).isEqualTo("https://img.youtube.com/vi/dQw4w9WgXcQ/mqdefault.jpg");
        assertThat(item.getVideoUrl()).isEqualTo("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
    }

    @Test
    void should_return_null_thumbnail_when_medium_thumbnail_missing() {
        YTPlaylistItemsResponse yt = buildFullResponse();
        // Remove medium thumbnail
        yt.getItems().get(0).getSnippet().getThumbnails().setMedium(null);

        PlaylistResponse result = mapper.toPlaylistResponse(yt);

        assertThat(result.getPlaylist().get(0).getThumbnail()).isNull();
    }

    @Test
    void should_return_empty_playlist_when_items_array_is_empty() {
        YTPlaylistItemsResponse yt = new YTPlaylistItemsResponse();
        var pageInfo = new YTPlaylistItemsResponse.PageInfo();
        pageInfo.setTotalResults(0);
        pageInfo.setResultsPerPage(25);
        yt.setPageInfo(pageInfo);
        yt.setItems(List.of());
        // nextPageToken left null

        PlaylistResponse result = mapper.toPlaylistResponse(yt);

        assertThat(result.getTotalResults()).isEqualTo(0);
        assertThat(result.getPlaylist()).isEmpty();
        assertThat(result.getNextPageToken()).isNull();
    }

    // --- helper ---

    private YTPlaylistItemsResponse buildFullResponse() {
        var thumbnail = new YTPlaylistItemsResponse.Item.Snippet.Thumbnails.Thumbnail();
        thumbnail.setUrl("https://img.youtube.com/vi/dQw4w9WgXcQ/mqdefault.jpg");

        var thumbnails = new YTPlaylistItemsResponse.Item.Snippet.Thumbnails();
        thumbnails.setMedium(thumbnail);

        var resourceId = new YTPlaylistItemsResponse.Item.Snippet.ResourceId();
        resourceId.setVideoId("dQw4w9WgXcQ");

        var snippet = new YTPlaylistItemsResponse.Item.Snippet();
        snippet.setTitle("Never Gonna Give You Up");
        snippet.setDescription("The official music video.");
        snippet.setPublishedAt("2009-10-25T06:57:33Z");
        snippet.setPosition(0);
        snippet.setThumbnails(thumbnails);
        snippet.setResourceId(resourceId);

        var item = new YTPlaylistItemsResponse.Item();
        item.setSnippet(snippet);

        var pageInfo = new YTPlaylistItemsResponse.PageInfo();
        pageInfo.setTotalResults(50);
        pageInfo.setResultsPerPage(25);

        var yt = new YTPlaylistItemsResponse();
        yt.setNextPageToken("EAAelgEKADiD");
        yt.setPageInfo(pageInfo);
        yt.setItems(List.of(item));
        return yt;
    }
}
```

### Java & Maven Environment

```bash
export JAVA_HOME=/Users/admin/java/jdk-21.0.5+11/Contents/Home  # macOS JDK path includes Contents/Home
export PATH=$JAVA_HOME/bin:/usr/bin:/bin
mvn generate-sources compile   # always generate-sources first — PlaylistResponse/PlaylistItem come from target/
mvn test                        # run full suite
```

### Files to Create in This Story

| File | Type | Notes |
|------|------|-------|
| `src/main/java/com/example/youtubeplaylistapi/dto/youtube/YTPlaylistItemsResponse.java` | NEW | Raw YouTube playlistItems response model with nested static classes |
| `src/main/java/com/example/youtubeplaylistapi/mapper/PlaylistMapper.java` | NEW | `@Component`, pure Java transform — no WebClient, no service logic |
| `src/test/java/com/example/youtubeplaylistapi/mapper/PlaylistMapperTest.java` | NEW | Plain JUnit 5 unit tests — 3 test methods |

All paths are relative to `dest-spring-youtube-playlist-api/`.

### What This Story Does NOT Include

- **No `PlaylistController.java`** — that is Story 2.3
- **No `PlaylistService.java`** — that is Story 2.2
- **No WebClient calls** — the mapper receives an already-deserialized `YTPlaylistItemsResponse`; HTTP calls are Story 2.2's concern
- **No `pageToken` validation** — that is Story 2.3's concern
- **No WireMock integration tests** — those are Epic 6; this story uses plain unit tests only
- **No hand-written `PlaylistResponse` or `PlaylistItem` DTOs** — they are generated

### Anti-Patterns to Avoid

```java
// ❌ Hand-writing a PlaylistResponse DTO — it's already generated
// src/main/java/.../dto/PlaylistResponse.java  ← NEVER CREATE THIS

// ❌ Using items.size() as totalResults — use pageInfo.totalResults from YouTube response
response.setTotalResults(ytResponse.getItems().size()); // WRONG

// ❌ Reformatting publishedAt — pass through as-is (ISO 8601 string from YouTube)
item.setPublishedAt(LocalDateTime.parse(snippet.getPublishedAt()).toString()); // WRONG

// ❌ Hardcoding videoUrl base — it's always the same, but make the derivation explicit
item.setVideoUrl("https://www.youtube.com/watch?v=" + videoId); // correct
item.setVideoUrl(videoId); // WRONG — missing base URL

// ❌ Missing @JsonIgnoreProperties on YTPlaylistItemsResponse — YouTube returns many extra fields
public class YTPlaylistItemsResponse { ... } // WRONG — no annotation causes deserialization failure

// ❌ Using constructor on generated DTOs
PlaylistItem item = new PlaylistItem(position, videoId, title, desc, publishedAt, videoUrl); // WRONG
// Use no-arg constructor + setters instead

// ❌ Static mapper methods with no @Component — PlaylistService (Story 2.2) will inject this
public class PlaylistMapper { public static PlaylistResponse toPlaylistResponse(...) { ... } } // WRONG
```

### Cross-Story Impact

- **Story 2.2 (PlaylistService)** will inject `PlaylistMapper` as a Spring bean and call `toPlaylistResponse()` after receiving the raw YouTube API response. The method signature `PlaylistResponse toPlaylistResponse(YTPlaylistItemsResponse)` must remain stable.
- **Story 2.3 (PlaylistController)** uses `PlaylistResponse` (the output of this mapper) as its return type — already generated.
- **Epic 6 tests** will use the same `YTPlaylistItemsResponse` class to build WireMock response bodies for integration tests.

### Project Structure Notes

New files align with the established package layout:

```
src/main/java/com/example/youtubeplaylistapi/
├── dto/
│   └── youtube/
│       └── YTPlaylistItemsResponse.java   ← NEW (this story)
└── mapper/
    └── PlaylistMapper.java                ← NEW (this story)

src/test/java/com/example/youtubeplaylistapi/
└── mapper/
    └── PlaylistMapperTest.java            ← NEW (this story)
```

No changes to existing files.

### References

- Epic 2 story definitions: [Source: docs/3-product-manager-artifacts/epics-and-stories/epic-2-playlist-retrieval-end-to-end.md#Story 2.1]
- Package structure + file names (`YTPlaylistItemsResponse`, `PlaylistMapper`): [Source: docs/4-architect-artifacts/architecture/05-project-structure-boundaries.md]
- `@Component` mapper pattern, setter usage, camelCase JSON, no hand-written OAS DTOs: [Source: docs/4-architect-artifacts/architecture/04-implementation-patterns-consistency-rules.md]
- Generated DTOs location and import path: [Source: docs/4-architect-artifacts/architecture/03-core-architectural-decisions.md#API & Communication Patterns]
- `@JsonIgnoreProperties` pattern + YouTube raw response structure: [Source: docs/4-architect-artifacts/architecture/05-project-structure-boundaries.md#Middle Boundary]
- JAVA_HOME macOS path confirmed: [Source: docs/5-scrum-impl-artifacts/4-2-implement-upstream-and-unexpected-error-handling.md#Java & Maven Environment]

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6 (bmad-create-story 2026-06-21)
claude-sonnet-4-6 (bmad-dev-story 2026-06-21)

### Debug Log References

### Completion Notes List

- Created `YTPlaylistItemsResponse` upstream model with all required nested static classes (`PageInfo`, `Item`, `Snippet`, `Thumbnails`, `Thumbnail`, `ResourceId`), all annotated with `@JsonIgnoreProperties(ignoreUnknown = true)` for safe deserialization of YouTube API responses.
- Created `PlaylistMapper` as a `@Component` Spring bean implementing `toPlaylistResponse(YTPlaylistItemsResponse)`. Uses setter-based mapping to generated DTOs (`PlaylistResponse`, `PlaylistItem`). Handles null thumbnails (AC 2), null/empty items list (AC 3), and derives `videoUrl` from `resourceId.videoId`. Maps `pageInfo.totalResults` (not `items.size()`) for AC 1.
- Created `PlaylistMapperTest` with 3 plain JUnit 5 tests (no Spring context): `should_map_all_fields_when_full_youtube_response`, `should_return_null_thumbnail_when_medium_thumbnail_missing`, `should_return_empty_playlist_when_items_array_is_empty`. All pass.
- Final `mvn test`: 12 tests total (9 existing GlobalExceptionHandlerTest + 3 new mapper tests), 0 failures, BUILD SUCCESS.
- `mvn generate-sources compile`: BUILD SUCCESS. No hand-written DTOs created in `src/main/java/.../dto/` root.

### File List

- `dest-spring-youtube-playlist-api/src/main/java/com/example/youtubeplaylistapi/dto/youtube/YTPlaylistItemsResponse.java` (NEW)
- `dest-spring-youtube-playlist-api/src/main/java/com/example/youtubeplaylistapi/mapper/PlaylistMapper.java` (NEW)
- `dest-spring-youtube-playlist-api/src/test/java/com/example/youtubeplaylistapi/mapper/PlaylistMapperTest.java` (NEW)

## Change Log

- 2026-06-21: Implemented Story 2.1 — created `YTPlaylistItemsResponse` upstream model, `PlaylistMapper` Spring component, and `PlaylistMapperTest` unit tests (3 tests). All 12 tests pass (9 regression + 3 new). BUILD SUCCESS.
- 2026-06-21: Code review patches applied — null snippet guard in `toPlaylistItem()`, null `resourceId` guard with extracted `videoId`, null-safe `videoUrl` construction. All 19 tests pass. Story done.

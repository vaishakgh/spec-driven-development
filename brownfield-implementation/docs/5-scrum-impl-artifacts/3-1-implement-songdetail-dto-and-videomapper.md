---
baseline_commit: b1318e8096366d5200c2744cb42f5ae8816d8e20
---

# Story 3.1: Implement SongDetail DTO and VideoMapper

Status: in-progress

## Story

As an **internal developer**,
I want a `YTVideoDetailsResponse` upstream model and a `VideoMapper` that transforms the raw YouTube API response into the contract-defined shape,
so that the video endpoint returns correctly structured data with all required fields including the `channelTitle` → `channelName` rename.

## Acceptance Criteria

1. **Given** a raw YouTube `/videos` API response with one item
   **When** `VideoMapper.toSongDetail()` is called
   **Then** the returned `SongDetail` contains: `videoId` (from `item.id`), `title`, `description`, `channelName` (mapped from YouTube's `channelTitle` field), `publishedAt` (ISO 8601), `duration` (ISO 8601 e.g. `PT3M33S`), `viewCount` (string), `likeCount` (string), `thumbnail` (high-quality URL or null), `videoUrl` formatted as `https://www.youtube.com/watch?v={videoId}`

2. **Given** a raw YouTube response where the video has no high-quality thumbnail
   **When** `VideoMapper.toSongDetail()` is called
   **Then** the `thumbnail` field is `null`

3. **Given** a raw YouTube response with an empty `items[]` array
   **When** `VideoMapper.toSongDetail()` is called
   **Then** `null` is returned (signal to `VideoService` to throw `VideoNotFoundException`)

## Tasks / Subtasks

- [ ] Task 1 — Create `YTVideoDetailsResponse` upstream model (AC: 1–3)
  - [ ] Create `src/main/java/com/example/youtubeplaylistapi/dto/youtube/YTVideoDetailsResponse.java`
  - [ ] Annotate class and all static inner classes with `@JsonIgnoreProperties(ignoreUnknown = true)`
  - [ ] Top-level field: `List<Item> items`
  - [ ] Inner class `Item`: `String id`, `Snippet snippet`, `ContentDetails contentDetails`, `Statistics statistics`
  - [ ] Inner class `Snippet`: `String publishedAt`, `String title`, `String description`, `String channelTitle`, `Thumbnails thumbnails`
  - [ ] Inner class `Thumbnails`: only `Thumbnail high` (high-quality; ignore `default`, `medium`, `standard`, `maxres` via `@JsonIgnoreProperties`)
  - [ ] Inner class `Thumbnail`: `String url`
  - [ ] Inner class `ContentDetails`: `String duration`
  - [ ] Inner class `Statistics`: `String viewCount`, `String likeCount`
  - [ ] All fields use standard Java camelCase — matches YouTube API's camelCase JSON exactly; no `@JsonProperty` annotations needed
  - [ ] `viewCount` and `likeCount` are `String` — YouTube API returns them as strings

- [ ] Task 2 — Create `VideoMapper` (AC: 1–3)
  - [ ] Create `src/main/java/com/example/youtubeplaylistapi/mapper/VideoMapper.java`
  - [ ] Annotate with `@Component`
  - [ ] Public method: `SongDetail toSongDetail(YTVideoDetailsResponse ytResponse)`
  - [ ] Return `null` if `ytResponse == null || ytResponse.getItems() == null || ytResponse.getItems().isEmpty()`
  - [ ] Get `item = ytResponse.getItems().get(0)`
  - [ ] Map `item.getId()` → `videoId` (item-level id, NOT snippet.resourceId)
  - [ ] Map `snippet.getTitle()` → `title`
  - [ ] Map `snippet.getDescription()` → `description`
  - [ ] Map `snippet.getChannelTitle()` → `channelName` (renamed field — critical mapping)
  - [ ] Map `snippet.getPublishedAt()` → `publishedAt` (pass-through, no reformatting)
  - [ ] Map `item.getContentDetails().getDuration()` → `duration`
  - [ ] Map `item.getStatistics().getViewCount()` → `viewCount`
  - [ ] Map `item.getStatistics().getLikeCount()` → `likeCount`
  - [ ] `thumbnail`: set to `null` when `snippet.thumbnails` is null OR `snippet.thumbnails.high` is null; otherwise set to `high.url`
  - [ ] `videoUrl`: always `"https://www.youtube.com/watch?v=" + item.getId()`
  - [ ] Uses setters on `SongDetail` — NOT constructors (generated DTO has setters; constructor may change on regen)
  - [ ] Imports `SongDetail` from `com.example.youtubeplaylistapi.dto` (generated package)

- [ ] Task 3 — Write unit tests (AC: 1–3)
  - [ ] Create `src/test/java/com/example/youtubeplaylistapi/mapper/VideoMapperTest.java`
  - [ ] Plain JUnit 5, no `@SpringBootTest` — instantiate `VideoMapper` directly: `new VideoMapper()`
  - [ ] `should_map_all_fields_when_full_youtube_response()` — verifies AC 1: all `SongDetail` fields, especially `channelName` (not `channelTitle`)
  - [ ] `should_return_null_thumbnail_when_high_thumbnail_missing()` — verifies AC 2
  - [ ] `should_return_null_when_items_array_is_empty()` — verifies AC 3: mapper returns `null`
  - [ ] Assert `videoId` comes from `item.id` (not from snippet)
  - [ ] Assert `channelName` equals the `channelTitle` value from the raw YouTube response
  - [ ] Assert `publishedAt` is passed through as-is (no reformatting)
  - [ ] Run `mvn test` — all 3 new mapper tests pass

- [ ] Task 4 — Compile and verify (AC: all)
  - [ ] Run `mvn generate-sources compile` — must succeed (generated `SongDetail` required in classpath)
  - [ ] Run `mvn test` — all tests pass (existing tests + 3 new mapper tests)
  - [ ] Confirm no hand-written `SongDetail.java` under `src/main/java/.../dto/` (only in `target/`)

## Dev Notes

### CRITICAL: SongDetail Is GENERATED — Do NOT Hand-Write

`SongDetail` is generated by `openapi-generator-maven-plugin` from the OAS 3.0 spec, just like `PlaylistResponse` and `PlaylistItem` in Story 2.1.

**Correct import:**
```java
import com.example.youtubeplaylistapi.dto.SongDetail;
```

**Generated file location (never edit):**
```
target/generated-sources/openapi/com/example/youtubeplaylistapi/dto/SongDetail.java
```

Run `mvn generate-sources` first if the generated files are not present.

### Generated SongDetail API (confirmed from OAS spec)

**`SongDetail`** fields (all use setters):
- `setVideoId(String)` / `getVideoId()`
- `setTitle(String)` / `getTitle()`
- `setDescription(String)` / `getDescription()`
- `setChannelName(String)` / `getChannelName()` — mapped from YouTube's `channelTitle`
- `setPublishedAt(String)` / `getPublishedAt()` — ISO 8601 string passed through as-is
- `setDuration(String)` / `getDuration()` — ISO 8601 duration e.g. `PT3M33S`
- `setViewCount(String)` / `getViewCount()` — string, YouTube returns number as string
- `setLikeCount(String)` / `getLikeCount()` — string, YouTube returns number as string
- `setThumbnail(String)` / `getThumbnail()` — nullable, `null` by default
- `setVideoUrl(String)` / `getVideoUrl()`

**Use setters — not constructors.**

### YTVideoDetailsResponse Structure

Create this class at `src/main/java/com/example/youtubeplaylistapi/dto/youtube/YTVideoDetailsResponse.java`.

The YouTube Data API v3 `/videos` response shape (only fields we need):

```json
{
  "items": [
    {
      "id": "dQw4w9WgXcQ",
      "snippet": {
        "publishedAt": "2009-10-25T06:57:33Z",
        "title": "Never Gonna Give You Up",
        "description": "The official music video.",
        "channelTitle": "RickAstleyVEVO",
        "thumbnails": {
          "high": {
            "url": "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg"
          }
        }
      },
      "contentDetails": {
        "duration": "PT3M33S"
      },
      "statistics": {
        "viewCount": "1400000000",
        "likeCount": "15000000"
      }
    }
  ]
}
```

**Important differences from YTPlaylistItemsResponse (Story 2.1):**
- `item.id` = videoId (top-level, NOT inside snippet.resourceId)
- Thumbnail is `high` quality (NOT `medium`)
- Has `contentDetails.duration` and `statistics.viewCount`/`likeCount`
- No `pageInfo` (no pagination)
- No `nextPageToken`
- `snippet.channelTitle` must map to `channelName` (field rename)

**Java structure:**

```java
@JsonIgnoreProperties(ignoreUnknown = true)
public class YTVideoDetailsResponse {
    private List<Item> items;
    // getter/setter

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        private String id;
        private Snippet snippet;
        private ContentDetails contentDetails;
        private Statistics statistics;
        // getters/setters

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Snippet {
            private String publishedAt;
            private String title;
            private String description;
            private String channelTitle;    // maps to channelName in SongDetail
            private Thumbnails thumbnails;  // null when video has no thumbnails
            // getters/setters

            @JsonIgnoreProperties(ignoreUnknown = true)
            public static class Thumbnails {
                private Thumbnail high;     // high-quality; null when unavailable
                // getter/setter

                @JsonIgnoreProperties(ignoreUnknown = true)
                public static class Thumbnail {
                    private String url;
                    // getter/setter
                }
            }
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class ContentDetails {
            private String duration;        // ISO 8601 duration e.g. "PT3M33S"
            // getter/setter
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Statistics {
            private String viewCount;       // YouTube returns number as string
            private String likeCount;       // YouTube returns number as string
            // getters/setters
        }
    }
}
```

### VideoMapper Implementation

```java
package com.example.youtubeplaylistapi.mapper;

import com.example.youtubeplaylistapi.dto.SongDetail;
import com.example.youtubeplaylistapi.dto.youtube.YTVideoDetailsResponse;
import org.springframework.stereotype.Component;

@Component
public class VideoMapper {

    public SongDetail toSongDetail(YTVideoDetailsResponse ytResponse) {
        // Return null → VideoService will throw VideoNotFoundException → HTTP 404
        if (ytResponse == null || ytResponse.getItems() == null || ytResponse.getItems().isEmpty()) {
            return null;
        }

        var item = ytResponse.getItems().get(0);
        var snippet = item.getSnippet();

        SongDetail detail = new SongDetail();
        detail.setVideoId(item.getId());                        // item-level id
        detail.setTitle(snippet.getTitle());
        detail.setDescription(snippet.getDescription());
        detail.setChannelName(snippet.getChannelTitle());       // channelTitle → channelName
        detail.setPublishedAt(snippet.getPublishedAt());        // pass-through, no reformatting
        detail.setDuration(item.getContentDetails().getDuration());
        detail.setViewCount(item.getStatistics().getViewCount());
        detail.setLikeCount(item.getStatistics().getLikeCount());

        // high-quality thumbnail (nullable)
        String thumbnail = null;
        if (snippet.getThumbnails() != null && snippet.getThumbnails().getHigh() != null) {
            thumbnail = snippet.getThumbnails().getHigh().getUrl();
        }
        detail.setThumbnail(thumbnail);

        detail.setVideoUrl("https://www.youtube.com/watch?v=" + item.getId());

        return detail;
    }
}
```

### VideoMapperTest Implementation

```java
package com.example.youtubeplaylistapi.mapper;

import com.example.youtubeplaylistapi.dto.SongDetail;
import com.example.youtubeplaylistapi.dto.youtube.YTVideoDetailsResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VideoMapperTest {

    private final VideoMapper mapper = new VideoMapper();

    @Test
    void should_map_all_fields_when_full_youtube_response() {
        YTVideoDetailsResponse yt = buildFullResponse();

        SongDetail result = mapper.toSongDetail(yt);

        assertThat(result).isNotNull();
        assertThat(result.getVideoId()).isEqualTo("dQw4w9WgXcQ");
        assertThat(result.getTitle()).isEqualTo("Never Gonna Give You Up");
        assertThat(result.getDescription()).isEqualTo("The official music video.");
        assertThat(result.getChannelName()).isEqualTo("RickAstleyVEVO");  // channelTitle mapped
        assertThat(result.getPublishedAt()).isEqualTo("2009-10-25T06:57:33Z");
        assertThat(result.getDuration()).isEqualTo("PT3M33S");
        assertThat(result.getViewCount()).isEqualTo("1400000000");
        assertThat(result.getLikeCount()).isEqualTo("15000000");
        assertThat(result.getThumbnail()).isEqualTo("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg");
        assertThat(result.getVideoUrl()).isEqualTo("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
    }

    @Test
    void should_return_null_thumbnail_when_high_thumbnail_missing() {
        YTVideoDetailsResponse yt = buildFullResponse();
        yt.getItems().get(0).getSnippet().getThumbnails().setHigh(null);

        SongDetail result = mapper.toSongDetail(yt);

        assertThat(result.getThumbnail()).isNull();
    }

    @Test
    void should_return_null_when_items_array_is_empty() {
        YTVideoDetailsResponse yt = new YTVideoDetailsResponse();
        yt.setItems(List.of());

        SongDetail result = mapper.toSongDetail(yt);

        assertThat(result).isNull();
    }

    // --- helper ---

    private YTVideoDetailsResponse buildFullResponse() {
        var thumbnail = new YTVideoDetailsResponse.Item.Snippet.Thumbnails.Thumbnail();
        thumbnail.setUrl("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg");

        var thumbnails = new YTVideoDetailsResponse.Item.Snippet.Thumbnails();
        thumbnails.setHigh(thumbnail);

        var snippet = new YTVideoDetailsResponse.Item.Snippet();
        snippet.setPublishedAt("2009-10-25T06:57:33Z");
        snippet.setTitle("Never Gonna Give You Up");
        snippet.setDescription("The official music video.");
        snippet.setChannelTitle("RickAstleyVEVO");
        snippet.setThumbnails(thumbnails);

        var contentDetails = new YTVideoDetailsResponse.Item.ContentDetails();
        contentDetails.setDuration("PT3M33S");

        var statistics = new YTVideoDetailsResponse.Item.Statistics();
        statistics.setViewCount("1400000000");
        statistics.setLikeCount("15000000");

        var item = new YTVideoDetailsResponse.Item();
        item.setId("dQw4w9WgXcQ");
        item.setSnippet(snippet);
        item.setContentDetails(contentDetails);
        item.setStatistics(statistics);

        var yt = new YTVideoDetailsResponse();
        yt.setItems(List.of(item));
        return yt;
    }
}
```

### Critical Differences from Story 2.1 (PlaylistMapper)

| | Story 2.1 (PlaylistMapper) | Story 3.1 (VideoMapper) |
|---|---|---|
| Empty items result | Empty `PlaylistResponse` | `null` (signals 404) |
| Video ID source | `snippet.resourceId.videoId` | `item.id` (top-level) |
| Thumbnail quality | `medium` | `high` |
| Field rename | None | `channelTitle` → `channelName` |
| Stats fields | None | `viewCount`, `likeCount`, `duration` |
| Pagination | `nextPageToken`, `pageInfo` | None |

### Java & Maven Environment

```bash
export JAVA_HOME=/Users/admin/java/jdk-21.0.5+11/Contents/Home
export PATH=$JAVA_HOME/bin:/usr/bin:/bin
mvn generate-sources compile   # always generate-sources first — SongDetail comes from target/
mvn test                        # run full suite
```

### Files to Create in This Story

| File | Type | Notes |
|------|------|-------|
| `src/main/java/com/example/youtubeplaylistapi/dto/youtube/YTVideoDetailsResponse.java` | NEW | Raw YouTube /videos response model with nested static classes |
| `src/main/java/com/example/youtubeplaylistapi/mapper/VideoMapper.java` | NEW | `@Component`, pure Java transform — returns `null` for empty items |
| `src/test/java/com/example/youtubeplaylistapi/mapper/VideoMapperTest.java` | NEW | Plain JUnit 5 unit tests — 3 test methods |

All paths are relative to `dest-spring-youtube-playlist-api/`.

### What This Story Does NOT Include

- **No `VideoController.java`** — that is Story 3.3
- **No `VideoService.java`** — that is Story 3.2
- **No WebClient calls** — the mapper receives an already-deserialized `YTVideoDetailsResponse`
- **No 404 logic in mapper** — mapper returns `null`; VideoService (Story 3.2) throws `VideoNotFoundException`
- **No hand-written `SongDetail.java`** — it is generated from OAS spec
- **No WireMock integration tests** — those are Epic 6

### Anti-Patterns to Avoid

```java
// ❌ Hand-writing SongDetail — it's already generated
// src/main/java/.../dto/SongDetail.java  ← NEVER CREATE THIS

// ❌ Getting videoId from snippet.resourceId (that's for playlist items, not videos)
detail.setVideoId(snippet.getResourceId().getVideoId());  // WRONG for /videos endpoint
detail.setVideoId(item.getId());  // CORRECT

// ❌ Using "medium" thumbnail instead of "high"
snippet.getThumbnails().getMedium()  // WRONG — videos use high quality
snippet.getThumbnails().getHigh()    // CORRECT

// ❌ Mapping channelTitle to wrong field name
detail.setChannelTitle(snippet.getChannelTitle());  // WRONG — SongDetail has no channelTitle
detail.setChannelName(snippet.getChannelTitle());   // CORRECT — rename on mapping

// ❌ Throwing VideoNotFoundException in mapper — mapper returns null, service throws
if (ytResponse.getItems().isEmpty()) {
    throw new VideoNotFoundException();  // WRONG — mapper must return null
}

// ❌ Using constructor on generated DTO
SongDetail detail = new SongDetail(videoId, title, ...);  // WRONG — use no-arg + setters

// ❌ Missing @JsonIgnoreProperties — YouTube /videos returns many extra fields
public class YTVideoDetailsResponse { ... }  // WRONG without annotation
```

### Cross-Story Impact

- **Story 3.2 (VideoService)** will inject `VideoMapper`, call `toSongDetail()`, and check the null return → throw `VideoNotFoundException`. Method signature `SongDetail toSongDetail(YTVideoDetailsResponse)` must remain stable.
- **Story 3.3 (VideoController)** uses `SongDetail` as its return type — already generated.
- **Epic 6 tests** will use `YTVideoDetailsResponse` to build WireMock response bodies.

### References

- Epic 3 story definitions: [Source: docs/3-product-manager-artifacts/epics-and-stories/epic-3-video-detail-retrieval-end-to-end.md#Story 3.1]
- Package structure + file names: [Source: docs/4-architect-artifacts/architecture/05-project-structure-boundaries.md]
- `@Component` mapper pattern, setter usage, no hand-written OAS DTOs: [Source: docs/4-architect-artifacts/architecture/04-implementation-patterns-consistency-rules.md]
- Generated DTOs location and import path: [Source: docs/4-architect-artifacts/architecture/03-core-architectural-decisions.md#API & Communication Patterns]
- `@JsonIgnoreProperties` pattern: [Source: docs/4-architect-artifacts/architecture/05-project-structure-boundaries.md#Middle Boundary]
- Story 2.1 as reference pattern for mapper/upstream DTO structure: [Source: docs/5-scrum-impl-artifacts/2-1-implement-playlist-response-dtos-and-mapper.md]
- JAVA_HOME macOS path: [Source: docs/5-scrum-impl-artifacts/4-2-implement-upstream-and-unexpected-error-handling.md#Java & Maven Environment]

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6 (bmad-create-story 2026-06-21)

### Debug Log References

### Completion Notes List

### File List

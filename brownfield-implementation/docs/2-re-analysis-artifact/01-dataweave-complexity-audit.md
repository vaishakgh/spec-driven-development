# DataWeave Complexity Audit — youtube-playlist-api

**Date:** 2026-06-19
**Scope:** All DataWeave 2.0 expressions found in `source-mule-youtube-playlist-api/`
**Method:** Full source read of all Mule XML files

---

## Audit Summary

| Transform ID | Location | Complexity | Lines (approx) | Effort |
|---|---|---|---|---|
| DW-1 | `playlist.xml` — `ee:transform` "Transform Playlist Response" | **Simple** | ~20 | 0.5–1 hr |
| DW-2 | `playlist-video.xml` — `ee:transform` "Transform Song Detail" | **Simple** | ~15 | 0.5–1 hr |

**Plus 8 inline error response transforms** (each 3–4 lines, trivial — constant JSON output):

| # | Location | Output | Complexity |
|---|---|---|---|
| E1 | youtube-playlist.xml — BAD_REQUEST handler | `{ error, message, code }` | Trivial |
| E2 | youtube-playlist.xml — NOT_FOUND handler | `{ error, message, code }` | Trivial |
| E3 | youtube-playlist.xml — METHOD_NOT_ALLOWED handler | `{ error, message, code }` | Trivial |
| E4 | youtube-playlist.xml — UNSUPPORTED_MEDIA_TYPE handler | `{ error, message, code }` | Trivial |
| E5 | youtube-playlist.xml — NOT_ACCEPTABLE handler | `{ error, message, code }` | Trivial |
| E6 | playlist.xml — UNAUTHORIZED handler | `{ error, message, code }` | Trivial |
| E7 | playlist.xml — CONNECTIVITY handler | `{ error, message, code }` | Trivial |
| E8 | playlist.xml — ANY (500) handler | `{ error, message, code }` | Trivial |
| E9 | playlist-video.xml — UNAUTHORIZED handler | `{ error, message, code }` | Trivial |
| E10 | playlist-video.xml — CONNECTIVITY handler | `{ error, message, code }` | Trivial |
| E11 | playlist-video.xml — ANY (500) handler | `{ error, message, code }` | Trivial |

**Total unique DataWeave scripts:** 13 (2 business transforms + 11 error response constants)

---

## Detailed Analysis: DW-1 (Playlist Transform)

**Input shape (YouTube `playlistItems` response):**
```json
{
  "pageInfo": { "totalResults": N, "resultsPerPage": N },
  "nextPageToken": "...",
  "items": [
    {
      "snippet": {
        "position": N, "title": "...", "description": "...",
        "publishedAt": "...", "thumbnails": { "medium": { "url": "..." } }
      },
      "contentDetails": { "videoId": "..." }
    }
  ]
}
```

**Output shape (API response):**
```json
{
  "totalResults": N,
  "resultsPerPage": N,
  "nextPageToken": "...",
  "playlist": [
    {
      "position": N, "videoId": "...", "title": "...", "description": "...",
      "publishedAt": "...", "thumbnail": "...", "videoUrl": "https://www.youtube.com/watch?v=..."
    }
  ]
}
```

**DataWeave features used:**
- `map` operator (array iteration)
- `default` operator (null coalescing)
- String concatenation (`++`)
- Field access via dot notation
- `var` declaration (1 variable)

**DataWeave features NOT used:** conditionals, recursion, custom functions, namespaces, type coercions, XML, CSV, multi-format, regex, groupBy, flatten, filter, reduce

**Complexity classification: SIMPLE**

**Migration approach:** Jackson `@JsonProperty` DTOs — `PlaylistItemDto` and `PlaylistResponseDto` with a mapping method or `@Service` bean that maps from `YouTubePlaylistResponse` (deserialized YouTube API response) to `PlaylistResponseDto`. No MapStruct needed for this scale; a plain Java method is sufficient. JOLT is optional.

---

## Detailed Analysis: DW-2 (Song Detail Transform)

**Input shape (YouTube `videos` response):**
```json
{
  "items": [
    {
      "id": "...",
      "snippet": {
        "title": "...", "description": "...", "channelTitle": "...",
        "publishedAt": "...", "thumbnails": { "high": { "url": "..." } }
      },
      "contentDetails": { "duration": "..." },
      "statistics": { "viewCount": "...", "likeCount": "..." }
    }
  ]
}
```

**Output shape (API response):**
```json
{
  "videoId": "...", "title": "...", "description": "...", "channelName": "...",
  "publishedAt": "...", "duration": "...", "viewCount": "...", "likeCount": "...",
  "thumbnail": "...", "videoUrl": "https://www.youtube.com/watch?v=..."
}
```

**DataWeave features used:**
- Array index access (`payload.items[0]`)
- `default` operator (null coalescing)
- String concatenation (`++`)
- Field access via dot notation
- `var` declaration (1 variable)
- Field name renaming (`channelTitle` → `channelName`)

**DataWeave features NOT used:** same as DW-1

**Complexity classification: SIMPLE**

**Migration approach:** Same pattern — `SongDetailDto` Jackson DTO + a mapping method. Rename `channelTitle` → `channelName` is a trivial field rename in the mapping method.

---

## Migration Strategy for DataWeave

### Recommended: Plain Java mapping methods (no framework)

Given the simplicity of both transforms, the lowest-risk migration approach is:

```java
// PlaylistMapper.java
public PlaylistResponseDto mapPlaylistResponse(YouTubePlaylistItemsResponse raw) {
    List<PlaylistItemDto> items = Optional.ofNullable(raw.getItems())
        .orElse(List.of())
        .stream()
        .map(item -> PlaylistItemDto.builder()
            .position(item.getSnippet().getPosition())
            .videoId(item.getContentDetails().getVideoId())
            .title(item.getSnippet().getTitle())
            .description(item.getSnippet().getDescription())
            .publishedAt(item.getSnippet().getPublishedAt())
            .thumbnail(Optional.ofNullable(item.getSnippet().getThumbnails())
                .map(t -> t.getMedium()).map(m -> m.getUrl()).orElse(null))
            .videoUrl("https://www.youtube.com/watch?v=" + item.getContentDetails().getVideoId())
            .build())
        .collect(Collectors.toList());

    return PlaylistResponseDto.builder()
        .totalResults(Optional.ofNullable(raw.getPageInfo().getTotalResults()).orElse(0))
        .resultsPerPage(Optional.ofNullable(raw.getPageInfo().getResultsPerPage()).orElse(0))
        .nextPageToken(raw.getNextPageToken())
        .playlist(items)
        .build();
}
```

### Alternatives (not needed for this project's scale):
- **MapStruct**: Overkill for 2 simple transforms; adds annotation processing complexity
- **JOLT**: Appropriate for teams that prefer declarative JSON-to-JSON specs
- **AI-assisted translation**: Useful when DataWeave is complex; not needed here

---

## Cross-Reference Against Generic Analysis

The generic analysis (`05-technology-stack-analysis.md`) states:

> "DataWeave is MuleSoft's proprietary transformation DSL and represents the highest-effort migration component (30–40% of total project effort)."

**Assessment for this project:** This is **NOT accurate for `youtube-playlist-api`**. The correct picture:

| Generic Analysis | This Project's Reality |
|---|---|
| DataWeave = 30–40% of total effort | DataWeave ≈ 5–10% of total effort |
| Complex scripts need 1–3 days each | Both transforms: 0.5–1 hr each |
| AI-assisted translation recommended | Not needed — plain Java mapping is sufficient |
| MapStruct recommended for simple mappings | Plain Java method is sufficient for 2 transforms |

**Root cause:** The generic analysis was scoped for large MuleSoft projects with 50–500+ DataWeave scripts. This project has 2 business-logic transforms, both of which are trivially simple.

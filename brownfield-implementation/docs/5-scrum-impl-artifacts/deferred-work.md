# Deferred Work

## Deferred from: code review of Epic 2 stories (2026-06-21)

- **[Story 2.1] Null Integer in pageInfo** — YouTube rarely returns null `totalResults`/`resultsPerPage` but it is possible; `setTotalResults(null)` would serialize as null in the API response. Consider adding null-safe defaults if this becomes a runtime issue. [`PlaylistMapper.java:221`]

- **[Story 2.2] Virtual thread blocking concern** — `.block()` on virtual threads is speculative; no virtual thread executor is configured in this project. Low risk; revisit if/when virtual threads are adopted. [`PlaylistService.java:119`]

- **[Story 2.2] Non-401 YouTube errors (400, 403, 429) fall through to 500** — By design per Epic 4 architecture. Epic 4 established the catch-all 500 for unhandled upstream statuses. Targeted handling of 403/429 is a future enhancement if needed. [`PlaylistService.java:116`]

- **[Story 2.2] API key visible in URI debug logs** — Standard behavior for YouTube API integrations; the key appears in WebClient request URIs which can surface in logs at DEBUG level. A dedicated security hardening story should address key masking if log aggregation to external systems is added. [`PlaylistService.java`]

- **[Story 2.3] playlistId format not validated** — No format or length check on the `{playlistId}` path variable. YouTube playlists start with `PL`. Out of Epic 2 scope; consider adding validation in Epic 7 or a dedicated hardening story. [`PlaylistController.java:23`]

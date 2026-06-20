# 5. Non-Goals (Explicit)

- **No consumer-facing authentication.** The API is internal and unauthenticated. No API key, OAuth2, or JWT is added to the consumer-facing interface as part of this migration.
- **No Apache Camel or Spring Integration.** The migrated service uses plain Spring Boot and WebClient. No EIP framework is introduced.
- **No caching.** The current Mule service has no caching; caching is not added in this migration.
- **No rate limiting.** Not present in current service; not added in this migration.
- **No YouTube write operations.** The service is read-only (GET requests only).
- **No changes to the Playlist response shape** (other than adding pagination support via `pageToken`). The `PlaylistResponse` and `PlaylistItem` field shapes are preserved exactly.
- **No changes to the SongDetail response shape.** The `SongDetail` field set is preserved exactly; `channelName` (mapped from `channelTitle`) is already the existing field name.
- **No new endpoints.** The migration is scope-exact: two GET endpoints.

---

# 6. MVP Scope

## 6.1 In Scope

- Migrate `GET /api/youtube/playlists/{playlistId}` with full parity to the current Mule implementation.
- Add `pageToken` query parameter support to the Playlist endpoint.
- Migrate `GET /api/youtube/song/{videoId}` with corrected HTTP 404 for non-existent video IDs.
- Migrate all error handling (HTTP 400, 401, 404, 405, 406, 415, 500, 503) with identical ErrorResponse shape.
- Convert RAML 1.0 spec to OpenAPI 3.0; expose via interactive API explorer in non-prod.
- Multi-environment configuration (local, dev, prod, test profiles).
- YouTube API key via `YOUTUBE_API_KEY` environment variable.
- Kubernetes health endpoints (liveness + readiness).
- Structured application logging.
- Port all 7 MUnit test scenarios to JUnit 5 equivalent tests.
- Shadow mode validation before cutover.
- CloudHub decommission after stability confirmation.

## 6.2 Out of Scope for MVP

- Consumer-facing authentication. *(Deferred — assess after migration if security posture requires it.)*
- Response caching. *(Deferred — assess if latency or YouTube API quota becomes a concern post-migration.)*
- Exposing a `maxResults` caller-override parameter. *(Deferred — not present in current service; low demand signal.)*
- Kubernetes autoscaling (HPA/KEDA). *(Deferred — current MICRO worker is 1 instance; reassess after traffic baseline is established.)*
- Metrics / dashboard (Prometheus/Grafana). *(Deferred — Spring Actuator provides baseline; full observability stack is a platform concern.)*

---

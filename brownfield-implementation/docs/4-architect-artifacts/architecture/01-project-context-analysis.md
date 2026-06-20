# Project Context Analysis

## Requirements Overview

**Functional Requirements (22 FRs across 6 groups):**

- **Endpoint behaviour (FR-1–FR-7):** Two GET endpoints proxying the YouTube Data API v3. `GET /api/youtube/playlists/{playlistId}` returns paginated PlaylistResponse; `GET /api/youtube/song/{videoId}` returns SongDetail or HTTP 404 (breaking change from Mule 200-with-nulls).
- **Error handling (FR-8–FR-15):** All non-2xx responses return `{ error, message, code }` ErrorResponse. Eight specific error scenarios: 400 (bad request), 401 (invalid key), 404 (unknown route), 405 (wrong method), 406 (wrong Accept), 415 (wrong Content-Type), 503 (connectivity failure), 500 (unhandled).
- **API contract (FR-16–FR-17):** OAS 3.0 spec is the source of truth. Base path `/api/*` preserved for consumer URL compatibility. New additions vs RAML: `pageToken` query param, HTTP 404 on video endpoint, nullable thumbnail fields.
- **API explorer (FR-18):** Swagger UI at `/swagger-ui.html` — local and dev profiles only.
- **Configuration & secrets (FR-19–FR-20):** Four environment profiles (local/dev/prod/test). `YOUTUBE_API_KEY` from environment variable only — never in source-controlled files.
- **Observability (FR-21–FR-22):** Kubernetes liveness + readiness health probes. Structured JSON logging at INFO (requests, success metrics) and ERROR (exceptions with context).

**Non-Functional Requirements (5 NFRs):**

- NFR-1: Latency must not regress more than 20% vs Mule/CloudHub baseline, measured in shadow mode.
- NFR-2: All 7 MUnit scenarios must have JUnit 5 + WireMock equivalents before cutover (plus 2 additional = 9 total).
- NFR-3: Shadow mode field-by-field parity gate is mandatory before any traffic switch.
- NFR-4: Production container must operate within 512 MB heap — constrains dependency choices.
- NFR-5: No credentials in any source-controlled file.

**Scale & Complexity:**

- Primary domain: Backend REST API — stateless HTTP proxy
- Complexity level: Low
- Estimated architectural components: 2 controllers, 2 services, 2 mappers, 1 global exception handler, 1 WebClient configuration bean, 1 Spring Boot application entry point (~8–10 classes total)

## Technical Constraints & Dependencies

- **Source system:** Mule 4.6.0, CloudHub MICRO (0.1 vCore, ~500MB RAM), 1 instance, us-east-1
- **Target system:** Spring Boot 4.1.0, Java 21, Docker + Kubernetes
- **Upstream dependency:** YouTube Data API v3 (googleapis.com) — HTTPS only, key-authenticated
- **API key:** Single secret (`YOUTUBE_API_KEY`) — must flow from Kubernetes Secret → env var
- **No Camel:** Explicitly excluded — zero EIP patterns in source; plain Spring suffices
- **No MapStruct:** Two Simple-complexity transforms are cleaner as plain Java mapper methods
- **No database:** Stateless — no persistence layer of any kind
- **No messaging:** No Kafka, no MQ, no async patterns
- **Memory ceiling:** 512 MB heap eliminates heavy framework dependencies
- **Consumer URL compatibility:** Base path `/api/*` must be preserved exactly

## Cross-Cutting Concerns Identified

- **Error handling:** Every request/response path must funnel through a single `GlobalExceptionHandler` returning consistent `ErrorResponse` shape. This is the primary consistency risk.
- **Structured logging:** Every endpoint logs at INFO on request entry and success; at ERROR on failure. Log output must be valid JSON in all profiles (`logstash-logback-encoder`).
- **Configuration profiles:** Four profiles (local/dev/prod/test) affect `maxResults` value, API key source, Swagger UI availability, and logging format.
- **Secret injection:** `YOUTUBE_API_KEY` must reach the `WebClient` bean without appearing in any source-controlled file. K8s Secret → env var is the canonical path.
- **Breaking change isolation:** The FR-7 404 behaviour change is the only consumer-visible delta. All other response shapes are preserved exactly. Consumer notification gate must be tracked.
- **Shadow mode gate:** Mule and Spring Boot run in parallel during validation. Response parity (field-by-field) must be confirmed before the traffic switch. Latency delta measured simultaneously.

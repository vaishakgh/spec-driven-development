# Migration Strategy — Refined for youtube-playlist-api

**Date:** 2026-06-19
**Based on:** Actual Mule 4.6.0 source code analysis

---

## Executive Summary

The `youtube-playlist-api` is a minimal 2-endpoint HTTP proxy. It does not use EIP patterns, messaging, databases, or batch processing. The Strangler Fig multi-phase migration described in the generic analysis is appropriate for large, multi-domain MuleSoft estates — it is **architecturally over-prescribed** for this project. The appropriate strategy is a **direct rewrite with parallel validation**.

---

## Recommended Migration Strategy: Direct Rewrite + Shadow Mode Validation

### Rationale

| Factor | Impact on Strategy |
|---|---|
| 2 endpoints only | No phased domain-by-domain migration needed |
| No database | No data migration, no schema versioning |
| No messaging | No Kafka/MQ migration phase |
| No EIP patterns | No scatter-gather, aggregators, or complex routing to rethink |
| Simple DataWeave (2 transforms) | Can be rewritten in < 1 day total |
| RAML spec exists | Enables automated OpenAPI 3.0 generation as starting contract |
| 7 MUnit tests exist | Test suite can be ported to JUnit/WireMock to gate parity |

### Migration Steps

```
Step 1: Contract Migration (Day 1)
  ├── Convert RAML 1.0 → OpenAPI 3.0 (use raml-to-oas tool or Anypoint export)
  ├── Generate Spring Boot controller stubs from OpenAPI spec (openapi-generator-maven-plugin)
  └── Commit generated DTOs and controller interfaces to new Spring Boot project

Step 2: Spring Boot Project Scaffold (Day 1–2)
  ├── Bootstrap: spring-boot-starter-web + spring-boot-starter-actuator
  ├── Add spring-boot-starter-webflux (for WebClient) or keep RestTemplate
  ├── Set up multi-environment config: application-local.yml, -dev.yml, -prod.yml, -test.yml
  ├── Configure YouTube API key via environment variable: ${YOUTUBE_API_KEY}
  └── Set up logging: Logback with JSON structured logging

Step 3: Business Logic Migration (Day 2–3)
  ├── Implement GET /youtube/playlists/{playlistId}
  │   ├── WebClient call to https://www.googleapis.com/youtube/v3/playlistItems
  │   ├── Query params: part, playlistId, maxResults, key
  │   └── Java mapping method (DW-1 equivalent): YouTube response → PlaylistResponseDto
  ├── Implement GET /youtube/song/{videoId}
  │   ├── WebClient call to https://www.googleapis.com/youtube/v3/videos
  │   ├── Query params: part, id, key
  │   └── Java mapping method (DW-2 equivalent): YouTube response → SongDetailDto
  └── Implement global @ControllerAdvice error handler (replaces all DataWeave error transforms)

Step 4: Test Migration (Day 3–4)
  ├── Port 7 MUnit tests → 7 JUnit 5 + WireMock tests
  ├── WireMock stubs replace munit-tools:mock-when HTTP mocks
  ├── AssertJ assertions replace munit-tools:assert-that
  └── Spring Boot @ActiveProfiles("test") replaces global-property env=test

Step 5: Shadow Mode Validation (Day 5–7)
  ├── Deploy Spring Boot app alongside Mule on different port
  ├── Route 5–10% of traffic to Spring Boot (or replicate requests via sidecar)
  ├── Compare responses: assert payload parity and error handling parity
  └── Fix any divergences before traffic cutover

Step 6: Cutover (Day 8)
  ├── Update DNS/load balancer to point to Spring Boot app
  ├── Monitor error rates, latency, and response correctness for 48h
  └── Decommission Mule CloudHub deployment after stability confirmed
```

**Total migration: 8–10 working days (2 weeks)**

---

## What the Generic Analysis Recommended (and Why It Differs)

The generic analysis (`08-implementation-approaches-and-technology-adoption.md`) recommends a **5-phase playbook spanning 2–24 months** with:
- Phase 0: Install Spring Cloud Gateway, set up Kafka, Redis, Vault, Config Server
- Phase 1–4: Progressive domain-by-domain migration

**Why this does not apply here:**

| Generic Analysis Recommendation | Applicable to youtube-playlist-api? |
|---|---|
| Spring Cloud Gateway | No — not an API gateway; it's a single service |
| Kafka, Redis, Vault, Config Server | No — no messaging, no caching, no secrets manager needed |
| Multi-phase domain migration | No — single domain, 2 endpoints |
| Strangler Fig pattern | Optional — valid if multiple consumers, but adds operational complexity |
| 2–4 months minimum timeline | No — 2 weeks is realistic for this scope |

---

## Simplified Target Architecture

```
Consumer
    │  HTTPS GET /youtube/playlists/{playlistId}
    │  HTTPS GET /youtube/song/{videoId}
    ▼
[Spring Boot 3.3.x — youtube-playlist-api]
  Controller: @RestController (OpenAPI 3.0 contract)
  Service: YouTubeService
    ├── WebClient → https://www.googleapis.com/youtube/v3/playlistItems
    └── WebClient → https://www.googleapis.com/youtube/v3/videos
  Mapper: PlaylistMapper, SongMapper (plain Java methods)
  Error handling: @ControllerAdvice
  Config: application-{profile}.yml + YOUTUBE_API_KEY env var
    ▼
[YouTube Data API v3 — googleapis.com]
```

**No Apache Camel. No Spring Integration. No Kafka. No API Gateway. No Redis.**

---

## Deployment Target

| Current (Mule/CloudHub) | Target (Spring Boot/Kubernetes) |
|---|---|
| CloudHub MICRO (0.1 vCore, ~500MB RAM) | Kubernetes pod: 100m–250m CPU, 256Mi–512Mi RAM |
| 1 CloudHub worker | 1–2 replicas (HPA optional) |
| us-east-1 CloudHub | Match existing cloud region |
| Environment via CloudHub properties | Spring Boot profiles + env vars via K8s ConfigMap/Secret |
| CloudHub built-in logging | Fluentd/Logstash + structured Logback JSON |
| No health check | Spring Actuator `/actuator/health` for liveness/readiness |

**Alternative to Kubernetes for this small project:** Docker Compose (dev), Render / Railway / Fly.io (simple cloud deployment). Kubernetes is appropriate if this is part of a larger K8s estate.

---

## Considerations for the PRD

1. **Consumer impact**: What consumes this API? The migration should be transparent — same URL, same response shape, same error codes.
2. **API key management**: Currently via `${YOUTUBE_API_KEY}` env var. In Spring Boot/K8s, use Kubernetes Secret.
3. **Observability gap**: Current Mule logging is basic (log4j2, unstructured). The target should add structured logging (JSON format) and Spring Actuator metrics.
4. **Console endpoint**: The APIKit Console at `/api/console/*` should be replaced with Springdoc OpenAPI UI (`/swagger-ui.html`) in non-prod environments.
5. **Pagination**: The `nextPageToken` field is returned but no endpoint is provided to page through results (no `pageToken` query parameter exposed). This is a functional gap that may warrant a story in the PRD.

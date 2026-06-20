# Effort Estimation — youtube-playlist-api Migration

**Date:** 2026-06-19
**Scope:** Full migration from Mule 4.6.0 to Spring Boot 3.3.x

---

## Summary

| Phase | Description | Effort |
|---|---|---|
| Contract migration | RAML → OpenAPI 3.0 conversion + Spring Boot stub generation | 0.5 day |
| Project scaffold | Spring Boot setup, config profiles, build pipeline | 0.5 day |
| Business logic | 2 controllers, 2 service methods, 2 mapper methods | 1.5 days |
| Error handling | Global `@ControllerAdvice` (replaces 11 DW error transforms) | 0.5 day |
| Test migration | 7 MUnit tests → 7 JUnit 5 + WireMock tests | 1 day |
| Shadow mode + validation | Deploy in parallel, compare outputs, validate parity | 2 days |
| Deployment setup | Dockerfile, Kubernetes manifests, CI/CD update | 1 day |
| **Total** | | **7 working days (1.5–2 calendar weeks)** |

**Team size assumed:** 1 Spring Boot developer (mid-senior level)

---

## Detailed Breakdown

### Phase 1: Contract Migration (0.5 day)

| Task | Detail | Hours |
|---|---|---|
| Export RAML 1.0 → OAS 3.0 | Use `raml2openapi` or Anypoint Studio export | 1h |
| Review and clean generated OAS | Verify types, nullable fields, examples | 1h |
| Configure `openapi-generator-maven-plugin` | Spring generator, interfaceOnly=true | 1h |
| Run code generation and verify stubs | Compile-check generated DTOs and interfaces | 1h |
| **Total** | | **4h** |

---

### Phase 2: Spring Boot Scaffold (0.5 day)

| Task | Detail | Hours |
|---|---|---|
| `spring initializr` project creation | Web, WebFlux, Actuator, Test | 0.5h |
| Multi-profile config files | application-local.yml, -dev.yml, -prod.yml, -test.yml | 1h |
| WebClient bean configuration | YouTube API base URL, headers | 1h |
| Logback JSON logging config | `logstash-logback-encoder` setup | 0.5h |
| Springdoc OpenAPI setup | Swagger UI in non-prod | 0.5h |
| **Total** | | **3.5h** |

---

### Phase 3: Business Logic (1.5 days)

| Task | Detail | Hours |
|---|---|---|
| `PlaylistController` implementation | `GET /youtube/playlists/{playlistId}` | 1h |
| `YouTubePlaylistService.getPlaylistItems()` | WebClient call + error handling | 1.5h |
| `PlaylistMapper.toDto()` | DW-1 equivalent — YouTube response → PlaylistResponseDto | 1.5h |
| `SongController` implementation | `GET /youtube/song/{videoId}` | 0.5h |
| `YouTubeVideoService.getVideoDetails()` | WebClient call + error handling | 1h |
| `SongMapper.toDto()` | DW-2 equivalent — YouTube response → SongDetailDto | 1h |
| YouTube response DTOs | `YouTubePlaylistItemsResponse`, `YouTubeVideoListResponse` Jackson models | 1.5h |
| **Total** | | **8h** |

---

### Phase 4: Error Handling (0.5 day)

| Task | Detail | Hours |
|---|---|---|
| `GlobalExceptionHandler` class | `@ControllerAdvice` covering all 8 error scenarios | 2h |
| `ErrorResponse` DTO | `{ error, message, code }` matching current shape | 0.5h |
| Manual test of each error path | Verify 400/401/404/405/406/415/500/503 responses | 1h |
| **Total** | | **3.5h** |

---

### Phase 5: Test Migration (1 day)

| MUnit Test | JUnit 5 + WireMock Equivalent | Hours |
|---|---|---|
| `test-get-youtube-playlists-success` | WireMock stub `/playlistItems` → 200, assert response shape | 1h |
| `test-get-youtube-playlists-empty` | WireMock stub `/playlistItems` → empty items | 0.5h |
| `test-get-youtube-playlists-unauthorized` | WireMock stub `/playlistItems` → 401 | 0.5h |
| `test-get-youtube-playlists-connectivity-error` | WireMock stub → connection refused | 0.5h |
| `test-get-youtube-song-by-id-success` | WireMock stub `/videos` → 200, assert all fields | 1h |
| `test-get-youtube-song-not-found` | WireMock stub `/videos` → empty items | 0.5h |
| `test-get-youtube-song-generic-error` | WireMock stub `/videos` → 500 | 0.5h |
| Integration test setup | `@SpringBootTest`, `WireMockServer`, `@ActiveProfiles("test")` | 1h |
| **Total** | | **5.5h** |

---

### Phase 6: Shadow Mode Validation (2 days)

| Task | Detail | Hours |
|---|---|---|
| Deploy Spring Boot on separate port/host | Docker Compose for local shadow testing | 2h |
| Write request replay script | Replay production requests to both Mule and Spring | 2h |
| Compare responses | Verify payload parity across all endpoints and error paths | 3h |
| Fix divergences | Likely minor — field order, null handling edge cases | 2h |
| Document parity test results | Sign-off checklist | 1h |
| **Total** | | **10h** |

---

### Phase 7: Deployment Setup (1 day)

| Task | Detail | Hours |
|---|---|---|
| `Dockerfile` | Multi-stage build, Java 21, minimal image | 1h |
| Kubernetes `Deployment` manifest | Resource limits: 100m–250m CPU, 256Mi–512Mi RAM | 1h |
| Kubernetes `Service` manifest | ClusterIP + optional LoadBalancer | 0.5h |
| Kubernetes `ConfigMap` | Non-secret properties | 0.5h |
| Kubernetes `Secret` | `YOUTUBE_API_KEY` | 0.5h |
| Liveness/readiness probes | `/actuator/health/liveness`, `/actuator/health/readiness` | 0.5h |
| CI/CD pipeline update | Add build/push/deploy steps | 1h |
| Smoke test on staging | Verify all endpoints respond correctly | 1h |
| **Total** | | **6h** |

---

## Comparison with Generic Analysis Estimates

The generic analysis (`13-reference-materials-and-appendices.md`) provides this effort model:

| Size | Flow Count | Estimated Effort |
|---|---|---|
| Small | 5–20 flows | 2–4 months |
| Medium | 20–100 flows | 6–12 months |
| Large | 100+ flows | 12–30 months |

**This project has 4 flows (2 implementation + 1 entry + 1 console). It is below the "Small" threshold.**

The generic estimates include:
- Team ramp-up and training time
- Multi-domain migration with phased rollout
- Complex DataWeave migration
- Kafka/messaging migration
- Database schema migration
- API Gateway setup

**None of these apply to this project.** The correct effort estimate is 7–10 working days (1.5–2 calendar weeks), not 2–4 months.

---

## Risk Adjustments

| Risk | Probability | Impact | Contingency |
|---|---|---|---|
| YouTube API response shape differs from RAML spec | Low | Medium | Add 0.5 day for mapping adjustments |
| RAML → OAS conversion produces incorrect nullable handling | Low | Low | Manual fix takes < 1h |
| Pagination (`nextPageToken`) feature request added to scope | Medium | Medium | Add 1 day if `pageToken` query param is required |
| Consumer requires zero-downtime cutover | Low | Medium | Add 0.5 day for blue/green setup |
| **Total contingency buffer** | | | **+2 days (total: 9–12 working days)** |

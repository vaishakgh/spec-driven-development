# PRD Addendum — youtube-playlist-api Migration

_Technical depth and implementation detail that informed the PRD but belongs downstream (architecture, solution design, stories). Not part of the requirements contract._

---

## Technology Stack Decision

**Decision:** Java 21 + Spring Boot 3.3.x + Spring WebClient. No Apache Camel.

**Rationale:** The project has 2 endpoints, 2 simple DataWeave transforms (both rated Simple in the complexity audit), and no EIP patterns. Apache Camel adds significant dependency weight for zero functional benefit at this scale. Spring Boot + WebClient is the simplest correct solution.

**Rejected alternative — Apache Camel on Spring Boot:**
The generic research recommended Camel as the default migration target. For complex MuleSoft projects with SAP/Salesforce connectors, scatter-gather flows, and message transformations, this is correct. For a 2-endpoint HTTP proxy with Simple DataWeave, it is over-engineering. Camel was explicitly ruled out after the project inventory confirmed the absence of any connector ecosystem dependency.

**Reference:** `docs/2-re-analysis-artifact/03-technology-stack-refined.md`

---

## Connector Implementation Map

| Mule Component | Spring Boot Equivalent |
|---|---|
| `http:listener` | Spring Boot embedded Tomcat, `@RestController` |
| `apikit:router` | `openapi-generator-maven-plugin` stubs from OAS 3.0 |
| `apikit:console` | Springdoc Swagger UI (`/swagger-ui.html`) |
| `http:request-config` (YouTube outbound) | `WebClient` bean with base URL `https://www.googleapis.com/youtube/v3` |
| `http:request` (outbound GET) | `webClient.get().uri(...).retrieve()` |
| `ee:transform` (DataWeave) | Java mapper method + Jackson DTOs |
| `configuration-properties` | Spring Boot `application-{profile}.yml` profiles |
| Mule `on-error-propagate` scopes | `@ControllerAdvice` + `@ExceptionHandler` |
| MUnit `mock-when http:request` | WireMock `stubFor(...)` |

---

## DataWeave Migration Detail

Both DataWeave transforms are **Simple** complexity. No MapStruct, no JOLT, no AI assistance required.

**Transform 1 (Playlist):** Map `payload.items[]` → `PlaylistItem[]`. Single `map` operator, `default` null-coalescing, string concatenation for `videoUrl`. ~20 lines DW → ~25 lines Java (including builder).

**Transform 2 (Video):** Map `payload.items[0]` → `SongDetail`. Single item extraction, field access, `default` null-coalescing, field rename (`channelTitle` → `channelName`). ~15 lines DW → ~20 lines Java.

**Reference:** `docs/2-re-analysis-artifact/01-dataweave-complexity-audit.md`

---

## RAML to OpenAPI 3.0 Conversion Notes

The RAML 1.0 spec is well-structured and should convert cleanly. Key mapping points:

| RAML Feature | OAS 3.0 Equivalent |
|---|---|
| `baseUri` | `servers[0].url` |
| `types` | `components/schemas` |
| `traits.commonErrors` | `components/responses` (reused across endpoints) |
| `datetime` with `format: rfc3339` | `string` with `format: date-time` |
| `string \| nil` | `string` with `nullable: true` |
| URI parameters | `parameters` (path) |

**New in OAS spec (not in RAML):**
- `pageToken` query parameter on `GET /youtube/playlists/{playlistId}`
- HTTP 404 response on `GET /youtube/song/{videoId}` when video not found

---

## Effort Estimate Summary

| Phase | Effort |
|---|---|
| Contract migration (RAML → OAS, code-gen) | 0.5 day |
| Spring Boot scaffold + config | 0.5 day |
| Business logic (2 controllers, 2 services, 2 mappers) | 1.5 days |
| Error handling (`@ControllerAdvice`) | 0.5 day |
| Test migration (7 MUnit → 7 JUnit + WireMock) | 1 day |
| Shadow mode validation | 2 days |
| Deployment (Dockerfile, K8s manifests, CI/CD) | 1 day |
| **Total** | **7 working days** |
| Contingency buffer | +2 days |

**Reference:** `docs/2-re-analysis-artifact/05-effort-estimation.md`

---

## Deployment Target Specification

| Parameter | Current (Mule/CloudHub) | Target (Spring Boot/K8s) |
|---|---|---|
| Worker type | MICRO (0.1 vCore, ~500MB RAM) | Pod: 100m–250m CPU, 256Mi–512Mi RAM |
| Instances | 1 CloudHub worker | 1–2 replicas |
| Region | us-east-1 | Match cloud region |
| Secrets | CloudHub property injection | Kubernetes Secret → env var |
| Config | CloudHub properties | Kubernetes ConfigMap + `application-{profile}.yml` |
| Health check | None | `/actuator/health/liveness`, `/actuator/health/readiness` |
| Logging | log4j2 (unstructured) | Logback + JSON structured output |

---

## Test Migration Map

| MUnit Test | JUnit 5 + WireMock Equivalent |
|---|---|
| `test-get-youtube-playlists-success` | WireMock stub `/playlistItems` → 200, assert all PlaylistResponse fields |
| `test-get-youtube-playlists-empty` | WireMock stub `/playlistItems` → empty items, assert `totalResults=0` |
| `test-get-youtube-playlists-unauthorized` | WireMock stub `/playlistItems` → 401, assert ErrorResponse |
| `test-get-youtube-playlists-connectivity-error` | WireMock fault → CONNECTION_RESET, assert HTTP 503 |
| `test-get-youtube-song-by-id-success` | WireMock stub `/videos` → 200, assert all SongDetail fields |
| `test-get-youtube-song-not-found` | WireMock stub `/videos` → empty items, assert HTTP 404 (not 200) |
| `test-get-youtube-song-generic-error` | WireMock stub `/videos` → 500, assert HTTP 500 ErrorResponse |

**Additional tests needed beyond MUnit parity:**
- `GET /youtube/playlists/{playlistId}?pageToken={token}` — assert token forwarded to upstream
- `GET /youtube/song/{videoId}` video-not-found → assert HTTP 404 body contains `code: 404`

**Reference:** `docs/2-re-analysis-artifact/06-test-migration-plan.md`

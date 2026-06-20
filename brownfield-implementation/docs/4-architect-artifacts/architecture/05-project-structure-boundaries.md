# Project Structure & Boundaries

## Complete Project Directory Structure

Items marked `[gen]` are produced by `openapi-generator-maven-plugin` at build time and live in
`target/` — they must **not** be committed to source control. The `src/main/java/.../dto/`
directory contains only hand-written upstream models; all OAS contract DTOs are generated.

```
youtube-playlist-api/
├── .github/
│   └── workflows/
│       └── ci.yml                               # GitHub Actions: build → test → Docker build
├── .mvn/
│   └── wrapper/
│       └── maven-wrapper.properties             # Maven wrapper — pins Maven version
├── k8s/
│   ├── configmap.yaml                           # Non-secret config (youtube.api.base-url, max-results)
│   ├── deployment.yaml                          # Deployment + resource limits (NFR-4)
│   └── service.yaml                             # ClusterIP service on port 8081
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/youtubeplaylistapi/
│   │   │       ├── YoutubePlaylistApiApplication.java    # @SpringBootApplication entry point
│   │   │       ├── config/
│   │   │       │   ├── OpenApiConfig.java                # springdoc OpenAPI info (title/version)
│   │   │       │   └── WebClientConfig.java              # WebClient bean; baseUrl + timeout wiring
│   │   │       ├── controller/
│   │   │       │   ├── PlaylistController.java           # implements generated PlaylistApi
│   │   │       │   └── VideoController.java              # implements generated VideoApi
│   │   │       ├── service/
│   │   │       │   ├── PlaylistService.java              # WebClient call → invokes PlaylistMapper
│   │   │       │   └── VideoService.java                 # WebClient call → invokes VideoMapper
│   │   │       ├── mapper/
│   │   │       │   ├── PlaylistMapper.java               # YTPlaylistItemsResponse → PlaylistResponse
│   │   │       │   └── VideoMapper.java                  # YTVideoDetailsResponse → SongDetail
│   │   │       ├── dto/
│   │   │       │   └── youtube/
│   │   │       │       ├── YTPlaylistItemsResponse.java  # raw YouTube playlistItems response model
│   │   │       │       └── YTVideoDetailsResponse.java   # raw YouTube videos response model
│   │   │       └── exception/
│   │   │           ├── GlobalExceptionHandler.java       # @RestControllerAdvice — single error map
│   │   │           ├── UpstreamUnauthorizedException.java  # 401 from YouTube → 401 response
│   │   │           ├── UpstreamConnectivityException.java  # 5xx/timeout → 503 response
│   │   │           └── VideoNotFoundException.java          # empty items[] → 404 response
│   │   └── resources/
│   │       ├── api/
│   │       │   └── youtube-playlist-api.yaml             # OAS 3.0 spec (source for code-gen)
│   │       ├── application.yml                           # server.port=8081, virtual threads=true
│   │       ├── application-local.yml                     # local dev overrides
│   │       ├── application-dev.yml                       # dev environment overrides
│   │       └── application-prod.yml                      # prod (springdoc.api-docs.enabled=false)
│   └── test/
│       ├── java/
│       │   └── com/example/youtubeplaylistapi/
│       │       ├── PlaylistEndpointTest.java    # T01 happy path, T02 empty, T03 invalid ID
│       │       ├── VideoEndpointTest.java       # T04 happy path, T05 video not found → 404
│       │       ├── ErrorHandlingTest.java       # T06 YouTube 401, T07 YouTube 503
│       │       └── mapper/
│       │           ├── PlaylistMapperTest.java  # unit — mapper field-mapping correctness
│       │           └── VideoMapperTest.java     # unit — mapper field-mapping correctness
│       └── resources/
│           └── application-test.yml             # WireMock port binding; disables real YouTube calls
├── target/
│   └── generated-sources/
│       └── openapi/
│           └── com/example/youtubeplaylistapi/
│               ├── controller/
│               │   ├── PlaylistApi.java   [gen] GET /youtube/playlists/{playlistId} interface
│               │   └── VideoApi.java      [gen] GET /youtube/song/{videoId} interface
│               └── dto/
│                   ├── PlaylistResponse.java  [gen]
│                   ├── PlaylistItem.java      [gen]
│                   ├── SongDetail.java        [gen]
│                   └── ErrorResponse.java     [gen]
├── Dockerfile                               # multi-stage: eclipse-temurin:21-jdk-alpine → jre-alpine
├── .dockerignore                            # excludes target/, .git/, docs/
├── .gitignore                               # excludes target/, *.env
├── mvnw                                     # Maven wrapper executable (Unix)
├── mvnw.cmd                                 # Maven wrapper (Windows)
└── pom.xml                                  # Spring Boot 4.1.0 parent, all deps, openapi-generator
```

## Architectural Boundaries

**North Boundary — Inbound API**

| Element | Location | Rule |
|---|---|---|
| OAS 3.0 contract | `src/main/resources/api/youtube-playlist-api.yaml` | Single source of truth; never edited based on implementation convenience |
| Generated interfaces | `target/…/controller/PlaylistApi.java`, `VideoApi.java` | Never edited manually — modify spec, regenerate |
| Generated DTOs | `target/…/dto/PlaylistResponse.java` etc. | Never edited manually |
| Controllers | `controller/PlaylistController.java`, `VideoController.java` | Delegation only — no business logic, no field mapping |

**Middle Boundary — Service + Mapper Layer**

| Element | Location | Rule |
|---|---|---|
| Orchestration | `service/PlaylistService.java`, `VideoService.java` | Calls WebClient with `.block()`, then delegates to mapper; no field-mapping inline |
| Field mapping | `mapper/PlaylistMapper.java`, `VideoMapper.java` | All YouTube-to-OAS DTO transformation lives here — nowhere else |
| Raw YouTube models | `dto/youtube/YTPlaylistItemsResponse.java`, `YTVideoDetailsResponse.java` | Isolated from OAS DTOs; YouTube API shape changes contained here |

**South Boundary — Outbound WebClient**

| Element | Location | Rule |
|---|---|---|
| HTTP calls to YouTube | `service/PlaylistService.java`, `VideoService.java` | `.onStatus()` error mapping is mandatory before `.bodyToMono().block()` |
| WebClient config | `config/WebClientConfig.java` | Single bean with baseUrl, timeout, api.key — no inline `WebClient.create()` elsewhere |

**Cross-Cutting — Error Handling**

| Element | Location | Rule |
|---|---|---|
| Global handler | `exception/GlobalExceptionHandler.java` | Single `@RestControllerAdvice`; all exception→HTTP mapping lives here |
| Custom exceptions | `exception/Upstream*.java`, `exception/VideoNotFoundException.java` | Thrown in service layer only; never caught/swallowed in service or controller |

## Requirements-to-Structure Mapping

**Epic 1 — Project Foundation**

| Story | Files |
|---|---|
| 1.1 Maven scaffold | `pom.xml`, `mvnw`, `.mvn/wrapper/`, `YoutubePlaylistApiApplication.java` |
| 1.2 Application config | `application.yml`, `application-local.yml`, `application-dev.yml`, `application-prod.yml` |
| 1.3 WebClient bean | `config/WebClientConfig.java` |

**Epic 2 — API Contract**

| Story | Files |
|---|---|
| 2.1 OAS 3.0 spec | `src/main/resources/api/youtube-playlist-api.yaml` |
| 2.2 Code generation | `target/generated-sources/openapi/…` (PlaylistApi, VideoApi, PlaylistResponse, PlaylistItem, SongDetail, ErrorResponse) |
| 2.3 springdoc-openapi | `config/OpenApiConfig.java` + `springdoc.*` YAML keys |

**Epic 3 — Playlist Endpoint**

| Story | Files |
|---|---|
| 3.1 Playlist controller | `controller/PlaylistController.java` |
| 3.2 Playlist service | `service/PlaylistService.java` |
| 3.3 Playlist mapper + model | `mapper/PlaylistMapper.java`, `dto/youtube/YTPlaylistItemsResponse.java` |

**Epic 4 — Song Endpoint**

| Story | Files |
|---|---|
| 4.1 Video controller | `controller/VideoController.java` |
| 4.2 Video service | `service/VideoService.java` |
| 4.3 Video mapper + model | `mapper/VideoMapper.java`, `dto/youtube/YTVideoDetailsResponse.java` |

**Epic 5 — Error Handling**

| Story | Files |
|---|---|
| 5.1 Exception hierarchy | `exception/UpstreamUnauthorizedException.java`, `exception/UpstreamConnectivityException.java`, `exception/VideoNotFoundException.java` |
| 5.2 Global error handler | `exception/GlobalExceptionHandler.java` |

**Epic 6 — Testing**

| Story | Files | Type |
|---|---|---|
| 6.1 Playlist tests (T01–T03) | `test/…/PlaylistEndpointTest.java` | Integration — `@SpringBootTest` + WireMock |
| 6.2 Video tests (T04–T05) | `test/…/VideoEndpointTest.java` | Integration — `@SpringBootTest` + WireMock |
| 6.3 Error tests (T06–T07) | `test/…/ErrorHandlingTest.java` | Integration — `@SpringBootTest` + WireMock |
| 6.4 Mapper unit tests | `test/…/mapper/PlaylistMapperTest.java`, `VideoMapperTest.java` | Unit — plain JUnit 5 |
| 6.5 Test config | `test/resources/application-test.yml` | Config |

**Epic 7 — Shadow Mode & Deployment**

| Story | Files |
|---|---|
| 7.1 Dockerfile | `Dockerfile`, `.dockerignore` |
| 7.2 K8s manifests | `k8s/deployment.yaml`, `k8s/service.yaml`, `k8s/configmap.yaml` |
| 7.3 CI pipeline | `.github/workflows/ci.yml` |

## Integration Points

**Internal Communication**

All internal calls are synchronous in-process Java method invocations. No async framework, no event bus, no inter-service messaging.

```
HTTP Request
    │
    ▼
PlaylistController / VideoController    (implements generated interface — delegation only)
    │
    ▼
PlaylistService / VideoService          (WebClient .retrieve().onStatus().block())
    │                │
    ▼                ▼
YouTube Data API v3  PlaylistMapper / VideoMapper
[external HTTPS]     (YT raw DTO → OAS DTO)
```

**External Integration**

| Integration | Protocol | Config Keys | Error Strategy |
|---|---|---|---|
| YouTube Data API v3 `/playlistItems` | HTTPS REST | `youtube.api.base-url`, `youtube.api.key` | `.onStatus(401 → Unauth, 5xx → Connectivity)` |
| YouTube Data API v3 `/videos` | HTTPS REST | `youtube.api.base-url`, `youtube.api.key` | `.onStatus(401 → Unauth, 5xx → Connectivity)` |

`YOUTUBE_API_KEY` injected via K8s Secret → environment variable. Never committed to source control.

**Data Flow**

```
Inbound path/query params (playlistId / videoId / maxResults)
    │  validated by generated controller interface contract
    ▼
Service builds YouTube query parameters
    ▼
Service calls YouTube → WebClient.block() → raw YT response
    ▼
Mapper transforms raw YT fields → OAS DTOs (PlaylistResponse / SongDetail)
    ▼
Controller returns OAS DTO → Spring serialises to camelCase JSON response
```

## File Organisation Patterns

**Configuration**

| File | Purpose |
|---|---|
| `application.yml` | `server.port=8081`, `spring.threads.virtual.enabled=true`, actuator endpoints, INFO logging baseline |
| `application-local.yml` | Local overrides — YouTube base URL, swagger enabled |
| `application-dev.yml` | Dev environment — env-var bindings for YouTube URL |
| `application-prod.yml` | `springdoc.api-docs.enabled=false`, `springdoc.swagger-ui.enabled=false` |
| `application-test.yml` | WireMock port wiring; disables real outbound YouTube calls |
| `k8s/configmap.yaml` | Non-secret config (`youtube.api.base-url`, `max-results`) injected as env vars |

Property key format: kebab-case throughout (`youtube.api.base-url`). No camelCase YAML keys.

**Source Organisation**

One package per architectural layer; no sub-packages within a layer except `dto/youtube/` for upstream isolation. Layer packages: `config`, `controller`, `service`, `mapper`, `dto`, `dto.youtube`, `exception`.

**Test Organisation**

Integration tests (NFR-2 mandatory) at test root; unit tests in mirrored sub-packages:

| Test Class / Package | Scope | Mechanism |
|---|---|---|
| `PlaylistEndpointTest` | T01–T03 (integration) | `@SpringBootTest(RANDOM_PORT)` + WireMock inline stubs |
| `VideoEndpointTest` | T04–T05 (integration) | `@SpringBootTest(RANDOM_PORT)` + WireMock inline stubs |
| `ErrorHandlingTest` | T06–T07 (integration) | `@SpringBootTest(RANDOM_PORT)` + WireMock inline stubs |
| `mapper/PlaylistMapperTest` | mapper logic (unit) | Plain JUnit 5, no Spring context |
| `mapper/VideoMapperTest` | mapper logic (unit) | Plain JUnit 5, no Spring context |

WireMock stubs declared inline per test method — no shared stub JSON files.

## Development Workflow Integration

**Build Process**

Maven lifecycle: `validate` → `generate-sources` (openapi-generator) → `compile` → `test` → `package`.

```bash
./mvnw verify                     # full build + all tests (CI standard)
./mvnw package -DskipTests        # jar only — used in Docker builder stage
./mvnw spring-boot:run -Plocal    # local dev with application-local.yml active
```

Generated sources in `target/generated-sources/openapi/` are added to compile classpath automatically by the plugin — no manual source-root configuration needed.

**Deployment Pipeline**

```
./mvnw package -DskipTests → target/youtube-playlist-api-*.jar
    │
    ▼ Dockerfile (multi-stage)
FROM eclipse-temurin:21-jdk-alpine  (builder stage — compile only)
    → FROM eclipse-temurin:21-jre-alpine  (runtime image — JRE only)
    │
    ▼ kubectl apply -f k8s/
configmap.yaml → deployment.yaml → service.yaml
    │
    ▼ Shadow mode gate (NFR-3)
Mule (CloudHub) + Spring Boot (K8s) both live
Traffic replayed to both; field-by-field parity required before switch
    │
    ▼ Cutover: 100% traffic → Spring Boot; Mule decommissioned
```

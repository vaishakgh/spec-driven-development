# Core Architectural Decisions

## Decision Priority Analysis

**Critical Decisions (Block Implementation):**
- Spring Boot 4.1.0 + Java 21 as runtime (settled in Step 3)
- Contract-first OAS 3.0 with openapi-generator (`interfaceOnly=true`) — defines all controller shapes
- `GlobalExceptionHandler` as single error boundary — every endpoint must funnel through it
- `YOUTUBE_API_KEY` via env var only — blocks any config YAML that touches the key value

**Important Decisions (Shape Architecture):**
- WebClient blocking style with virtual threads — shapes service layer code pattern
- Package structure convention — must be consistent across all stories
- Docker multi-stage build + `eclipse-temurin:21-jre-alpine` — shapes deployment story
- springdoc profile-gating — must disable Swagger UI in prod

**Deferred Decisions (Post-Migration):**
- CI/CD pipeline — not in PRD scope; out of scope for this engagement
- Ingress/TLS termination — Kubernetes cluster concern, not application concern
- Auto-scaling (HPA) — MICRO worker had 1 instance; 1–2 replicas covers initial need
- Upgrade from Spring Boot 4.1.0 — natural follow-on after cutover stabilises

## Data Architecture

**Decision:** No persistence layer.

This is a stateless HTTP proxy. Every request fetches fresh data from the YouTube API. No database, no cache, no session store. Data flows: inbound request → upstream YouTube API call → transform → response. Nothing is stored between requests.

- **Caching:** explicitly a non-goal (PRD). Not implemented.
- **Validation:** OAS 3.0 schema validation via generated stubs + Spring `@Valid` annotations on path/query parameters. No custom validator beans needed beyond the `GlobalExceptionHandler`.

## Authentication & Security

**Decision:** No consumer-facing authentication. Internal API only.

- Consumer calls arrive without authentication headers — matches current Mule behaviour and is explicitly a PRD non-goal.
- The only credential in the system is `YOUTUBE_API_KEY` (outbound). Injection path: Kubernetes Secret → `YOUTUBE_API_KEY` environment variable → `@Value("${youtube.api.key}")` in `WebClientConfig` bean. Key never touches any source-controlled file.
- HTTPS termination: handled at Kubernetes ingress/load balancer layer. The Spring Boot app listens on plain HTTP port 8081 internally (same as Mule).
- No Spring Security dependency added — adds unnecessary weight for zero benefit.

## API & Communication Patterns

**Decision:** Contract-first REST via OAS 3.0 + openapi-generator (`interfaceOnly` mode).

**API contract approach:**
- RAML 1.0 spec converted to OAS 3.0 (once, manually — Story 1.2)
- `openapi-generator-maven-plugin` generates `PlaylistApi` and `VideoApi` Java interfaces from the OAS spec during `mvn generate-sources`
- Controller classes implement these interfaces; divergence from spec causes compile failure

**openapi-generator configuration:**
```xml
<configuration>
  <inputSpec>${project.basedir}/src/main/resources/api/youtube-playlist-api.yaml</inputSpec>
  <generatorName>spring</generatorName>
  <configOptions>
    <interfaceOnly>true</interfaceOnly>
    <useSpringBoot3>true</useSpringBoot3>
    <useJakartaEe>true</useJakartaEe>
    <useTags>true</useTags>
  </configOptions>
  <modelPackage>com.example.youtubeplaylistapi.dto</modelPackage>
  <apiPackage>com.example.youtubeplaylistapi.controller</apiPackage>
</configuration>
```

**WebClient calling style — Blocking with Virtual Threads:**
- Controllers are standard Spring MVC `@RestController` (not reactive)
- Service methods call `webClient.get()...bodyToMono(X.class).block()`
- Java 21 virtual threads (`spring.threads.virtual.enabled=true`) make `.block()` cheap — no thread-pool starvation risk; each request runs on a virtual thread
- Rationale: keeps service code synchronous and readable; matches Mule's synchronous flow structure; avoids WebFlux controller complexity for zero throughput benefit at this scale

**Error handling — single GlobalExceptionHandler:**
- All 11 Mule `on-error-propagate` scopes replaced by one `@RestControllerAdvice` class
- Catches: Spring MVC exceptions (400, 404, 405, 406, 415), WebClient upstream exceptions (401, 503), and `Exception` catch-all (500)
- Returns `ErrorResponse { error, message, code }` for every non-2xx scenario
- No error handling in controller or service methods — exceptions propagate upward to handler

**pageToken validation:**
- Local format validation in `PlaylistController` before any upstream call (FR-3)
- Invalid format → immediate HTTP 400 without calling YouTube API
- Valid format forwarded to service → upstream rejection → caught and propagated as HTTP 400

**API versioning:** None. Base path `/api/` preserved exactly. No version prefix added.

**API documentation:**
- springdoc-openapi 3.0.3 auto-generates Swagger UI from the OAS 3.0 spec
- Available at `/swagger-ui.html` in local + dev profiles
- Disabled in prod via `springdoc.api-docs.enabled=false` in `application-prod.yml`

## Frontend Architecture

Not applicable. This is a backend REST API with no user-facing interface. The only browser-accessible UI is Swagger UI (developer tool, non-prod only).

## Infrastructure & Deployment

**Containerisation — Docker multi-stage build:**

```dockerfile
# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY . .
RUN ./mvnw package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Base image: `eclipse-temurin:21-jre-alpine` — vendor-neutral (Eclipse Adoptium), Alpine-based for minimal footprint. JRE-only runtime stage (JDK excluded from final image).

**Kubernetes manifest set:**

| Manifest | Purpose |
|----------|---------|
| `Deployment` | 1–2 replicas, resource limits, env vars, health probes |
| `Service` | ClusterIP — internal cluster access only |
| `ConfigMap` | Non-secret config: `maxResults`, YouTube base URL, Spring profile |
| `Secret` | `YOUTUBE_API_KEY` only |

**Resource limits (NFR-4 compliance):**
```yaml
resources:
  requests:
    cpu: "100m"
    memory: "256Mi"
  limits:
    cpu: "250m"
    memory: "512Mi"
```

**Health probes (FR-21):**
```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8081
readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8081
```

**Shadow mode strategy (NFR-3):**
- Mule (CloudHub) and Spring Boot (Kubernetes) run simultaneously during validation window
- Request-replay harness sends identical requests to both; compares responses field-by-field
- Gate: zero field-level discrepancies required before traffic switch
- Latency captured during shadow runs to verify NFR-1 (≤20% regression vs Mule baseline)

**Monitoring and logging:**
- Structured JSON logging via `logstash-logback-encoder` — all profiles, stdout
- Log aggregation handled by cluster log collector reading stdout JSON
- Spring Actuator exposes `/actuator/health` — sufficient for this scale

## Package Structure

```
com.example.youtubeplaylistapi/
├── controller/        # PlaylistController, VideoController (@RestController)
├── service/           # PlaylistService, VideoService (WebClient + .block())
├── mapper/            # PlaylistMapper, VideoMapper (plain Java transform methods)
├── dto/               # PlaylistResponse, PlaylistItem, SongDetail, ErrorResponse
│   └── youtube/       # YouTubePlaylistItemsResponse, YouTubeVideoResponse (upstream shapes)
├── exception/         # GlobalExceptionHandler (@RestControllerAdvice) + typed exceptions
└── config/            # WebClientConfig (WebClient bean), SwaggerConfig (profile-gated)
```

## Decision Impact Analysis

**Implementation Sequence (matches epic order):**
1. Spring Initializr scaffold → manual dependencies → `WebClientConfig` bean (Epic 1)
2. OAS 3.0 spec → openapi-generator stubs → controller interfaces (Epic 1)
3. `application-{profile}.yml` × 4 → `YOUTUBE_API_KEY` env var wiring (Epic 1)
4. `PlaylistMapper` + DTOs → `PlaylistService` (blocking WebClient) → `PlaylistController` (Epic 2)
5. `VideoMapper` + DTOs → `VideoService` → `VideoController` + 404 logic (Epic 3)
6. `ErrorResponse` DTO → `GlobalExceptionHandler` (all 8 error scenarios) (Epic 4)
7. springdoc profile config → Actuator probes → Logback JSON config (Epic 5)
8. WireMock test infra → playlist tests → video tests (Epic 6)
9. Dockerfile → K8s manifests → shadow harness → cutover (Epic 7)

**Cross-Component Dependencies:**
- `WebClientConfig` bean injected into both `PlaylistService` and `VideoService`
- Generated OAS DTOs used by mappers, controllers, and `GlobalExceptionHandler`
- `GlobalExceptionHandler` catches exceptions from both services — no service coupling
- Profile YAMLs control Swagger UI visibility, `maxResults`, and API key injection across all components

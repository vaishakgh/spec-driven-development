---
baseline_commit: b1318e8096366d5200c2744cb42f5ae8816d8e20
---

# Story 5.2: Configure Kubernetes Health Probes and Structured JSON Logging

Status: in-progress

## Story

As an **operations engineer**,
I want liveness and readiness health endpoints and structured JSON logs,
so that Kubernetes can probe service health accurately and log aggregation tools can parse and query application logs.

## Acceptance Criteria

1. **Given** the application is running and healthy **When** `GET /actuator/health/liveness` is called **Then** HTTP 200 is returned with `{ "status": "UP" }` without requiring authentication (FR-21)
2. **Given** the application is running and ready to receive traffic **When** `GET /actuator/health/readiness` is called **Then** HTTP 200 is returned with `{ "status": "UP" }` without requiring authentication (FR-21)
3. **Given** an inbound request arrives at either endpoint **When** the request is processed **Then** a structured JSON log entry is written at INFO level containing the endpoint called and the key input parameter (`playlistId` or `videoId`) (FR-22)
4. **Given** a successful response is returned **When** the response is sent **Then** a structured JSON log entry is written at INFO level containing the key result metric (`totalResults` count for playlist; video found/not-found for video) (FR-22)
5. **Given** any error condition occurs **When** the error is handled **Then** a structured JSON log entry is written at ERROR level with the exception type, message, and sufficient context to diagnose the failure (FR-22)
6. **Given** the application is running in any profile **When** any log statement is emitted **Then** all log output is valid JSON — no unstructured or plain-text log lines (FR-22)

## Tasks / Subtasks

- [x] Verify health probe YAML in `application.yml` — NO CHANGE NEEDED (AC: 1, 2)
  - [x] Confirm `management.endpoint.health.probes.enabled: true` present
  - [x] Confirm `management.health.livenessState.enabled: true` present
  - [x] Confirm `management.health.readinessState.enabled: true` present
  - [x] Confirm `management.endpoints.web.exposure.include: health` present
- [x] Create `src/main/resources/logback-spring.xml` with LogstashEncoder (AC: 6)
  - [x] ConsoleAppender with `net.logstash.logback.encoder.LogstashEncoder`
  - [x] Root logger at INFO level
  - [x] No profile-based switching — all profiles emit JSON
- [x] Add `log.info` entry and success log statements to `PlaylistController` (AC: 3, 4)
  - [x] Entry log: `playlistId` + optional `pageToken` presence
  - [x] Success log: `totalResults` count from response
- [ ] Add `log.info` entry and success log statements to `VideoController` (AC: 3, 4)
  - [ ] Entry log: `videoId`
  - [ ] Success log: video found (indicate videoId)
- [x] Verify `GlobalExceptionHandler` already logs errors at WARN/ERROR level (AC: 5)
  - [x] Confirm existing handlers use `log.warn` / `log.error` — NO CHANGE NEEDED

## Dev Notes

### Critical Pre-existing State — Read Before Implementing

**`application.yml` health probe config ALREADY COMPLETE — do not touch:**
```yaml
management:
  endpoint:
    health:
      probes:
        enabled: true
  endpoints:
    web:
      exposure:
        include: health
  health:
    livenessState:
      enabled: true
    readinessState:
      enabled: true
```
ACs 1 and 2 are already satisfied by existing config. Zero changes to `application.yml`.

**`GlobalExceptionHandler.java` already logs at WARN/ERROR — do not touch:**
```java
// Request-level errors (400, 404, 405, 406, 415) — log.warn
log.warn("endpoint={} status={} error={}", request.getRequestURI(), 404, ex.getClass().getSimpleName());

// Catch-all — log.error
log.error("endpoint={} error={} message={}", request.getRequestURI(),
        ex.getClass().getSimpleName(), ex.getMessage(), ex);
```
AC 5 is satisfied by existing handler. Zero changes to `GlobalExceptionHandler.java`.

**logstash-logback-encoder already in pom.xml — do not change pom.xml:**
```xml
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>8.0</version>
    <scope>runtime</scope>
</dependency>
```

### Task 1: Create logback-spring.xml

Create `src/main/resources/logback-spring.xml` with the following exact content:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>

    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder"/>
    </appender>

    <root level="INFO">
        <appender-ref ref="STDOUT"/>
    </root>

</configuration>
```

**Why `logback-spring.xml` (not `logback.xml`):** The `-spring` suffix lets Spring Boot participate in logback initialisation. This is required when using `<springProfile>` or `<springProperty>` tags. Even if no Spring-specific tags are used now, the `-spring` convention is the project standard and enables future profile-based tuning without a filename change.

**Why no `<springProfile>` switching:** AC 6 requires JSON in all profiles. Do not add a `<springProfile name="local">` block that falls back to a `PatternLayoutEncoder`. All profiles emit JSON to stdout. Log aggregation tools and `kubectl logs` can both read JSON.

**`LogstashEncoder` output fields** (logstash-logback-encoder 8.0 defaults):
| JSON field | Source |
|---|---|
| `@timestamp` | ISO-8601 UTC |
| `@version` | `"1"` |
| `message` | logger message |
| `logger_name` | fully-qualified class name |
| `thread_name` | virtual thread name |
| `level` | `INFO` / `WARN` / `ERROR` |
| `level_value` | numeric |
| `stack_trace` | present only on exceptions |

No custom field mapping or `customFields` block is needed — defaults satisfy FR-22.

### Task 2: Add Logging to PlaylistController

`PlaylistController` currently delegates to `PlaylistService` with no logging. Add a `Logger` field and two log calls — one on entry and one on success. Follow the existing `GlobalExceptionHandler` log format (structured key=value pairs in the message string — logstash-logback-encoder serialises these as the `message` field; no MDC or custom markers required):

```java
private static final Logger log = LoggerFactory.getLogger(PlaylistController.class);

// Inside the getPlaylist handler method, at top:
log.info("endpoint=/youtube/playlists/{} pageToken={}", playlistId, pageToken != null ? "present" : "absent");

// After receiving PlaylistResponse from service, before return:
log.info("endpoint=/youtube/playlists/{} status=200 totalResults={}", playlistId,
         response.getPageInfo() != null ? response.getPageInfo().getTotalResults() : 0);
```

Import: `org.slf4j.Logger`, `org.slf4j.LoggerFactory` (already on classpath via `spring-boot-starter-web`).

### Task 3: Add Logging to VideoController

`VideoController` currently delegates to `VideoService` with no logging. Same pattern:

```java
private static final Logger log = LoggerFactory.getLogger(VideoController.class);

// Inside the getSong handler method, at top:
log.info("endpoint=/youtube/song/{}", videoId);

// After receiving SongDetail from service, before return:
log.info("endpoint=/youtube/song/{} status=200 videoFound=true", videoId);
```

Note: `VideoNotFoundException` (when video not found) is already logged by `GlobalExceptionHandler` at WARN level — no duplicate logging needed in controller or service.

### Logging Pattern Rules

From architecture `04-implementation-patterns-consistency-rules.md`:
- Controllers log at INFO on entry and on success
- `GlobalExceptionHandler` logs errors (WARN for 4xx, ERROR for 5xx catch-all) — already done
- No try/catch blocks in controllers or services — exceptions propagate to handler
- No `log.debug` calls — INFO baseline is sufficient (logging.level.root=INFO in application.yml)
- SLF4J only — never `System.out.println`

### Architecture Constraints

- Health probes via Spring Actuator only — no custom endpoint classes needed
- Actuator exposes only `health` (`management.endpoints.web.exposure.include: health`) — sufficient for Kubernetes probes
- Structured logging via `logstash-logback-encoder` — no other logging library or format
- All log output to stdout — no file appenders, no Logback `RollingFileAppender`

[Source: docs/4-architect-artifacts/architecture/03-core-architectural-decisions.md#Monitoring and logging]

### Kubernetes Probe Config (reference only — Epic 7 owns k8s manifests)

The `k8s/deployment.yaml` (Epic 7 scope) will reference these health endpoints:
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
This story only ensures the Spring Boot side exposes them correctly — which `application.yml` already does.

### No New Test Class Required

- Health probe endpoints are standard Actuator — covered by Epic 6 integration tests if needed
- Logging configuration is infrastructure; no unit test for logback XML
- Controller log statements are incidental to the AC tests already planned in Epic 6
- Do not create a new test class for this story

### Project Structure Notes

- **NEW FILE**: `src/main/resources/logback-spring.xml`
- **UPDATE**: `src/main/java/com/example/youtubeplaylistapi/controller/PlaylistController.java` — add Logger + 2 log statements
- **UPDATE**: `src/main/java/com/example/youtubeplaylistapi/controller/VideoController.java` — add Logger + 2 log statements
- **VERIFY (NO CHANGE)**: `src/main/resources/application.yml`
- **VERIFY (NO CHANGE)**: `src/main/java/com/example/youtubeplaylistapi/exception/GlobalExceptionHandler.java`
- No new packages, no new dependencies

### References

- [Source: docs/3-product-manager-artifacts/epics-and-stories/epic-5-observability-api-explorer.md#Story 5.2]
- [Source: docs/4-architect-artifacts/architecture/03-core-architectural-decisions.md#Monitoring and logging]
- [Source: docs/4-architect-artifacts/architecture/03-core-architectural-decisions.md#Health probes]
- [Source: docs/4-architect-artifacts/architecture/04-implementation-patterns-consistency-rules.md]
- [Source: docs/4-architect-artifacts/architecture/05-project-structure-boundaries.md#Configuration]
- logstash-logback-encoder 8.0 docs: `net.logstash.logback.encoder.LogstashEncoder` — default JSON fields require no custom configuration

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- **PARTIAL HALT (2026-06-21):** `VideoController.java` does not exist — Epic 3 (Story 3-3) must be implemented before Task 4 (VideoController logging) can be completed. Tasks 1 (health probe verify), 2 (logback-spring.xml), 3 (PlaylistController logging), and 5 (GlobalExceptionHandler verify) are complete. Resuming after Epic 3 is done.

### Completion Notes List

### File List

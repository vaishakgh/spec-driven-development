---
baseline_commit: d39500e7c2b6be612c060ad33070eb0f26c9a323
---

# Story 1.1: Initialise Spring Boot Project Scaffold

Status: done

## Story

As an **internal developer**,
I want a runnable Spring Boot Maven project with all required dependencies and WebClient configured,
so that I have a working foundation to build the two API endpoints on.

## Acceptance Criteria

1. **Given** a fresh checkout of the repository
   **When** `mvn spring-boot:run -Dspring-boot.run.profiles=local` is executed
   **Then** the application starts successfully on port 8081 with no errors

2. **And** `pom.xml` declares Java 21, Spring Boot **4.1.0** (see Dev Notes — AC in epic erroneously says 3.3.x), `spring-boot-starter-web`, `spring-boot-starter-webflux` (for WebClient), and `springdoc-openapi-starter-webmvc-ui:3.0.3`

3. **And** a `WebClient` bean is configured in `WebClientConfig.java` with base URL read from `${youtube.api.base-url}` config property (NOT hardcoded)

4. **And** no API key value is hardcoded anywhere in any source-controlled file

## Tasks / Subtasks

- [x] Task 1 — Bootstrap Maven project with Spring Initializr (AC: 1, 2)
  - [x] Run the `spring init` command exactly as specified in Dev Notes
  - [x] Verify the generated `pom.xml` has `spring-boot-starter-parent` 4.1.0

- [x] Task 2 — Add manual dependencies to `pom.xml` (AC: 2)
  - [x] Add `springdoc-openapi-starter-webmvc-ui:3.0.3` (runtime scope)
  - [x] Add `logstash-logback-encoder` latest stable (runtime scope) — enables JSON logging in Epic 5
  - [x] Add `wiremock-standalone` latest stable (test scope) — enables WireMock in Epic 6
  - [x] Add `openapi-generator-maven-plugin` latest stable compatible with Spring Boot 4.x — configure plugin in Story 1.2, declare dep here
  - [x] Confirm `pom.xml` compiles: `mvn compile`

- [x] Task 3 — Configure `application.yml` (AC: 1)
  - [x] Set `server.port: 8081`
  - [x] Enable virtual threads: `spring.threads.virtual.enabled: true`
  - [x] Configure Actuator health probes (liveness + readiness exposure)
  - [x] Set base logging level INFO

- [x] Task 4 — Create `application-local.yml` (AC: 1, 3)
  - [x] Add `youtube.api.base-url: https://www.googleapis.com/youtube/v3`
  - [x] Add `youtube.api.max-results: 25`

- [x] Task 5 — Create `WebClientConfig.java` (AC: 3, 4)
  - [x] Read `${youtube.api.base-url}` via `@Value`
  - [x] Expose a `WebClient` `@Bean` with that base URL
  - [x] **Do NOT wire `${youtube.api.key}` here** — key injection is Story 1.3; service layer wires it as a query param in Stories 2 and 3

- [x] Task 6 — Verify startup (AC: 1)
  - [x] `mvn spring-boot:run -Dspring-boot.run.profiles=local` starts with no errors on port 8081
  - [x] `mvn compile` succeeds with no errors
  - [x] Grep all `src/` and `*.yml`/`*.yaml` files — confirm no hardcoded API key value

## Dev Notes

### CRITICAL: Spring Boot Version Override

> **The epic file says "Spring Boot 3.3.x". This is WRONG. Use Spring Boot 4.1.0.**

The architecture (ADR recorded in `docs/4-architect-artifacts/architecture/02-starter-template-evaluation.md`) explicitly selected **Spring Boot 4.1.0** (GA June 10, 2026) because Spring Boot 3.5.x reaches OSS EOL June 30, 2026 — days after this migration completes. The architecture overrides the epic AC.

Impact of Spring Boot 4.x on this project (none of these apply):
- Gradle 8.14+ required → Not affected (Maven build)
- Undertow removed → Not affected (embedded Tomcat)
- OkHttp3 removed → Not affected (WebClient/Reactor Netty)
- Spring Security 7 CSRF defaults → Not affected (no security layer)
- `javax.*` → `jakarta.*` → Already done in Spring Boot 3.3.x; 4.x continues Jakarta EE 11

### Spring Initializr Command (copy-paste exact)

```bash
spring init \
  --build=maven \
  --java-version=21 \
  --boot-version=4.1.0 \
  --dependencies=web,webflux,actuator,validation \
  --group-id=com.example \
  --artifact-id=youtube-playlist-api \
  --name=youtube-playlist-api \
  --description="YouTube Playlist API — MuleSoft to Spring Boot Migration" \
  youtube-playlist-api
```

Alternatively: `https://start.spring.io` with the same selections.

This produces the Maven wrapper (`mvnw`, `mvnw.cmd`, `.mvn/wrapper/`), `YoutubePlaylistApiApplication.java` with `@SpringBootApplication`, and the base `pom.xml`.

### Complete `pom.xml` Dependency List

| Dependency | GroupId | ArtifactId | Version | Scope |
|---|---|---|---|---|
| Spring Boot BOM | `org.springframework.boot` | `spring-boot-starter-parent` | `4.1.0` | parent |
| Web MVC | `org.springframework.boot` | `spring-boot-starter-web` | managed | compile |
| WebFlux (WebClient) | `org.springframework.boot` | `spring-boot-starter-webflux` | managed | compile |
| Actuator | `org.springframework.boot` | `spring-boot-starter-actuator` | managed | compile |
| Validation | `org.springframework.boot` | `spring-boot-starter-validation` | managed | compile |
| **Swagger UI** | `org.springdoc` | `springdoc-openapi-starter-webmvc-ui` | **`3.0.3`** | compile |
| **JSON logging** | `net.logstash.logback` | `logstash-logback-encoder` | latest stable | compile |
| **WireMock** | `org.wiremock` | `wiremock-standalone` | latest stable | **test** |
| Test runner | `org.springframework.boot` | `spring-boot-starter-test` | managed | test |

**openapi-generator-maven-plugin** (build plugin, not a dependency):
- Add to `<build><plugins>` section
- GroupId: `org.openapitools`, ArtifactId: `openapi-generator-maven-plugin`, Version: latest stable compatible with Spring Boot 4.x / Spring Framework 7
- **Do NOT add the `<configuration>` block yet** — that is Story 1.2 work. In this story just declare the plugin with version.

### `application.yml` (complete)

```yaml
server:
  port: 8081

spring:
  application:
    name: youtube-playlist-api
  threads:
    virtual:
      enabled: true

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

logging:
  level:
    root: INFO
```

### `application-local.yml` (minimum for Story 1.1)

```yaml
youtube:
  api:
    base-url: https://www.googleapis.com/youtube/v3
    max-results: 25
```

> **Note:** `application-dev.yml`, `application-prod.yml`, and `application-test.yml` are created in Story 1.3. Only `application-local.yml` is needed to pass Story 1.1 AC (local profile startup).

### `WebClientConfig.java` (complete implementation)

```java
package com.example.youtubeplaylistapi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${youtube.api.base-url}")
    private String baseUrl;

    @Bean
    public WebClient webClient() {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }
}
```

**Do NOT add `${youtube.api.key}` here.** The API key is passed as a query param in service layer code (Stories 2 and 3). The `WebClient` bean only holds the base URL. Key injection as env var is wired in Story 1.3.

### Architecture Constraints Relevant to This Story

**Package root:** `com.example.youtubeplaylistapi` — all classes must live under this root.

**Port:** 8081 — same as the existing Mule service; consumer URL compatibility depends on this.

**Virtual threads:** MUST be enabled (`spring.threads.virtual.enabled=true`). This is what makes `.block()` safe in the service layer (Epic 2/3). Without it, blocking WebClient calls risk thread-pool starvation.

**No Spring Security:** Do NOT add `spring-boot-starter-security` to the pom.xml. The service is an internal API with no consumer-facing authentication. Adding Security would lock down all endpoints and break all tests.

**No DevTools:** Do NOT add `spring-boot-devtools`. This is a rewrite from scratch — live reload is not needed.

**No database:** No JPA, no H2, no Flyway. This is a stateless HTTP proxy with zero persistence.

**springdoc-openapi version 3.0.3 is mandatory** — this is the Spring Boot 4.x compatible release. Earlier versions (2.x) are Spring Boot 3.x only. Do not use any other version.

### Files Created in This Story

| File | Type | Notes |
|---|---|---|
| `pom.xml` | UPDATE | Add manual deps; plugin placeholder |
| `mvnw` / `mvnw.cmd` | NEW | Maven wrapper (generated by spring init) |
| `.mvn/wrapper/maven-wrapper.properties` | NEW | Maven version pin (generated) |
| `src/main/java/.../YoutubePlaylistApiApplication.java` | NEW | `@SpringBootApplication` entry point (generated) |
| `src/main/resources/application.yml` | UPDATE | Port 8081, virtual threads, actuator probes |
| `src/main/resources/application-local.yml` | NEW | YouTube base URL + maxResults=25 |
| `src/main/java/.../config/WebClientConfig.java` | NEW | WebClient bean (base URL only) |
| `.gitignore` | UPDATE | Ensure `target/` and `*.env` are excluded |

### Anti-Patterns to Avoid

```java
// ❌ Hardcoding YouTube base URL
WebClient.create("https://www.googleapis.com/youtube/v3")

// ❌ Hardcoding API key
@Value("my-real-api-key-abc123")

// ❌ Adding Spring Security (locks all endpoints)
// spring-boot-starter-security in pom.xml

// ❌ Using Spring Boot 3.x
// <version>3.3.0</version> or any 3.x — use 4.1.0
```

### Project Structure Notes

This story creates the `youtube-playlist-api/` directory alongside the existing `source-mule-youtube-playlist-api/` source (the Mule source reference). The Spring Boot project is a **net-new service** built adjacent to the Mule source, not a modification of it.

Target directory structure after this story:

```
youtube-playlist-api/
├── .mvn/wrapper/maven-wrapper.properties
├── mvnw
├── mvnw.cmd
├── pom.xml
├── .gitignore
└── src/
    └── main/
        ├── java/com/example/youtubeplaylistapi/
        │   ├── YoutubePlaylistApiApplication.java
        │   └── config/
        │       └── WebClientConfig.java
        └── resources/
            ├── application.yml
            └── application-local.yml
```

`src/test/` directory is created by spring init but remains empty until Epic 6.

### References

- Spring Boot version decision: [Source: docs/4-architect-artifacts/architecture/02-starter-template-evaluation.md]
- WebClient pattern + package structure: [Source: docs/4-architect-artifacts/architecture/03-core-architectural-decisions.md#API & Communication Patterns]
- Full project directory tree: [Source: docs/4-architect-artifacts/architecture/05-project-structure-boundaries.md#Complete Project Directory Structure]
- Naming conventions: [Source: docs/4-architect-artifacts/architecture/04-implementation-patterns-consistency-rules.md#Naming Patterns]
- Anti-patterns: [Source: docs/4-architect-artifacts/architecture/04-implementation-patterns-consistency-rules.md#Anti-Patterns to Avoid]
- maxResults default = 25: [Source: docs/4-architect-artifacts/architecture/06-architecture-validation-results.md#Gap Analysis Results]
- FR-19, FR-20 (config profiles, API key): [Source: docs/3-product-manager-artifacts/prd/4-features.md#4.5 Configuration and Secrets Management]
- NFR-4 (512MB heap ceiling — confirms JRE-only final image): [Source: docs/3-product-manager-artifacts/prd/7-non-functional-requirements.md]

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6 (bmad-create-story 2026-06-20)
claude-sonnet-4-6 (bmad-dev-story 2026-06-20)

### Debug Log References

- `spring` CLI not available on machine → created all project files manually (equivalent to `spring init` output)
- Only Java 8 (temurin-8) installed → downloaded Temurin 21.0.5 tar.gz from Adoptium to `~/java/jdk-21.0.5+11/`; downloaded Maven 3.9.9 binary to `~/java/apache-maven-3.9.9/`; no sudo required
- Homebrew `openjdk@21` formula needs Xcode.app (only formula, not cask) → used Adoptium tar.gz instead
- `brew install --cask temurin@21` requires interactive sudo for pkg installer → used tar.gz extraction approach
- `mvn compile` BUILD SUCCESS on first attempt; 2 source files compiled with javac [debug parameters release 21]
- `mvn spring-boot:run -Dspring-boot.run.profiles=local` with `YOUTUBE_API_KEY=test-key-for-startup-check` started on port 8081 in 5.029s — no errors
- SpringDoc WARN messages at startup are expected (prod profile disables these in Story 1.3)
- Secret scan: `grep -r "AIza" src/` → nothing; `grep -r "youtube.api.key = [^$]" src/` → nothing ✓

### Completion Notes List

- Created project as `dest-spring-youtube-playlist-api/` (user-specified directory name, not `youtube-playlist-api/`)
- All 4 ACs verified: port 8081 ✓, pom.xml Spring Boot 4.1.0 + correct deps ✓, WebClient bean with `${youtube.api.base-url}` ✓, no hardcoded API key ✓
- pom.xml versions used: springdoc 3.0.3 (mandated), logstash-logback-encoder 8.0, wiremock-standalone 3.10.0, openapi-generator-maven-plugin 7.10.0 (plugin declared only — config in Story 1.2)
- `springdoc-openapi-starter-webmvc-ui:3.0.3` added as compile scope (not runtime) — springdoc requires compile-time annotation processing
- Java 21 + Maven 3.9.9 available at `~/java/` for subsequent stories; set JAVA_HOME + PATH before running mvn

### File List

- `dest-spring-youtube-playlist-api/pom.xml` (NEW)
- `dest-spring-youtube-playlist-api/mvnw` (NEW)
- `dest-spring-youtube-playlist-api/mvnw.cmd` (NEW)
- `dest-spring-youtube-playlist-api/.mvn/wrapper/maven-wrapper.properties` (NEW)
- `dest-spring-youtube-playlist-api/.gitignore` (NEW)
- `dest-spring-youtube-playlist-api/src/main/resources/application.yml` (NEW)
- `dest-spring-youtube-playlist-api/src/main/resources/application-local.yml` (NEW)
- `dest-spring-youtube-playlist-api/src/main/java/com/example/youtubeplaylistapi/YoutubePlaylistApiApplication.java` (NEW)
- `dest-spring-youtube-playlist-api/src/main/java/com/example/youtubeplaylistapi/config/WebClientConfig.java` (NEW)

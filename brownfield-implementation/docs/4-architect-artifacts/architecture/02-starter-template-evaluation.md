# Starter Template Evaluation

## Primary Technology Domain

Java Backend REST API — bootstrapped via Spring Initializr (`start.spring.io`). No JavaScript/frontend starter template applies; the Spring Initializr scaffold is the canonical equivalent.

## Version Decision

**Selected: Spring Boot 4.1.0** (GA, released June 10, 2026)

Spring Boot 3.5.x reaches OSS EOL on June 30, 2026 — days after this migration is expected to complete. Targeting 4.1.0 avoids immediate post-delivery obsolescence. Impact analysis confirms the Spring Boot 4.x breaking changes do not affect this project:

| Breaking change category | Impact on this project |
|--------------------------|----------------------|
| Gradle 8.14+ required | Not affected — Maven build |
| Undertow removed | Not affected — embedded Tomcat |
| OkHttp3 removed | Not affected — using WebClient/Reactor Netty |
| Spring Security 7 CSRF defaults | Not affected — no security layer |
| AOP starter renamed | Not affected — AOP not used directly |
| javax.* → jakarta.* | Not affected — Spring Boot 3.3.x already migrated to Jakarta EE |

## Selected Starter: Spring Initializr — Spring Boot 4.1.0

**Initialization Command:**

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

Alternatively: configure via `https://start.spring.io` with the same selections.

**Architectural Decisions Provided by Starter:**

**Language & Runtime:**
- Java 21 (LTS) — minimum required by Spring Boot 4.x
- Virtual threads enabled via `spring.threads.virtual.enabled=true` in `application.yml`
- Spring Framework 7.0 (bundled with Spring Boot 4.1.0)
- Jakarta EE 11 (`jakarta.*` package namespace throughout)

**Build Tooling:**
- Maven with `spring-boot-starter-parent` 4.1.0 as parent POM
- `openapi-generator-maven-plugin` added manually for contract-first code generation from OAS 3.0 spec

**Testing Framework:**
- JUnit 5 (bundled via `spring-boot-starter-test`)
- Mockito (bundled)
- WireMock (`wiremock-standalone`) added manually, test scope

**Code Organisation:**
- Standard Maven layout: `src/main/java`, `src/main/resources`, `src/test/java`
- Root package: `com.example.youtubeplaylistapi`
- Spring Boot auto-configuration and component scan from root package

**Development Experience:**
- Spring DevTools excluded (rewrite from scratch — no need for live reload)
- Spring Actuator: liveness at `/actuator/health/liveness`, readiness at `/actuator/health/readiness`

**Additional Dependencies (added manually to `pom.xml` after init):**

| Dependency | GroupId | Version | Purpose |
|-----------|---------|---------|---------|
| `springdoc-openapi-starter-webmvc-ui` | `org.springdoc` | `3.0.3` | Swagger UI + OAS docs (Spring Boot 4.x line) |
| `logstash-logback-encoder` | `net.logstash.logback` | latest stable | Structured JSON logging |
| `wiremock-standalone` | `org.wiremock` | latest stable | WireMock test infrastructure (test scope) |
| `openapi-generator-maven-plugin` | `org.openapitools` | latest stable | Generate controller stubs from OAS 3.0 spec |

**Note:** Project initialization using the above `spring init` command is the first concrete action in Story 1.1 (Initialise Spring Boot Project Scaffold).

# Technology Stack — Refined for youtube-playlist-api

**Date:** 2026-06-19
**Based on:** Actual Mule project analysis + generic research cross-reference

---

## Summary Verdict

The generic analysis recommends **Java 21 + Spring Boot 3.3.x + Apache Camel 4.x** as the target stack. For `youtube-playlist-api`, Apache Camel is not needed — it adds significant dependency weight for zero functional benefit on a 2-endpoint HTTP proxy.

**Recommended stack for this project:**

| Layer | Technology | Rationale |
|---|---|---|
| Runtime | Java 21 (LTS) | Virtual threads benefit I/O-bound proxy; compatible with Spring Boot 3.3.x |
| Framework | Spring Boot 3.3.x | Industry standard; direct equivalent for Mule's HTTP listener/routing |
| HTTP Client | Spring WebClient (Reactor Netty) | Non-blocking HTTP calls to YouTube API; replaces `mule-http-connector` outbound |
| API spec | OpenAPI 3.0 (converted from RAML 1.0) | Replaces RAML; enables code generation |
| API doc | Springdoc OpenAPI (`springdoc-openapi-starter-webmvc-ui`) | Replaces APIKit Console |
| Config | Spring Boot profiles (`application-{profile}.yml`) | Direct equivalent to `config-${env}.yaml` |
| Secrets | Kubernetes Secret → env var | Same pattern as current `${YOUTUBE_API_KEY}` |
| Serialization | Jackson 2.x (bundled with Spring Boot) | Replaces DataWeave JSON output |
| Error handling | `@ControllerAdvice` + `@ExceptionHandler` | Replaces Mule error handler scopes |
| Testing | JUnit 5 + Mockito + WireMock | Replaces MUnit + munit-tools mock-when |
| Logging | Logback + `logstash-logback-encoder` (JSON) | Replaces log4j2; adds structured logging |
| Health | Spring Actuator | Liveness/readiness for Kubernetes |
| Build | Maven (keep existing tooling) | No change needed |
| Containerization | Docker | CloudHub → container |
| Orchestration | Kubernetes | CloudHub → K8s |

---

## Component Mapping: MuleSoft → Spring Boot

| MuleSoft Component | Spring Boot Equivalent | Notes |
|---|---|---|
| `http:listener` (port 8081) | Spring Boot embedded Tomcat/Netty | Port configurable via `server.port` |
| `apikit:router` (RAML-driven) | `openapi-generator` + `@RestController` | Generate stubs from OAS 3.0 spec |
| `apikit:console` | Springdoc Swagger UI | Available at `/swagger-ui.html` |
| `http:request-config` (YouTube HTTPS) | `WebClient.Builder` bean | Base URL: `https://www.googleapis.com/youtube/v3` |
| `http:request` (outbound GET) | `webClient.get().uri(...).retrieve()` | Reactive, non-blocking |
| `ee:transform` (DataWeave) | Java mapper method + Jackson DTOs | See `01-dataweave-complexity-audit.md` |
| `configuration-properties file=config-${env}.yaml` | `spring.profiles.active={env}` → `application-{env}.yml` | 1:1 mapping |
| Mule error handler `on-error-propagate` | `@ControllerAdvice` + `@ExceptionHandler` | One handler class covers all error types |
| `logger` (Mule logger processor) | `@Slf4j` + `log.info(...)` | Same semantics |
| MUnit `mock-when http:request` | WireMock `stubFor(get(...).willReturn(...))` | Exact functional equivalent |
| MUnit `assert-that` | AssertJ `assertThat(...).isEqualTo(...)` | Similar fluent API |
| CloudHub `${env}` property | `SPRING_PROFILES_ACTIVE` env var | Set in K8s pod spec |
| CloudHub `${youtube.api.key}` | `YOUTUBE_API_KEY` env var → Kubernetes Secret | Same env var name can be kept |
| CloudHub deployment | Docker image → Kubernetes Deployment | See deployment guide |

---

## What NOT to Include (Differences from Generic Analysis)

| Generic Analysis Recommendation | Decision for This Project | Rationale |
|---|---|---|
| Apache Camel 4.x | **Not needed** | No EIP patterns, no connector ecosystem needed; plain Spring suffices |
| Spring Integration | **Not needed** | Same rationale as Camel |
| Spring Cloud Gateway | **Not needed** | Not an API gateway; it's a single microservice |
| Kafka | **Not needed** | No messaging in current Mule project |
| Redis | **Not needed** | No caching in current Mule project |
| Spring Batch | **Not needed** | No batch processing |
| Resilience4j circuit breaker | **Optional** | Could add `@CircuitBreaker` around YouTube API calls, but not a migration requirement |
| Testcontainers | **Not needed** | No database or external stateful services; WireMock is sufficient |
| Pact (contract testing) | **Not needed** | No downstream Spring services to contract-test against |
| JOLT | **Not needed** | DataWeave transforms are simple enough for plain Java mapping |
| MapStruct | **Optional** | Only 2 transforms; plain Java methods are cleaner at this scale |
| HashiCorp Vault | **Optional** | K8s Secrets sufficient for a single API key |

---

## Java 21 Virtual Threads Consideration

The generic analysis highlights Java 21 virtual threads as a key migration benefit. For this project:

- The Mule `http:request` to YouTube API is a blocking I/O call inside a Mule reactive runtime.
- With Spring Boot 3.3.x + Java 21 and `spring.threads.virtual.enabled=true`, the Spring Boot equivalent will use virtual threads automatically for `@RestController` methods.
- For a proxy with low concurrent load (expected given MICRO worker sizing), this is a "nice to have" rather than a critical requirement.
- **Recommendation:** Enable virtual threads in the Spring Boot app for simplicity and future headroom. No code change required.

---

## Maven Dependencies (Proposed pom.xml additions)

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.x</version>
</parent>

<dependencies>
    <!-- Core web -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <!-- WebClient (reactive HTTP client) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webflux</artifactId>
    </dependency>
    <!-- Health endpoints -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    <!-- OpenAPI docs / Swagger UI -->
    <dependency>
        <groupId>org.springdoc</groupId>
        <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
        <version>2.x.x</version>
    </dependency>
    <!-- Structured JSON logging -->
    <dependency>
        <groupId>net.logstash.logback</groupId>
        <artifactId>logstash-logback-encoder</artifactId>
        <version>7.x</version>
    </dependency>
    <!-- Test -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.wiremock</groupId>
        <artifactId>wiremock-standalone</artifactId>
        <version>3.x.x</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

---

## OpenAPI Migration Path

The RAML 1.0 spec (`youtube-playlist-api.raml`) is well-structured. Migration path:

1. **Convert RAML to OpenAPI 3.0:**
   - Tool: `raml2openapi` CLI or Anypoint Studio "Export as OAS" feature
   - Expected output: `youtube-playlist-api.yaml` (OAS 3.0)

2. **Generate Spring Boot stubs:**
   - Use `openapi-generator-maven-plugin` with `spring` generator
   - Configure: `interfaceOnly=true`, `useSpringBoot3=true`, `useJakartaEe=true`

3. **Implement generated interfaces:**
   - Each generated controller interface maps to one `@RestController` implementation class

4. **Key RAML → OAS field mappings:**
   - `baseUri` → `servers[0].url`
   - `types` → `components.schemas`
   - `traits.commonErrors` → `components.responses` (reused across endpoints)
   - `uriParameters` → `parameters` (path params)
   - RAML `datetime` → OAS `string` with `format: date-time`
   - RAML `string | nil` → OAS `string` with `nullable: true`

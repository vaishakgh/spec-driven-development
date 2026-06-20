---
baseline_commit: d39500e7c2b6be612c060ad33070eb0f26c9a323
---

# Story 1.3: Configure Multi-Environment Profiles and API Key Injection

Status: done

## Story

As an **internal developer**,
I want distinct Spring Boot configuration profiles for local, dev, production, and test environments with the YouTube API key injected via environment variable,
so that the service behaves correctly in each environment and no secrets appear in source control.

## Acceptance Criteria

1. **Given** `application-local.yml` is active
   **When** the service starts
   **Then** `maxResults` is set to 25 and the YouTube base URL is `https://www.googleapis.com/youtube/v3`

2. **Given** `application-prod.yml` is active
   **When** the service starts
   **Then** `maxResults` is set to 50 and Swagger UI / API docs are disabled

3. **Given** `application-test.yml` is active
   **When** the service starts
   **Then** a static placeholder API key is used so tests run without a real YouTube connection

4. **Given** `YOUTUBE_API_KEY` is set in the environment
   **When** the service starts on any profile
   **Then** the key is available for service layer injection and no API key value appears in any source-controlled file (FR-20)

5. **Given** `YOUTUBE_API_KEY` is NOT set in the environment
   **When** the service attempts to start (on any non-test profile)
   **Then** the application fails to start with a clear configuration error: `Could not resolve placeholder 'YOUTUBE_API_KEY'`

## Tasks / Subtasks

- [x] Task 1 — Update `application.yml` with the youtube.api base config block (AC: 4, 5)
  - [x] Add `youtube.api.key: ${YOUTUBE_API_KEY}` (no default — forces startup failure when env var missing)
  - [x] Add `youtube.api.base-url: https://www.googleapis.com/youtube/v3` as base default
  - [x] Add `youtube.api.max-results: 25` as base default
  - [x] Verify no API key value (string literal) appears in the file

- [x] Task 2 — Update `application-local.yml` (AC: 1)
  - [x] Profile already has `base-url` and `max-results` from Story 1.1 — no changes needed if base `application.yml` covers them
  - [x] Verify maxResults=25 resolves correctly when local profile is active

- [x] Task 3 — Create `application-dev.yml` (AC: 4)
  - [x] Set `youtube.api.base-url` and `youtube.api.max-results: 25`
  - [x] API key resolves from base `application.yml` via `${YOUTUBE_API_KEY}` — no override needed

- [x] Task 4 — Create `application-prod.yml` (AC: 2)
  - [x] Set `youtube.api.max-results: 50`
  - [x] Disable springdoc: `springdoc.api-docs.enabled: false` and `springdoc.swagger-ui.enabled: false`
  - [x] API key resolves from base `application.yml` via `${YOUTUBE_API_KEY}`

- [x] Task 5 — Create `application-test.yml` (AC: 3)
  - [x] Set `youtube.api.key: test-api-key-placeholder` — overrides `${YOUTUBE_API_KEY}` so tests run without env var
  - [x] Set `youtube.api.base-url: http://localhost:8089` — WireMock host (Story 6.1 finalises port)
  - [x] Set `youtube.api.max-results: 25`

- [x] Task 6 — Update `WebClientConfig.java` to inject API key (AC: 5)
  - [x] Add `@Value("${youtube.api.key}") private String apiKey;` field
  - [x] This field is NOT used in the WebClient bean yet — its purpose is to force Spring to resolve the property at startup
  - [x] Service layer (Stories 2 and 3) will also inject `@Value("${youtube.api.key}")` directly in their service classes
  - [x] Run `mvn spring-boot:run -Dspring-boot.run.profiles=local` WITH `YOUTUBE_API_KEY` set → starts OK
  - [x] Run without env var → confirm startup fails with property resolution error

- [x] Task 7 — Regression check
  - [x] `mvn compile` still succeeds
  - [x] `mvn spring-boot:run -Dspring-boot.run.profiles=local` (with `YOUTUBE_API_KEY` set) starts on port 8081
  - [x] Grep all `src/` YAML/properties files for any literal API key value — must find none

## Dev Notes

### Previous Story Context

- **Story 1.1** created: `application.yml` (port, virtual threads, actuator), `application-local.yml` (base-url, max-results=25), `WebClientConfig.java` (base URL injection only)
- **Story 1.2** created: OAS spec, openapi-generator config in pom.xml, `OpenApiConfig.java`

**Files this story modifies:** `application.yml` (update), `application-local.yml` (minimal update), `WebClientConfig.java` (update). **Creates:** `application-dev.yml`, `application-prod.yml`, `application-test.yml`.

### Mule Source Config — Exact Values Being Migrated

Source: `source-mule-youtube-playlist-api/src/main/resources/properties/`

| Mule Property | Local | Dev | Prod | Spring Boot Key |
|---|---|---|---|---|
| `youtube.api.baseUrl` | `www.googleapis.com` | `www.googleapis.com` | `www.googleapis.com` | `youtube.api.base-url` |
| `youtube.api.key` | `${YOUTUBE_API_KEY}` | `${YOUTUBE_API_KEY}` | `${YOUTUBE_API_KEY}` | `youtube.api.key: ${YOUTUBE_API_KEY}` |
| `youtube.api.maxResults` | `25` | `25` | `50` | `youtube.api.max-results` |
| `http.listener.port` | `8081` | `8081` | `8081` | `server.port: 8081` (already in application.yml) |

**Key naming change:** Mule uses camelCase (`baseUrl`, `maxResults`). Spring Boot must use kebab-case (`base-url`, `max-results`). Spring normalises both, but the architecture mandates kebab-case in YAML.

**Base URL change:** Mule stores just `www.googleapis.com` (host) and constructs the full URL in the connector. Spring Boot stores the full base URL including path: `https://www.googleapis.com/youtube/v3`. This matches what `WebClientConfig` already expects (Story 1.1).

### Complete File Contents

#### `application.yml` (UPDATE — add youtube.api block)

Add the following to the existing `application.yml` content from Story 1.1:

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

youtube:
  api:
    base-url: https://www.googleapis.com/youtube/v3
    key: ${YOUTUBE_API_KEY}
    max-results: 25
```

> **`${YOUTUBE_API_KEY}` has NO default value.** Spring will throw `IllegalArgumentException: Could not resolve placeholder 'YOUTUBE_API_KEY' in value "${YOUTUBE_API_KEY}"` at startup if the env var is not set. This satisfies AC 5.

#### `application-local.yml` (MINIMAL UPDATE)

Story 1.1 already set `base-url` and `max-results` here. With Story 1.3 moving those to `application.yml` as defaults, the local profile file can be simplified. Keep it as-is or simplify to remove redundant keys — both work. The profile-level value overrides the base value.

Recommended final state:
```yaml
# Local dev profile — overrides base application.yml
# youtube.api.base-url and max-results inherited from application.yml
# YOUTUBE_API_KEY must be set in your local environment (or .env file — do NOT commit)
```

If you prefer explicit local values for clarity:
```yaml
youtube:
  api:
    base-url: https://www.googleapis.com/youtube/v3
    max-results: 25
```

Either is correct. Do NOT add `youtube.api.key` with any value here.

#### `application-dev.yml` (NEW)

```yaml
# Dev environment profile
# Deployed via Kubernetes; YOUTUBE_API_KEY injected from K8s Secret
youtube:
  api:
    base-url: https://www.googleapis.com/youtube/v3
    max-results: 25
```

#### `application-prod.yml` (NEW)

```yaml
# Production environment profile
# Deployed via Kubernetes; YOUTUBE_API_KEY injected from K8s Secret
youtube:
  api:
    max-results: 50

springdoc:
  api-docs:
    enabled: false
  swagger-ui:
    enabled: false
```

> `springdoc` disablement is the only cross-cutting change in prod. Do NOT use `@ConditionalOnProperty` or `@Profile` annotations — YAML-only gating per architecture rules.

#### `application-test.yml` (NEW)

```yaml
# Test profile — used by @SpringBootTest with profiles={"test"}
# Overrides YOUTUBE_API_KEY requirement with static placeholder
# base-url points to WireMock (Story 6.1 finalises port and stub setup)
youtube:
  api:
    base-url: http://localhost:8089
    key: test-api-key-placeholder
    max-results: 25
```

> **Note for Story 6.1:** The WireMock server must bind to port 8089 (or this URL must be updated to match). WireMock stubs for service calls will use paths relative to this base, e.g. `/playlistItems` and `/videos`. The `test-api-key-placeholder` value will appear in WireMock stub query param matching — stubs should use `equalTo("test-api-key-placeholder")` for the `key` param.

### `WebClientConfig.java` (UPDATE — add apiKey injection)

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

    // Injected to enforce startup validation — if YOUTUBE_API_KEY env var is missing,
    // Spring fails here with a clear property resolution error.
    // Service layer classes (PlaylistService, VideoService) inject this directly
    // via @Value("${youtube.api.key}") in their own Stories (2.x, 3.x).
    @Value("${youtube.api.key}")
    private String apiKey;

    @Bean
    public WebClient webClient() {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }
}
```

**Why `apiKey` is injected here but not used in the `webClient()` bean:**
- Spring instantiates `WebClientConfig` at startup and resolves all `@Value` fields
- If `${youtube.api.key}` can't be resolved (env var missing), Spring throws immediately — satisfying AC 5
- The WebClient bean itself doesn't need the key — the key is passed as a query param in each service call
- Service classes will use `@Value("${youtube.api.key}")` directly (Stories 2 and 3)

**Why NOT add a default value like `${YOUTUBE_API_KEY:}` (empty default):**
- An empty default would suppress the startup failure
- `YOUTUBE_API_KEY=` (set but empty) should still fail — empty key is invalid
- AC 5 requires failure when unset — no default enforces this

### Architecture Compliance

**Property key format:** All YAML keys use kebab-case per architecture mandate:
```yaml
# Correct
youtube.api.base-url
youtube.api.max-results

# Wrong — Mule used camelCase; do not copy
youtube.api.baseUrl       ❌
youtube.api.maxResults    ❌
```

**Secret injection path (FR-20, NFR-5):**
```
K8s Secret
    └──→ YOUTUBE_API_KEY env var
              └──→ ${YOUTUBE_API_KEY} in application.yml
                        └──→ @Value("${youtube.api.key}") in WebClientConfig + PlaylistService + VideoService
```

No API key value ever touches:
- Any `.yml` or `.properties` file (except `application-test.yml` which uses a dummy placeholder)
- Any Java source file as a string literal
- Any `.gitignore`-bypassed file

**Profile activation in Kubernetes (for reference):**
- `SPRING_PROFILES_ACTIVE=prod` environment variable (set in K8s Deployment or ConfigMap)
- This activates `application-prod.yml` which sets `max-results: 50` and disables Swagger

**Profile activation locally:**
- `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` — as established in Story 1.1
- Or: `SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run`
- With `YOUTUBE_API_KEY=<any-value>` set in shell or `.env` (never commit `.env`)

### Anti-Patterns to Avoid

```yaml
# ❌ Never put a real or default API key in any YAML
youtube:
  api:
    key: "AIzaSy..."                # commits secret to source control
    key: ${YOUTUBE_API_KEY:}        # empty default hides missing key
    key: ${YOUTUBE_API_KEY:dummy}   # fallback hides missing key in prod

# ❌ Never use camelCase YAML keys
youtube:
  api:
    baseUrl: ...    # wrong — use base-url
    maxResults: 25  # wrong — use max-results
```

```java
// ❌ Never hardcode or log the API key
private String apiKey = "AIzaSy...";          // hardcoded
log.info("Using key: {}", apiKey);            // logged — violates NFR-5
log.debug("apiKey={}", apiKey);               // also logged
```

### Files Created/Modified in This Story

| File | Type | Notes |
|---|---|---|
| `src/main/resources/application.yml` | UPDATE | Add `youtube.api.*` block with `${YOUTUBE_API_KEY}` |
| `src/main/resources/application-local.yml` | UPDATE | Minimal — verify existing entries, no key value |
| `src/main/resources/application-dev.yml` | NEW | Dev profile; base-url + max-results=25 |
| `src/main/resources/application-prod.yml` | NEW | Prod profile; max-results=50 + springdoc disabled |
| `src/main/resources/application-test.yml` | NEW | Test profile; placeholder key + WireMock URL |
| `src/main/java/.../config/WebClientConfig.java` | UPDATE | Add `@Value("${youtube.api.key}") private String apiKey;` |

### Post-Epic 1 State Verification

After this story, all three Epic 1 stories are done. The following should be true:

- `mvn spring-boot:run -Dspring-boot.run.profiles=local` with `YOUTUBE_API_KEY=anything` → starts on port 8081 ✓
- `mvn spring-boot:run` without `YOUTUBE_API_KEY` → fails with property error ✓
- No API key value in any source file (`grep -r "AIza" src/`) → finds nothing ✓
- `mvn compile` succeeds ✓
- `target/generated-sources/openapi/` contains `PlaylistApi.java`, `VideoApi.java`, contract DTOs ✓
- OAS spec at `src/main/resources/api/youtube-playlist-api.yaml` ✓

### References

- FR-19, FR-20 (multi-environment config, API key via env var): [Source: docs/3-product-manager-artifacts/prd/4-features.md#4.5 Configuration and Secrets Management]
- NFR-5 (no secrets in source control): [Source: docs/3-product-manager-artifacts/prd/7-non-functional-requirements.md]
- Configuration file table: [Source: docs/4-architect-artifacts/architecture/05-project-structure-boundaries.md#File Organisation Patterns]
- Swagger UI profile gating: [Source: docs/4-architect-artifacts/architecture/04-implementation-patterns-consistency-rules.md#Swagger UI Profile Gating]
- YOUTUBE_API_KEY injection path: [Source: docs/4-architect-artifacts/architecture/03-core-architectural-decisions.md#Authentication & Security]
- Mule source configs: `source-mule-youtube-playlist-api/src/main/resources/properties/config-{local,dev,prod}.yaml`

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6 (bmad-create-story 2026-06-20)
claude-sonnet-4-6 (bmad-dev-story 2026-06-20)

### Debug Log References

- AC1 verified: `java -jar` started in 13.294s on port 8081 with `YOUTUBE_API_KEY=test-key-ac1` and local profile ✓
- AC5 verified: `PlaceholderResolutionException: Could not resolve placeholder 'YOUTUBE_API_KEY' in value "${YOUTUBE_API_KEY}" <-- "${youtube.api.key}"` followed by BUILD FAILURE ✓
- Secret scan: `grep -r "AIza" src/` → nothing; `grep -rE "key: [^$]" application*.yml` → only `test-api-key-placeholder` in test profile ✓
- `mvn compile` BUILD SUCCESS — 10 source files compiled ✓

### Completion Notes List

- All 5 ACs satisfied: local profile starts ✓, prod disables springdoc ✓, test uses placeholder key ✓, key from env var only ✓, startup fails without key ✓
- `application-local.yml` retains explicit `base-url` and `max-results` values for local dev clarity (both options valid per story spec)
- `application-prod.yml` disables springdoc via YAML only (no `@Profile` annotations) per architecture mandate
- `application-test.yml` WireMock port 8089 — Story 6.1 must bind WireMock to this port and use `test-api-key-placeholder` in stub `key` param matching

### File List

- `dest-spring-youtube-playlist-api/src/main/resources/application.yml` (UPDATE — added youtube.api block with key/base-url/max-results)
- `dest-spring-youtube-playlist-api/src/main/resources/application-local.yml` (UPDATE — explicit values retained, comment added)
- `dest-spring-youtube-playlist-api/src/main/resources/application-dev.yml` (NEW)
- `dest-spring-youtube-playlist-api/src/main/resources/application-prod.yml` (NEW)
- `dest-spring-youtube-playlist-api/src/main/resources/application-test.yml` (NEW)
- `dest-spring-youtube-playlist-api/src/main/java/com/example/youtubeplaylistapi/config/WebClientConfig.java` (UPDATE — added apiKey @Value field)

## Senior Developer Review (AI)

**Review Date:** 2026-06-20
**Scope:** Epic 1 combined review — Stories 1.1, 1.2, 1.3
**Outcome:** Changes Requested

### Action Items

- [x] [Review][Defer] Empty `YOUTUBE_API_KEY` (set but blank) bypasses startup validation — `${YOUTUBE_API_KEY}` resolves to `""` when env var is exported empty; app starts but all YouTube API calls fail at runtime with 401. AC5 covers "not set" only. Deferred: validate key at service layer where it is first used in API calls (Stories 2.x/3.x). [Story 1.3 — `application.yml` / `WebClientConfig.java`]
- [x] [Review][Patch] `logstash-logback-encoder` declared at compile scope — should be `<scope>runtime</scope>`; encoder is a Logback appender, never imported directly by application code. [Story 1.1 — `pom.xml`] ✅ Fixed
- [x] [Review][Patch] `sprint-status.yaml` shows story `1-3` as `in-progress` but story file says `review` — status write was incomplete when session ended. [Story 1.3 — `docs/5-scrum-impl-artifacts/sprint-status.yaml`] ✅ Fixed
- [x] [Review][Defer] OAS `servers` array only contains `http://localhost:8081` — no production/staging server entries; Swagger UI will always point at localhost. Defer to Epic 5 (Observability). [Story 1.2 — `api/youtube-playlist-api.yaml`]
- [x] [Review][Defer] Inconsistent inline examples across OAS error responses — `401`/`404` have inline examples, `500`/`503` do not. Low-priority documentation gap. Defer to OAS cleanup. [Story 1.2 — `api/youtube-playlist-api.yaml`]
- [x] [Review][Defer] No `.env.example` for local developer onboarding — no machine-readable template listing required env vars. Defer to Epic 7 / docs. [Story 1.1 — repo root]
- [x] [Review][Defer] WireMock port 8089 hardcoded in `application-test.yml` — Story 6.1 must bind WireMock to this exact port or tests will silently fail. Forward dependency documented; Story 6.1 owns resolution. [Story 1.3 — `application-test.yml`]
- [x] [Review][Defer] `mvn clean` then `mvn compile` fails until `generate-sources` has run — standard Maven code-gen behaviour; CI/CD should use `mvn verify` not bare `mvn compile`. Document in contributor guide. [Story 1.2 — build]
- [x] [Review][Defer] `pageToken` query parameter has no length/format constraints in OAS spec — any string accepted; YouTube API validates downstream. Future hardening opportunity. [Story 1.2 — `api/youtube-playlist-api.yaml`]

### Review Follow-ups (AI)

- [ ] [AI-Review] Resolve decision: empty `YOUTUBE_API_KEY` behaviour (High — blocks service correctness)
- [ ] [AI-Review] Fix `logstash-logback-encoder` scope to `runtime` in `pom.xml` (Low — correctness)
- [ ] [AI-Review] Fix `sprint-status.yaml` story 1-3 status to `review` (Low — housekeeping)

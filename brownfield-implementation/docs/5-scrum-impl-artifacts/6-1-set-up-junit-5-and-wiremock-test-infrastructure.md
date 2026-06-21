# Story 6.1: Set Up JUnit 5 and WireMock Test Infrastructure

Status: ready-for-dev

## Story

As an **internal developer**,
I want a JUnit 5 + WireMock test infrastructure configured for the `test` Spring profile,
so that all subsequent test stories have a working harness that intercepts outbound YouTube API calls without requiring a real network connection.

## Acceptance Criteria

1. **Given** `wiremock-standalone:3.10.0` is present in `pom.xml` test scope **When** `mvn test` is executed with the `test` profile **Then** the WireMock server starts on port 8089 and the WebClient base URL is pointed at it via `application-test.yml`
2. **Given** the `test` profile is active **When** any test runs **Then** no real outbound HTTP calls are made to `googleapis.com` **And** the static `test-api-key-placeholder` from `application-test.yml` is used
3. **Given** the test infrastructure shell classes are in place with no @Test methods **When** `mvn test` is run **Then** the build completes successfully with 0 failures

## Tasks / Subtasks

- [ ] Verify no pom.xml changes needed — `wiremock-standalone:3.10.0` already present (AC: 1)
- [ ] Create `src/test/java/com/example/youtubeplaylistapi/PlaylistEndpointTest.java` shell (AC: 1, 2, 3)
  - [ ] Class annotations: `@WireMockTest(httpPort = 8089)`, `@SpringBootTest(webEnvironment = RANDOM_PORT)`, `@ActiveProfiles("test")`
  - [ ] Fields: `@LocalServerPort int port`, `@Autowired TestRestTemplate restTemplate`
  - [ ] No @Test methods yet
- [ ] Create `src/test/java/com/example/youtubeplaylistapi/VideoEndpointTest.java` shell (AC: 3)
  - [ ] Same class annotations as PlaylistEndpointTest
  - [ ] No @Test methods yet
- [ ] Create `src/test/java/com/example/youtubeplaylistapi/ErrorHandlingTest.java` shell (AC: 3)
  - [ ] Same class annotations as PlaylistEndpointTest
  - [ ] No @Test methods yet
- [ ] Run `./mvnw test` to confirm 0 tests, 0 failures (AC: 3)

## Dev Notes

### Pre-existing Infrastructure — No Changes Needed

**pom.xml already has WireMock:**
```xml
<dependency>
    <groupId>org.wiremock</groupId>
    <artifactId>wiremock-standalone</artifactId>
    <version>3.10.0</version>
    <scope>test</scope>
</dependency>
```

**application-test.yml already exists at `src/main/resources/application-test.yml`:**
```yaml
youtube:
  api:
    base-url: http://localhost:8089    # WireMock fixed port
    key: test-api-key-placeholder
    max-results: 25
```
This is intentionally in `src/main/resources/` (not `src/test/resources/`). Do not move it — the project convention places it here.

**Existing unit tests already present — DO NOT touch these:**
```
src/test/java/com/example/youtubeplaylistapi/
├── controller/PlaylistControllerTest.java    # MockMvc standalone unit test
├── service/PlaylistServiceTest.java          # @WireMockTest service unit test
├── mapper/PlaylistMapperTest.java            # pure JUnit 5 unit test
└── exception/GlobalExceptionHandlerTest.java # pure JUnit 5 unit test
```

### Test Infrastructure Pattern

The integration tests (created in Stories 6.2 and 6.3) use a different pattern from the existing unit tests:
- **Existing unit tests:** MockMvc standalone OR `@WireMockTest` without Spring context — fast, no application startup
- **Integration tests (Epic 6):** `@SpringBootTest(RANDOM_PORT)` + `@WireMockTest(httpPort = 8089)` — full application stack, end-to-end through servlet layer

The two test styles coexist. `mvn test` runs all of them.

### Integration Test Class Template

All three shell classes follow the same pattern:

```java
package com.example.youtubeplaylistapi;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

@WireMockTest(httpPort = 8089)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PlaylistEndpointTest {   // or VideoEndpointTest, ErrorHandlingTest

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    // Story 6.2 will add @Test methods here
}
```

**Why `@WireMockTest(httpPort = 8089)` (fixed port) instead of dynamic port:**
- `application-test.yml` hardcodes `youtube.api.base-url: http://localhost:8089`
- The `WebClientConfig` bean is initialized once at Spring context startup with this URL
- Fixed port 8089 ensures WireMock and the Spring context agree without needing `@DynamicPropertySource`
- Test parallelism is not enabled for this project — no port conflict risk

**Why `TestRestTemplate` instead of `WebTestClient`:**
- The primary web stack is Spring MVC (servlet), not WebFlux
- `TestRestTemplate` is auto-configured by Spring Boot for `RANDOM_PORT` test contexts
- It has the correct base URL (`http://localhost:{port}`) built in when `@Autowired`
- Call paths relative to root: `restTemplate.getForEntity("/api/youtube/playlists/PLtest", String.class)`

**Why `@ActiveProfiles("test")`:**
- Activates `application-test.yml` overlay (disables real `YOUTUBE_API_KEY` requirement)
- Without it, Spring fails to start because `${YOUTUBE_API_KEY}` env var is not set in CI/local test environment

### WireMock Stub Imports (for Stories 6.2 and 6.3)

```java
import com.github.tomakehurst.wiremock.client.WireMock.*;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

// Stubs are declared inline in test methods:
stubFor(get(urlPathEqualTo("/playlistItems"))
    .withQueryParam("playlistId", equalTo("PLtest"))
    .willReturn(aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody(PLAYLIST_SUCCESS_JSON)));
```

Stubs are **reset automatically between tests** by `@WireMockTest`. Define them inline per test method — no shared setup in `@BeforeEach` unless identical for all tests.

`WireMockRuntimeInfo` can be injected as a test method parameter if needed for assertions (e.g., `verify(getRequestedFor(...))`):
```java
@Test
void should_something(WireMockRuntimeInfo wmInfo) {
    // wmInfo.getHttpBaseUrl() == "http://localhost:8089"
}
```

### Project Structure Notes

- NEW: `src/test/java/com/example/youtubeplaylistapi/PlaylistEndpointTest.java`
- NEW: `src/test/java/com/example/youtubeplaylistapi/VideoEndpointTest.java`
- NEW: `src/test/java/com/example/youtubeplaylistapi/ErrorHandlingTest.java`
- NO changes to: `pom.xml`, `application-test.yml`, any existing test classes, any production code
- Integration test classes are in the ROOT test package (not in sub-packages like `controller/` or `service/`)

### References

- [Source: docs/3-product-manager-artifacts/epics-and-stories/epic-6-test-suite-quality-gate.md#Story 6.1]
- [Source: docs/4-architect-artifacts/architecture/04-implementation-patterns-consistency-rules.md#Structure Patterns]
- [Source: docs/4-architect-artifacts/architecture/05-project-structure-boundaries.md#Test Organisation]
- wiremock-standalone 3.10.0: `@WireMockTest(httpPort = N)` starts WireMock on fixed port N; stubs reset between tests; `WireMockRuntimeInfo` injectable as method parameter

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

### Completion Notes List

### File List

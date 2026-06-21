# Deferred Work

## Deferred from: code review of Epic 1 (Stories 1.1, 1.2, 1.3) (2026-06-20)

- **Empty `YOUTUBE_API_KEY` (set but blank) bypasses startup validation** — `application.yml` / `WebClientConfig.java` — `${YOUTUBE_API_KEY}` resolves to `""` when exported empty; app starts but YouTube API calls fail at runtime with 401. Deferred: validate key at service layer where it is first used in API calls (Stories 2.x/3.x).

- **OAS servers array only contains localhost** — `api/youtube-playlist-api.yaml` — no production/staging server entries; Swagger UI always points at localhost. Recommended follow-up: Epic 5 (Observability & API Explorer).

- **Inconsistent inline examples across OAS error responses** — `api/youtube-playlist-api.yaml` — `401`/`404` responses have inline examples; `500`/`503` do not. Low-priority documentation gap. Recommended follow-up: OAS cleanup pass before Epic 5.

- **No `.env.example` for local developer onboarding** — repo root — no machine-readable template listing required env vars (`YOUTUBE_API_KEY`, etc.). Recommended follow-up: Epic 7 deployment story or a standalone docs task.

- **WireMock port 8089 hardcoded in `application-test.yml`** — `src/main/resources/application-test.yml` — Story 6.1 must bind WireMock to port 8089 and use `test-api-key-placeholder` in stub `key` param matching. Forward dependency acknowledged; Story 6.1 owns resolution.

- **`mvn clean` then `mvn compile` fails until `generate-sources` has run** — build — standard Maven code-generation lifecycle; CI/CD pipelines should use `mvn verify` or `mvn generate-sources compile` rather than bare `mvn compile`. Document in contributor guide (Epic 7 or standalone).

- **`pageToken` query parameter has no length/format constraints in OAS spec** — `api/youtube-playlist-api.yaml` — any string accepted and forwarded to YouTube API which validates downstream. Future hardening opportunity: add `maxLength` and possibly a `pattern` constraint to prevent extreme input forwarding.

## Deferred from: code review of 4-1 and 4-2 (2026-06-21)

- **`NoHandlerFoundException` handler may never fire** — `GlobalExceptionHandler.java:27` — Spring Boot's resource handler intercepts before MVC 404 dispatch even with `spring.web.resources.add-mappings: false`; in practice `NoResourceFoundException` covers all 404 paths. Handler is harmless but dead. Recommended follow-up: verify and remove in Epic 6 (test suite).

- **Unit-only tests — no `@WebMvcTest` integration layer** — `GlobalExceptionHandlerTest.java` — handlers are tested by direct instantiation; full MVC content-negotiation pipeline (serialization, `Accept` header processing, `@RestControllerAdvice` registration) not exercised. Recommended follow-up: Epic 6 WireMock/integration tests cover the contract end-to-end.

- **406 content-negotiation paradox** — `GlobalExceptionHandler.java:47` — returning a JSON body when the client declared it does not accept `application/json` is self-contradictory; Spring may send the response anyway or suppress the body. Per-spec design decision: consistent `ErrorResponse` shape takes precedence. Recommended follow-up: validate behavior with integration test in Epic 6.

- **`NoHandlerFoundException` handler branch untested** — `GlobalExceptionHandlerTest.java:37` — `should_return_404_when_route_not_found` exercises `NoResourceFoundException` only; the `NoHandlerFoundException` path in the `@ExceptionHandler` array has no dedicated test. Recommended follow-up: add test in Epic 6 or a future story-4.1 patch.

- **`MethodArgumentNotValidException` handler branch untested** — `GlobalExceptionHandlerTest.java:84` — `should_return_400` uses `MissingServletRequestParameterException`; `MethodArgumentNotValidException` (the other member of the multi-handler array) is not exercised. Recommended follow-up: add test in Epic 6.

- **`UpstreamConnectivityException` is dead code** — `UpstreamConnectivityException.java` — class was created as a stub in Story 4.1 and Story 4.2 does not register a handler for it (architecture says `WebClientRequestException` propagates naturally). Class is unused and confusing. Recommended follow-up: remove in Epic 2/3 service implementation or when it becomes clear it will never be used.

- **`Exception` catch-all may intercept Spring-internal 4xx exceptions** — `GlobalExceptionHandler.java:112` — `ResponseStatusException` and similar Spring-internal exceptions not included in specific handlers could be caught by the `Exception` catch-all, returning HTTP 500 instead of the intended 4xx status. Recommended follow-up: audit in Epic 6; add `ResponseStatusException` handler if integration tests surface the issue.

- **`WebClientRequestException` reactive-to-servlet propagation path untested end-to-end** — `GlobalExceptionHandler.java:90` — it is unclear whether a Reactor Netty `WebClientRequestException` actually propagates across the reactive/servlet boundary and reaches `GlobalExceptionHandler` in a blocking Servlet (non-reactive) context. Unit test calls handler directly, bypassing the real dispatch path. Recommended follow-up: WireMock integration test in Epic 6 (T07 — YouTube 503 scenario).

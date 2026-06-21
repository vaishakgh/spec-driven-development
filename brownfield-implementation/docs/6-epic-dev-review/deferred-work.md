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

## Deferred from: code review of Epic 2 stories (2026-06-21)

- **[Story 2.1] Null Integer in pageInfo** — YouTube rarely returns null `totalResults`/`resultsPerPage` but it is possible; `setTotalResults(null)` would serialize as null in the API response. Consider adding null-safe defaults if this becomes a runtime issue. [`PlaylistMapper.java:221`]

- **[Story 2.2] Virtual thread blocking concern** — `.block()` on virtual threads is speculative; no virtual thread executor is configured in this project. Low risk; revisit if/when virtual threads are adopted. [`PlaylistService.java:119`]

- **[Story 2.2] Non-401 YouTube errors (400, 403, 429) fall through to 500** — By design per Epic 4 architecture. Epic 4 established the catch-all 500 for unhandled upstream statuses. Targeted handling of 403/429 is a future enhancement if needed. [`PlaylistService.java:116`]

- **[Story 2.2] API key visible in URI debug logs** — Standard behavior for YouTube API integrations; the key appears in WebClient request URIs which can surface in logs at DEBUG level. A dedicated security hardening story should address key masking if log aggregation to external systems is added. [`PlaylistService.java`]

- **[Story 2.3] playlistId format not validated** — No format or length check on the `{playlistId}` path variable. YouTube playlists start with `PL`. Out of Epic 2 scope; consider adding validation in Epic 7 or a dedicated hardening story. [`PlaylistController.java:23`]

## Deferred from: code review of Epic 3 + Epic 5 (2026-06-21)

- **[HIGH] `.block()` on potentially reactive thread** [`VideoService.java:115`] — Project-wide pattern identical to `PlaylistService`; no reactive dispatch configured; revisit if reactive stack is adopted.
- **[HIGH] YouTube non-401 error statuses (403, 429, 500) not intercepted** [`VideoService.java:112–113`] — Consistent with PlaylistService; GlobalExceptionHandler returns HTTP 500 for unhandled `WebClientResponseException`; expanding error mapping is deliberate future scope.
- **[MED] No videoId path parameter validation** [`VideoController.java`] — OAS-generated interface; Spring routing handles binding; explicit validation not required by story spec; consider if raised in a security review.
- **[MED] ConnectionTimeout not configured in WebClientConfig** [`WebClientConfig.java`] — Pre-existing, shared by both services; add connection-level timeout in a dedicated hardening story.
- **[LOW] Whitespace-only pageToken silently becomes null** [`PlaylistController.java`] — Pre-existing PlaylistController behaviour; add `.trim()` + blank check in a future validation story.
- **[LOW] pageToken regex allows unlimited length** [`PlaylistController.java`] — Pre-existing; no upper bound on pageToken length; low exploitability risk; worth capping in a future validation story.
- **[LOW] videoUrl built with potentially null item.getId()** [`VideoMapper.java:247`] — YouTube API guarantees `id` on any returned item; outer empty-list guard covers absent videos; review if API contract changes.

## Deferred from: code review of Epic 6 (Stories 6.1–6.3) (2026-06-21)

- **[Story 6.1] Empty `ErrorHandlingTest` shell covers nothing** — intentional per spec; reserved for future error scenarios (403, timeout, 400 validation). Expand when scenarios are defined.
- **[Story 6.1] Fixed WireMock port 8089 parallel-execution bind conflict** — Maven Surefire sequential default makes this safe; revisit if parallel class execution is enabled in CI.
- **[Story 6.2] `key=test-api-key-placeholder` stub assertion couples test to config value** — intentional; `application-test.yml` controls the value. Revisit if test key management changes.
- **[Story 6.2] `pageToken` stub (Test 5) doesn't constrain all mandatory upstream query params** — per-test WireMock reset ensures isolation; add full param assertions in future test hardening pass.
- **[Story 6.2] String `contains()` assertions brittle to serializer changes** — acceptable trade-off for integration level; upgrade to typed DTO assertions in a future test quality pass.
- **[Story 6.2] Empty-playlist and 401 stubs missing `part`/`key` param assertions** — per-test reset mitigates; low priority hardening for future.
- **[Story 6.3] YouTube HTTP 403 falls through to 500 catch-all** — pre-existing `GlobalExceptionHandler` gap; add dedicated 403 handler and integration test in a future hardening story.
- **[Story 6.3] Missing integration coverage: video 401, video 503 fault, `pageToken` 400, response timeout** — valid next-level tests beyond Story 6.3 scope; add in a future Epic 6 extension or standalone test story.
- **[Story 6.3] `should_return_500_when_youtube_returns_5xx` covers only status 500** — all YouTube 5xx → Spring 500 via catch-all; rename or add status-range coverage in a future test pass.

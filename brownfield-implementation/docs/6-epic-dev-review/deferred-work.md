# Deferred Work

## Deferred from: code review of Epic 1 (Stories 1.1, 1.2, 1.3) (2026-06-20)

- **Empty `YOUTUBE_API_KEY` (set but blank) bypasses startup validation** — `application.yml` / `WebClientConfig.java` — `${YOUTUBE_API_KEY}` resolves to `""` when exported empty; app starts but YouTube API calls fail at runtime with 401. Deferred: validate key at service layer where it is first used in API calls (Stories 2.x/3.x).

- **OAS servers array only contains localhost** — `api/youtube-playlist-api.yaml` — no production/staging server entries; Swagger UI always points at localhost. Recommended follow-up: Epic 5 (Observability & API Explorer).

- **Inconsistent inline examples across OAS error responses** — `api/youtube-playlist-api.yaml` — `401`/`404` responses have inline examples; `500`/`503` do not. Low-priority documentation gap. Recommended follow-up: OAS cleanup pass before Epic 5.

- **No `.env.example` for local developer onboarding** — repo root — no machine-readable template listing required env vars (`YOUTUBE_API_KEY`, etc.). Recommended follow-up: Epic 7 deployment story or a standalone docs task.

- **WireMock port 8089 hardcoded in `application-test.yml`** — `src/main/resources/application-test.yml` — Story 6.1 must bind WireMock to port 8089 and use `test-api-key-placeholder` in stub `key` param matching. Forward dependency acknowledged; Story 6.1 owns resolution.

- **`mvn clean` then `mvn compile` fails until `generate-sources` has run** — build — standard Maven code-generation lifecycle; CI/CD pipelines should use `mvn verify` or `mvn generate-sources compile` rather than bare `mvn compile`. Document in contributor guide (Epic 7 or standalone).

- **`pageToken` query parameter has no length/format constraints in OAS spec** — `api/youtube-playlist-api.yaml` — any string accepted and forwarded to YouTube API which validates downstream. Future hardening opportunity: add `maxLength` and possibly a `pattern` constraint to prevent extreme input forwarding.

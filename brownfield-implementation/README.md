# Brownfield Implementation — YouTube Playlist API Migration

## What This Project Is

This workspace documents and executes a **brownfield MuleSoft → Spring Boot migration** of an internal YouTube Playlist API service.

The existing service runs on **Mule 4.6.0 / Anypoint Platform / CloudHub** and exposes 2 GET endpoints that proxy the YouTube Data API v3. The goal is to rewrite it as a **Spring Boot 4.1.0 / Java 21** service that is functionally equivalent, passes shadow-mode parity validation, and allows the Mule service to be decommissioned.

The migration is executed using the **BMad spec-driven development method** — a structured, AI-agent-assisted workflow that takes a project from analysis through architecture, story creation, implementation, and review, one phase at a time with a human in the loop at every gate.

**Scope:** 2 GET endpoints · 7 MUnit tests → 7 JUnit 5 + WireMock tests · 7 Epics · 19 Stories

## Migration: Source → Destination

| | Source | | Destination |
|---|---|---|---|
| **Project** | [`source-mule-youtube-playlist-api/`](./source-mule-youtube-playlist-api/) | → | [`dest-spring-youtube-playlist-api/`](./dest-spring-youtube-playlist-api/) |
| **Runtime** | Mule 4.6.0 / Anypoint Platform / CloudHub | → | Spring Boot 4.1.0 / Java 21 / Docker |
| **Contract** | RAML 1.0 | → | OAS 3.0 (openapi-generator, interfaceOnly) |
| **HTTP Client** | Mule HTTP Connector | → | WebClient + virtual threads |
| **Error Handling** | 11 Mule error scopes | → | Single `GlobalExceptionHandler` |
| **Tests** | 7 MUnit tests | → | 7 JUnit 5 + WireMock tests |
| **Deployment** | CloudHub (manual via Anypoint) | → | Kubernetes (shadow mode gate before cutover) |

---

## Project Status

| Phase | Status | Gate |
|---|---|---|
| 1 — Analysis | ✅ Complete | — |
| 2 — Planning (PRD) | ✅ Complete | — |
| 3 — Solutioning (Architecture + Epics) | ✅ Complete | Implementation Readiness: READY |
| 4 — Implementation | 🔄 In Progress | Epic 1 ✅ done · Next: Epic 4 (Error Handling) |

**Sprint status:** [`docs/5-scrum-impl-artifacts/sprint-status.yaml`](./docs/5-scrum-impl-artifacts/sprint-status.yaml)

| Epic | Stories | Status |
|---|---|---|
| Epic 1 — Project Foundation & API Contract | 3 / 3 | ✅ Done |
| Epic 4 — Error Handling | 0 / 2 | ⬜ Backlog |
| Epic 2 — Playlist Retrieval | 0 / 3 | ⬜ Backlog |
| Epic 3 — Video Detail Retrieval | 0 / 3 | ⬜ Backlog |
| Epic 5 — Observability & API Explorer | 0 / 2 | ⬜ Backlog |
| Epic 6 — Test Suite & Quality Gate | 0 / 3 | ⬜ Backlog |
| Epic 7 — Deployment, Shadow Mode & Cutover | 0 / 3 | ⬜ Backlog |

**Master index for AI agent context:** [docs/index.md](./docs/index.md)

---

## Session Log

---

## ━━━ Phase 1 — Analysis ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

### 👤 Mary — Business Analyst

#### Session 1 · 2026-06-19: Technical Research — MuleSoft to Spring Boot Migration

Comprehensive feasibility study covering migration patterns, DataWeave effort, TCO impact, and stack selection across the MuleSoft ecosystem.

**Key Findings (Generic Research):**
- **Recommended stack:** Java 21 + Spring Boot 3.3.x + Apache Camel 4.x + Spring Cloud Gateway
- **Migration strategy:** Strangler Fig — domain by domain, gateway first, shadow mode before every cutover
- **DataWeave effort:** 30–40% of total migration effort; use MapStruct, JOLT, AI-assisted translation
- **Cost impact:** 40–70% TCO reduction after eliminating MuleSoft licensing and CloudHub
- **Effort range:** 2–4 months (small), 6–12 months (medium), 12–30 months (large)

#### Session 2 · 2026-06-19: Project Documentation Scan + Re-Analysis — youtube-playlist-api

Deep scan of the actual source project. All generic research findings overridden by project-specific facts.

**Key Re-Analysis Findings:**
- **Target stack:** Java 21 + Spring Boot 4.1.0 + WebClient — Apache Camel NOT needed (no EIP patterns)
- **DataWeave complexity:** SIMPLE for both transforms — ~5–10% of effort (not 30–40%)
- **Migration strategy:** Direct rewrite + shadow mode validation (not Strangler Fig)
- **Effort:** 7–10 working days (below the "Small" threshold)
- **Tests:** 7 MUnit tests → 7 JUnit 5 + WireMock tests (1:1 migration)

**Decisions that feed PRD:**
1. `GET /song/INVALID` returns **HTTP 404** (breaking change from Mule's 200-with-nulls)
2. `pageToken` input parameter added to OAS 3.0 spec
3. Internal API only — cutover via shadow mode gate

#### Artifacts · [`docs/1-analysis-artifacts/`](./docs/1-analysis-artifacts/) · [`docs/2-re-analysis-artifact/`](./docs/2-re-analysis-artifact/)

| # | Document | Content |
|---|---|---|
| — | [Analysis index](./docs/1-analysis-artifacts/index.md) | Navigation for all 14 research sections |
| 03 | [Executive Summary](./docs/1-analysis-artifacts/03-executive-summary.md) | Top 5 recommendations, TCO impact |
| 05 | [Technology Stack Analysis](./docs/1-analysis-artifacts/05-technology-stack-analysis.md) | Java 21, Spring Boot, DataWeave migration strategies |
| 09 | [Technical Recommendations](./docs/1-analysis-artifacts/09-technical-research-recommendations.md) | Phased roadmap, stack, success KPIs |
| — | [Re-analysis index](./docs/2-re-analysis-artifact/index.md) | All project-specific overrides and PRD inputs |
| 00 | [Project Inventory](./docs/2-re-analysis-artifact/00-project-inventory.md) | Mule flows, connectors, DW transforms, tests, configs |
| 02 | [Migration Strategy](./docs/2-re-analysis-artifact/02-migration-strategy-refined.md) | Direct rewrite + shadow mode; 8-step plan |
| 06 | [Test Migration Plan](./docs/2-re-analysis-artifact/06-test-migration-plan.md) | All 7 MUnit tests mapped to JUnit 5 + WireMock |

---

## ━━━ Phase 2 — Planning ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

### 👤 John — Product Manager

#### Session 3 · 2026-06-19: PRD — youtube-playlist-api Spring Boot Migration

Full PRD produced via coached discovery. 22 Functional Requirements, 5 Non-Functional Requirements.

#### Session 4 · 2026-06-19: Epics and Stories

7 Epics and 19 Stories covering the complete migration scope.

**Key PRD Decisions:**
- FR-7: `GET /youtube/song/{videoId}` returns HTTP 404 when video not found (consumer-visible breaking change)
- `pageToken` query parameter added (was in RAML but not exposed in the Mule flow)
- Shadow mode parity gate is a hard prerequisite before any traffic cutover (NFR-3)
- API key injected via `YOUTUBE_API_KEY` environment variable — never in source control (FR-20)

#### Artifacts · [`docs/3-product-manager-artifacts/`](./docs/3-product-manager-artifacts/)

| File | Description |
|---|---|
| [prd/index.md](./docs/3-product-manager-artifacts/prd/index.md) | PRD (sharded — 12 sections) |
| [prd-artifacts/.decision-log.md](./docs/3-product-manager-artifacts/prd-artifacts/.decision-log.md) | PRD decision audit trail |
| [epics-and-stories/index.md](./docs/3-product-manager-artifacts/epics-and-stories/index.md) | 7 Epics, 19 Stories |

---

## ━━━ Phase 3 — Solutioning ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

### 👤 Winston — System Architect

#### Session 5 · 2026-06-19: Implementation Readiness Check

**Verdict: READY FOR IMPLEMENTATION** — 22/22 FRs covered, 5/5 NFRs covered, 0 critical issues, 0 major issues.

#### Session 6 · 2026-06-19: Architecture Decision Document

Full architecture produced through 8-step collaborative workflow.

**Key Architecture Decisions:**
- **Runtime:** Spring Boot 4.1.0 + Java 21 (Spring Boot 3.5.x reaches OSS EOL June 30, 2026)
- **API contract:** OAS 3.0 + `openapi-generator-maven-plugin` (`interfaceOnly=true`)
- **HTTP client:** WebClient with `.block()` — safe with Java 21 virtual threads
- **Error handling:** Single `GlobalExceptionHandler` (`@RestControllerAdvice`) replacing all 11 Mule error scopes
- **API docs:** springdoc-openapi 3.0.3; Swagger UI enabled local/dev only, disabled in prod via YAML
- **Container:** Docker multi-stage — `eclipse-temurin:21-jdk-alpine` → `eclipse-temurin:21-jre-alpine`
- **Implementation order:** Epic 1 → Epic 4 → Epics 2 & 3 (parallel) → Epic 5 → Epic 6 → Epic 7

#### Artifacts · [`docs/4-architect-artifacts/`](./docs/4-architect-artifacts/)

| File | Description |
|---|---|
| [architecture/index.md](./docs/4-architect-artifacts/architecture/index.md) | Architecture document (sharded — 6 sections) |
| [architecture/02-starter-template-evaluation.md](./docs/4-architect-artifacts/architecture/02-starter-template-evaluation.md) | Spring Boot 4.1.0 version decision; `spring init` command |
| [architecture/03-core-architectural-decisions.md](./docs/4-architect-artifacts/architecture/03-core-architectural-decisions.md) | All critical/important decisions with code snippets |
| [architecture/04-implementation-patterns-consistency-rules.md](./docs/4-architect-artifacts/architecture/04-implementation-patterns-consistency-rules.md) | Naming conventions, anti-patterns, consistency rules |
| [architecture/05-project-structure-boundaries.md](./docs/4-architect-artifacts/architecture/05-project-structure-boundaries.md) | Full annotated file tree; Epic 1–7 file mapping |
| [architecture/06-architecture-validation-results.md](./docs/4-architect-artifacts/architecture/06-architecture-validation-results.md) | 16/16 checklist ✅; READY FOR IMPLEMENTATION |
| [implementation-readiness-report/index.md](./docs/4-architect-artifacts/implementation-readiness-report/index.md) | IR report — 22/22 FRs, 5/5 NFRs; 0 critical, 0 major |

---

## ━━━ Phase 4 — Implementation ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

### 👤 Amelia — Senior Developer

#### Session 7 · 2026-06-20: Sprint Planning

Sprint tracking initialised from epics. `sprint-status.yaml` generated covering all 7 epics and 19 stories.

#### Session 8 · 2026-06-20: Epic 1 — Project Foundation & API Contract

All three Epic 1 stories implemented and code reviewed.

**Story 1.1 — Initialise Spring Boot Project Scaffold** ✅ `done`
- `dest-spring-youtube-playlist-api/` created with Spring Boot 4.1.0, Java 21, Maven
- Port 8081, virtual threads enabled, Actuator health probes, WebClient bean
- `mvn spring-boot:run -Dspring-boot.run.profiles=local` starts cleanly on port 8081

**Story 1.2 — Convert RAML 1.0 Spec to OAS 3.0 and Generate Controller Stubs** ✅ `done`
- OAS 3.0 spec produced from RAML; full paths `/api/youtube/...` correctly included
- `openapi-generator-maven-plugin:7.10.0` with `interfaceOnly=true`, `openApiNullable=false`
- Generates `PlaylistApi`, `VideoApi` interfaces + 4 contract DTOs; `mvn compile` succeeds

**Story 1.3 — Configure Multi-Environment Profiles and API Key Injection** ✅ `done`
- Profiles: `local`, `dev`, `prod` (max-results=50, Swagger disabled), `test` (WireMock + placeholder key)
- `YOUTUBE_API_KEY` env var required at startup; app fails with clear error if unset

**Code Review — Epic 1** ✅ `done`
- 2 patches applied: `logstash-logback-encoder` scope → `runtime`; sprint-status sync fix
- 7 items deferred to later epics (WireMock port, OAS examples, `.env.example`, etc.)
- Decision: empty `YOUTUBE_API_KEY` validation deferred to service layer (Stories 2.x/3.x)

#### Artifacts · [`docs/5-scrum-impl-artifacts/`](./docs/5-scrum-impl-artifacts/) · [`docs/6-epic-dev-review/`](./docs/6-epic-dev-review/)

| File | Description |
|---|---|
| [sprint-status.yaml](./docs/5-scrum-impl-artifacts/sprint-status.yaml) | All story statuses — live tracker |
| [1-1-initialise-spring-boot-project-scaffold.md](./docs/5-scrum-impl-artifacts/1-1-initialise-spring-boot-project-scaffold.md) | Story 1.1 — done |
| [1-2-convert-raml-1-0-spec-to-oas-3-0-and-generate-controller-stubs.md](./docs/5-scrum-impl-artifacts/1-2-convert-raml-1-0-spec-to-oas-3-0-and-generate-controller-stubs.md) | Story 1.2 — done |
| [1-3-configure-multi-environment-profiles-and-api-key-injection.md](./docs/5-scrum-impl-artifacts/1-3-configure-multi-environment-profiles-and-api-key-injection.md) | Story 1.3 — done · includes Epic 1 code review findings |
| [deferred-work.md](./docs/6-epic-dev-review/deferred-work.md) | Deferred findings from Epic 1 code review |

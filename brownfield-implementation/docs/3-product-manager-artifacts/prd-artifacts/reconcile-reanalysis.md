# Reconciliation: Re-Analysis vs PRD

**Date:** 2026-06-19
**PRD:** docs/prds/prd-artifacts/prd.md
**Sources checked:**
- `00-project-inventory.md`
- `07-gaps-analysis.md`
- `02-migration-strategy-refined.md`

---

## Covered (no gap)

- FR-1 through FR-6: Playlist and PlaylistItem field contracts match the DataWeave transform inventory in `00-project-inventory.md` exactly.
- FR-7: "Video not found returns HTTP 404" — the behaviour correction is captured and the breaking-change notification requirement is addressed in §9.
- FR-8 through FR-15: All 8 error status codes (400, 401, 404, 405, 406, 415, 500, 503) and the `{ error, message, code }` ErrorResponse shape match the APIKit error handler table in `00-project-inventory.md`.
- FR-3 / FR-4: Pagination via `pageToken` and environment-configurable `maxResults` (25/50 per profile) — confirmed against `00-project-inventory.md` §5 and noted gap in `07-gaps-analysis.md`.
- FR-16 through FR-18: RAML → OAS 3.0 conversion and Swagger UI in non-prod environments — confirmed covered.
- FR-19 / FR-20: Four environment profiles (local, dev, prod, test) with `YOUTUBE_API_KEY` env var — matches `00-project-inventory.md` §5 exactly.
- FR-21 / FR-22: Spring Actuator liveness/readiness endpoints and structured logging — confirmed as additions identified in `02-migration-strategy-refined.md` §Deployment Target.
- NFR-1 through NFR-5: Latency, test parity, zero-downtime cutover, 512 MB memory, no secrets in source control — all confirmed against source documents.
- §9 rollout and cutover sequence: shadow mode as hard gate, 48-hour monitoring, CloudHub decommission, Mule source archived — consistent with `02-migration-strategy-refined.md` Step 5 and Step 6.
- §5 non-goals: No Camel, no caching, no rate limiting, no write operations, no new endpoints — directly derived from `07-gaps-analysis.md` sections 03 and 05.
- §6.2 deferred items: consumer auth, response caching, `maxResults` override, HPA/KEDA, Prometheus/Grafana — consistent with `07-gaps-analysis.md` assessments.

---

## Gaps Found

- **[Gap 1] `youtube.api.playlistId` unused CloudHub property is unaddressed.**
  Source: `00-project-inventory.md` §5 (property table) and `07-gaps-analysis.md` (gap table, row "youtube.api.playlistId unused property"). The inventory identifies that `youtube.api.playlistId` is declared as a CloudHub deploy-time property in `pom.xml` but is never referenced in any flow. The PRD has no requirement or explicit decision noting that this property should be dropped in the Spring Boot configuration and why. Without this, a story author may carry it forward unnecessarily or ask questions during implementation.
  Missing from: PRD §4.5 (Configuration and Secrets Management) and the Assumptions Index.

- **[Gap 2] Log4j2 → Logback migration is not stated as a requirement.**
  Source: `07-gaps-analysis.md` (gap table, row "Log4j2 → Logback migration") and `02-migration-strategy-refined.md` Step 2 ("Set up logging: Logback with JSON structured logging"). The source inventory confirms the current Mule service uses log4j2 with an unstructured pattern format (`00-project-inventory.md` §9). The migration to Logback and the adoption of structured JSON log output is identified as a concrete migration action. The PRD's FR-22 only states that structured logging must exist; it does not note that the logging framework itself is changing, that the current format is unstructured, or that the format change (plain text → JSON) is intentional and constitutes an observable operational difference.
  Missing from: PRD §4.6 (Observability) and §9 (Rollout — operational considerations).

- **[Gap 3] Kubernetes Secret as the secrets-management target for `YOUTUBE_API_KEY` is unspecified.**
  Source: `02-migration-strategy-refined.md` §Deployment Target ("Spring Boot profiles + env vars via K8s ConfigMap/Secret") and §Considerations for the PRD ("use Kubernetes Secret"). The PRD's FR-20 correctly states the key is injected via `YOUTUBE_API_KEY` env var and must not appear in source-controlled files, but does not state the mechanism by which the env var is populated in Kubernetes. This is a behavioral/operational constraint: if the deployment uses a plain ConfigMap rather than a Secret, the NFR-5 "no secrets in source control" requirement is not violated but the secrets are still stored in plaintext in the cluster. The source documents flag this explicitly; the PRD is silent on it.
  Missing from: PRD §4.5 (FR-20) and §7 (NFR-5).

- **[Gap 4] Shadow mode traffic routing mechanism is unspecified.**
  Source: `02-migration-strategy-refined.md` Step 5 ("Route 5–10% of traffic to Spring Boot (or replicate requests via sidecar)"). The PRD §9 declares shadow mode is a hard gate and that responses must be compared for parity, but provides no requirement for how shadow traffic is routed — percentage split, request mirroring, sidecar, or replay. For a PRD that will drive story creation, this leaves the acceptance criteria for "shadow mode confirmed" undefined. The source document provides the concrete mechanism (5–10% or sidecar mirroring) that the PRD omits.
  Missing from: PRD §9 (Rollout and Cutover) and NFR-3.

- **[Gap 5] Alternative deployment targets (non-Kubernetes) are not acknowledged.**
  Source: `02-migration-strategy-refined.md` §Deployment Target ("Alternative to Kubernetes for this small project: Docker Compose (dev), Render / Railway / Fly.io"). The PRD §1 and §6 reference Kubernetes as the target platform without acknowledging that the source analysis explicitly notes simpler alternatives are viable if the service is not part of a larger K8s estate. This is a constraint that may affect Open Question #3 (which cluster/namespace). The PRD presents Kubernetes as the only option, whereas the source document treats it as conditional.
  Missing from: PRD §1 (Vision), §9, and Open Questions.

- **[Gap 6] Inbound port (8081) is not carried forward as a migration constraint.**
  Source: `00-project-inventory.md` §4 ("HTTP Listener: Host 0.0.0.0, Port 8081"). The current Mule service listens on port 8081. The PRD correctly preserves the `/api/*` base path (FR-17) but does not state what port the Spring Boot service should listen on, nor whether port 8081 must be preserved for internal callers or whether port mapping is handled by the Kubernetes ingress layer. In a K8s environment the container port is abstracted, but this is a deployment configuration constraint that belongs at minimum in an open question.
  Missing from: PRD §4.5 (FR-19) or §10 (Open Questions).

- **[Gap 7] The `part` query parameters sent to the YouTube API are not specified as migration constraints.**
  Source: `00-project-inventory.md` §2 Flow 2 (`part=snippet,contentDetails` for playlistItems) and Flow 3 (`part=snippet,contentDetails,statistics` for videos). These `part` values are not configuration — they are hard-coded behavioral requirements that determine which YouTube API response fields are available for mapping. If the wrong `part` values are passed upstream, required response fields (`statistics.viewCount`, `contentDetails.duration`) are absent and the SongDetail or PlaylistItem contracts cannot be fulfilled. The PRD defines the response field contracts (FR-2, FR-6) but does not specify the upstream API parameters that are necessary to satisfy them.
  Missing from: PRD §4.1 (FR-1) and §4.2 (FR-5) — could be captured as testable consequences.

---

## Minor / Low Priority

- **[Minor 1] The 7 MUnit test scenarios are listed in `00-project-inventory.md` §7** with their exact flow-under-test and scenario names, but the PRD (§6.1, NFR-2) only references "7 MUnit test scenarios" without naming them. Story authors porting tests would benefit from knowing that test #6 (`test-get-youtube-song-not-found`) represents the 200-with-nulls scenario that maps to the corrected FR-7 behaviour, since it is the scenario most likely to require non-trivial assertion changes.

- **[Minor 2] The PRD's SM-3 success metric ("≤ 0% payload divergence")** may be too strict as written — zero divergence is unachievable if the FR-7 correction (404 vs 200-with-nulls for unknown video IDs) is intentional. The source documents confirm this is a deliberate breaking change, so SM-3 should either exclude this error path from the parity comparison or specify that parity is measured for all paths except the FR-7 correction. This is an internal consistency issue rather than a gap from source documents.

- **[Minor 3] `02-migration-strategy-refined.md` explicitly recommends using `openapi-generator-maven-plugin`** to generate controller stubs from the OAS spec as Step 1 of the migration. The PRD's FR-16 requires the OAS 3.0 spec to exist and be validated but does not specify whether code generation from the spec is required or optional. This is implementation detail that belongs in the addendum, but the PRD could be more explicit that the OAS spec is the code-generation source of truth, not just documentation.

- **[Minor 4] `00-project-inventory.md` §6 records the current base URI as `https://youtube-playlist-api.cloudhub.io`**. The PRD §4.4 (FR-17) requires the base path `/api/*` to be preserved but does not address what the new base URI (hostname) will be in Kubernetes, nor whether the CloudHub hostname is used by internal callers and must be replaced with a DNS alias or ingress hostname. This feeds Open Question #3 but is not explicitly listed there.

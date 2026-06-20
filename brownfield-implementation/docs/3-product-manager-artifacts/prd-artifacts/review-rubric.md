# PRD Quality Review — youtube-playlist-api Migration

## Overall verdict

A well-executed, scope-disciplined migration PRD that earns its formalism for most dimensions; the chief weaknesses are a thin Open Questions section that does not surface the two genuinely hard decisions (shadow-mode tooling and the Kubernetes deployment target), one Assumptions Index formatting inconsistency that would block a clean machine-parse, and minor FR consequence gaps in the error-handling section that leave three requirements untestable as written.

---

## 1. Decision-readiness — adequate

The PRD honestly surfaces trade-offs (cost vs. migration risk, FR-7 breaking-change notification, rollback plan) and makes the critical latency-regression threshold explicit in NFR-1/SM-4. The cutover sequence in §9 is concrete and actionable. The rollback posture (Mule stays live until step 5) is a real decision, not a platitude.

Two gaps prevent a "strong" rating:

### Findings

- **high** Shadow-mode tooling is unspecified (§9) — The PRD mandates shadow mode as a "hard gate" but never names the mechanism: traffic mirroring? Replay harness? Manual side-by-side curl? A decision-maker cannot approve the cutover plan without knowing what "response comparison" means operationally. *Fix:* Add a shadow-mode mechanism note — even one sentence: "Shadow mode will be implemented via [Diffy / manual replay / traffic mirror] against the named Kubernetes cluster."

- **high** Open Question #3 (Kubernetes cluster/namespace) is genuinely open and blocks the deployment story (§10) — The PRD acknowledges it but treats it as a three-item footnote rather than a gate. If the namespace is not resolved before story creation, the Deployment phase estimate (1 day, addendum) may be wildly wrong. *Fix:* Promote OQ-3 to a pre-story-creation blocker; annotate it with an owner and a resolution deadline.

- **medium** Breaking-change notification lacks a target (§9, §4.2 FR-7) — "The internal team must be notified" names no owner, no communication channel, and no confirmation mechanism. Open Question #1 asks about lead time but not about who sends the notification. *Fix:* Add: "Owner: [PM/EM name]. Channel: [Slack channel / email]. Confirmation: team lead acknowledges in writing before cutover proceeds."

- **low** YouTube API quota question (OQ-2) is not framed as a gate — It reads as informational. If the quota is insufficient post-migration the CloudHub cancellation (SM-1) cannot safely proceed. *Fix:* Add: "If quota is insufficient, defer cancellation until a higher quota tier is approved."

---

## 2. Substance over theater — strong

The Vision (§1) is a single factual paragraph — no aspirational language, no market sizing, no stakeholder-feel-good phrasing. It states exactly what the service does and exactly why it is being migrated. The four motivations are concrete and ordered by impact.

The Target User (§2) correctly identifies that there is no consumer-facing UX and provides exactly one user journey (UJ-1), which is the only journey that exists. The Non-Users list does real work: it explicitly blocks future requests to add external consumers or UI flows.

The NFRs are grounded. NFR-1 has a measurable regression threshold (20%). NFR-4 ties the memory limit to the existing MICRO worker allocation — it is not a made-up number. NFR-2 ties test coverage to a concrete count (7 MUnit scenarios).

### Findings

- **medium** NFR-3 (zero-downtime cutover) is stated as a policy, not a measurable requirement (§7) — "Traffic must not be fully switched… until shadow mode validation has confirmed response parity" is a process rule, not an NFR with a consequence. It is enforced by the cutover sequence in §9, but the NFR itself has no testable consequence. *Fix:* Either add a consequence (e.g., "Verified by shadow-mode comparison report signed off by [role] before traffic switch") or move this to §9 as a process gate rather than an NFR.

- **low** SM-3's "≤ 0% payload divergence" is likely a typo or overclaim (§8) — Zero divergence across all error-path combinations is an extremely high bar; even byte-ordering of JSON keys could cause divergence depending on the comparison tool. If this is intentional, the comparison methodology must be specified. If it means "no semantic divergence," say that. *Fix:* Clarify: "≤ 0% semantic payload divergence (field-level JSON comparison, field order ignored)" or relax to a defensible threshold.

---

## 3. Strategic coherence — strong

The thesis is clear and stated once: replace a vendor runtime for a two-endpoint proxy without changing the observable contract, except for two named deliberate exceptions. Every FR traces to that thesis. The Non-Goals (§5) actively defend the thesis against scope creep: no caching, no rate limiting, no new endpoints, no EIP framework.

The success metrics validate the thesis at multiple levels: SM-1 (cost objective), SM-2 (parity), SM-3 (parity gate), SM-4 (latency), SM-5 (consumer impact). The counter-metrics are a genuine differentiator — they prevent optimising speed over parity.

### Findings

- **low** FR-16 through FR-18 (§4.4) are implementation-adjacent, not behavioral — The OpenAPI spec conversion is a means to an end (contract governance), and FR-16's consequence ("The OAS 3.0 spec is generated from the existing RAML 1.0 spec") is a process step, not a behavioral consequence testable against the running service. *Fix:* Reframe FR-16's consequences as runtime-observable: "A GET to `/v3/api-docs` returns a valid OAS 3.0 document listing both endpoints." The conversion process detail belongs in the addendum.

---

## 4. Done-ness clarity — adequate

The majority of FRs have testable consequences written at the right level of specificity. FR-1, FR-2, FR-3, FR-5, FR-6, FR-7 are particularly clean — a test author could write assertions directly from the consequence bullets without interpretation.

Three FRs in §4.3 have no consequences at all (FR-11, FR-12, FR-13), which creates an inconsistency in the error-handling section that is otherwise well-specified.

### Findings

- **high** FR-11, FR-12, FR-13 have no testable consequences (§4.3) — These three FRs state the requirement but provide no consequence bullets, unlike every surrounding FR. An engineer could implement any HTTP 404/405/406/415 body shape and claim compliance. *Fix:* Add consequence bullets consistent with the ErrorResponse contract already established in FR-8. Example for FR-11: "A request to `/api/nonexistent` returns HTTP 404 and an ErrorResponse with `code: 404`."

- **medium** FR-15 (HTTP 500) has no consequence (§4.3) — Same gap as above. The requirement states "any unhandled error returns HTTP 500 and an ErrorResponse" but does not specify testable triggering conditions or the expected body. *Fix:* Add: "An unhandled exception (e.g., NullPointerException in a mapper) returns HTTP 500 and an ErrorResponse with `code: 500` and a non-empty `message`."

- **medium** FR-21 (health endpoints) does not specify failure behavior (§4.6) — The consequences specify what the endpoints return when healthy, but not what they return when unhealthy. Kubernetes liveness/readiness probes depend on the failure response (typically HTTP 503/DOWN) being well-defined. *Fix:* Add: "`GET /actuator/health/liveness` returns HTTP 503 when the application is not live. `GET /actuator/health/readiness` returns HTTP 503 when the application is not ready to receive traffic."

- **low** FR-22 (structured logging) does not define "structured" (§4.6) — The consequence bullets specify log levels and content but not format (JSON, logfmt, plain text). The addendum specifies "Logback + JSON structured output" but this is downstream implementation detail. Since the Kubernetes logging stack may depend on the format, the requirement should name it. *Fix:* Add: "Logs are emitted in JSON format, one JSON object per log event."

---

## 5. Scope honesty — strong

The Non-Goals (§5) are precise and each one explicitly anchors to the current service state or the migration constraint. The MVPs Out of Scope items (§6.2) each carry a deferral rationale and a reassessment trigger, which is above average for internal PRDs.

The Assumptions Index (§11) is present, indexed, and links assumptions to FR sections. A1 is correctly marked RESOLVED with a decision log reference.

### Findings

- **medium** Assumptions Index has inconsistent formatting that breaks machine-parse (§11) — A1 uses `[A1 — RESOLVED]`, while A2–A5 use `[ASSUMPTION A2, §4.2 FR-6]`. The label prefix, bracket style, and field order differ. A downstream tool or template that parses assumptions by label will either miss A1 or miss A2–A5. *Fix:* Standardise all entries to a single format, e.g., `[A1, §4.2 FR-6, RESOLVED]` / `[A2, §4.2 FR-6, OPEN]`.

- **low** The addendum lists "Additional tests needed beyond MUnit parity" (addendum §Test Migration Map) that are not reflected in the PRD — FR-3's consequences mention pageToken forwarding, but the addendum's two additional test cases (including the 404 body content assertion) are only in the addendum, not in the NFR-2 scope definition. If the story team reads only the PRD, they will produce a test suite that is missing two cases. *Fix:* Add the two additional test cases to NFR-2's scope or add a consequence bullet to FR-3 and FR-7 that covers them.

---

## 6. Downstream usability — adequate

The Glossary (§3) is tightly scoped to the domain terms that appear in FR consequences and response shapes. Terms are consistently capitalised throughout the document (PlaylistItem, PlaylistResponse, SongDetail, ErrorResponse, VideoId, PlaylistId, pageToken) and align with the field names in FR-2 and FR-6.

FR IDs are contiguous (FR-1 through FR-22) with no gaps. NFR IDs are contiguous (NFR-1 through NFR-5). SM IDs are contiguous (SM-1 through SM-5, SM-C1, SM-C2). The UJ-1 reference in §4.1 resolves correctly to §2.3.

SM-1 in §8 cross-references "FR-17 to FR-20" as what it validates — this is slightly off: SM-1 (CloudHub cancellation) is the outcome of the entire migration, not specifically of the contract/configuration FRs. The mapping overfits.

### Findings

- **medium** SM-1 cross-reference is misleading (§8) — SM-1 is listed as validating "FR-17 to FR-20" (contract + config FRs), but CloudHub cancellation validates the entire migration, not just those FRs. An architect building a traceability matrix from this will misattribute the primary success metric. *Fix:* Either remove the "Validates" annotation from SM-1 or change it to "Validates the overall migration objective; see SM-2, SM-3 for behavioral parity gates."

- **low** UJ-1 covers only the Playlist endpoint; no user journey exists for the Video endpoint (§2.3) — A story author creating user stories for FR-5 through FR-7 has no UJ to anchor them to. The "Key User Journey" section heading implies completeness. *Fix:* Add UJ-2: "Developer retrieves metadata for a specific video by VideoId" or rename the section to "Primary User Journey" and add a note that the Video endpoint serves the same user in a single-item lookup context.

- **low** The addendum is referenced in the PRD header (§0) but the link is by description only ("lives in `addendum.md`") — no relative path or anchor. A reader in a rendered document viewer cannot navigate directly. *Fix:* Add a relative link: `[addendum.md](./addendum.md)`.

---

## 7. Shape fit — strong

This is the PRD's strongest dimension. The document does not pretend to be a product strategy document or a market requirements document. It correctly identifies its audience as "engineering team executing the migration and downstream architecture and story-creation workflows" and writes at exactly that level.

The Vision section is three paragraphs. There are no personas with stock-photo names. There is no "north star metric." The NFRs are tied to operational constraints that already exist (MICRO worker allocation, 7 MUnit scenarios). The addendum cleanly absorbs implementation detail (effort estimates, DataWeave line counts, connector mapping table, K8s manifest parameters) that would have inflated the PRD body without adding requirements value.

The formalism level — FR IDs, consequence bullets, Assumptions Index, Glossary — is appropriate for the downstream use case (architecture document + story creation). It is not over-engineered for what is, functionally, a 7-day migration project.

### Findings

- **low** The effort estimate in the addendum (7 + 2 days) is not referenced or acknowledged anywhere in the PRD (§6, §9) — For a decision-maker approving the migration, the effort is directly relevant to the "go/no-go" decision. Omitting it from the PRD forces the reader to find it in the addendum. *Fix:* Add a single line to §6.1 or §9: "Estimated effort: 7 working days + 2-day contingency (see addendum)."

---

## Mechanical notes

**Glossary drift:** No drift detected. All Glossary terms (§3) are used consistently with their defined capitalisation throughout FR consequences, response shape descriptions, and NFRs. `pageToken` is correctly lowercased as a query parameter name throughout.

**ID continuity:** FR-1 through FR-22 — contiguous, no gaps or duplicates. NFR-1 through NFR-5 — contiguous. SM-1 through SM-5, SM-C1, SM-C2 — contiguous. UJ-1 — singular; UJ-2 is absent (flagged above). A1 through A5 — contiguous.

**Assumptions Index roundtrip:** A1 resolves (decision log entry #11 cited). A2 resolves (§4.2 FR-6). A3 resolves (§4.6 FR-22). A4 resolves (§6.2). A5 resolves (§9). The formatting inconsistency between A1 and A2–A5 is flagged under §5 above; the content is sound.

**Cross-reference check:**
- UJ-1 reference in §4.1 → §2.3: resolves correctly.
- SM-1 → "FR-17 to FR-20": flagged as misleading (§6 finding above).
- SM-2 → "FR-1 through FR-15": resolves correctly.
- SM-3 → "FR-1 through FR-15": resolves correctly.
- SM-4 → "NFR-1": resolves correctly.
- FR-7 note → §9: resolves correctly.
- §0 reference to `addendum.md`: unlinked (flagged above).

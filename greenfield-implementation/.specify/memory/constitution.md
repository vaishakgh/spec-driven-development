<!--
SYNC IMPACT REPORT
==================
Version change: [unversioned template] → 1.0.0 (initial ratification)

Modified principles: N/A — first-time fill from template
Added sections:
  - Core Principles (5 principles defined)
  - Authorization & Access Control
  - Performance & Reliability Standards
  - Governance
Removed sections: N/A (template placeholders replaced)

Templates reviewed:
  ✅ .specify/templates/plan-template.md — Constitution Check gate present; aligns with principles
  ✅ .specify/templates/spec-template.md — FR/SC structure compatible; no changes required
  ✅ .specify/templates/tasks-template.md — Phase structure compatible; no changes required

Deferred TODOs: none
-->

# Balance Enquiry API Constitution

## Core Principles

### I. Security-First (NON-NEGOTIABLE)

Every request MUST be authenticated via a valid JWT token. Authorization MUST verify that the
`accountId` path parameter matches the `sub` (subject) claim in the JWT; cross-account access
MUST be rejected with HTTP 403. Unauthenticated requests (missing or invalid token) MUST be
rejected with HTTP 401. Suspended accounts MUST block balance access with HTTP 403 even when
the balance data is technically readable.

**Rationale**: Payment account data is sensitive PII. A breach of account isolation is a
regulatory and financial liability. These controls are non-negotiable and must be verified in
every code review and test run.

### II. Read-Only API Contract

The balance enquiry endpoint MUST produce zero side effects. No database writes, no
state mutations, and no calls to external services are permitted as part of a balance
read operation. The response MUST include `Cache-Control: no-store` to prevent client-side
caching of sensitive financial data.

**Rationale**: A read endpoint that mutates state is a correctness hazard. Caching financial
balances can expose stale data to customers and violates PCI-DSS guidance on sensitive
account information.

### III. Explicit Error Semantics

The API MUST return precise HTTP status codes that distinguish between authentication failure
(401), authorization/suspension failure (403), and missing resource (404). Error response
bodies MUST include a machine-readable `code` field and a human-readable `message`. The API
MUST NOT leak internal implementation details (stack traces, SQL errors, internal IDs) in
error responses.

**Rationale**: Ambiguous error responses force clients to guess, create poor user experiences,
and can inadvertently expose security-sensitive information. Precise codes enable clients to
handle each failure mode correctly.

### IV. Performance & Rate Limiting

The endpoint MUST achieve a p99 response time under 500ms under normal load as measured
at the service boundary (excluding client network latency). Rate limiting MUST be enforced at
60 requests per minute per authenticated user, keyed on the JWT subject claim. Requests
exceeding the limit MUST receive HTTP 429 with a `Retry-After` header.

**Rationale**: Payment applications require predictable latency for a good user experience.
Rate limiting protects the database from abusive query patterns and prevents credential-stuffing
probes against the balance endpoint.

### V. Data Source Integrity

All balance data MUST be sourced exclusively from the internal PostgreSQL database. No
external service calls, caches (shared or otherwise), or computed/derived values may substitute
for the authoritative database record. The `lastUpdatedAt` field in the response MUST reflect
the actual database record timestamp.

**Rationale**: Multi-currency conversion, external ledger lookups, and cached aggregates are
explicitly out of scope. Sourcing from one authoritative store eliminates consistency hazards
and simplifies the audit trail.

## Authorization & Access Control

- JWT validation MUST occur before any database access.
- The `accountId` in the URL path MUST be compared against the JWT `sub` claim using
  strict string equality; no wildcard or prefix matching is permitted.
- Account suspension status MUST be checked after ownership validation and before
  returning balance data.
- All authorization failures MUST be logged with the requesting JWT subject and the
  target `accountId` (but never the JWT secret or full token).
- Admin cross-account queries are out of scope for this feature and MUST NOT be
  implemented as part of it.

## Performance & Reliability Standards

- Database queries for balance reads MUST use indexed lookups on `accountId`; full table
  scans are not acceptable.
- Rate-limit counters MUST be consistent within a single service instance; distributed
  rate limiting across replicas is a future concern and out of scope for v1.
- Response payload MUST conform to `{ accountId, availableBalance, currency, lastUpdatedAt }`;
  no additional fields may be added without a constitution amendment.
- Integration tests MUST assert `Cache-Control: no-store` is present on every 2xx response.
- Load tests MUST verify p99 latency < 500ms before the feature is marked production-ready.

## Governance

This constitution governs all design, implementation, and review decisions for the Balance
Enquiry feature. It supersedes informal conventions and verbal agreements.

**Amendment procedure**:
1. Propose a change with a rationale that references a concrete requirement or incident.
2. Increment the version following semantic versioning (MAJOR for removals/redefinitions,
   MINOR for new principles or sections, PATCH for clarifications).
3. Update the Sync Impact Report comment at the top of this file.
4. Validate that no dependent templates or command files contain contradictions.

**Compliance review**: Every pull request MUST include a "Constitution Check" section in the
implementation plan confirming that Principles I–V are satisfied or explicitly waived with
justification documented in the Complexity Tracking table.

**Versioning policy**: Follow semantic versioning. A principle removal or redefinition is
always a MAJOR bump. Adding a new principle is a MINOR bump. Wording clarifications that do
not change intent are PATCH bumps.

**Version**: 1.0.0 | **Ratified**: 2026-06-21 | **Last Amended**: 2026-06-21

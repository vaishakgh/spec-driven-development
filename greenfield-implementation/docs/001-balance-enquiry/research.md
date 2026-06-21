# Research: Balance Enquiry

**Date**: 2026-06-22
**Feature**: Balance Enquiry API
**Plan**: [plan.md](./plan.md)

## Decision Log

### Language & Runtime

**Decision**: Python 3.12

**Rationale**: Confirmed by user. Python 3.12 ships with improved performance over 3.11
(faster interpreter, lower overhead per function call) and has first-class async support
via `asyncio`, which pairs well with async database drivers for low-latency reads.

**Alternatives considered**: Not evaluated — language is fixed by project requirement.

---

### Web Framework

**Decision**: FastAPI (latest stable)

**Rationale**: FastAPI is built on Starlette + Pydantic and is the de-facto standard for
async Python REST APIs. Key reasons for this feature:
- Native async support aligns with asyncpg's async DB driver
- Pydantic response models enforce the exact `{ accountId, availableBalance, currency,
  lastUpdatedAt }` payload shape at the framework level, preventing accidental field leakage
- Dependency injection system makes JWT auth middleware clean and testable
- Automatic OpenAPI schema generation validates our contract artifact at runtime

**Alternatives considered**:
- Flask/Django REST Framework: synchronous by default; would require thread pool workarounds
  to hit the p99 < 500ms target under load
- aiohttp: lower-level, more boilerplate for auth/validation

---

### Database Driver

**Decision**: asyncpg (direct async PostgreSQL driver)

**Rationale**: asyncpg is the fastest Python PostgreSQL driver available, with benchmarks
showing 3–5× throughput over psycopg2 in async workloads. Since the balance endpoint does
a single indexed primary-key lookup, the difference materialises directly in p99 latency.
Connection pooling is managed via asyncpg's built-in `asyncpg.create_pool()`.

**Alternatives considered**:
- SQLAlchemy async (with asyncpg backend): adds ORM abstraction overhead not needed for
  a single SELECT query; introduces migration complexity out of scope for this feature
- psycopg2: synchronous; incompatible with FastAPI's async request handlers without
  running in a thread pool

---

### JWT Validation

**Decision**: PyJWT (≥ 2.8)

**Rationale**: PyJWT is the standard JWT library for Python, actively maintained, and
supports RS256 (asymmetric) and HS256 (symmetric) algorithms. We validate the token's
`sub` claim and expiry on every request inside a FastAPI dependency.

**Key implementation note**: The JWT secret/public key is injected from an environment
variable (`JWT_SECRET` or `JWT_PUBLIC_KEY`). The algorithm must be explicitly specified —
never `algorithms=["*"]` — to prevent algorithm-confusion attacks.

**Alternatives considered**:
- python-jose: broader JOSE support but heavier dependency tree; not needed for JWT-only use
- authlib: full OAuth2 server capability; overkill for a consumer-side JWT validator

---

### Rate Limiting

**Decision**: slowapi (built on the `limits` library)

**Rationale**: slowapi is the standard FastAPI-native rate-limiting extension. It integrates
as a middleware decorator, keys limits on arbitrary callables (we use the JWT `sub` claim),
and automatically sets `Retry-After` on 429 responses. For a single-instance v1 deployment,
in-memory storage (`MemoryStorage`) is sufficient and introduces no external dependencies.

**Key implementation note**: The rate-limit key function extracts the JWT `sub` claim from
the `Authorization` header. If the token is missing or invalid, the auth middleware fires
first and returns 401 before the rate limiter is consulted.

**Alternatives considered**:
- Redis-backed rate limiting (e.g., redis-py + custom middleware): correct for distributed
  deployments but out of scope for v1 single-instance constraint
- NGINX/API gateway rate limiting: infrastructure-level; valid for production hardening but
  not part of this feature's implementation scope

---

### Testing Stack

**Decision**: pytest + httpx (AsyncClient) + pytest-asyncio

**Rationale**:
- `pytest` is the Python testing standard
- `httpx.AsyncClient` with FastAPI's `app` transport allows async end-to-end contract tests
  without a live server
- `pytest-asyncio` enables `async def` test functions
- Integration tests use a real PostgreSQL test database (not mocks) to validate query
  correctness and connection pool behaviour

**Alternatives considered**:
- TestClient (synchronous): works for sync routes; incompatible with async routes without
  `anyio` event loop workarounds
- unittest: more verbose; no meaningful benefit over pytest for this use case

---

## Resolved Clarifications

All technical choices were made from first principles against the spec. No `NEEDS
CLARIFICATION` items were raised. The language requirement (Python) was confirmed by the
user during the planning phase.

## Best Practices Applied

| Area | Practice |
|------|----------|
| JWT security | Algorithm explicitly allowlisted; secret from env var only |
| Error responses | `code` + `message` fields only; no tracebacks via custom exception handlers |
| DB queries | Primary-key lookup on `account_id`; connection pool; no raw string interpolation |
| Rate limiting | Keyed on JWT `sub`, not IP address (IP can be shared/spoofed) |
| Cache control | `Cache-Control: no-store` set at middleware level, not per-handler |
| Observability | Auth failures and rate-limit events logged with JWT subject + target accountId |

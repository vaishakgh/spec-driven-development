# Implementation Plan: Balance Enquiry

**Branch**: `001-balance-enquiry` | **Date**: 2026-06-22 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `docs/001-balance-enquiry/spec.md`

## Summary

An authenticated customer can retrieve their own payment account balance via a single
read-only REST endpoint (`GET /api/accounts/{accountId}/balance`). The implementation is
a backend web service backed by PostgreSQL, with JWT-based authentication and ownership
enforcement, rate limiting (60 req/min per user), and zero external service dependencies.
All balance data is read directly from the database with no caching and no side effects.

## Technical Context

**Language/Version**: Python 3.12

**Primary Dependencies**: FastAPI (async REST framework), asyncpg (PostgreSQL driver),
PyJWT (JWT validation), slowapi (rate limiting), uvicorn (ASGI server)

**Storage**: PostgreSQL — internal `accounts` table (pre-existing); no migrations owned
by this feature beyond ensuring the table exists with the required schema.

**Testing**: pytest + httpx (async HTTP test client), pytest-asyncio

**Target Platform**: Linux server (containerised)

**Project Type**: Web service (single backend API)

**Performance Goals**: p99 response time < 500ms under normal load at the service boundary

**Constraints**: 60 req/min per authenticated user; `Cache-Control: no-store` mandatory;
no external service calls; no write side effects

**Scale/Scope**: Single deployment instance (v1); rate limiting is in-process (not
distributed); one endpoint, one user story

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Gate | Status | Evidence |
|-----------|------|--------|---------|
| I. Security-First | JWT validation runs before DB access; accountId == JWT sub enforced; 401/403 returned correctly; auth failures logged | ✅ PASS | JWT middleware at route layer; ownership check in service before query; suspension check after ownership |
| II. Read-Only API Contract | GET only; no DB writes; no external calls; Cache-Control: no-store on 200 | ✅ PASS | Single GET route; repository is read-only; header set in response middleware |
| III. Explicit Error Semantics | 401/403/404/429 returned for distinct failure modes; no stack traces in responses | ✅ PASS | Exception handlers mapped per error type; error body has `code` + `message` only |
| IV. Performance & Rate Limiting | Indexed DB lookup; rate limit 60/min/user via JWT sub; HTTP 429 + Retry-After | ✅ PASS | slowapi keyed on JWT sub claim; DB query uses primary-key lookup on accountId |
| V. Data Source Integrity | PostgreSQL only; lastUpdatedAt from DB record; no caches substitute | ✅ PASS | Single asyncpg query; no Redis or in-process cache; lastUpdatedAt returned as-is from row |

**Gate result: ALL PASS — no violations to justify.**

## Project Structure

### Documentation (this feature)

```text
docs/001-balance-enquiry/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── balance-enquiry.yaml   # OpenAPI contract (Phase 1 output)
└── tasks.md             # Phase 2 output (/speckit-tasks command)
```

### Source Code

```text
code/001-balance-enquiry/
├── pyproject.toml
├── requirements.txt
├── .env.example
├── Dockerfile
├── src/
│   ├── main.py                      # FastAPI app entry point, lifespan, middleware wiring
│   ├── core/
│   │   ├── config.py                # Settings (env vars: DATABASE_URL, JWT_SECRET, etc.)
│   │   └── database.py              # asyncpg connection pool lifecycle
│   ├── auth/
│   │   └── jwt_handler.py           # JWT decode, sub extraction, 401 guard
│   ├── middleware/
│   │   └── rate_limiter.py          # slowapi setup, 429 + Retry-After handling
│   ├── models/
│   │   └── account.py               # Pydantic response model (BalanceResponse)
│   ├── repositories/
│   │   └── account_repository.py    # Single async SELECT query on accounts table
│   └── api/
│       └── routes/
│           └── balance.py           # GET /api/accounts/{accountId}/balance handler
└── tests/
    ├── conftest.py
    ├── fixtures/
    │   └── seed.sql
    ├── contract/
    │   └── test_balance_contract.py # Response shape, headers, status codes
    ├── integration/
    │   └── test_balance_integration.py  # Full request flow against test DB
    └── unit/
        └── test_balance_service.py  # Ownership check, suspension check, 404 logic
```

**Structure Decision**: Single web-service project under `code/001-balance-enquiry/`. No frontend. No mobile. One service, one endpoint, one database. All source under `src/`, all tests under `tests/`. Run all commands from `code/001-balance-enquiry/` as the working directory.

## Complexity Tracking

> No constitution violations were identified. This table is intentionally empty.

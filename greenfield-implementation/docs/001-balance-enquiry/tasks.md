---

description: "Task list for Balance Enquiry API implementation"
---

# Tasks: Balance Enquiry

**Input**: Design documents from `docs/001-balance-enquiry/`

**Prerequisites**: plan.md ✅ · spec.md ✅ · research.md ✅ · data-model.md ✅ · contracts/ ✅

**Tests**: Included — test directories defined in plan.md project structure.

**Organization**: Tasks grouped by phase. Phase 2 (Foundational) must complete before User
Story 1 begins. All [P]-marked tasks within a phase can run in parallel.

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Can run in parallel (different files, no dependencies within phase)
- **[Story]**: Which user story this task belongs to (US1 = Check My Account Balance)
- Exact file paths included in every description

## Path Conventions

- Source: `src/` at repository root
- Tests: `tests/` at repository root

---

## Phase 1: Setup

**Purpose**: Project initialization — directory structure and dependency declaration.

- [x] T001 Create project directory structure: `src/core/`, `src/auth/`, `src/middleware/`, `src/models/`, `src/repositories/`, `src/services/`, `src/api/routes/`, `tests/contract/`, `tests/integration/`, `tests/unit/`, `tests/fixtures/`
- [x] T002 Create `pyproject.toml` with Python 3.12 target, all runtime dependencies (fastapi, uvicorn[standard], asyncpg, PyJWT, slowapi), and dev/test dependencies (pytest, httpx, pytest-asyncio, ruff)
- [x] T003 [P] Create `requirements.txt` pinned from `pyproject.toml` for Docker build reproducibility
- [x] T004 [P] Create `.env.example` declaring `DATABASE_URL`, `JWT_SECRET`, `JWT_ALGORITHM=HS256`, `RATE_LIMIT_PER_MINUTE=60`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before User Story 1 can be implemented.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [x] T005 Implement `src/core/config.py` — `Settings` class using `pydantic-settings` reading `DATABASE_URL`, `JWT_SECRET`, `JWT_ALGORITHM`, `RATE_LIMIT_PER_MINUTE` from environment; instantiate a `settings` singleton for import
- [x] T006 [P] Implement `src/core/database.py` — `init_pool()` / `close_pool()` using `asyncpg.create_pool(settings.DATABASE_URL)`, `get_connection()` async context manager; pool stored as module-level variable for lifespan wiring
- [x] T007 [P] Implement `src/core/exceptions.py` — custom exception classes: `UnauthorizedException`, `ForbiddenException`, `AccountSuspendedException`, `NotFoundException`; each carries a `code: str` and `message: str` field matching the OpenAPI `ErrorResponse` schema
- [x] T008 [P] Implement `src/core/exception_handlers.py` — FastAPI exception handlers for each custom exception: `UnauthorizedException` → 401, `ForbiddenException` → 403, `AccountSuspendedException` → 403, `NotFoundException` → 404; response body `{"code": ..., "message": ...}` only; no tracebacks
- [x] T009 Implement `src/auth/jwt_handler.py` — `decode_jwt(token: str) -> str` decodes Bearer token using `PyJWT`, explicitly allowlisting `settings.JWT_ALGORITHM` (never `"*"`), returns `sub` claim string; raises `UnauthorizedException(code="UNAUTHORIZED")` for missing, malformed, expired, or algorithm-mismatch tokens; FastAPI `Depends`-compatible `get_jwt_sub` dependency function that extracts the Bearer value from the `Authorization` header
- [x] T010 [P] Implement `src/middleware/rate_limiter.py` — `slowapi` `Limiter` keyed on a callable that returns the JWT `sub` claim from the request (not IP); limit string `"60/minute"`; `_rate_limit_exceeded_handler` sets `Retry-After` header and returns `{"code": "RATE_LIMIT_EXCEEDED", "message": ...}` with HTTP 429
- [x] T011 Implement `src/main.py` — create `FastAPI` app with lifespan context manager calling `init_pool()` on startup and `close_pool()` on shutdown; register all exception handlers from `exception_handlers.py`; add `slowapi` state and exception handler; add middleware that appends `Cache-Control: no-store` to every response; router placeholder comment for Phase 3 wiring

**Checkpoint**: Foundation ready — `uvicorn src.main:app` starts without error; all exception handlers respond correctly to synthetic inputs.

---

## Phase 3: User Story 1 — Check My Account Balance (Priority: P1) 🎯 MVP

**Goal**: Implement `GET /api/accounts/{accountId}/balance` returning `{ accountId, availableBalance, currency, lastUpdatedAt }` for an authenticated account owner.

**Independent Test**: A customer with a valid JWT whose `sub` matches `accountId` calls the endpoint and receives HTTP 200 with all four response fields and `Cache-Control: no-store`. All six acceptance scenarios in spec.md pass.

### Implementation for User Story 1

- [x] T012 [P] [US1] Implement `src/models/account.py` — `AccountRow` dataclass with fields matching the DB query result (`account_id`, `owner_subject`, `available_balance`, `currency`, `status`, `last_updated_at`); `BalanceResponse` Pydantic model with `accountId: str`, `availableBalance: Decimal`, `currency: str`, `lastUpdatedAt: datetime | None` and `model_config = {"populate_by_name": True}`
- [x] T013 [US1] Implement `src/repositories/account_repository.py` — `get_account_by_id(conn, account_id: str) -> AccountRow | None` executes `SELECT account_id, owner_subject, available_balance, currency, status, last_updated_at FROM accounts WHERE account_id = $1` (primary-key lookup, no filtered WHERE for auth); returns `AccountRow` or `None` if not found
- [x] T014 [US1] Implement `src/services/balance_service.py` — `get_balance(account_id: str, jwt_sub: str, conn) -> BalanceResponse`: (1) calls `account_repository.get_account_by_id`; (2) if `None` → raise `NotFoundException(code="NOT_FOUND")`; (3) if `row.owner_subject != jwt_sub` → raise `ForbiddenException(code="FORBIDDEN")`; (4) if `row.status == "suspended"` → raise `AccountSuspendedException(code="ACCOUNT_SUSPENDED")`; (5) return `BalanceResponse(accountId=row.account_id, availableBalance=row.available_balance, currency=row.currency, lastUpdatedAt=row.last_updated_at)`
- [x] T015 [US1] Implement `src/api/routes/balance.py` — `APIRouter` with `GET /accounts/{accountId}/balance`; inject `jwt_sub: str = Depends(get_jwt_sub)` and `conn = Depends(get_connection)`; apply `@limiter.limit("60/minute")` decorator from `rate_limiter.py`; call `balance_service.get_balance(accountId, jwt_sub, conn)`; return `BalanceResponse`
- [x] T016 [US1] Register balance router in `src/main.py` — `app.include_router(balance_router, prefix="/api")`; remove placeholder comment from T011

**Checkpoint**: All six acceptance scenarios from spec.md are manually verifiable using the `curl` commands in `quickstart.md`. The endpoint returns HTTP 200 with the correct payload and `Cache-Control: no-store` header for an active account owner.

---

## Phase 4: Polish & Cross-Cutting Concerns

**Purpose**: Test coverage, containerisation, and final validation.

- [x] T017 [P] Create `tests/fixtures/seed.sql` — INSERT two rows: `acc_test_001` (owner `customer-sub-abc`, active, balance 1024.50 GBP) and `acc_test_002` (owner `customer-sub-xyz`, suspended, balance 500.00 USD); used by integration tests
- [x] T018 [P] Create `tests/conftest.py` — pytest fixtures: `async_client` (httpx `AsyncClient` wrapping the FastAPI app), `db_conn` (asyncpg test connection using `TEST_DATABASE_URL`), `make_token(sub)` factory generating HS256 JWT signed with `settings.JWT_SECRET`
- [x] T019 [P] Write `tests/unit/test_balance_service.py` — unit tests for `balance_service.get_balance`: (a) owner mismatch → `ForbiddenException`; (b) suspended account → `AccountSuspendedException`; (c) account not found → `NotFoundException`; (d) valid active account → returns `BalanceResponse` with correct field values; mock `account_repository` using `unittest.mock.AsyncMock`
- [x] T020 [P] Write `tests/contract/test_balance_contract.py` — contract tests using httpx `AsyncClient`: (a) 200 response body has exactly `{accountId, availableBalance, currency, lastUpdatedAt}` and no extra keys; (b) `Cache-Control: no-store` header present on 200; (c) 401 body has `{code, message}` only; (d) 403 body has `{code, message}` only; (e) 404 body has `{code, message}` only; (f) 429 response has `Retry-After` header
- [x] T021 [P] Write `tests/integration/test_balance_integration.py` — end-to-end tests against test DB (seeded from `seed.sql`): (a) happy path → 200 with correct balance fields; (b) no token → 401; (c) JWT `sub` ≠ `accountId` → 403 FORBIDDEN; (d) suspended account (own token) → 403 ACCOUNT_SUSPENDED; (e) non-existent account → 404; (f) 61st request in window → 429 with `Retry-After` header
- [x] T022 [P] Create `Dockerfile` — multi-stage build: `python:3.12-slim` builder installs dependencies from `requirements.txt`; `python:3.12-slim` runtime copies `src/`; `CMD ["uvicorn", "src.main:app", "--host", "0.0.0.0", "--port", "8000"]`
- [x] T023 Run all 7 quickstart validation scenarios from `docs/001-balance-enquiry/quickstart.md` — SC-1 through SC-6 covered by integration tests; SC-7 (p99 load test) requires live service + wrk/locust (run manually post-deploy)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 completion — BLOCKS User Story 1
- **User Story 1 (Phase 3)**: Depends on Phase 2 completion
- **Polish (Phase 4)**: Depends on Phase 3 completion

### Within Phase 2

```
T005 (config) → T006, T007, T008, T009, T010 [can all run in parallel once T005 is done]
T005, T006, T007, T008, T009, T010 → T011 (main.py wires everything together)
```

### Within Phase 3

```
T012 (models) → T013 (repository uses AccountRow)
T013 → T014 (service uses repository + AccountRow)
T014 → T015 (route handler calls service)
T015 → T016 (register router in main.py)
```

### Within Phase 4

All Phase 4 tasks marked [P] can run in parallel once Phase 3 is complete.
T023 (quickstart validation) depends on all others in Phase 4 completing first.

### Parallel Opportunities

```bash
# Phase 1 — run in parallel:
Task: T003 Create requirements.txt
Task: T004 Create .env.example

# Phase 2 — after T005 completes, run in parallel:
Task: T006 Implement src/core/database.py
Task: T007 Implement src/core/exceptions.py
Task: T008 Implement src/core/exception_handlers.py
Task: T009 Implement src/auth/jwt_handler.py  (depends on T007)
Task: T010 Implement src/middleware/rate_limiter.py

# Phase 4 — run in parallel:
Task: T017 Create tests/fixtures/seed.sql
Task: T018 Create tests/conftest.py
Task: T019 Write tests/unit/test_balance_service.py
Task: T020 Write tests/contract/test_balance_contract.py
Task: T021 Write tests/integration/test_balance_integration.py
Task: T022 Create Dockerfile
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL — blocks everything)
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: Run quickstart scenarios SC-1 through SC-6 manually
5. Feature is functional — deploy or demo if ready

### Full Delivery (All Phases)

1. Phase 1 → Phase 2 → Phase 3 → Phase 4
2. Run `pytest tests/ -v` — all tests must pass
3. Run quickstart SC-7 (load test) — p99 < 500ms
4. Build and smoke-test Docker image
5. Feature is production-ready

---

## Notes

- [P] tasks = different files, no dependencies on other incomplete tasks in the same phase
- [US1] label maps every task to User Story 1 for traceability
- T009 (`jwt_handler.py`) logically depends on T007 (`exceptions.py`) — implement T007 first
- T016 (router wiring) modifies `src/main.py` created in T011 — sequence matters
- Never filter the SQL WHERE clause by `owner_subject` — authorization is enforced in Python (T014) to prevent timing-based account enumeration (see data-model.md)
- `Cache-Control: no-store` is set at middleware level in T011, not per-handler — do not add it in T015
- Rate limiter (T010) keys on JWT `sub`, not IP address — the callable must extract sub from the Authorization header

# Data Model: Balance Enquiry

**Date**: 2026-06-22
**Feature**: Balance Enquiry API
**Plan**: [plan.md](./plan.md)

## Overview

This feature reads from a single pre-existing PostgreSQL table: `accounts`. No new tables
are created by this feature. The model below is the required schema that MUST exist before
the service can operate.

---

## Entity: Account

Represents a customer's payment account. This is the authoritative source of balance data.

### PostgreSQL Table: `accounts`

```sql
CREATE TABLE accounts (
    account_id       VARCHAR(64)     PRIMARY KEY,
    owner_subject    VARCHAR(255)    NOT NULL,
    available_balance NUMERIC(19, 4) NOT NULL,
    currency         CHAR(3)         NOT NULL,
    status           VARCHAR(16)     NOT NULL DEFAULT 'active',
    last_updated_at  TIMESTAMPTZ     NULL,

    CONSTRAINT accounts_status_check CHECK (status IN ('active', 'suspended'))
);

-- Index required by Constitution Principle V (Data Source Integrity)
-- account_id PRIMARY KEY already creates a unique index — no additional index needed.

-- Index for ownership lookups (JWT sub → account ownership check)
CREATE INDEX idx_accounts_owner_subject ON accounts (owner_subject);
```

### Field Definitions

| Column | Type | Nullable | Description |
|--------|------|----------|-------------|
| `account_id` | VARCHAR(64) | NOT NULL | Primary key. Unique account identifier. Matched against URL path parameter. |
| `owner_subject` | VARCHAR(255) | NOT NULL | JWT `sub` claim of the account owner. Used for ownership enforcement. |
| `available_balance` | NUMERIC(19,4) | NOT NULL | Spendable balance (not pending). Stored in account's native currency. |
| `currency` | CHAR(3) | NOT NULL | ISO 4217 currency code (e.g., `USD`, `GBP`, `EUR`). |
| `status` | VARCHAR(16) | NOT NULL | `'active'` or `'suspended'`. Suspension blocks balance access (HTTP 403). |
| `last_updated_at` | TIMESTAMPTZ | NULL | Timestamp of last balance update. May be `null` for newly-created accounts. |

### Validation Rules

- `account_id`: non-empty string; format determined by upstream account creation service
- `owner_subject`: must match the `sub` claim in the customer's JWT exactly (string equality)
- `available_balance`: non-negative; precision up to 4 decimal places
- `currency`: exactly 3 uppercase alphabetic characters (ISO 4217)
- `status`: constrained to `'active'` or `'suspended'` via CHECK constraint
- `last_updated_at`: nullable; when null, returned as JSON `null` (field is never omitted)

---

## Pydantic Response Model

The API response shape is locked by the constitution. This Pydantic model enforces it:

```python
from pydantic import BaseModel
from datetime import datetime
from decimal import Decimal

class BalanceResponse(BaseModel):
    accountId: str
    availableBalance: Decimal
    currency: str
    lastUpdatedAt: datetime | None

    model_config = {"populate_by_name": True}
```

### Field Mapping (DB column → JSON key)

| DB Column | JSON Key | Type in Response |
|-----------|----------|-----------------|
| `account_id` | `accountId` | string |
| `available_balance` | `availableBalance` | number (decimal) |
| `currency` | `currency` | string (3-char ISO 4217) |
| `last_updated_at` | `lastUpdatedAt` | ISO 8601 datetime string or `null` |

---

## State Transitions

Account status transitions are managed by a separate upstream service (out of scope).
This feature is read-only with respect to account state.

```
       [upstream service]
active ─────────────────► suspended
       [upstream service]
suspended ───────────────► active
```

The balance endpoint observes state but never mutates it.

---

## Query: Balance Read

The single database query executed by this feature:

```sql
SELECT account_id,
       owner_subject,
       available_balance,
       currency,
       status,
       last_updated_at
FROM   accounts
WHERE  account_id = $1;
```

**Access pattern**: Primary-key lookup on `account_id`. Expected index hit on every call.
Expected execution time: single-digit milliseconds under normal load.

**Authorization logic** (applied in Python, not SQL):
1. If no row returned → HTTP 404
2. If `owner_subject` ≠ JWT `sub` claim → HTTP 403 (ownership mismatch)
3. If `status == 'suspended'` → HTTP 403 (account suspended)
4. Otherwise → return balance fields as HTTP 200

No filtered WHERE clauses are used for authorization (avoids timing-based enumeration
attacks by always fetching the full row before checking ownership in application code).

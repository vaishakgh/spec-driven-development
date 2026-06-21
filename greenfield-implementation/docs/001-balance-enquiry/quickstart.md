# Quickstart Validation Guide: Balance Enquiry

**Date**: 2026-06-22
**Feature**: Balance Enquiry API
**Contract**: [contracts/balance-enquiry.yaml](./contracts/balance-enquiry.yaml)
**Data Model**: [data-model.md](./data-model.md)

This guide walks through validating the Balance Enquiry feature end-to-end once the
service is running. It is not an implementation guide — see `tasks.md` for that.

---

## Prerequisites

- Python 3.12 installed
- PostgreSQL running locally (or accessible via `DATABASE_URL`)
- The `accounts` table exists with the schema defined in `data-model.md`
- `JWT_SECRET` environment variable set (HS256 signing secret used by the auth service)

---

## 1. Environment Setup

```bash
# All commands below run from the code directory
cd code/001-balance-enquiry

# Install dependencies
pip install -r requirements.txt

# Set required environment variables
export DATABASE_URL="postgresql://user:password@localhost:5432/payments"
export JWT_SECRET="your-secret-key"
export RATE_LIMIT_PER_MINUTE=60

# Start the service
uvicorn src.main:app --host 0.0.0.0 --port 8000 --reload
```

Service is ready when you see:
```
INFO:     Application startup complete.
```

---

## 2. Seed a Test Account

Run once against your local database:

```sql
INSERT INTO accounts (account_id, owner_subject, available_balance, currency, status, last_updated_at)
VALUES
  ('acc_test_001', 'customer-sub-abc', 1024.50, 'GBP', 'active',    NOW()),
  ('acc_test_002', 'customer-sub-xyz', 500.00,  'USD', 'suspended', NOW());
```

---

## 3. Generate Test JWT Tokens

Use the helper below (replace `JWT_SECRET` with your actual value):

```python
import jwt, datetime

def make_token(sub: str, secret: str = "your-secret-key") -> str:
    payload = {
        "sub": sub,
        "exp": datetime.datetime.utcnow() + datetime.timedelta(hours=1),
    }
    return jwt.encode(payload, secret, algorithm="HS256")

# Tokens for manual testing:
valid_token   = make_token("customer-sub-abc")   # owns acc_test_001
foreign_token = make_token("customer-sub-xyz")   # owns acc_test_002 (suspended)
other_token   = make_token("customer-sub-999")   # owns nothing
```

---

## 4. Validation Scenarios

Run each `curl` command and verify the expected outcome.

### SC-1 — Happy path: valid owner, active account

```bash
curl -s -i \
  -H "Authorization: Bearer <valid_token>" \
  http://localhost:8000/api/accounts/acc_test_001/balance
```

**Expected**:
- Status: `200 OK`
- Header: `cache-control: no-store`
- Body:
  ```json
  {
    "accountId": "acc_test_001",
    "availableBalance": 1024.5,
    "currency": "GBP",
    "lastUpdatedAt": "<ISO 8601 timestamp>"
  }
  ```

---

### SC-2 — Unauthenticated: no token

```bash
curl -s -i http://localhost:8000/api/accounts/acc_test_001/balance
```

**Expected**:
- Status: `401 Unauthorized`
- Body: `{ "code": "UNAUTHORIZED", "message": "..." }`

---

### SC-3 — Cross-account: JWT sub ≠ accountId

```bash
curl -s -i \
  -H "Authorization: Bearer <other_token>" \
  http://localhost:8000/api/accounts/acc_test_001/balance
```

**Expected**:
- Status: `403 Forbidden`
- Body: `{ "code": "FORBIDDEN", "message": "..." }`

---

### SC-4 — Suspended account (owner's own token)

```bash
curl -s -i \
  -H "Authorization: Bearer <foreign_token>" \
  http://localhost:8000/api/accounts/acc_test_002/balance
```

**Expected**:
- Status: `403 Forbidden`
- Body: `{ "code": "ACCOUNT_SUSPENDED", "message": "..." }`

---

### SC-5 — Non-existent account

```bash
curl -s -i \
  -H "Authorization: Bearer <valid_token>" \
  http://localhost:8000/api/accounts/acc_does_not_exist/balance
```

**Expected**:
- Status: `404 Not Found`
- Body: `{ "code": "NOT_FOUND", "message": "..." }`

---

### SC-6 — Rate limit: 61st request within 1 minute

```bash
for i in $(seq 1 61); do
  curl -s -o /dev/null -w "%{http_code}\n" \
    -H "Authorization: Bearer <valid_token>" \
    http://localhost:8000/api/accounts/acc_test_001/balance
done
```

**Expected**: First 60 lines print `200`, the 61st (and beyond) print `429`.
The 429 response includes a `Retry-After` header.

---

### SC-7 — Cache-Control header always present

```bash
curl -s -I \
  -H "Authorization: Bearer <valid_token>" \
  http://localhost:8000/api/accounts/acc_test_001/balance | grep -i cache-control
```

**Expected**: `cache-control: no-store`

---

## 5. Run Automated Tests

```bash
# All tests
pytest tests/ -v

# Contract tests only
pytest tests/contract/ -v

# Integration tests only (requires DATABASE_URL to be set)
pytest tests/integration/ -v
```

All tests must pass before the feature is marked production-ready.

---

## 6. Performance Check

Use `k6`, `locust`, or `wrk` to confirm p99 < 500ms under load.
Example with `wrk` (30s, 10 threads, 100 concurrent connections):

```bash
wrk -t10 -c100 -d30s \
  -H "Authorization: Bearer <valid_token>" \
  http://localhost:8000/api/accounts/acc_test_001/balance
```

**Expected**: p99 latency column < 500ms.

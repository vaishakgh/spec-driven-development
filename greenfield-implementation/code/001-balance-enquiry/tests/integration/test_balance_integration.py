"""Integration tests — full request flow against a real PostgreSQL test database.

Prerequisites:
- TEST_DATABASE_URL env var pointing to a test PostgreSQL database
- Database seeded using tests/fixtures/seed.sql

Run with: TEST_DATABASE_URL=... pytest tests/integration/ -v
"""
import os

import asyncpg
import pytest
import pytest_asyncio

from src.core import database as db_module


@pytest_asyncio.fixture(autouse=True, scope="module")
async def seed_database():
    """Ensure test database is seeded before integration tests run."""
    test_db_url = os.environ.get("TEST_DATABASE_URL")
    if not test_db_url:
        pytest.skip("TEST_DATABASE_URL not set — skipping integration tests")

    conn = await asyncpg.connect(test_db_url)
    seed_path = os.path.join(os.path.dirname(__file__), "../fixtures/seed.sql")
    with open(seed_path) as f:
        await conn.execute(f.read())
    await conn.close()

    original_pool = db_module._pool
    db_module._pool = await asyncpg.create_pool(test_db_url)
    yield
    await db_module._pool.close()
    db_module._pool = original_pool


@pytest.mark.asyncio
async def test_happy_path_returns_200(async_client, make_token):
    token = make_token("customer-sub-abc")
    response = await async_client.get(
        "/api/accounts/acc_test_001/balance",
        headers={"Authorization": f"Bearer {token}"},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["accountId"] == "acc_test_001"
    assert body["availableBalance"] == 1024.5
    assert body["currency"] == "GBP"
    assert body["lastUpdatedAt"] is not None
    assert response.headers.get("cache-control") == "no-store"


@pytest.mark.asyncio
async def test_no_token_returns_401(async_client):
    response = await async_client.get("/api/accounts/acc_test_001/balance")
    assert response.status_code == 401
    assert response.json()["code"] == "UNAUTHORIZED"


@pytest.mark.asyncio
async def test_cross_account_returns_403_forbidden(async_client, make_token):
    token = make_token("customer-sub-xyz")  # owns acc_test_002, not acc_test_001
    response = await async_client.get(
        "/api/accounts/acc_test_001/balance",
        headers={"Authorization": f"Bearer {token}"},
    )
    assert response.status_code == 403
    assert response.json()["code"] == "FORBIDDEN"


@pytest.mark.asyncio
async def test_suspended_account_returns_403_account_suspended(async_client, make_token):
    token = make_token("customer-sub-xyz")  # correct owner, but acc_test_002 is suspended
    response = await async_client.get(
        "/api/accounts/acc_test_002/balance",
        headers={"Authorization": f"Bearer {token}"},
    )
    assert response.status_code == 403
    assert response.json()["code"] == "ACCOUNT_SUSPENDED"


@pytest.mark.asyncio
async def test_nonexistent_account_returns_404(async_client, make_token):
    token = make_token("customer-sub-abc")
    response = await async_client.get(
        "/api/accounts/acc_does_not_exist/balance",
        headers={"Authorization": f"Bearer {token}"},
    )
    assert response.status_code == 404
    assert response.json()["code"] == "NOT_FOUND"


@pytest.mark.asyncio
async def test_rate_limit_returns_429_after_60_requests(async_client, make_token):
    token = make_token("customer-sub-rate-test")
    responses = []
    for _ in range(62):
        r = await async_client.get(
            "/api/accounts/acc_test_001/balance",
            headers={"Authorization": f"Bearer {token}"},
        )
        responses.append(r)

    rate_limited = [r for r in responses if r.status_code == 429]
    assert rate_limited, "Expected HTTP 429 after exceeding rate limit"
    assert "retry-after" in rate_limited[0].headers
    assert rate_limited[0].json()["code"] == "RATE_LIMIT_EXCEEDED"

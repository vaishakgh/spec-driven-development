"""Contract tests — verify response shape, headers, and status codes against the OpenAPI contract.

These tests use the FastAPI app directly via httpx.AsyncClient and do NOT require a real
database. The DB layer is patched for each case.
"""
from decimal import Decimal
from datetime import datetime, timezone
from unittest.mock import AsyncMock, patch

import pytest

from src.core.exceptions import (
    AccountSuspendedException,
    ForbiddenException,
    NotFoundException,
    UnauthorizedException,
)
from src.models.account import AccountRow

_ACTIVE_ROW = AccountRow(
    account_id="acc_test_001",
    owner_subject="customer-sub-abc",
    available_balance=Decimal("1024.50"),
    currency="GBP",
    status="active",
    last_updated_at=datetime(2026, 6, 21, 14, 32, 0, tzinfo=timezone.utc),
)

_ALLOWED_KEYS = {"accountId", "availableBalance", "currency", "lastUpdatedAt"}
_ERROR_KEYS = {"code", "message"}


@pytest.mark.asyncio
async def test_200_response_shape(async_client, make_token):
    token = make_token("customer-sub-abc")
    with patch("src.repositories.account_repository.get_account_by_id", new=AsyncMock(return_value=_ACTIVE_ROW)):
        response = await async_client.get(
            "/api/accounts/acc_test_001/balance",
            headers={"Authorization": f"Bearer {token}"},
        )

    assert response.status_code == 200
    body = response.json()
    assert set(body.keys()) == _ALLOWED_KEYS, f"Extra/missing keys: {body.keys()}"
    assert body["accountId"] == "acc_test_001"
    assert body["currency"] == "GBP"


@pytest.mark.asyncio
async def test_200_cache_control_no_store(async_client, make_token):
    token = make_token("customer-sub-abc")
    with patch("src.repositories.account_repository.get_account_by_id", new=AsyncMock(return_value=_ACTIVE_ROW)):
        response = await async_client.get(
            "/api/accounts/acc_test_001/balance",
            headers={"Authorization": f"Bearer {token}"},
        )

    assert response.status_code == 200
    assert response.headers.get("cache-control") == "no-store"


@pytest.mark.asyncio
async def test_401_response_shape(async_client):
    response = await async_client.get("/api/accounts/acc_test_001/balance")

    assert response.status_code == 401
    body = response.json()
    assert set(body.keys()) == _ERROR_KEYS
    assert body["code"] == "UNAUTHORIZED"


@pytest.mark.asyncio
async def test_403_forbidden_response_shape(async_client, make_token):
    token = make_token("wrong-sub")
    with patch("src.repositories.account_repository.get_account_by_id", new=AsyncMock(return_value=_ACTIVE_ROW)):
        response = await async_client.get(
            "/api/accounts/acc_test_001/balance",
            headers={"Authorization": f"Bearer {token}"},
        )

    assert response.status_code == 403
    body = response.json()
    assert set(body.keys()) == _ERROR_KEYS
    assert body["code"] == "FORBIDDEN"


@pytest.mark.asyncio
async def test_404_response_shape(async_client, make_token):
    token = make_token("customer-sub-abc")
    with patch("src.repositories.account_repository.get_account_by_id", new=AsyncMock(return_value=None)):
        response = await async_client.get(
            "/api/accounts/acc_does_not_exist/balance",
            headers={"Authorization": f"Bearer {token}"},
        )

    assert response.status_code == 404
    body = response.json()
    assert set(body.keys()) == _ERROR_KEYS
    assert body["code"] == "NOT_FOUND"


@pytest.mark.asyncio
async def test_429_has_retry_after_header(async_client, make_token):
    token = make_token("customer-sub-abc")
    with patch("src.repositories.account_repository.get_account_by_id", new=AsyncMock(return_value=_ACTIVE_ROW)):
        responses = []
        for _ in range(62):
            r = await async_client.get(
                "/api/accounts/acc_test_001/balance",
                headers={"Authorization": f"Bearer {token}"},
            )
            responses.append(r)

    rate_limited = [r for r in responses if r.status_code == 429]
    assert rate_limited, "Expected at least one 429 response after 60 requests"
    assert "retry-after" in rate_limited[0].headers

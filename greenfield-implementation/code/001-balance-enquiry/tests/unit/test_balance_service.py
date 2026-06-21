from datetime import datetime, timezone
from decimal import Decimal
from unittest.mock import AsyncMock, patch

import pytest

from src.core.exceptions import AccountSuspendedException, ForbiddenException, NotFoundException
from src.models.account import AccountRow, BalanceResponse
from src.services.balance_service import get_balance

_ACTIVE_ROW = AccountRow(
    account_id="acc_test_001",
    owner_subject="customer-sub-abc",
    available_balance=Decimal("1024.50"),
    currency="GBP",
    status="active",
    last_updated_at=datetime(2026, 6, 21, 14, 32, 0, tzinfo=timezone.utc),
)

_SUSPENDED_ROW = AccountRow(
    account_id="acc_test_002",
    owner_subject="customer-sub-xyz",
    available_balance=Decimal("500.00"),
    currency="USD",
    status="suspended",
    last_updated_at=datetime(2026, 6, 21, 10, 0, 0, tzinfo=timezone.utc),
)


@pytest.fixture
def mock_conn():
    return AsyncMock()


@pytest.mark.asyncio
async def test_happy_path_returns_balance_response(mock_conn):
    with patch("src.services.balance_service.account_repository.get_account_by_id", new=AsyncMock(return_value=_ACTIVE_ROW)):
        result = await get_balance("acc_test_001", "customer-sub-abc", mock_conn)

    assert isinstance(result, BalanceResponse)
    assert result.accountId == "acc_test_001"
    assert result.availableBalance == Decimal("1024.50")
    assert result.currency == "GBP"
    assert result.lastUpdatedAt == datetime(2026, 6, 21, 14, 32, 0, tzinfo=timezone.utc)


@pytest.mark.asyncio
async def test_account_not_found_raises_not_found(mock_conn):
    with patch("src.services.balance_service.account_repository.get_account_by_id", new=AsyncMock(return_value=None)):
        with pytest.raises(NotFoundException) as exc_info:
            await get_balance("acc_nonexistent", "customer-sub-abc", mock_conn)

    assert exc_info.value.code == "NOT_FOUND"


@pytest.mark.asyncio
async def test_ownership_mismatch_raises_forbidden(mock_conn):
    with patch("src.services.balance_service.account_repository.get_account_by_id", new=AsyncMock(return_value=_ACTIVE_ROW)):
        with pytest.raises(ForbiddenException) as exc_info:
            await get_balance("acc_test_001", "some-other-sub", mock_conn)

    assert exc_info.value.code == "FORBIDDEN"


@pytest.mark.asyncio
async def test_suspended_account_raises_account_suspended(mock_conn):
    with patch("src.services.balance_service.account_repository.get_account_by_id", new=AsyncMock(return_value=_SUSPENDED_ROW)):
        with pytest.raises(AccountSuspendedException) as exc_info:
            await get_balance("acc_test_002", "customer-sub-xyz", mock_conn)

    assert exc_info.value.code == "ACCOUNT_SUSPENDED"


@pytest.mark.asyncio
async def test_ownership_checked_before_suspension(mock_conn):
    """Ownership mismatch must fire before suspension check (Constitution § Auth & Access Control)."""
    with patch("src.services.balance_service.account_repository.get_account_by_id", new=AsyncMock(return_value=_SUSPENDED_ROW)):
        with pytest.raises(ForbiddenException):
            await get_balance("acc_test_002", "wrong-sub", mock_conn)

import asyncpg

from src.core.exceptions import AccountSuspendedException, ForbiddenException, NotFoundException
from src.models.account import BalanceResponse
from src.repositories import account_repository


async def get_balance(account_id: str, jwt_sub: str, conn: asyncpg.Connection) -> BalanceResponse:
    """Return the balance for account_id after ownership and suspension checks.

    Authorization order (Constitution § Authorization & Access Control):
      1. Fetch full row (never filter WHERE by owner to avoid timing attacks)
      2. 404 if account does not exist
      3. 403 FORBIDDEN if JWT sub does not match owner_subject
      4. 403 ACCOUNT_SUSPENDED if account is suspended
      5. Return BalanceResponse
    """
    row = await account_repository.get_account_by_id(conn, account_id)

    if row is None:
        raise NotFoundException(code="NOT_FOUND", message="Account not found.")

    if row.owner_subject != jwt_sub:
        raise ForbiddenException(code="FORBIDDEN", message="You do not have access to this account.")

    if row.status == "suspended":
        raise AccountSuspendedException(
            code="ACCOUNT_SUSPENDED",
            message="This account has been suspended. Contact support.",
        )

    return BalanceResponse(
        accountId=row.account_id,
        availableBalance=row.available_balance,
        currency=row.currency,
        lastUpdatedAt=row.last_updated_at,
    )

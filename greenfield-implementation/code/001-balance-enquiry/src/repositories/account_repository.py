from decimal import Decimal

import asyncpg

from src.models.account import AccountRow

_QUERY = """
    SELECT account_id,
           owner_subject,
           available_balance,
           currency,
           status,
           last_updated_at
    FROM   accounts
    WHERE  account_id = $1
"""


async def get_account_by_id(conn: asyncpg.Connection, account_id: str) -> AccountRow | None:
    """Primary-key lookup on accounts. Returns AccountRow or None if not found.

    Authorization is NOT performed here — ownership and suspension checks are
    applied in the service layer to prevent timing-based account enumeration.
    """
    row = await conn.fetchrow(_QUERY, account_id)
    if row is None:
        return None
    return AccountRow(
        account_id=row["account_id"],
        owner_subject=row["owner_subject"],
        available_balance=Decimal(str(row["available_balance"])),
        currency=row["currency"],
        status=row["status"],
        last_updated_at=row["last_updated_at"],
    )

from dataclasses import dataclass
from datetime import datetime
from decimal import Decimal


@dataclass
class AccountRow:
    """Internal representation of a row returned by the accounts DB query."""
    account_id: str
    owner_subject: str
    available_balance: Decimal
    currency: str
    status: str
    last_updated_at: datetime | None


from pydantic import BaseModel


class BalanceResponse(BaseModel):
    """API response payload — shape locked by Constitution Principle II."""
    accountId: str
    availableBalance: Decimal
    currency: str
    lastUpdatedAt: datetime | None

    model_config = {"populate_by_name": True}

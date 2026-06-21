from fastapi import APIRouter, Depends, Request

import asyncpg

from src.auth.jwt_handler import get_jwt_sub
from src.core.database import get_connection
from src.middleware.rate_limiter import limiter
from src.models.account import BalanceResponse
from src.services import balance_service

router = APIRouter(tags=["Balance"])


@router.get(
    "/accounts/{accountId}/balance",
    response_model=BalanceResponse,
    summary="Get account balance",
    responses={
        401: {"description": "Missing or invalid JWT token"},
        403: {"description": "Ownership mismatch or account suspended"},
        404: {"description": "Account not found"},
        429: {"description": "Rate limit exceeded"},
    },
)
@limiter.limit("60/minute")
async def get_account_balance(
    request: Request,
    accountId: str,
    jwt_sub: str = Depends(get_jwt_sub),
    conn: asyncpg.Connection = Depends(get_connection),
) -> BalanceResponse:
    return await balance_service.get_balance(accountId, jwt_sub, conn)

from contextlib import asynccontextmanager
from typing import AsyncGenerator

import asyncpg

from src.core.config import settings

_pool: asyncpg.Pool | None = None


async def init_pool() -> None:
    global _pool
    _pool = await asyncpg.create_pool(settings.database_url)


async def close_pool() -> None:
    global _pool
    if _pool is not None:
        await _pool.close()
        _pool = None


@asynccontextmanager
async def _acquire() -> AsyncGenerator[asyncpg.Connection, None]:
    if _pool is None:
        raise RuntimeError("Database pool is not initialised")
    async with _pool.acquire() as conn:
        yield conn


async def get_connection() -> AsyncGenerator[asyncpg.Connection, None]:
    async with _acquire() as conn:
        yield conn

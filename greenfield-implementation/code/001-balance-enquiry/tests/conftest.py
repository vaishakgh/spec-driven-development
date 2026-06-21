import os
from datetime import datetime, timedelta, timezone

import asyncpg
import jwt
import pytest
import pytest_asyncio
from httpx import ASGITransport, AsyncClient

from src.core.config import settings
from src.core.database import _pool, close_pool, init_pool
from src.main import app


@pytest.fixture(scope="session")
def make_token():
    """Factory fixture: returns a JWT signed with the test JWT_SECRET."""
    def _make(sub: str, expires_in_seconds: int = 3600) -> str:
        payload = {
            "sub": sub,
            "exp": datetime.now(tz=timezone.utc) + timedelta(seconds=expires_in_seconds),
            "iat": datetime.now(tz=timezone.utc),
        }
        return jwt.encode(payload, settings.jwt_secret, algorithm=settings.jwt_algorithm)

    return _make


@pytest_asyncio.fixture(scope="function")
async def async_client():
    """httpx AsyncClient wrapping the FastAPI app — no live server needed."""
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        yield client


@pytest_asyncio.fixture(scope="session")
async def db_conn():
    """asyncpg connection to the test database. Requires TEST_DATABASE_URL env var."""
    test_db_url = os.environ.get("TEST_DATABASE_URL", settings.database_url)
    conn = await asyncpg.connect(test_db_url)
    yield conn
    await conn.close()

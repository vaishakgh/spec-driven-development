import jwt
from fastapi import Request
from fastapi.responses import JSONResponse
from slowapi import Limiter
from slowapi.errors import RateLimitExceeded

from src.core.config import settings


def _get_rate_limit_key(request: Request) -> str:
    """Key rate limits on JWT sub claim; fall back to client IP for unauthenticated requests."""
    auth_header = request.headers.get("Authorization", "")
    if auth_header.startswith("Bearer "):
        token = auth_header[7:]
        try:
            payload = jwt.decode(
                token,
                settings.jwt_secret,
                algorithms=[settings.jwt_algorithm],
            )
            sub = payload.get("sub")
            if sub:
                return str(sub)
        except Exception:
            pass
    return request.client.host if request.client else "unknown"


limiter = Limiter(key_func=_get_rate_limit_key)


def rate_limit_exceeded_handler(request: Request, exc: RateLimitExceeded) -> JSONResponse:
    retry_after = getattr(exc, "retry_after", 60)
    return JSONResponse(
        status_code=429,
        content={"code": "RATE_LIMIT_EXCEEDED", "message": f"Too many requests. Please retry after {retry_after} seconds."},
        headers={"Retry-After": str(retry_after)},
    )

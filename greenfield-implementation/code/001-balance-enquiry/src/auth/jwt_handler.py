from fastapi import Request
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

import jwt
from jwt.exceptions import ExpiredSignatureError, InvalidTokenError

from src.core.config import settings
from src.core.exceptions import UnauthorizedException

_bearer_scheme = HTTPBearer(auto_error=False)


def decode_jwt(token: str) -> str:
    """Decode a JWT and return the sub claim. Raises UnauthorizedException on any failure."""
    try:
        payload = jwt.decode(
            token,
            settings.jwt_secret,
            algorithms=[settings.jwt_algorithm],
        )
    except ExpiredSignatureError:
        raise UnauthorizedException(code="UNAUTHORIZED", message="Token has expired.")
    except InvalidTokenError:
        raise UnauthorizedException(code="UNAUTHORIZED", message="Invalid token.")

    sub = payload.get("sub")
    if not sub:
        raise UnauthorizedException(code="UNAUTHORIZED", message="Token is missing subject claim.")
    return str(sub)


async def get_jwt_sub(request: Request) -> str:
    """FastAPI dependency: extract and validate JWT from Authorization header, return sub."""
    auth_header = request.headers.get("Authorization", "")
    if not auth_header.startswith("Bearer "):
        raise UnauthorizedException(
            code="UNAUTHORIZED",
            message="A valid Bearer token is required.",
        )
    token = auth_header[7:]
    return decode_jwt(token)

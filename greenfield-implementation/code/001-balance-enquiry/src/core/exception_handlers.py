from fastapi import Request
from fastapi.responses import JSONResponse

from src.core.exceptions import (
    AccountSuspendedException,
    AppException,
    ForbiddenException,
    NotFoundException,
    UnauthorizedException,
)

_STATUS_MAP: dict[type[AppException], int] = {
    UnauthorizedException: 401,
    ForbiddenException: 403,
    AccountSuspendedException: 403,
    NotFoundException: 404,
}


def _make_handler(status_code: int):
    async def handler(request: Request, exc: AppException) -> JSONResponse:
        return JSONResponse(
            status_code=status_code,
            content={"code": exc.code, "message": exc.message},
        )

    return handler


unauthorized_handler = _make_handler(401)
forbidden_handler = _make_handler(403)
not_found_handler = _make_handler(404)


def register_exception_handlers(app) -> None:
    app.add_exception_handler(UnauthorizedException, unauthorized_handler)
    app.add_exception_handler(ForbiddenException, forbidden_handler)
    app.add_exception_handler(AccountSuspendedException, forbidden_handler)
    app.add_exception_handler(NotFoundException, not_found_handler)

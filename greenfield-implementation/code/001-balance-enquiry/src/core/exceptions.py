class AppException(Exception):
    def __init__(self, code: str, message: str) -> None:
        self.code = code
        self.message = message
        super().__init__(message)


class UnauthorizedException(AppException):
    pass


class ForbiddenException(AppException):
    pass


class AccountSuspendedException(AppException):
    pass


class NotFoundException(AppException):
    pass

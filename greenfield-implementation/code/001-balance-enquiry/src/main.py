from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.responses import Response
from slowapi.errors import RateLimitExceeded

from src.api.routes.balance import router as balance_router
from src.core.database import close_pool, init_pool
from src.core.exception_handlers import register_exception_handlers
from src.middleware.rate_limiter import limiter, rate_limit_exceeded_handler


@asynccontextmanager
async def lifespan(app: FastAPI):
    await init_pool()
    yield
    await close_pool()


app = FastAPI(
    title="Balance Enquiry API",
    version="1.0.0",
    lifespan=lifespan,
)

# slowapi state — required for @limiter.limit() decorators to function
app.state.limiter = limiter
app.add_exception_handler(RateLimitExceeded, rate_limit_exceeded_handler)

# Register domain exception handlers (401, 403, 404)
register_exception_handlers(app)


@app.middleware("http")
async def cache_control_middleware(request: Request, call_next) -> Response:
    """Append Cache-Control: no-store to every response (Constitution Principle II)."""
    response = await call_next(request)
    response.headers["Cache-Control"] = "no-store"
    return response

app.include_router(balance_router, prefix="/api")

"""ASGI middleware: stats collection (FR-002) and rate limiting."""

from __future__ import annotations

import logging
import math
import time
from datetime import UTC, datetime

from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import JSONResponse, Response

from crypto_price_feed.stats.models import RequestRecord
from crypto_price_feed.stats.store import StatsStore
from server.rate_limiter import RateLimiter

logger = logging.getLogger(__name__)

_EXEMPT_PREFIXES = ("/stats", "/docs", "/openapi.json", "/redoc")


class StatsMiddleware(BaseHTTPMiddleware):
    """Intercepts all requests; /stats paths are skipped to avoid self-pollution (FR-002-4 AC4)."""

    def __init__(self, app, store: StatsStore) -> None:
        super().__init__(app)
        self._store = store

    async def dispatch(self, request: Request, call_next) -> Response:
        skip = request.url.path.startswith("/stats")
        if skip:
            return await call_next(request)

        t0 = time.perf_counter()
        response = await call_next(request)
        duration_ms = (time.perf_counter() - t0) * 1000

        try:
            record = RequestRecord(
                endpoint=request.url.path,
                status_code=response.status_code,
                duration_ms=duration_ms,
                recorded_at=datetime.now(tz=UTC),
            )
            self._store.append(record)
        except Exception:
            logger.exception("stats collection failed for %s", request.url.path)

        return response


class RateLimitMiddleware(BaseHTTPMiddleware):
    """Fixed-window rate limiter middleware. Exempt paths bypass the limit."""

    def __init__(self, app, limiter: RateLimiter) -> None:
        super().__init__(app)
        self._limiter = limiter

    async def dispatch(self, request: Request, call_next) -> Response:
        path = request.url.path
        if any(path.startswith(prefix) for prefix in _EXEMPT_PREFIXES):
            return await call_next(request)

        # Prefer X-Forwarded-For / X-Real-IP set by a trusted reverse proxy.
        client_ip = (
            request.headers.get("X-Forwarded-For", "").split(",")[0].strip()
            or request.headers.get("X-Real-IP", "").strip()
            or (request.client.host if request.client else None)
        )
        if not client_ip:
            return JSONResponse(status_code=400, content={"detail": "Unable to determine client IP"})

        allowed, retry_secs = self._limiter.check(client_ip)
        if not allowed:
            return JSONResponse(
                status_code=429,
                content={"detail": "Too Many Requests"},
                headers={"Retry-After": str(math.ceil(retry_secs))},
            )

        return await call_next(request)

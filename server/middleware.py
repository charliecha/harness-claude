"""ASGI stats middleware: auto-collects call counts and latencies for all endpoints (FR-002)."""

from __future__ import annotations

import logging
import time
from datetime import UTC, datetime

from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import Response

from crypto_price_feed.stats.models import RequestRecord
from crypto_price_feed.stats.store import StatsStore

logger = logging.getLogger(__name__)


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

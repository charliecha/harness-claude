"""GET /stats query endpoint (FR-002-4)."""

from __future__ import annotations

from fastapi import APIRouter
from fastapi.responses import JSONResponse

from crypto_price_feed.stats.query import StatsQuery
from crypto_price_feed.stats.windows import SUPPORTED_WINDOWS


def make_stats_router(query: StatsQuery) -> APIRouter:
    router = APIRouter()

    @router.get("/stats")
    async def get_stats(endpoint: str | None = None, window: str | None = None):
        if endpoint is None:
            return JSONResponse(
                status_code=400,
                content={"error": "missing_parameter", "missing": "endpoint"},
            )
        if window is None:
            return JSONResponse(
                status_code=400,
                content={
                    "error": "missing_parameter",
                    "missing": "window",
                    "allowed_values": list(SUPPORTED_WINDOWS),
                },
            )
        if window not in SUPPORTED_WINDOWS:
            return JSONResponse(
                status_code=400,
                content={
                    "error": "invalid_parameter",
                    "param": "window",
                    "allowed_values": list(SUPPORTED_WINDOWS),
                },
            )

        result = query.get(endpoint, window)
        if result is None:
            return {"endpoint": endpoint, "window": window, "message": "no data"}

        return {
            "endpoint": result.endpoint,
            "window": result.window,
            "total_requests": result.total_requests,
            "error_requests": result.error_requests,
            "error_rate": result.error_rate,
            "mean_ms": result.mean_ms,
            "p95_ms": result.p95_ms,
            "p99_ms": result.p99_ms,
        }

    return router

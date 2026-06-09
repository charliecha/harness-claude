"""FastAPI application factory (FR-002)."""

from __future__ import annotations

import os

from fastapi import FastAPI

from crypto_price_feed.stats._retention import RetentionWorker
from crypto_price_feed.stats.query import StatsQuery
from crypto_price_feed.stats.store import InMemoryStatsStore
from server.middleware import RateLimitMiddleware, StatsMiddleware
from server.rate_limiter import RateLimiter
from server.routes import prices
from server.routes.health import router as health_router
from server.routes.stats import make_stats_router

_DEFAULT_RATE_LIMIT = 60
_DEFAULT_RATE_WINDOW = 60


def create_app() -> FastAPI:
    store = InMemoryStatsStore()
    query = StatsQuery(store)
    retention = RetentionWorker(store)

    rate_limit = int(os.environ.get("RATE_LIMIT_REQUESTS", _DEFAULT_RATE_LIMIT))
    rate_window = int(os.environ.get("RATE_LIMIT_WINDOW", _DEFAULT_RATE_WINDOW))
    limiter = RateLimiter(limit=rate_limit, window=rate_window)

    app = FastAPI(title="crypto-price-feed")
    app.add_middleware(StatsMiddleware, store=store)
    app.add_middleware(RateLimitMiddleware, limiter=limiter)
    app.include_router(prices.router)
    app.include_router(health_router)
    app.include_router(make_stats_router(query))

    @app.on_event("startup")
    async def _start_retention() -> None:
        retention.start()

    @app.on_event("shutdown")
    async def _stop_retention() -> None:
        retention.stop()

    return app


app = create_app()

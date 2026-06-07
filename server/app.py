"""FastAPI application factory (FR-002)."""

from __future__ import annotations

from fastapi import FastAPI

from crypto_price_feed.stats._retention import RetentionWorker
from crypto_price_feed.stats.query import StatsQuery
from crypto_price_feed.stats.store import InMemoryStatsStore
from server.middleware import StatsMiddleware
from server.routes import prices
from server.routes.stats import make_stats_router


def create_app() -> FastAPI:
    store = InMemoryStatsStore()
    query = StatsQuery(store)
    retention = RetentionWorker(store)

    app = FastAPI(title="crypto-price-feed")
    app.add_middleware(StatsMiddleware, store=store)
    app.include_router(prices.router)
    app.include_router(make_stats_router(query))

    @app.on_event("startup")
    async def _start_retention() -> None:
        retention.start()

    @app.on_event("shutdown")
    async def _stop_retention() -> None:
        retention.stop()

    return app


app = create_app()

"""Tests for StatsMiddleware (FR-002)."""

from __future__ import annotations

from datetime import UTC, datetime, timedelta

from fastapi import FastAPI
from fastapi.testclient import TestClient

from crypto_price_feed.stats.store import InMemoryStatsStore
from server.middleware import StatsMiddleware


def _make_app(store: InMemoryStatsStore) -> FastAPI:
    app = FastAPI()
    app.add_middleware(StatsMiddleware, store=store)

    @app.get("/prices/single")
    async def single():
        return {"ok": True}

    @app.get("/prices/error")
    async def error():
        from fastapi.responses import JSONResponse
        return JSONResponse(status_code=500, content={"err": "oops"})

    @app.get("/stats")
    async def stats():
        return {"stats": True}

    return app


class TestStatsMiddleware:
    def test_records_successful_request(self) -> None:
        store = InMemoryStatsStore()
        client = TestClient(_make_app(store))
        client.get("/prices/single")
        since = datetime.now(tz=UTC) - timedelta(minutes=1)
        records = store.query("/prices/single", since)
        assert len(records) == 1
        assert records[0].status_code == 200
        assert records[0].duration_ms >= 0

    def test_records_error_response(self) -> None:
        store = InMemoryStatsStore()
        client = TestClient(_make_app(store))
        client.get("/prices/error")
        since = datetime.now(tz=UTC) - timedelta(minutes=1)
        records = store.query("/prices/error", since)
        assert len(records) == 1
        assert records[0].status_code == 500

    def test_stats_path_not_recorded(self) -> None:
        store = InMemoryStatsStore()
        client = TestClient(_make_app(store))
        client.get("/stats")
        since = datetime.now(tz=UTC) - timedelta(minutes=1)
        records = store.query("/stats", since)
        assert len(records) == 0

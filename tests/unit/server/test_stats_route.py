"""Tests for GET /stats route (FR-002-4)."""

from __future__ import annotations

from datetime import UTC, datetime, timedelta

from fastapi.testclient import TestClient

from crypto_price_feed.stats.models import RequestRecord
from crypto_price_feed.stats.query import StatsQuery
from crypto_price_feed.stats.store import InMemoryStatsStore


def _seed(store: InMemoryStatsStore, n: int = 5, status: int = 200, duration_ms: float = 100.0) -> None:
    for _ in range(n):
        store.append(
            RequestRecord(
                endpoint="/prices/single",
                status_code=status,
                duration_ms=duration_ms,
                recorded_at=datetime.now(tz=UTC) - timedelta(seconds=10),
            )
        )


class TestStatsRoute:
    def setup_method(self) -> None:
        self.store = InMemoryStatsStore()
        # create_app builds its own store; inject our store directly instead
        from fastapi import FastAPI

        from server.middleware import StatsMiddleware
        from server.routes import prices
        from server.routes.stats import make_stats_router

        self.app = FastAPI()
        self.app.add_middleware(StatsMiddleware, store=self.store)
        self.app.include_router(prices.router)
        query = StatsQuery(self.store)
        self.app.include_router(make_stats_router(query))
        self.client = TestClient(self.app, raise_server_exceptions=False)

    def test_returns_stats_with_data(self) -> None:
        _seed(self.store)
        resp = self.client.get("/stats?endpoint=/prices/single&window=1m")
        assert resp.status_code == 200
        data = resp.json()
        assert data["total_requests"] == 5
        assert data["error_rate"] == 0.0
        assert "mean_ms" in data

    def test_returns_no_data_message(self) -> None:
        resp = self.client.get("/stats?endpoint=/prices/single&window=1m")
        assert resp.status_code == 200
        assert resp.json()["message"] == "no data"

    def test_missing_endpoint_param_returns_400(self) -> None:
        resp = self.client.get("/stats?window=1m")
        assert resp.status_code == 400
        assert resp.json()["missing"] == "endpoint"

    def test_missing_window_param_returns_400(self) -> None:
        resp = self.client.get("/stats?endpoint=/prices/single")
        assert resp.status_code == 400
        assert resp.json()["missing"] == "window"

    def test_invalid_window_returns_400(self) -> None:
        resp = self.client.get("/stats?endpoint=/prices/single&window=99h")
        assert resp.status_code == 400

    def test_stats_query_does_not_pollute_counts(self) -> None:
        _seed(self.store, n=3)
        # /stats queries must not increment /stats endpoint counters
        self.client.get("/stats?endpoint=/prices/single&window=1m")
        self.client.get("/stats?endpoint=/prices/single&window=1m")
        since = datetime.now(tz=UTC) - timedelta(minutes=1)
        stats_records = self.store.query("/stats", since)
        assert len(stats_records) == 0
        prices_records = self.store.query("/prices/single", since)
        assert len(prices_records) == 3

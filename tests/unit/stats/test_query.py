"""Tests for StatsQuery (FR-002)."""

from __future__ import annotations

from datetime import UTC, datetime, timedelta

import pytest

from crypto_price_feed.stats.models import RequestRecord
from crypto_price_feed.stats.query import StatsQuery
from crypto_price_feed.stats.store import InMemoryStatsStore


def _add_record(
    store: InMemoryStatsStore,
    status: int = 200,
    duration_ms: float = 100.0,
    offset_seconds: int = 10,
    endpoint: str = "/prices/single",
) -> None:
    store.append(
        RequestRecord(
            endpoint=endpoint,
            status_code=status,
            duration_ms=duration_ms,
            recorded_at=datetime.now(tz=UTC) - timedelta(seconds=offset_seconds),
        )
    )


class TestStatsQuery:
    def test_returns_none_when_no_data(self) -> None:
        store = InMemoryStatsStore()
        q = StatsQuery(store)
        assert q.get("/prices/single", "1m") is None

    def test_counts_total_and_errors(self) -> None:
        store = InMemoryStatsStore()
        _add_record(store, status=200)
        _add_record(store, status=500)
        _add_record(store, status=404)
        q = StatsQuery(store)
        result = q.get("/prices/single", "1m")
        assert result is not None
        assert result.total_requests == 3
        assert result.error_requests == 2
        assert result.error_rate == round(2 / 3, 4)

    def test_mean_ms_correct(self) -> None:
        store = InMemoryStatsStore()
        _add_record(store, duration_ms=100.0)
        _add_record(store, duration_ms=200.0)
        q = StatsQuery(store)
        result = q.get("/prices/single", "1m")
        assert result is not None
        assert result.mean_ms == 150.0

    def test_p95_p99_single_record(self) -> None:
        store = InMemoryStatsStore()
        _add_record(store, duration_ms=42.0)
        q = StatsQuery(store)
        result = q.get("/prices/single", "1m")
        assert result is not None
        assert result.p95_ms == 42.0
        assert result.p99_ms == 42.0

    def test_window_filters_old_records(self) -> None:
        store = InMemoryStatsStore()
        _add_record(store, offset_seconds=10)    # 在 1m 窗口内
        _add_record(store, offset_seconds=120)   # 超出 1m 窗口
        q = StatsQuery(store)
        result = q.get("/prices/single", "1m")
        assert result is not None
        assert result.total_requests == 1

    def test_invalid_window_raises(self) -> None:
        store = InMemoryStatsStore()
        q = StatsQuery(store)
        with pytest.raises(ValueError):
            q.get("/prices/single", "99h")

    def test_zero_errors_gives_zero_rate(self) -> None:
        store = InMemoryStatsStore()
        _add_record(store, status=200)
        q = StatsQuery(store)
        result = q.get("/prices/single", "1m")
        assert result is not None
        assert result.error_rate == 0.0

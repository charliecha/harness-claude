"""Tests for InMemoryStatsStore (FR-002)."""

from __future__ import annotations

from datetime import UTC, datetime, timedelta

from crypto_price_feed.stats.models import RequestRecord
from crypto_price_feed.stats.store import InMemoryStatsStore


def _record(
    endpoint: str = "/prices/single",
    status: int = 200,
    duration_ms: float = 50.0,
    offset_seconds: int = 0,
) -> RequestRecord:
    return RequestRecord(
        endpoint=endpoint,
        status_code=status,
        duration_ms=duration_ms,
        recorded_at=datetime.now(tz=UTC) - timedelta(seconds=offset_seconds),
    )


class TestInMemoryStatsStore:
    def test_append_and_query(self) -> None:
        store = InMemoryStatsStore()
        r = _record()
        store.append(r)
        since = datetime.now(tz=UTC) - timedelta(minutes=1)
        results = store.query("/prices/single", since)
        assert r in results

    def test_query_filters_by_endpoint(self) -> None:
        store = InMemoryStatsStore()
        store.append(_record(endpoint="/prices/single"))
        store.append(_record(endpoint="/prices/batch"))
        since = datetime.now(tz=UTC) - timedelta(minutes=1)
        assert len(store.query("/prices/single", since)) == 1
        assert len(store.query("/prices/batch", since)) == 1

    def test_query_filters_by_since(self) -> None:
        store = InMemoryStatsStore()
        store.append(_record(offset_seconds=120))  # 2 分钟前
        store.append(_record(offset_seconds=10))   # 10 秒前
        since = datetime.now(tz=UTC) - timedelta(minutes=1)
        results = store.query("/prices/single", since)
        assert len(results) == 1

    def test_purge_before_removes_old_records(self) -> None:
        store = InMemoryStatsStore()
        store.append(_record(offset_seconds=200))
        store.append(_record(offset_seconds=10))
        cutoff = datetime.now(tz=UTC) - timedelta(seconds=60)
        removed = store.purge_before(cutoff)
        assert removed == 1
        since = datetime.now(tz=UTC) - timedelta(days=1)
        assert len(store.query("/prices/single", since)) == 1

    def test_query_returns_copy(self) -> None:
        store = InMemoryStatsStore()
        store.append(_record())
        since = datetime.now(tz=UTC) - timedelta(minutes=1)
        r1 = store.query("/prices/single", since)
        r2 = store.query("/prices/single", since)
        assert r1 is not r2

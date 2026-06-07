"""Stats aggregation query (FR-002)."""

from __future__ import annotations

import statistics
from datetime import UTC, datetime

from crypto_price_feed.stats.models import WindowStats
from crypto_price_feed.stats.store import StatsStore
from crypto_price_feed.stats.windows import SUPPORTED_WINDOWS, window_to_timedelta


def _percentile(sorted_data: list[float], p: float) -> float:
    """Linear interpolation percentile over actual samples (FR-002-2 AC3)."""
    n = len(sorted_data)
    if n == 1:
        return sorted_data[0]
    # statistics.quantiles requires >= 2 data points; n=100 gives per-percentile buckets
    qs = statistics.quantiles(sorted_data, n=100, method="inclusive")
    idx = max(0, min(int(p) - 1, len(qs) - 1))
    return qs[idx]


class StatsQuery:
    def __init__(self, store: StatsStore) -> None:
        self._store = store

    def get(self, endpoint: str, window: str) -> WindowStats | None:
        if window not in SUPPORTED_WINDOWS:
            raise ValueError(f"unsupported window: {window!r}")

        since = datetime.now(tz=UTC) - window_to_timedelta(window)
        records = self._store.query(endpoint, since)

        if not records:
            return None

        total = len(records)
        errors = sum(1 for r in records if r.status_code >= 400)
        error_rate = round(errors / total, 4)

        durations = sorted(r.duration_ms for r in records)
        mean_ms = sum(durations) / total
        p95_ms = _percentile(durations, 95)
        p99_ms = _percentile(durations, 99)

        return WindowStats(
            endpoint=endpoint,
            window=window,
            total_requests=total,
            error_requests=errors,
            error_rate=error_rate,
            mean_ms=mean_ms,
            p95_ms=p95_ms,
            p99_ms=p99_ms,
        )

"""Thread-safe in-memory stats store (FR-002)."""

from __future__ import annotations

import threading
from collections import deque
from collections.abc import Sequence
from datetime import datetime
from typing import Protocol

from crypto_price_feed.stats.models import RequestRecord


class StatsStore(Protocol):
    def append(self, record: RequestRecord) -> None: ...

    def query(self, endpoint: str, since: datetime) -> Sequence[RequestRecord]: ...

    def purge_before(self, cutoff: datetime) -> int: ...


class InMemoryStatsStore:
    def __init__(self) -> None:
        self._records: deque[RequestRecord] = deque()
        self._lock = threading.Lock()

    def append(self, record: RequestRecord) -> None:
        with self._lock:
            self._records.append(record)

    def query(self, endpoint: str, since: datetime) -> list[RequestRecord]:
        with self._lock:
            return [
                r
                for r in self._records
                if r.endpoint == endpoint and r.recorded_at >= since
            ]

    def purge_before(self, cutoff: datetime) -> int:
        with self._lock:
            before = len(self._records)
            self._records = deque(r for r in self._records if r.recorded_at >= cutoff)
            return before - len(self._records)

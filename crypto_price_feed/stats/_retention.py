"""Background retention worker: purges raw samples older than 7 days (FR-002 NFR-002-3)."""

from __future__ import annotations

import threading
from datetime import UTC, datetime, timedelta

from crypto_price_feed.stats.store import StatsStore

_RETENTION_DAYS = 7
_INTERVAL_SECONDS = 300  # 每 5 分钟清理一次


class RetentionWorker:
    def __init__(self, store: StatsStore) -> None:
        self._store = store
        self._stop = threading.Event()
        self._thread = threading.Thread(target=self._run, daemon=True)

    def start(self) -> None:
        self._thread.start()

    def stop(self) -> None:
        self._stop.set()

    def _run(self) -> None:
        while not self._stop.wait(_INTERVAL_SECONDS):
            cutoff = datetime.now(tz=UTC) - timedelta(days=_RETENTION_DAYS)
            self._store.purge_before(cutoff)

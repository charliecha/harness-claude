"""Fixed-window rate limiter keyed by client identifier (IP address)."""

from __future__ import annotations

import threading
import time
from dataclasses import dataclass, field


@dataclass
class _Window:
    count: int = 0
    start: float = field(default_factory=time.monotonic)


class RateLimiter:
    """Thread-safe fixed-window rate limiter.

    Args:
        limit:  Maximum requests allowed per window. 0 means block all.
        window: Window duration in seconds.
    """

    def __init__(self, limit: int, window: int) -> None:
        self._limit = limit
        self._window = window
        self._windows: dict[str, _Window] = {}
        self._lock = threading.Lock()

    def check(self, key: str) -> tuple[bool, float]:
        """Atomically check and record a request.

        Returns:
            (allowed, retry_after_seconds) — both values under a single lock
            acquisition, eliminating the TOCTOU race between is_allowed and
            retry_after.
        """
        with self._lock:
            now = time.monotonic()
            w = self._windows.get(key)

            # Passive sweep: remove other expired windows to bound memory growth.
            if len(self._windows) > 1:
                expired = [k for k, v in self._windows.items() if now - v.start >= self._window]
                for k in expired:
                    del self._windows[k]

            if w is None or now - w.start >= self._window:
                self._windows[key] = _Window(count=1, start=now)
                return self._limit > 0, 0.0

            w.count += 1
            if w.count <= self._limit:
                return True, 0.0

            remaining = max(0.0, self._window - (now - w.start))
            return False, remaining

    def is_allowed(self, key: str) -> bool:
        """Return True if the request is within the rate limit."""
        allowed, _ = self.check(key)
        return allowed

    def retry_after(self, key: str) -> float:
        """Return seconds until the current window resets; 0 if requests are still allowed."""
        with self._lock:
            w = self._windows.get(key)
            if w is None or w.count < self._limit:
                return 0
            remaining = self._window - (time.monotonic() - w.start)
            return max(0.0, remaining)

    def reset(self, key: str) -> None:
        """Clear the rate-limit state for a given key."""
        with self._lock:
            self._windows.pop(key, None)

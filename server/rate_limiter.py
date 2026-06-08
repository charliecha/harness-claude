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

    def is_allowed(self, key: str) -> bool:
        """Return True if the request is within the rate limit."""
        with self._lock:
            now = time.monotonic()
            w = self._windows.get(key)
            if w is None or now - w.start >= self._window:
                self._windows[key] = _Window(count=1, start=now)
                return self._limit > 0
            w.count += 1
            return w.count <= self._limit

    def retry_after(self, key: str) -> float:
        """Return seconds until the current window resets; 0 if not rate-limited."""
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

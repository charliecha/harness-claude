"""Time window constants and helpers (FR-002)."""

from __future__ import annotations

from datetime import timedelta

SUPPORTED_WINDOWS = ("1m", "5m", "1h", "24h")

_WINDOW_DELTAS: dict[str, timedelta] = {
    "1m": timedelta(minutes=1),
    "5m": timedelta(minutes=5),
    "1h": timedelta(hours=1),
    "24h": timedelta(hours=24),
}


def window_to_timedelta(window: str) -> timedelta:
    if window not in _WINDOW_DELTAS:
        raise ValueError(f"unsupported window: {window!r}; allowed: {SUPPORTED_WINDOWS}")
    return _WINDOW_DELTAS[window]

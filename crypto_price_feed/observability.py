"""Structured logging + timing context manager (ADR-001 §3.5, NFR-005)."""

from __future__ import annotations

import logging
import time
from collections.abc import Iterator
from contextlib import contextmanager
from typing import Any

logger = logging.getLogger("crypto_price_feed")


@contextmanager
def measure(operation: str, **fields: Any) -> Iterator[dict[str, Any]]:
    """Time `operation` and emit a single structured log line on exit.

    Caller may mutate the yielded dict to add late fields (e.g. source_timestamp).
    `elapsed_ms` is filled automatically.
    """
    extra: dict[str, Any] = dict(fields)
    start = time.perf_counter()
    try:
        yield extra
    finally:
        elapsed_ms = int((time.perf_counter() - start) * 1000)
        extra["elapsed_ms"] = elapsed_ms
        logger.info("op=%s %s", operation, _format_fields(extra))


def _format_fields(fields: dict[str, Any]) -> str:
    """Render a fields dict as `k=v` pairs (no URL/secret leakage by convention)."""
    return " ".join(f"{k}={v!r}" for k, v in sorted(fields.items()))

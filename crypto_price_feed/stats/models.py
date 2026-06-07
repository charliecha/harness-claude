"""Stats domain models (FR-002)."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime


@dataclass(frozen=True, slots=True)
class RequestRecord:
    """Raw sample of a single request (retained for 7 days)."""

    endpoint: str
    status_code: int
    duration_ms: float
    recorded_at: datetime  # UTC, tz-aware


@dataclass(frozen=True, slots=True)
class WindowStats:
    """某端点在某时间窗口内的聚合统计结果。"""

    endpoint: str
    window: str
    total_requests: int
    error_requests: int
    error_rate: float
    mean_ms: float
    p95_ms: float
    p99_ms: float

"""GET /health endpoint (FR-003)."""

from __future__ import annotations

from datetime import UTC, datetime

import psutil
from fastapi import APIRouter

router = APIRouter(tags=["health"])

_VERSION = "0.1.0"


def _memory_ok() -> bool:
    """Return True if memory usage is below 90%."""
    return psutil.virtual_memory().percent < 90


@router.get("/health")
async def health():
    return {
        "status": "ok",
        "version": _VERSION,
        "timestamp": datetime.now(tz=UTC).isoformat(),
        "checks": {
            "memory": "healthy" if _memory_ok() else "degraded",
        },
    }

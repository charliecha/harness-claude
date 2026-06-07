"""Domain models for crypto_price_feed (ADR-001 §3.2)."""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from decimal import Decimal
from enum import Enum


class FiatCurrency(str, Enum):
    """Supported fiat currencies (FR-001-2 AC1)."""

    USD = "USD"
    EUR = "EUR"
    CNY = "CNY"


@dataclass(frozen=True, slots=True)
class Quote:
    """Single-symbol successful quote. Price uses Decimal to avoid float drift."""

    symbol: str
    fiat: FiatCurrency
    price: Decimal
    source_timestamp: datetime


@dataclass(frozen=True, slots=True)
class QuoteError:
    """Per-symbol failure for batch results (FR-001-3 AC4 / FR-001-4 AC3).

    `symbol` preserves the user's original input (FR-001-4 AC2).
    """

    symbol: str
    code: str
    message: str


QuoteResult = Quote | QuoteError


@dataclass(frozen=True, slots=True)
class BatchResult:
    """Batch query result. successes + failures together = input symbols, order preserved."""

    successes: list[Quote] = field(default_factory=list)
    failures: list[QuoteError] = field(default_factory=list)
    elapsed_ms: int = 0

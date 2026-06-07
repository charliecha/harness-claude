"""Exception hierarchy for crypto_price_feed (ADR-001 §3.4).

All exceptions carry `code`, `message`, `offending_input` (NFR-003).
"""

from __future__ import annotations

from typing import Any


class PriceFeedError(Exception):
    """Base class for all library exceptions."""

    code: str = "PRICE_FEED_ERROR"

    def __init__(self, message: str, offending_input: Any = None) -> None:
        super().__init__(message)
        self.message = message
        self.offending_input = offending_input

    def __str__(self) -> str:
        return f"[{self.code}] {self.message} (input={self.offending_input!r})"


class InvalidSymbol(PriceFeedError):
    """Symbol is empty, malformed, or unknown to the provider (FR-001-4)."""

    code = "INVALID_SYMBOL"


class UnsupportedFiat(PriceFeedError):
    """Requested fiat currency is not in FiatCurrency enum (FR-001-2 AC3)."""

    code = "UNSUPPORTED_FIAT"


class BatchSizeExceeded(PriceFeedError):
    """Batch request size exceeds the hard cap of 20 symbols (FR-001-3 AC2)."""

    code = "BATCH_SIZE_EXCEEDED"


class StaleData(PriceFeedError):
    """source_timestamp differs from now by more than 60 seconds (NFR-002)."""

    code = "STALE_DATA"


class ProviderError(PriceFeedError):
    """Upstream 5xx / network timeout / response parse failure."""

    code = "PROVIDER_ERROR"

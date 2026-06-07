"""crypto_price_feed — Real-time cryptocurrency price feed (FR-001 / ADR-001)."""

from crypto_price_feed.api import get_price, get_prices
from crypto_price_feed.exceptions import (
    BatchSizeExceeded,
    InvalidSymbol,
    PriceFeedError,
    ProviderError,
    StaleData,
    UnsupportedFiat,
)
from crypto_price_feed.models import BatchResult, FiatCurrency, Quote, QuoteError, QuoteResult

__all__ = [
    "BatchResult",
    "BatchSizeExceeded",
    "FiatCurrency",
    "InvalidSymbol",
    "PriceFeedError",
    "ProviderError",
    "Quote",
    "QuoteError",
    "QuoteResult",
    "StaleData",
    "UnsupportedFiat",
    "get_price",
    "get_prices",
]

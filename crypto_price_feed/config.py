"""Centralized configuration (ADR-001 §3, §6.1)."""

from __future__ import annotations

import os
from dataclasses import dataclass

from crypto_price_feed.models import FiatCurrency

# Hard caps and base constants (ADR-001 §6.2, §6.3).
BATCH_SIZE_LIMIT: int = 20
SYMBOL_MAX_LEN: int = 16
STALE_THRESHOLD_SECONDS: int = 60
DEFAULT_FIAT: FiatCurrency = FiatCurrency.USD

# Network (ADR-001 §6.3 / NFR-001).
COINGECKO_BASE_URL: str = "https://api.coingecko.com/api/v3"
HTTP_CONNECT_TIMEOUT: float = 1.0
HTTP_READ_TIMEOUT: float = 1.5
HTTP_TOTAL_TIMEOUT: float = 2.0

_COINGECKO_API_KEY_ENV: str = "COINGECKO_API_KEY"


@dataclass(frozen=True, slots=True)
class Config:
    """Immutable runtime configuration. API key is read once from env var."""

    coingecko_api_key: str | None
    base_url: str = COINGECKO_BASE_URL


def load_config() -> Config:
    """Load configuration from environment. API key is optional (Public endpoint)."""
    key = os.environ.get(_COINGECKO_API_KEY_ENV)
    if key is not None and not key.strip():
        key = None
    return Config(coingecko_api_key=key)

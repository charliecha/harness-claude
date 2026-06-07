"""CoinGecko `/simple/price` provider (ADR-001 §3, §5, §6).

CoinGecko's endpoint takes lowercase `coin_id` values (e.g. "bitcoin") rather than
ticker symbols. We bundle a curated symbol -> id mapping for common tokens; symbols
outside this table are surfaced as InvalidSymbol failures by the API layer.
"""

from __future__ import annotations

import logging
from collections.abc import Sequence
from datetime import UTC, datetime
from decimal import Decimal, InvalidOperation
from typing import Any

from crypto_price_feed._internal.http import get_json
from crypto_price_feed.config import Config, load_config
from crypto_price_feed.exceptions import ProviderError
from crypto_price_feed.models import FiatCurrency, Quote

logger = logging.getLogger("crypto_price_feed.coingecko")

# Curated mapping — extend as needed. Keep deliberately small for the first version.
SYMBOL_TO_COIN_ID: dict[str, str] = {
    "BTC": "bitcoin",
    "ETH": "ethereum",
    "BNB": "binancecoin",
    "SOL": "solana",
    "XRP": "ripple",
    "USDT": "tether",
    "USDC": "usd-coin",
    "ADA": "cardano",
    "DOGE": "dogecoin",
    "DOT": "polkadot",
}


class CoinGeckoProvider:
    """First-version provider backed by CoinGecko Public/Demo `/simple/price`."""

    def __init__(self, config: Config | None = None) -> None:
        self._config = config if config is not None else load_config()

    def fetch(
        self,
        symbols: Sequence[str],
        fiat: FiatCurrency,
    ) -> list[Quote]:
        # Resolve symbols → coin_ids; drop unknowns (API layer flags them as InvalidSymbol).
        symbol_to_id: dict[str, str] = {}
        for sym in symbols:
            coin_id = SYMBOL_TO_COIN_ID.get(sym)
            if coin_id is not None:
                symbol_to_id[sym] = coin_id

        if not symbol_to_id:
            return []

        params: dict[str, Any] = {
            "ids": ",".join(symbol_to_id.values()),
            "vs_currencies": fiat.value.lower(),
            "include_last_updated_at": "true",
        }

        payload = get_json(self._config, "/simple/price", params)
        if not isinstance(payload, dict):
            raise ProviderError(
                "upstream payload must be a JSON object",
                offending_input="/simple/price",
            )

        fiat_key = fiat.value.lower()
        quotes: list[Quote] = []
        for sym, coin_id in symbol_to_id.items():
            entry = payload.get(coin_id)
            if not isinstance(entry, dict):
                continue
            if fiat_key not in entry or "last_updated_at" not in entry:
                continue
            try:
                price = Decimal(str(entry[fiat_key]))
            except (InvalidOperation, TypeError, ValueError):
                logger.warning("coingecko_bad_price symbol=%s", sym)
                continue
            try:
                ts_epoch = int(entry["last_updated_at"])
            except (TypeError, ValueError):
                logger.warning("coingecko_bad_timestamp symbol=%s", sym)
                continue
            source_ts = datetime.fromtimestamp(ts_epoch, tz=UTC)
            quotes.append(
                Quote(symbol=sym, fiat=fiat, price=price, source_timestamp=source_ts)
            )
        return quotes

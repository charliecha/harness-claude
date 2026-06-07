"""Tests for the public Facade in crypto_price_feed.api."""

from __future__ import annotations

from collections.abc import Sequence
from datetime import UTC, datetime, timedelta
from decimal import Decimal

import pytest

from crypto_price_feed.api import get_price, get_prices
from crypto_price_feed.exceptions import (
    BatchSizeExceeded,
    InvalidSymbol,
    StaleData,
    UnsupportedFiat,
)
from crypto_price_feed.models import FiatCurrency, Quote


class StubProvider:
    """Hand-rolled stub provider for API-layer tests (no httpx involvement)."""

    def __init__(self, quotes: list[Quote] | Exception) -> None:
        self._quotes = quotes
        self.calls: list[tuple[tuple[str, ...], FiatCurrency]] = []

    def fetch(self, symbols: Sequence[str], fiat: FiatCurrency) -> list[Quote]:
        self.calls.append((tuple(symbols), fiat))
        if isinstance(self._quotes, Exception):
            raise self._quotes
        # Filter to only requested symbols, preserving the stub's order.
        wanted = set(symbols)
        return [q for q in self._quotes if q.symbol in wanted]


def _fresh_quote(symbol: str = "BTC", fiat: FiatCurrency = FiatCurrency.USD) -> Quote:
    return Quote(
        symbol=symbol,
        fiat=fiat,
        price=Decimal("50000.00"),
        source_timestamp=datetime.now(tz=UTC) - timedelta(seconds=5),
    )


class TestGetPrice:
    def test_returns_fresh_quote(self) -> None:
        q = _fresh_quote()
        provider = StubProvider([q])
        result = get_price("btc", provider=provider)
        assert result is q
        assert provider.calls == [(("BTC",), FiatCurrency.USD)]

    def test_defaults_to_usd(self) -> None:
        provider = StubProvider([_fresh_quote()])
        get_price("BTC", provider=provider)
        assert provider.calls[0][1] is FiatCurrency.USD

    def test_raises_invalid_symbol_when_provider_returns_empty(self) -> None:
        provider = StubProvider([])
        with pytest.raises(InvalidSymbol) as exc_info:
            get_price("ZZZ", provider=provider)
        assert exc_info.value.offending_input == "ZZZ"

    def test_raises_invalid_symbol_for_malformed_input(self) -> None:
        with pytest.raises(InvalidSymbol):
            get_price("bad symbol", provider=StubProvider([]))

    def test_raises_unsupported_fiat(self) -> None:
        with pytest.raises(UnsupportedFiat):
            get_price("BTC", fiat="JPY", provider=StubProvider([]))

    def test_raises_stale_data_when_quote_too_old(self) -> None:
        stale = Quote(
            symbol="BTC",
            fiat=FiatCurrency.USD,
            price=Decimal("100"),
            source_timestamp=datetime.now(tz=UTC) - timedelta(seconds=120),
        )
        with pytest.raises(StaleData) as exc_info:
            get_price("BTC", provider=StubProvider([stale]))
        assert exc_info.value.code == "STALE_DATA"


class TestGetPrices:
    def test_returns_successes_and_preserves_original_failures(self) -> None:
        btc = _fresh_quote("BTC")
        eth = _fresh_quote("ETH", FiatCurrency.USD)
        provider = StubProvider([btc, eth])
        result = get_prices(["btc", "ETH", "zzz"], provider=provider)
        assert [q.symbol for q in result.successes] == ["BTC", "ETH"]
        assert len(result.failures) == 1
        assert result.failures[0].symbol == "zzz"  # original casing preserved
        assert result.failures[0].code == "INVALID_SYMBOL"
        assert result.elapsed_ms >= 0

    def test_partial_failure_does_not_abort_batch(self) -> None:
        btc = _fresh_quote("BTC")
        # ETH absent from provider response -> goes into failures.
        provider = StubProvider([btc])
        result = get_prices(["BTC", "ETH"], provider=provider)
        assert [q.symbol for q in result.successes] == ["BTC"]
        assert [e.symbol for e in result.failures] == ["ETH"]

    def test_stale_quote_is_classified_as_failure(self) -> None:
        fresh = _fresh_quote("BTC")
        stale = Quote(
            symbol="ETH",
            fiat=FiatCurrency.USD,
            price=Decimal("3000"),
            source_timestamp=datetime.now(tz=UTC) - timedelta(seconds=120),
        )
        provider = StubProvider([fresh, stale])
        result = get_prices(["BTC", "ETH"], provider=provider)
        assert [q.symbol for q in result.successes] == ["BTC"]
        assert [e.symbol for e in result.failures] == ["ETH"]
        assert result.failures[0].code == "STALE_DATA"

    def test_rejects_batch_over_limit(self) -> None:
        with pytest.raises(BatchSizeExceeded):
            get_prices([f"S{i}" for i in range(21)], provider=StubProvider([]))

    def test_rejects_empty_batch(self) -> None:
        with pytest.raises(InvalidSymbol):
            get_prices([], provider=StubProvider([]))

    def test_rejects_unsupported_fiat(self) -> None:
        with pytest.raises(UnsupportedFiat):
            get_prices(["BTC"], fiat="JPY", provider=StubProvider([]))

    def test_dedupes_inputs(self) -> None:
        btc = _fresh_quote("BTC")
        provider = StubProvider([btc])
        result = get_prices(["BTC", "btc", "BTC"], provider=provider)
        assert len(result.successes) == 1
        # Provider only sees the deduped list.
        assert provider.calls[0][0] == ("BTC",)

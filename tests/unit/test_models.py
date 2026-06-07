"""Tests for crypto_price_feed.models (ADR-001 §3.2)."""

from __future__ import annotations

from datetime import UTC, datetime
from decimal import Decimal

import pytest

from crypto_price_feed.models import (
    BatchResult,
    FiatCurrency,
    Quote,
    QuoteError,
)


def test_fiat_currency_supports_three_options() -> None:
    assert {c.value for c in FiatCurrency} == {"USD", "EUR", "CNY"}


def test_quote_is_frozen_and_uses_decimal() -> None:
    ts = datetime(2026, 6, 7, 12, 0, 0, tzinfo=UTC)
    q = Quote(symbol="BTC", fiat=FiatCurrency.USD, price=Decimal("12345.67"), source_timestamp=ts)
    assert q.price == Decimal("12345.67")
    with pytest.raises(AttributeError):
        q.symbol = "ETH"  # type: ignore[misc]


def test_quote_error_preserves_original_symbol() -> None:
    err = QuoteError(symbol="btc!", code="INVALID_SYMBOL", message="bad symbol")
    assert err.symbol == "btc!"
    assert err.code == "INVALID_SYMBOL"


def test_batch_result_defaults_to_empty_lists() -> None:
    r = BatchResult()
    assert r.successes == []
    assert r.failures == []
    assert r.elapsed_ms == 0

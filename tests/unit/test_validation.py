"""Tests for crypto_price_feed._internal.validation."""

from __future__ import annotations

import pytest

from crypto_price_feed._internal.validation import (
    check_batch_size,
    normalize_symbol,
    normalize_symbols,
    parse_fiat,
)
from crypto_price_feed.exceptions import (
    BatchSizeExceeded,
    InvalidSymbol,
    UnsupportedFiat,
)
from crypto_price_feed.models import FiatCurrency


class TestNormalizeSymbol:
    def test_uppercases_and_trims(self) -> None:
        assert normalize_symbol("  btc  ") == "BTC"

    def test_rejects_empty(self) -> None:
        with pytest.raises(InvalidSymbol) as exc_info:
            normalize_symbol("   ")
        assert exc_info.value.offending_input == "   "
        assert exc_info.value.code == "INVALID_SYMBOL"

    def test_rejects_too_long(self) -> None:
        with pytest.raises(InvalidSymbol):
            normalize_symbol("A" * 17)

    @pytest.mark.parametrize("bad", ["BTC,", "BT/C", "BT?C", "BT&C", "BT C", "BT-C"])
    def test_rejects_meta_characters(self, bad: str) -> None:
        with pytest.raises(InvalidSymbol):
            normalize_symbol(bad)

    def test_rejects_non_string(self) -> None:
        with pytest.raises(InvalidSymbol):
            normalize_symbol(123)  # type: ignore[arg-type]


class TestNormalizeSymbols:
    def test_preserves_order_and_dedupes(self) -> None:
        out = normalize_symbols(["btc", "ETH", "btc", "doge"])
        assert out == ["BTC", "ETH", "DOGE"]

    def test_rejects_empty_list(self) -> None:
        with pytest.raises(InvalidSymbol):
            normalize_symbols([])

    def test_rejects_oversize(self) -> None:
        with pytest.raises(BatchSizeExceeded) as exc_info:
            normalize_symbols([f"S{i}" for i in range(21)])
        assert exc_info.value.code == "BATCH_SIZE_EXCEEDED"
        assert "20" in exc_info.value.message

    def test_propagates_invalid_symbol(self) -> None:
        with pytest.raises(InvalidSymbol):
            normalize_symbols(["BTC", "bad symbol"])


class TestCheckBatchSize:
    def test_allows_up_to_limit(self) -> None:
        check_batch_size(20)
        check_batch_size(1)

    def test_rejects_over_limit(self) -> None:
        with pytest.raises(BatchSizeExceeded):
            check_batch_size(21)


class TestParseFiat:
    def test_accepts_enum(self) -> None:
        assert parse_fiat(FiatCurrency.EUR) is FiatCurrency.EUR

    @pytest.mark.parametrize("raw,expected", [("usd", FiatCurrency.USD), ("EUR", FiatCurrency.EUR), ("  cny ", FiatCurrency.CNY)])
    def test_accepts_string(self, raw: str, expected: FiatCurrency) -> None:
        assert parse_fiat(raw) is expected

    def test_rejects_unknown(self) -> None:
        with pytest.raises(UnsupportedFiat) as exc_info:
            parse_fiat("JPY")
        assert exc_info.value.offending_input == "JPY"
        assert "USD" in exc_info.value.message

    def test_rejects_non_string(self) -> None:
        with pytest.raises(UnsupportedFiat):
            parse_fiat(42)  # type: ignore[arg-type]

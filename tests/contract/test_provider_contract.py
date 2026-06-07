"""Contract test: CoinGeckoProvider satisfies the PriceProvider Protocol."""

from __future__ import annotations

from crypto_price_feed.providers.base import PriceProvider
from crypto_price_feed.providers.coingecko import CoinGeckoProvider


def test_coingecko_satisfies_price_provider_protocol() -> None:
    provider = CoinGeckoProvider()
    assert isinstance(provider, PriceProvider)


def test_protocol_has_expected_signature() -> None:
    # `fetch` must accept (symbols, fiat) and return a list[Quote].
    fetch = CoinGeckoProvider.fetch
    annotations = fetch.__annotations__
    assert "symbols" in annotations
    assert "fiat" in annotations
    assert "return" in annotations

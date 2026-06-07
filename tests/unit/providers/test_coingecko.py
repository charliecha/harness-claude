"""Tests for crypto_price_feed.providers.coingecko using respx for HTTP mocking."""

from __future__ import annotations

import time
from decimal import Decimal

import httpx
import pytest
import respx

from crypto_price_feed.config import COINGECKO_BASE_URL, Config
from crypto_price_feed.exceptions import ProviderError
from crypto_price_feed.models import FiatCurrency
from crypto_price_feed.providers.coingecko import CoinGeckoProvider


def _config() -> Config:
    return Config(coingecko_api_key=None, base_url=COINGECKO_BASE_URL)


@pytest.fixture
def provider() -> CoinGeckoProvider:
    return CoinGeckoProvider(config=_config())


@respx.mock
def test_fetch_single_symbol_success(provider: CoinGeckoProvider) -> None:
    ts = int(time.time())
    respx.get(f"{COINGECKO_BASE_URL}/simple/price").mock(
        return_value=httpx.Response(
            200,
            json={"bitcoin": {"usd": 50123.45, "last_updated_at": ts}},
        )
    )
    out = provider.fetch(["BTC"], FiatCurrency.USD)
    assert len(out) == 1
    assert out[0].symbol == "BTC"
    assert out[0].fiat is FiatCurrency.USD
    assert out[0].price == Decimal("50123.45")
    assert out[0].source_timestamp.tzinfo is not None


@respx.mock
def test_fetch_batch_success(provider: CoinGeckoProvider) -> None:
    ts = int(time.time())
    respx.get(f"{COINGECKO_BASE_URL}/simple/price").mock(
        return_value=httpx.Response(
            200,
            json={
                "bitcoin": {"usd": 50000, "last_updated_at": ts},
                "ethereum": {"usd": 3000, "last_updated_at": ts},
            },
        )
    )
    out = provider.fetch(["BTC", "ETH"], FiatCurrency.USD)
    assert {q.symbol for q in out} == {"BTC", "ETH"}


@respx.mock
def test_fetch_unknown_symbol_is_silently_dropped(provider: CoinGeckoProvider) -> None:
    """Symbols outside SYMBOL_TO_COIN_ID never reach the network; result has only knowns."""
    ts = int(time.time())
    route = respx.get(f"{COINGECKO_BASE_URL}/simple/price").mock(
        return_value=httpx.Response(200, json={"bitcoin": {"usd": 1, "last_updated_at": ts}})
    )
    out = provider.fetch(["BTC", "ZZZ"], FiatCurrency.USD)
    assert [q.symbol for q in out] == ["BTC"]
    # And the URL never includes ZZZ.
    assert route.called
    assert "ZZZ" not in str(route.calls.last.request.url)


@respx.mock
def test_fetch_all_unknown_returns_empty_without_network(provider: CoinGeckoProvider) -> None:
    route = respx.get(f"{COINGECKO_BASE_URL}/simple/price").mock(
        return_value=httpx.Response(200, json={})
    )
    out = provider.fetch(["ZZZ", "YYY"], FiatCurrency.USD)
    assert out == []
    assert not route.called


@respx.mock
def test_5xx_retries_then_raises_provider_error(provider: CoinGeckoProvider) -> None:
    route = respx.get(f"{COINGECKO_BASE_URL}/simple/price").mock(
        return_value=httpx.Response(503, text="boom")
    )
    with pytest.raises(ProviderError) as exc_info:
        provider.fetch(["BTC"], FiatCurrency.USD)
    assert exc_info.value.code == "PROVIDER_ERROR"
    # One retry => called twice.
    assert route.call_count == 2


@respx.mock
def test_4xx_does_not_retry_and_raises(provider: CoinGeckoProvider) -> None:
    route = respx.get(f"{COINGECKO_BASE_URL}/simple/price").mock(
        return_value=httpx.Response(429, text="rate limited")
    )
    with pytest.raises(ProviderError):
        provider.fetch(["BTC"], FiatCurrency.USD)
    assert route.call_count == 1


@respx.mock
def test_timeout_maps_to_provider_error(provider: CoinGeckoProvider) -> None:
    route = respx.get(f"{COINGECKO_BASE_URL}/simple/price").mock(
        side_effect=httpx.ReadTimeout("slow upstream")
    )
    with pytest.raises(ProviderError) as exc_info:
        provider.fetch(["BTC"], FiatCurrency.USD)
    assert "timed out" in exc_info.value.message
    # Retry once on timeout.
    assert route.call_count == 2


@respx.mock
def test_transport_error_maps_to_provider_error(provider: CoinGeckoProvider) -> None:
    respx.get(f"{COINGECKO_BASE_URL}/simple/price").mock(
        side_effect=httpx.ConnectError("dns fail")
    )
    with pytest.raises(ProviderError):
        provider.fetch(["BTC"], FiatCurrency.USD)


@respx.mock
def test_non_json_response_maps_to_provider_error(provider: CoinGeckoProvider) -> None:
    respx.get(f"{COINGECKO_BASE_URL}/simple/price").mock(
        return_value=httpx.Response(200, text="not json")
    )
    with pytest.raises(ProviderError):
        provider.fetch(["BTC"], FiatCurrency.USD)


@respx.mock
def test_payload_not_object_raises(provider: CoinGeckoProvider) -> None:
    respx.get(f"{COINGECKO_BASE_URL}/simple/price").mock(
        return_value=httpx.Response(200, json=[1, 2, 3])
    )
    with pytest.raises(ProviderError):
        provider.fetch(["BTC"], FiatCurrency.USD)


@respx.mock
def test_missing_fields_for_symbol_are_dropped(provider: CoinGeckoProvider) -> None:
    ts = int(time.time())
    respx.get(f"{COINGECKO_BASE_URL}/simple/price").mock(
        return_value=httpx.Response(
            200,
            json={
                "bitcoin": {"usd": 1, "last_updated_at": ts},
                "ethereum": {"usd": 2},  # missing last_updated_at
            },
        )
    )
    out = provider.fetch(["BTC", "ETH"], FiatCurrency.USD)
    assert [q.symbol for q in out] == ["BTC"]


@respx.mock
def test_bad_price_value_is_dropped(provider: CoinGeckoProvider) -> None:
    ts = int(time.time())
    respx.get(f"{COINGECKO_BASE_URL}/simple/price").mock(
        return_value=httpx.Response(
            200,
            json={"bitcoin": {"usd": "not-a-number", "last_updated_at": ts}},
        )
    )
    out = provider.fetch(["BTC"], FiatCurrency.USD)
    assert out == []


@respx.mock
def test_bad_timestamp_is_dropped(provider: CoinGeckoProvider) -> None:
    respx.get(f"{COINGECKO_BASE_URL}/simple/price").mock(
        return_value=httpx.Response(
            200,
            json={"bitcoin": {"usd": 1, "last_updated_at": "not-a-number"}},
        )
    )
    out = provider.fetch(["BTC"], FiatCurrency.USD)
    assert out == []


@respx.mock
def test_api_key_sent_via_header_not_query(monkeypatch: pytest.MonkeyPatch) -> None:
    cfg = Config(coingecko_api_key="k-xyz", base_url=COINGECKO_BASE_URL)
    p = CoinGeckoProvider(config=cfg)
    ts = int(time.time())
    route = respx.get(f"{COINGECKO_BASE_URL}/simple/price").mock(
        return_value=httpx.Response(200, json={"bitcoin": {"usd": 1, "last_updated_at": ts}})
    )
    p.fetch(["BTC"], FiatCurrency.USD)
    assert route.called
    req = route.calls.last.request
    assert req.headers.get("x-cg-demo-api-key") == "k-xyz"
    # Never put the key in the query string.
    assert "k-xyz" not in str(req.url)


def test_default_provider_loads_config_from_env(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("COINGECKO_API_KEY", "env-val")
    p = CoinGeckoProvider()
    # We don't expose the key publicly — just confirm the provider was instantiable
    # and that the config it loaded picked up the env var.
    assert p._config.coingecko_api_key == "env-val"

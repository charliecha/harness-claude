"""PriceProvider Protocol (ADR-001 §3.1)."""

from __future__ import annotations

from collections.abc import Sequence
from typing import Protocol, runtime_checkable

from crypto_price_feed.models import FiatCurrency, Quote


@runtime_checkable
class PriceProvider(Protocol):
    """Given symbols + fiat, return Quote list or raise a PriceFeedError subclass.

    Implementations MUST:
    - map network / upstream errors to exceptions in `crypto_price_feed.exceptions`;
    - NOT perform input validation (the API layer does);
    - NOT cache or split batches (first version decision).

    Symbols absent from the provider response (i.e. unknown to upstream) are simply
    omitted from the returned list; the API layer reconciles missing symbols.
    """

    def fetch(
        self,
        symbols: Sequence[str],
        fiat: FiatCurrency,
    ) -> list[Quote]:
        ...

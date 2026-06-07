"""Public Facade API (ADR-001 §3.3, §4)."""

from __future__ import annotations

import time
from collections.abc import Sequence
from datetime import UTC, datetime

from crypto_price_feed._internal.validation import (
    normalize_symbol,
    normalize_symbols,
    parse_fiat,
)
from crypto_price_feed.config import DEFAULT_FIAT, STALE_THRESHOLD_SECONDS
from crypto_price_feed.exceptions import InvalidSymbol, StaleData
from crypto_price_feed.models import BatchResult, FiatCurrency, Quote, QuoteError
from crypto_price_feed.observability import measure
from crypto_price_feed.providers.base import PriceProvider
from crypto_price_feed.providers.coingecko import CoinGeckoProvider


def _default_provider() -> PriceProvider:
    return CoinGeckoProvider()


def _check_freshness(quote: Quote, now: datetime) -> None:
    """Raise StaleData if the quote's source_timestamp is older than the threshold."""
    delta = abs((now - quote.source_timestamp).total_seconds())
    if delta > STALE_THRESHOLD_SECONDS:
        raise StaleData(
            f"quote is {int(delta)}s old, exceeds {STALE_THRESHOLD_SECONDS}s freshness",
            offending_input=quote.symbol,
        )


def get_price(
    symbol: str,
    fiat: FiatCurrency | str = DEFAULT_FIAT,
    *,
    provider: PriceProvider | None = None,
) -> Quote:
    """Single-symbol query (FR-001-1). Raises on any failure (FR-001-4, NFR-002)."""
    sym = normalize_symbol(symbol)
    fiat_enum = parse_fiat(fiat)
    provider = provider if provider is not None else _default_provider()

    with measure("get_price", symbol=sym, fiat=fiat_enum.value) as fields:
        quotes = provider.fetch([sym], fiat_enum)
        if not quotes:
            raise InvalidSymbol(
                f"symbol {sym!r} is not supported by the upstream provider",
                offending_input=symbol,
            )
        quote = quotes[0]
        _check_freshness(quote, datetime.now(tz=UTC))
        fields["source_timestamp"] = quote.source_timestamp.isoformat()
        return quote


def get_prices(
    symbols: Sequence[str],
    fiat: FiatCurrency | str = DEFAULT_FIAT,
    *,
    provider: PriceProvider | None = None,
) -> BatchResult:
    """Batch query (FR-001-3). Per-symbol failures land in `BatchResult.failures`.

    Only input-violation errors (BatchSizeExceeded, UnsupportedFiat, malformed symbol)
    and full-batch provider failures raise; everything else degrades to a QuoteError.
    """
    # Normalize first (raises BatchSizeExceeded / InvalidSymbol on bad input).
    normalized = normalize_symbols(symbols)
    fiat_enum = parse_fiat(fiat)
    provider = provider if provider is not None else _default_provider()

    # Build the user-visible symbol mapping so failures carry the original input.
    # Order is preserved per the normalize_symbols contract.
    raw_by_norm: dict[str, str] = {}
    for raw in symbols:
        try:
            n = normalize_symbol(raw)
        except InvalidSymbol:  # pragma: no cover - normalize_symbols would have caught it
            continue
        raw_by_norm.setdefault(n, raw)

    with measure("get_prices", n=len(normalized), fiat=fiat_enum.value) as fields:
        start = time.perf_counter()
        quotes = provider.fetch(normalized, fiat_enum)
        by_symbol: dict[str, Quote] = {q.symbol: q for q in quotes}

        successes: list[Quote] = []
        failures: list[QuoteError] = []
        now = datetime.now(tz=UTC)

        for sym in normalized:
            original = raw_by_norm.get(sym, sym)
            q = by_symbol.get(sym)
            if q is None:
                failures.append(
                    QuoteError(
                        symbol=original,
                        code=InvalidSymbol.code,
                        message=f"symbol {sym!r} is not supported by the upstream provider",
                    )
                )
                continue
            try:
                _check_freshness(q, now)
            except StaleData as exc:
                failures.append(
                    QuoteError(symbol=original, code=exc.code, message=exc.message)
                )
                continue
            successes.append(q)

        elapsed_ms = int((time.perf_counter() - start) * 1000)
        fields["successes"] = len(successes)
        fields["failures"] = len(failures)
        return BatchResult(successes=successes, failures=failures, elapsed_ms=elapsed_ms)

"""Input validation (ADR-001 §6.2)."""

from __future__ import annotations

import re
from collections.abc import Sequence

from crypto_price_feed.config import BATCH_SIZE_LIMIT, SYMBOL_MAX_LEN
from crypto_price_feed.exceptions import (
    BatchSizeExceeded,
    InvalidSymbol,
    UnsupportedFiat,
)
from crypto_price_feed.models import FiatCurrency

# Reject URL meta-characters to prevent upstream path injection (ADR-001 §6.2).
_SYMBOL_RE = re.compile(r"^[A-Z0-9]{1,16}$")


def normalize_symbol(raw: str) -> str:
    """Validate and normalize a single symbol. Raises InvalidSymbol on failure."""
    if not isinstance(raw, str):  # type: ignore[reportUnnecessaryIsInstance]
        raise InvalidSymbol("symbol must be a string", offending_input=raw)
    s = raw.strip().upper()
    if not s:
        raise InvalidSymbol("symbol must be non-empty", offending_input=raw)
    if len(s) > SYMBOL_MAX_LEN:
        raise InvalidSymbol(
            f"symbol exceeds max length of {SYMBOL_MAX_LEN}",
            offending_input=raw,
        )
    if not _SYMBOL_RE.match(s):
        raise InvalidSymbol(
            "symbol must be alphanumeric (A-Z, 0-9 only)",
            offending_input=raw,
        )
    return s


def normalize_symbols(raws: Sequence[str]) -> list[str]:
    """Normalize a batch of symbols, preserving input order, deduplicating.

    Empty batch raises InvalidSymbol; size > BATCH_SIZE_LIMIT raises BatchSizeExceeded.
    """
    if len(raws) == 0:
        raise InvalidSymbol("symbols list must be non-empty", offending_input=raws)
    check_batch_size(len(raws))
    seen: set[str] = set()
    out: list[str] = []
    for r in raws:
        s = normalize_symbol(r)
        if s not in seen:
            seen.add(s)
            out.append(s)
    return out


def check_batch_size(n: int) -> None:
    """Enforce FR-001-3 AC1/AC2."""
    if n > BATCH_SIZE_LIMIT:
        raise BatchSizeExceeded(
            f"batch size {n} exceeds limit of {BATCH_SIZE_LIMIT}",
            offending_input=n,
        )


def parse_fiat(value: FiatCurrency | str) -> FiatCurrency:
    """Coerce string to FiatCurrency; raise UnsupportedFiat on miss."""
    if isinstance(value, FiatCurrency):
        return value
    if not isinstance(value, str):  # type: ignore[reportUnnecessaryIsInstance]
        raise UnsupportedFiat(
            f"fiat must be one of {[c.value for c in FiatCurrency]}",
            offending_input=value,
        )
    try:
        return FiatCurrency(value.strip().upper())
    except ValueError as exc:
        raise UnsupportedFiat(
            f"fiat must be one of {[c.value for c in FiatCurrency]}",
            offending_input=value,
        ) from exc

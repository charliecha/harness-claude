"""FR-001 price endpoints (/prices/single, /prices/batch)."""

from __future__ import annotations

from fastapi import APIRouter
from fastapi.responses import JSONResponse

from crypto_price_feed.api import get_price, get_prices
from crypto_price_feed.exceptions import (
    BatchSizeExceeded,
    InvalidSymbol,
    StaleData,
    UnsupportedFiat,
)

router = APIRouter()


@router.get("/prices/single")
async def single_price(symbol: str, fiat: str = "USD"):
    try:
        quote = get_price(symbol, fiat)
    except (InvalidSymbol, UnsupportedFiat, StaleData) as exc:
        return JSONResponse(status_code=400, content={"error": exc.code, "message": exc.message})
    return {
        "symbol": quote.symbol,
        "fiat": quote.fiat.value,
        "price": str(quote.price),
        "source_timestamp": quote.source_timestamp.isoformat(),
    }


@router.post("/prices/batch")
async def batch_prices(symbols: list[str], fiat: str = "USD"):
    try:
        result = get_prices(symbols, fiat)
    except (BatchSizeExceeded, InvalidSymbol, UnsupportedFiat) as exc:
        return JSONResponse(status_code=400, content={"error": exc.code, "message": exc.message})
    return {
        "successes": [
            {
                "symbol": q.symbol,
                "fiat": q.fiat.value,
                "price": str(q.price),
                "source_timestamp": q.source_timestamp.isoformat(),
            }
            for q in result.successes
        ],
        "failures": [
            {"symbol": e.symbol, "error": e.code, "message": e.message}
            for e in result.failures
        ],
        "elapsed_ms": result.elapsed_ms,
    }

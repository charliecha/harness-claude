#!/bin/bash
# scripts/smoke_test.sh — crypto_price_feed library smoke test
# 由 .harness/gatekeeper.sh 调用，退出码 0 = PASSED
#
# 本项目为 library（FR-001 / ADR-001），无 HTTP/CLI 入口。
# 冒烟测试通过 in-process 调用公共 API 验证：
#   1) 包可正常 import
#   2) 公共 API 完整暴露
#   3) 入参校验路径在不触网的前提下可工作
#   4) 注入 fake provider 走通 happy path

set -euo pipefail

cd "$(dirname "$0")/.."

# 选择带依赖的 python 解释器（miniforge 3.12 有 httpx/pytest，homebrew 3.14 没有）
if [ -x "/Users/chazongxun/miniforge3/bin/python3" ] && \
   /Users/chazongxun/miniforge3/bin/python3 -c "import httpx" 2>/dev/null; then
    PY=/Users/chazongxun/miniforge3/bin/python3
else
    PY=python3
fi

"$PY" - <<'PY'
"""crypto_price_feed smoke test — no network, deterministic."""

import sys
from collections.abc import Sequence
from datetime import UTC, datetime, timedelta
from decimal import Decimal

import crypto_price_feed as cpf
from crypto_price_feed.models import FiatCurrency, Quote


# 1. Public API symbols are exported.
required = {
    "get_price", "get_prices",
    "Quote", "QuoteError", "BatchResult", "FiatCurrency",
    "PriceFeedError", "InvalidSymbol", "UnsupportedFiat",
    "BatchSizeExceeded", "StaleData", "ProviderError",
}
missing = required - set(dir(cpf))
assert not missing, f"missing exports: {missing}"

# 2. Input validation rejects garbage without touching the network.
try:
    cpf.get_price("bad symbol")
except cpf.InvalidSymbol as e:
    assert e.code == "INVALID_SYMBOL"
    assert e.offending_input == "bad symbol"
else:
    sys.exit("expected InvalidSymbol for malformed input")

try:
    cpf.get_prices(["BTC"] * 21)
except cpf.BatchSizeExceeded as e:
    assert e.code == "BATCH_SIZE_EXCEEDED"
    assert "20" in e.message
else:
    sys.exit("expected BatchSizeExceeded for oversized batch")

try:
    cpf.get_price("BTC", fiat="JPY")
except cpf.UnsupportedFiat as e:
    assert e.code == "UNSUPPORTED_FIAT"
else:
    sys.exit("expected UnsupportedFiat for unknown fiat")


# 3. Inject a fake provider to walk the success path end-to-end.
class FakeProvider:
    def fetch(self, symbols: Sequence[str], fiat: FiatCurrency) -> list[Quote]:
        ts = datetime.now(tz=UTC) - timedelta(seconds=2)
        return [
            Quote(symbol=s, fiat=fiat, price=Decimal("123.45"), source_timestamp=ts)
            for s in symbols
        ]


quote = cpf.get_price("btc", provider=FakeProvider())
assert quote.symbol == "BTC"
assert quote.fiat is FiatCurrency.USD
assert quote.price == Decimal("123.45")

batch = cpf.get_prices(["BTC", "ETH"], provider=FakeProvider())
assert [q.symbol for q in batch.successes] == ["BTC", "ETH"]
assert batch.failures == []
assert batch.elapsed_ms >= 0

print("smoke: crypto_price_feed library OK (no network)")

# ──────────────────────────────────────────────────────────────
# Part 2: HTTP server layer smoke test (FastAPI TestClient)
# ──────────────────────────────────────────────────────────────
from fastapi.testclient import TestClient
from fastapi import FastAPI
from crypto_price_feed.stats.store import InMemoryStatsStore
from crypto_price_feed.stats.query import StatsQuery
from server.middleware import StatsMiddleware
from server.routes import prices as prices_router
from server.routes.stats import make_stats_router

# Build app with injected fake provider via monkeypatching api module
import crypto_price_feed._internal.validation as _v  # noqa: F401
import crypto_price_feed.api as _api

_orig_default = _api._default_provider

def _fake_provider():
    return FakeProvider()

_api._default_provider = _fake_provider

store = InMemoryStatsStore()
app = FastAPI()
app.add_middleware(StatsMiddleware, store=store)
app.include_router(prices_router.router)
app.include_router(make_stats_router(StatsQuery(store)))

client = TestClient(app, raise_server_exceptions=True)

# /prices/single — happy path
resp = client.get("/prices/single?symbol=BTC")
assert resp.status_code == 200, f"/prices/single returned {resp.status_code}: {resp.text}"
body = resp.json()
assert body["symbol"] == "BTC"
assert body["fiat"] == "USD"
assert "price" in body

# /prices/single — invalid symbol
resp = client.get("/prices/single?symbol=bad+symbol")
assert resp.status_code == 400, f"expected 400 for bad symbol, got {resp.status_code}"
assert resp.json()["error"] == "INVALID_SYMBOL"

# /prices/batch — happy path
resp = client.post("/prices/batch?fiat=USD", json=["BTC", "ETH"])
assert resp.status_code == 200, f"/prices/batch returned {resp.status_code}: {resp.text}"
body = resp.json()
assert len(body["successes"]) == 2
assert body["failures"] == []

# /stats — must have data: /prices/single was called twice above (200 + 400)
resp = client.get("/stats?endpoint=/prices/single&window=1m")
assert resp.status_code == 200, f"/stats returned {resp.status_code}"
body = resp.json()
assert "total_requests" in body, f"expected stats data, got: {body}"
assert body["total_requests"] == 2, f"expected 2 calls recorded, got {body['total_requests']}"
assert body["error_requests"] == 1, f"expected 1 error (400), got {body['error_requests']}"
assert body["error_rate"] == 0.5, f"expected error_rate 0.5, got {body['error_rate']}"
assert body["mean_ms"] >= 0
assert body["p95_ms"] >= 0
assert body["p99_ms"] >= 0

# /stats — missing parameter → 400
resp = client.get("/stats?endpoint=/prices/single")
assert resp.status_code == 400
assert resp.json()["missing"] == "window"

resp = client.get("/stats?window=1m")
assert resp.status_code == 400
assert resp.json()["missing"] == "endpoint"

# /stats — invalid window → 400
resp = client.get("/stats?endpoint=/prices/single&window=99h")
assert resp.status_code == 400

# /stats requests must not pollute stats counts
from datetime import UTC, datetime, timedelta
stats_records = store.query("/stats", datetime.now(tz=UTC) - timedelta(minutes=1))
assert len(stats_records) == 0, f"/stats path leaked into store: {len(stats_records)} records"

_api._default_provider = _orig_default
print("smoke: server HTTP layer OK (no network)")
PY

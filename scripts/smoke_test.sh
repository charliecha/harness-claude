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
PY

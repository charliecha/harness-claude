"""httpx.Client wrapper with timeouts and minimal retry (ADR-001 §3, §6.3, §6.4).

Never logs full URLs (query strings may contain API keys on paid endpoints).
"""

from __future__ import annotations

import logging
import time
from typing import Any

import httpx

from crypto_price_feed.config import (
    HTTP_CONNECT_TIMEOUT,
    HTTP_READ_TIMEOUT,
    HTTP_TOTAL_TIMEOUT,
    Config,
)
from crypto_price_feed.exceptions import ProviderError

logger = logging.getLogger("crypto_price_feed.http")

_RETRY_BACKOFF_SECONDS: float = 0.5


def _build_timeout() -> httpx.Timeout:
    return httpx.Timeout(
        connect=HTTP_CONNECT_TIMEOUT,
        read=HTTP_READ_TIMEOUT,
        write=HTTP_TOTAL_TIMEOUT,
        pool=HTTP_TOTAL_TIMEOUT,
    )


def _build_headers(config: Config) -> dict[str, str]:
    headers: dict[str, str] = {"Accept": "application/json"}
    if config.coingecko_api_key:
        # CoinGecko paid plan uses this header. Never put it in a query string.
        headers["x-cg-demo-api-key"] = config.coingecko_api_key
    return headers


def get_json(
    config: Config,
    path: str,
    params: dict[str, Any],
) -> Any:
    """GET <base_url><path>?<params>, return parsed JSON.

    Maps timeouts / 5xx / network errors / JSON parse failures to ProviderError.
    Performs ONE retry on 5xx or transport errors with 0.5s backoff.
    """
    if not path.startswith("/"):
        path = "/" + path
    url = config.base_url + path  # base_url is https only (ADR-001 §6.3).

    timeout = _build_timeout()
    headers = _build_headers(config)

    last_exc: Exception | None = None
    for attempt in (1, 2):
        try:
            with httpx.Client(timeout=timeout, headers=headers) as client:
                response = client.get(url, params=params)
            status = response.status_code
            if 500 <= status < 600:
                logger.warning("upstream_5xx status=%d attempt=%d path=%s", status, attempt, path)
                last_exc = ProviderError(
                    f"upstream returned HTTP {status}", offending_input=path
                )
                if attempt == 1:
                    time.sleep(_RETRY_BACKOFF_SECONDS)
                    continue
                raise last_exc
            if status >= 400:
                # 4xx is not retryable — surface immediately as ProviderError.
                raise ProviderError(
                    f"upstream returned HTTP {status}", offending_input=path
                )
            try:
                return response.json()
            except ValueError as exc:
                raise ProviderError(
                    "failed to parse upstream JSON", offending_input=path
                ) from exc
        except httpx.TimeoutException as exc:
            logger.warning("upstream_timeout attempt=%d path=%s", attempt, path)
            last_exc = ProviderError("upstream request timed out", offending_input=path)
            last_exc.__cause__ = exc
            if attempt == 1:
                time.sleep(_RETRY_BACKOFF_SECONDS)
                continue
            raise last_exc from exc
        except httpx.TransportError as exc:
            logger.warning("upstream_transport_error attempt=%d path=%s", attempt, path)
            last_exc = ProviderError("upstream transport error", offending_input=path)
            last_exc.__cause__ = exc
            if attempt == 1:
                time.sleep(_RETRY_BACKOFF_SECONDS)
                continue
            raise last_exc from exc

    # Unreachable; loop above either returns or raises.
    raise ProviderError("unreachable retry state", offending_input=path)  # pragma: no cover

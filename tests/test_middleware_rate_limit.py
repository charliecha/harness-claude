"""Integration tests for RateLimitMiddleware (TDD - RED phase)."""

from __future__ import annotations

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

from server.rate_limiter import RateLimiter
from server.middleware import RateLimitMiddleware


def _make_app(limit: int, window: int = 60) -> FastAPI:
    app = FastAPI()
    limiter = RateLimiter(limit=limit, window=window)
    app.add_middleware(RateLimitMiddleware, limiter=limiter)

    @app.get("/ping")
    def ping():
        return {"ok": True}

    @app.get("/stats/summary")
    def stats():
        return {"stats": True}

    return app


class TestRateLimitMiddleware:
    def test_allows_requests_within_limit(self):
        client = TestClient(_make_app(limit=3), raise_server_exceptions=True)
        for _ in range(3):
            assert client.get("/ping").status_code == 200

    def test_returns_429_when_limit_exceeded(self):
        client = TestClient(_make_app(limit=2), raise_server_exceptions=True)
        client.get("/ping")
        client.get("/ping")
        resp = client.get("/ping")
        assert resp.status_code == 429

    def test_429_response_has_retry_after_header(self):
        client = TestClient(_make_app(limit=1), raise_server_exceptions=True)
        client.get("/ping")
        resp = client.get("/ping")
        assert resp.status_code == 429
        assert "retry-after" in resp.headers
        assert int(resp.headers["retry-after"]) > 0

    def test_stats_path_is_exempt_from_rate_limit(self):
        client = TestClient(_make_app(limit=1), raise_server_exceptions=True)
        client.get("/ping")
        assert client.get("/ping").status_code == 429
        assert client.get("/stats/summary").status_code == 200

    def test_429_body_contains_detail(self):
        client = TestClient(_make_app(limit=1), raise_server_exceptions=True)
        client.get("/ping")
        resp = client.get("/ping")
        assert resp.status_code == 429
        assert "detail" in resp.json()

    def test_x_forwarded_for_is_used_as_key(self):
        """Requests with different X-Forwarded-For are bucketed independently."""
        client = TestClient(_make_app(limit=1), raise_server_exceptions=True)
        r1 = client.get("/ping", headers={"X-Forwarded-For": "10.0.0.1"})
        assert r1.status_code == 200
        r2 = client.get("/ping", headers={"X-Forwarded-For": "10.0.0.2"})
        assert r2.status_code == 200

    def test_x_forwarded_for_limit_exhausted(self):
        """Same X-Forwarded-For IP hits limit correctly."""
        client = TestClient(_make_app(limit=1), raise_server_exceptions=True)
        client.get("/ping", headers={"X-Forwarded-For": "10.0.0.5"})
        resp = client.get("/ping", headers={"X-Forwarded-For": "10.0.0.5"})
        assert resp.status_code == 429

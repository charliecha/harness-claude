"""Tests for GET /health endpoint (FR-003)."""

from __future__ import annotations

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

from server.rate_limiter import RateLimiter
from server.middleware import RateLimitMiddleware
from server.routes.health import router as health_router


def _make_app() -> FastAPI:
    app = FastAPI()
    limiter = RateLimiter(limit=1, window=60)
    app.add_middleware(RateLimitMiddleware, limiter=limiter)
    app.include_router(health_router)
    return app


class TestHealthEndpoint:
    def test_returns_200(self):
        client = TestClient(_make_app())
        resp = client.get("/health")
        assert resp.status_code == 200

    def test_response_has_status_ok(self):
        client = TestClient(_make_app())
        resp = client.get("/health")
        body = resp.json()
        assert body["status"] == "ok"

    def test_response_has_version(self):
        client = TestClient(_make_app())
        resp = client.get("/health")
        body = resp.json()
        assert isinstance(body["version"], str)
        assert len(body["version"]) > 0

    def test_response_has_timestamp_iso8601(self):
        client = TestClient(_make_app())
        resp = client.get("/health")
        body = resp.json()
        assert "T" in body["timestamp"]
        assert body["timestamp"].endswith("+00:00") or body["timestamp"].endswith("Z")

    def test_response_has_checks_memory(self):
        client = TestClient(_make_app())
        resp = client.get("/health")
        body = resp.json()
        assert "checks" in body
        assert "memory" in body["checks"]
        assert body["checks"]["memory"] in ("healthy", "degraded")

    def test_is_exempt_from_rate_limit(self):
        """/health should not be rate-limited even after exhausting the limit."""
        client = TestClient(_make_app(), raise_server_exceptions=True)
        client.get("/ping")  # should 429
        # health should still work
        assert client.get("/health").status_code == 200

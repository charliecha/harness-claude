"""Unit tests for rate limiter core logic (TDD - RED phase)."""

from __future__ import annotations

import time

import pytest

from server.rate_limiter import RateLimiter


class TestRateLimiterBasic:
    def test_allows_requests_within_limit(self):
        limiter = RateLimiter(limit=3, window=60)
        assert limiter.is_allowed("127.0.0.1") is True
        assert limiter.is_allowed("127.0.0.1") is True
        assert limiter.is_allowed("127.0.0.1") is True

    def test_blocks_request_exceeding_limit(self):
        limiter = RateLimiter(limit=2, window=60)
        limiter.is_allowed("127.0.0.1")
        limiter.is_allowed("127.0.0.1")
        assert limiter.is_allowed("127.0.0.1") is False

    def test_different_ips_are_independent(self):
        limiter = RateLimiter(limit=1, window=60)
        assert limiter.is_allowed("1.1.1.1") is True
        assert limiter.is_allowed("2.2.2.2") is True
        assert limiter.is_allowed("1.1.1.1") is False
        assert limiter.is_allowed("2.2.2.2") is False

    def test_window_reset_allows_new_requests(self):
        limiter = RateLimiter(limit=1, window=1)
        assert limiter.is_allowed("127.0.0.1") is True
        assert limiter.is_allowed("127.0.0.1") is False
        time.sleep(1.1)
        assert limiter.is_allowed("127.0.0.1") is True

    def test_retry_after_returns_positive_seconds_when_blocked(self):
        limiter = RateLimiter(limit=1, window=60)
        limiter.is_allowed("127.0.0.1")
        retry_after = limiter.retry_after("127.0.0.1")
        assert retry_after > 0
        assert retry_after <= 60

    def test_retry_after_returns_zero_when_not_blocked(self):
        limiter = RateLimiter(limit=5, window=60)
        assert limiter.retry_after("127.0.0.1") == 0

    def test_reset_clears_state_for_ip(self):
        limiter = RateLimiter(limit=1, window=60)
        limiter.is_allowed("127.0.0.1")
        assert limiter.is_allowed("127.0.0.1") is False
        limiter.reset("127.0.0.1")
        assert limiter.is_allowed("127.0.0.1") is True

    def test_limit_zero_blocks_all(self):
        limiter = RateLimiter(limit=0, window=60)
        assert limiter.is_allowed("127.0.0.1") is False


class TestRateLimiterCheck:
    def test_check_returns_allowed_true_within_limit(self):
        limiter = RateLimiter(limit=2, window=60)
        allowed, retry = limiter.check("127.0.0.1")
        assert allowed is True
        assert retry == 0.0

    def test_check_returns_allowed_false_when_exceeded(self):
        limiter = RateLimiter(limit=1, window=60)
        limiter.check("127.0.0.1")
        allowed, retry = limiter.check("127.0.0.1")
        assert allowed is False
        assert retry > 0

    def test_check_is_atomic_allowed_and_retry_consistent(self):
        """allowed=False and retry>0 must always be consistent (no TOCTOU)."""
        limiter = RateLimiter(limit=1, window=60)
        limiter.check("127.0.0.1")
        for _ in range(10):
            allowed, retry = limiter.check("127.0.0.1")
            assert allowed is False
            assert retry > 0

    def test_passive_sweep_removes_expired_windows(self):
        limiter = RateLimiter(limit=1, window=1)
        limiter.check("1.1.1.1")
        limiter.check("2.2.2.2")
        time.sleep(1.1)
        # Trigger sweep via a new check call
        limiter.check("3.3.3.3")
        assert len(limiter._windows) <= 2  # only the new entry (and possibly 3.3.3.3)

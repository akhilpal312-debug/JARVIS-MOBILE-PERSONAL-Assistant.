// Per-instance sliding-window rate limiter. Vercel functions are horizontally
// scaled, so this protects each instance; the durable guard for provider quotas
// is the router's provider circuit breaker plus the upstream free-tier limits.
import { HttpError } from './http.js';

const buckets = new Map();

export function checkRateLimit(key, { limit = 30, windowMs = 60_000 } = {}) {
  const now = Date.now();
  const entry = buckets.get(key);
  if (!entry || now - entry.start >= windowMs) {
    buckets.set(key, { start: now, count: 1 });
    return { allowed: true, remaining: limit - 1, resetMs: windowMs };
  }
  entry.count += 1;
  if (entry.count > limit) {
    return {
      allowed: false,
      remaining: 0,
      resetMs: windowMs - (now - entry.start),
    };
  }
  return { allowed: true, remaining: limit - entry.count, resetMs: windowMs - (now - entry.start) };
}

export function enforceRateLimit(key, options) {
  const result = checkRateLimit(key, options);
  if (!result.allowed) {
    throw new HttpError(429, 'rate_limited', 'Too many requests. Please retry shortly.', {
      retryAfterMs: result.resetMs,
    });
  }
  return result;
}

export function resetRateLimits() {
  buckets.clear();
}

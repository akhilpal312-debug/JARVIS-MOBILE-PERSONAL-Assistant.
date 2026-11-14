// Capability-aware model router with provider fallback and a per-instance
// circuit breaker. Never logs or returns provider keys.
import { configuredProviders } from './providers.js';
import { HttpError } from './http.js';

const breaker = new Map(); // slug -> { failures, openedUntil }
const FAILURE_WINDOW_MS = 30_000;
const MAX_FAILURES = 2;

export function routerState() {
  const now = Date.now();
  return [...breaker.entries()].map(([slug, state]) => ({
    slug,
    failures: state.failures,
    open: state.openedUntil > now,
    retryInMs: Math.max(0, state.openedUntil - now),
  }));
}

export function resetRouterState() {
  breaker.clear();
}

function recordFailure(slug) {
  const state = breaker.get(slug) || { failures: 0, openedUntil: 0 };
  state.failures += 1;
  if (state.failures >= MAX_FAILURES) {
    state.openedUntil = Date.now() + FAILURE_WINDOW_MS;
    state.failures = 0;
  }
  breaker.set(slug, state);
}

function recordSuccess(slug) {
  breaker.delete(slug);
}

export function buildChain({ needJson = false, needTools = false, env = process.env } = {}) {
  return configuredProviders(env)
    .filter((provider) => provider.caps.chat)
    .filter((provider) => (needJson ? provider.caps.json : true))
    .sort((a, b) => {
      // Providers with native JSON mode win for structured work, then priority.
      if (needJson && a.caps.json !== b.caps.json) return a.caps.json ? -1 : 1;
      if (needTools && a.caps.tools !== b.caps.tools) return a.caps.tools ? -1 : 1;
      return a.priority - b.priority;
    })
    .map((provider) => ({
      ...provider,
      // Free-tier friendly models first; the directory lists the free one first.
      model: provider.models[0],
      fallbackModels: provider.models.slice(1),
    }));
}

function extractText(payload) {
  const choice = payload?.choices?.[0];
  const message = choice?.message || {};
  const content = typeof message.content === 'string' ? message.content : '';
  if (content.trim()) return content;
  // Some reasoning models return only reasoning_content when max_tokens is tight.
  const reasoning = typeof message.reasoning_content === 'string' ? message.reasoning_content : '';
  return reasoning.trim();
}

async function callProvider(entry, body, { timeoutMs, fetchImpl, env }) {
  const url = `${entry.baseUrl.replace(/\/$/, '')}/chat/completions`;
  const headers = {
    'content-type': 'application/json',
    accept: 'application/json',
    'user-agent': 'Jarvis-Android-Backend/1.0',
  };
  if (entry.apiKey) headers.authorization = `Bearer ${entry.apiKey}`;
  if (entry.slug === 'openrouter') {
    headers['http-referer'] = String(env.APP_ORIGIN || 'https://jarvis-assistant.vercel.app');
    headers['x-title'] = 'Jarvis Personal Assistant';
  }

  const timeout = AbortSignal.timeout ? AbortSignal.timeout(timeoutMs) : undefined;
  const response = await fetchImpl(url, {
    method: 'POST',
    headers,
    body: JSON.stringify(body),
    signal: timeout,
  });

  if (!response.ok) {
    let detail = '';
    try {
      const text = await response.text();
      detail = text.slice(0, 400);
    } catch {
      /* ignore */
    }
    const error = new Error(`provider ${entry.slug} returned ${response.status}`);
    error.status = response.status;
    error.retryable = response.status === 429 || response.status >= 500;
    error.detail = detail;
    throw error;
  }

  const payload = await response.json();
  const text = extractText(payload);
  if (!text) {
    const error = new Error(`provider ${entry.slug} returned an empty completion`);
    error.retryable = true;
    throw error;
  }
  return {
    text,
    usage: payload.usage || null,
    raw: payload,
  };
}

/**
 * Runs a chat completion through the fallback chain.
 * Returns { text, provider, model, attempts, usage }. Throws HTTP 503 when every
 * candidate failed, so callers can degrade gracefully.
 */
export async function complete({
  messages,
  needJson = false,
  needTools = false,
  maxTokens = 800,
  temperature = 0.5,
  timeoutMs = 20_000,
  env = process.env,
  fetchImpl = fetch,
} = {}) {
  if (!Array.isArray(messages) || messages.length === 0) {
    throw new HttpError(400, 'invalid_messages', 'messages must be a non-empty array.');
  }

  const chain = buildChain({ needJson, needTools, env });
  const attempts = [];

  for (const entry of chain) {
    const state = breaker.get(entry.slug);
    if (state && state.openedUntil > Date.now()) {
      attempts.push({ provider: entry.slug, model: entry.model, skipped: 'circuit_open' });
      continue;
    }

    const models = [entry.model, ...entry.fallbackModels];
    for (const model of models) {
      const body = {
        model,
        messages,
        temperature,
        max_tokens: maxTokens,
        stream: false,
      };
      if (needJson && entry.caps.json) {
        body.response_format = { type: 'json_object' };
      }

      const started = Date.now();
      try {
        const result = await callProvider(entry, body, { timeoutMs, fetchImpl, env });
        recordSuccess(entry.slug);
        attempts.push({
          provider: entry.slug,
          model,
          ok: true,
          latencyMs: Date.now() - started,
        });
        return {
          text: result.text,
          provider: entry.slug,
          providerName: entry.name,
          model,
          usage: result.usage,
          attempts,
        };
      } catch (error) {
        attempts.push({
          provider: entry.slug,
          model,
          ok: false,
          status: error.status || null,
          reason: String(error.message || 'error').slice(0, 160),
          latencyMs: Date.now() - started,
        });
        recordFailure(entry.slug);
        if (error.status === 401 || error.status === 403) {
          // Bad credentials: skip the rest of this provider immediately.
          break;
        }
      }
    }
  }

  const error = new HttpError(
    503,
    chain.length === 0 ? 'no_provider_configured' : 'all_providers_failed',
    chain.length === 0
      ? 'No LLM provider is configured on this deployment. Set a provider key (see /api/assistant/status).'
      : 'Every configured LLM provider failed or was rate limited. Try again shortly.',
    { attempts },
  );
  throw error;
}

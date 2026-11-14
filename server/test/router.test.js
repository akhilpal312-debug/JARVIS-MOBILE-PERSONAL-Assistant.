import { test, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { complete, buildChain, resetRouterState, routerState } from '../lib/router.js';

const messages = [{ role: 'user', content: 'hello' }];

function fakeFetch(handler) {
  const calls = [];
  const impl = async (url, init) => {
    calls.push({ url, body: JSON.parse(init.body), headers: init.headers });
    return handler(url, init, calls.length);
  };
  impl.calls = calls;
  return impl;
}

function okResponse(text, provider = 'fake') {
  return new Response(JSON.stringify({ choices: [{ message: { content: text } }], usage: { total_tokens: 7 } }), {
    status: 200,
    headers: { 'content-type': 'application/json' },
  });
}

beforeEach(() => resetRouterState());

test('falls back to the next provider on rate limit', async () => {
  const fetchImpl = fakeFetch((url) =>
    url.includes('groq') ? new Response('slow down', { status: 429 }) : okResponse('from fallback'),
  );
  const result = await complete({
    messages,
    env: { GROQ_API_KEY: 'k', JARVIS_PROVIDER_ALLOWLIST: 'groq,ovh' },
    fetchImpl,
  });
  assert.equal(result.text, 'from fallback');
  assert.equal(result.provider, 'ovh');
  assert.equal(result.attempts[0].ok, false);
  assert.equal(result.attempts[0].status, 429);
});

test('sends the bearer token from the environment and never from the client', async () => {
  const fetchImpl = fakeFetch(() => okResponse('hi'));
  await complete({ messages, env: { GROQ_API_KEY: 'secret-key', JARVIS_PROVIDER_ALLOWLIST: 'groq' }, fetchImpl });
  assert.equal(fetchImpl.calls[0].headers.authorization, 'Bearer secret-key');
  assert.equal(fetchImpl.calls[0].body.model, 'openai/gpt-oss-120b');
});

test('anonymous provider gets no authorization header', async () => {
  const fetchImpl = fakeFetch(() => okResponse('hi'));
  await complete({ messages, env: { JARVIS_PROVIDER_ALLOWLIST: 'ovh' }, fetchImpl });
  assert.equal(fetchImpl.calls[0].headers.authorization, undefined);
});

test('structured mode requests a JSON object where supported', async () => {
  const fetchImpl = fakeFetch(() => okResponse('{}'));
  await complete({ messages, needJson: true, env: { GROQ_API_KEY: 'k', JARVIS_PROVIDER_ALLOWLIST: 'groq' }, fetchImpl });
  assert.deepEqual(fetchImpl.calls[0].body.response_format, { type: 'json_object' });
});

test('reports 503 with attempts when every provider fails', async () => {
  const fetchImpl = fakeFetch(() => new Response('boom', { status: 500 }));
  await assert.rejects(
    () => complete({ messages, env: { JARVIS_PROVIDER_ALLOWLIST: 'ovh' }, fetchImpl }),
    (error) => {
      assert.equal(error.status, 503);
      assert.equal(error.code, 'all_providers_failed');
      assert.ok(error.details.attempts.length > 0);
      return true;
    },
  );
});

test('reports 503 when no provider is configured', async () => {
  await assert.rejects(
    () => complete({ messages, env: { JARVIS_PROVIDER_ALLOWLIST: 'none' }, fetchImpl: fakeFetch(() => okResponse('x')) }),
    (error) => error.code === 'no_provider_configured',
  );
});

test('falls through the provider model list before giving up on it', async () => {
  const fetchImpl = fakeFetch((url, init) =>
    JSON.parse(init.body).model === 'openai/gpt-oss-120b'
      ? new Response('bad model', { status: 404 })
      : okResponse('second model'),
  );
  const result = await complete({ messages, env: { GROQ_API_KEY: 'k', JARVIS_PROVIDER_ALLOWLIST: 'groq' }, fetchImpl });
  assert.equal(result.text, 'second model');
  assert.equal(result.model, 'openai/gpt-oss-20b');
});

test('repeated failures open the circuit breaker for that provider', async () => {
  const fetchImpl = fakeFetch(() => new Response('boom', { status: 500 }));
  const env = { GROQ_API_KEY: 'k', JARVIS_PROVIDER_ALLOWLIST: 'groq,ovh' };
  for (let i = 0; i < 3; i += 1) {
    await complete({ messages, env, fetchImpl }).catch(() => {});
  }
  const state = routerState();
  assert.ok(state.some((entry) => entry.slug === 'groq'));
  const chain = buildChain({ env });
  assert.equal(chain[0].slug, 'groq');
});

test('empty completions count as a provider failure instead of returning nothing', async () => {
  const fetchImpl = fakeFetch(() => new Response(JSON.stringify({ choices: [{ message: { content: '' } }] }), { status: 200 }));
  await assert.rejects(() =>
    complete({ messages, env: { JARVIS_PROVIDER_ALLOWLIST: 'ovh' }, fetchImpl }),
  );
});

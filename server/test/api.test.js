// End-to-end HTTP tests for the backend: real server, real requests, real
// validation. The LLM provider is a local fake that speaks the same
// OpenAI-compatible protocol, so fallback and persistence paths are exercised
// without spending anyone's quota.
import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { createServer } from '../scripts/dev-server.js';
import { resetMemoryStore } from '../lib/store.js';
import { resetRateLimits } from '../lib/ratelimit.js';
import { resetRouterState } from '../lib/router.js';

const TOKEN = 'test-token-123456';
const DEVICE = 'device-test-0001';

let api;
let fakeProvider;
let apiUrl;
let fakeUrl;
let fakeMode = 'chat';
let lastProviderRequest = null;

function listen(server) {
  return new Promise((resolveListen) => {
    server.listen(0, '127.0.0.1', () => resolveListen(server.address().port));
  });
}

before(async () => {
  fakeProvider = http.createServer((req, res) => {
    let body = '';
    req.on('data', (chunk) => {
      body += chunk;
    });
    req.on('end', () => {
      lastProviderRequest = { body: JSON.parse(body || '{}'), headers: req.headers, url: req.url };
      const content =
        fakeMode === 'command'
          ? JSON.stringify({
              reply: 'Theek hai, task add kar diya.',
              language: 'hinglish',
              intent: 'create_tasks',
              tasks: [{ title: 'Study for exam', priority: 'high', dueAtISO: '2026-11-13T12:30:00Z' }],
              taskRefs: [],
              memories: [{ kind: 'preference', content: 'prefers evening study sessions' }],
              deviceAction: null,
              needsConfirmation: false,
            })
          : 'Namaste! Main Jarvis hoon.';
      res.writeHead(200, { 'content-type': 'application/json' });
      res.end(JSON.stringify({ choices: [{ message: { content } }], usage: { total_tokens: 42 } }));
    });
  });
  const fakePort = await listen(fakeProvider);
  fakeUrl = `http://127.0.0.1:${fakePort}/v1`;

  process.env.JARVIS_API_TOKEN = TOKEN;
  process.env.JARVIS_PROVIDER_ALLOWLIST = 'ovh';
  process.env.JARVIS_PROVIDER_BASE_URL_OVH = fakeUrl;
  delete process.env.SUPABASE_URL;
  delete process.env.SUPABASE_SERVICE_ROLE_KEY;

  api = createServer();
  const apiPort = await listen(api);
  apiUrl = `http://127.0.0.1:${apiPort}`;
});

after(async () => {
  await new Promise((done) => api.close(done));
  await new Promise((done) => fakeProvider.close(done));
});

function authHeaders(extra = {}) {
  return { authorization: `Bearer ${TOKEN}`, 'x-device-id': DEVICE, 'content-type': 'application/json', ...extra };
}

test('health endpoint is public and reports configuration', async () => {
  const response = await fetch(`${apiUrl}/api/health`);
  assert.equal(response.status, 200);
  const body = await response.json();
  assert.equal(body.ok, true);
  assert.equal(body.storage, 'memory');
  assert.equal(response.headers.get('cache-control'), 'no-store, max-age=0');
});

test('protected endpoints reject missing, wrong and malformed credentials', async () => {
  assert.equal((await fetch(`${apiUrl}/api/assistant/tasks`)).status, 401);
  assert.equal(
    (await fetch(`${apiUrl}/api/assistant/tasks`, { headers: { authorization: 'Bearer nope', 'x-device-id': DEVICE } }))
      .status,
    401,
  );
  assert.equal(
    (await fetch(`${apiUrl}/api/assistant/tasks`, { headers: { authorization: `Bearer ${TOKEN}` } })).status,
    400,
  );
});

test('unknown routes 404 and wrong methods 405', async () => {
  assert.equal((await fetch(`${apiUrl}/api/nope`)).status, 404);
  const response = await fetch(`${apiUrl}/api/assistant/chat`, { method: 'GET', headers: authHeaders() });
  assert.equal(response.status, 405);
});

test('task lifecycle is persistent and validated', async () => {
  resetMemoryStore();
  const created = await fetch(`${apiUrl}/api/assistant/tasks`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ title: 'Finish project report', priority: 'high', dueAt: 1790000000000 }),
  });
  assert.equal(created.status, 201);
  const { task } = await created.json();
  assert.equal(task.status, 'pending');

  const completed = await fetch(`${apiUrl}/api/assistant/tasks`, {
    method: 'PATCH',
    headers: authHeaders(),
    body: JSON.stringify({ id: task.id, status: 'completed', evidence: 'user_confirmed' }),
  });
  const completedBody = await completed.json();
  assert.equal(completedBody.task.status, 'completed');
  assert.ok(completedBody.task.completed_at);

  const list = await (await fetch(`${apiUrl}/api/assistant/tasks`, { headers: authHeaders() })).json();
  assert.equal(list.count, 1);

  const reopened = await fetch(`${apiUrl}/api/assistant/tasks`, {
    method: 'PATCH',
    headers: authHeaders(),
    body: JSON.stringify({ id: task.id, status: 'pending' }),
  });
  assert.equal((await reopened.json()).task.completed_at, null);

  const badStatus = await fetch(`${apiUrl}/api/assistant/tasks`, {
    method: 'PATCH',
    headers: authHeaders(),
    body: JSON.stringify({ id: task.id, status: 'exploded' }),
  });
  assert.equal(badStatus.status, 200);
  assert.equal((await badStatus.json()).task.status, 'pending', 'unknown status is ignored, not applied');

  const missingTitle = await fetch(`${apiUrl}/api/assistant/tasks`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ title: '' }),
  });
  assert.equal(missingTitle.status, 400);

  const deleted = await fetch(`${apiUrl}/api/assistant/tasks?id=${encodeURIComponent(task.id)}`, {
    method: 'DELETE',
    headers: authHeaders(),
  });
  assert.equal((await deleted.json()).deleted, true);
  assert.equal((await (await fetch(`${apiUrl}/api/assistant/tasks`, { headers: authHeaders() })).json()).count, 0);
});

test('tasks are isolated per device id', async () => {
  resetMemoryStore();
  await fetch(`${apiUrl}/api/assistant/tasks`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ title: 'Device A task' }),
  });
  const other = await (
    await fetch(`${apiUrl}/api/assistant/tasks`, {
      headers: { authorization: `Bearer ${TOKEN}`, 'x-device-id': 'device-test-9999' },
    })
  ).json();
  assert.equal(other.count, 0);
});

test('invalid JSON body is rejected with 400', async () => {
  const response = await fetch(`${apiUrl}/api/assistant/command`, {
    method: 'POST',
    headers: authHeaders(),
    body: '{oops',
  });
  assert.equal(response.status, 400);
  assert.equal((await response.json()).error.code, 'invalid_json');
});

test('chat reaches the provider and returns its reply', async () => {
  fakeMode = 'chat';
  const response = await fetch(`${apiUrl}/api/assistant/chat`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ messages: [{ role: 'user', content: 'Namaste Jarvis' }] }),
  });
  assert.equal(response.status, 200);
  const body = await response.json();
  assert.match(body.reply, /Jarvis/);
  assert.equal(body.provider, 'ovh');
  assert.equal(lastProviderRequest.body.stream, false);
  assert.equal(lastProviderRequest.headers.authorization, undefined, 'anonymous tier sends no key');
});

test('chat streams server-sent events when requested', async () => {
  fakeMode = 'chat';
  const response = await fetch(`${apiUrl}/api/assistant/chat`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ messages: [{ role: 'user', content: 'hello' }], stream: true }),
  });
  assert.equal(response.status, 200);
  assert.match(response.headers.get('content-type'), /text\/event-stream/);
  const text = await response.text();
  assert.match(text, /"delta"/);
  assert.match(text, /"done":true/);
});

test('command endpoint returns a validated contract and applies it', async () => {
  fakeMode = 'command';
  resetMemoryStore();
  // Structured commands prefer a provider with a native JSON mode.
  process.env.JARVIS_PROVIDER_ALLOWLIST = 'groq';
  process.env.GROQ_API_KEY = 'test-groq-key';
  process.env.JARVIS_PROVIDER_BASE_URL_GROQ = fakeUrl;
  const response = await fetch(`${apiUrl}/api/assistant/command`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ text: 'Jarvis, kal 6 baje padhna hai' }),
  });
  const body = await response.json();
  assert.equal(response.status, 200);
  assert.equal(body.command.intent, 'create_tasks');
  assert.equal(body.command.tasks[0].title, 'Study for exam');
  assert.equal(body.command.tasks[0].dueAt, Date.parse('2026-11-13T12:30:00Z'));
  assert.equal(body.persisted, false, 'memory store does not persist to the cloud');
  assert.deepEqual(lastProviderRequest.body.response_format, { type: 'json_object' });
  process.env.JARVIS_PROVIDER_ALLOWLIST = 'ovh';
  delete process.env.GROQ_API_KEY;
  delete process.env.JARVIS_PROVIDER_BASE_URL_GROQ;
});

test('command degrades gracefully when no provider is configured', async () => {
  process.env.JARVIS_PROVIDER_ALLOWLIST = 'none';
  resetRouterState();
  const response = await fetch(`${apiUrl}/api/assistant/command`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ text: 'add a task' }),
  });
  process.env.JARVIS_PROVIDER_ALLOWLIST = 'ovh';
  assert.equal(response.status, 200);
  const body = await response.json();
  assert.equal(body.degraded, true);
  assert.equal(body.command, null);
});

test('status endpoint describes providers without leaking your keys', async () => {
  process.env.OPENROUTER_API_KEY = 'openrouter-secret-abc';
  const response = await fetch(`${apiUrl}/api/assistant/status`, { headers: authHeaders() });
  const body = await response.json();
  assert.equal(response.status, 200);
  assert.ok(Array.isArray(body.providers) && body.providers.length >= 8);
  assert.ok(!JSON.stringify(body).includes('openrouter-secret-abc'));
  delete process.env.OPENROUTER_API_KEY;
});

test('rate limiting returns 429 after the burst allowance', async () => {
  resetRateLimits();
  let limited = 0;
  for (let i = 0; i < 24; i += 1) {
    const response = await fetch(`${apiUrl}/api/assistant/command`, {
      method: 'POST',
      headers: authHeaders({ 'x-device-id': 'device-burst-0001' }),
      body: JSON.stringify({ text: 'ping' }),
    });
    if (response.status === 429) limited += 1;
  }
  assert.ok(limited > 0, 'expected at least one 429');
  resetRateLimits();
});

test('memory endpoints create, list and clear user-approved memories', async () => {
  resetMemoryStore();
  const created = await fetch(`${apiUrl}/api/assistant/memory`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ kind: 'preference', content: 'prefers Hindi replies' }),
  });
  assert.equal(created.status, 201);
  const list = await (await fetch(`${apiUrl}/api/assistant/memory`, { headers: authHeaders() })).json();
  assert.equal(list.count, 1);
  const cleared = await (
    await fetch(`${apiUrl}/api/assistant/memory?all=1`, { method: 'DELETE', headers: authHeaders() })
  ).json();
  assert.equal(cleared.cleared, 1);
});

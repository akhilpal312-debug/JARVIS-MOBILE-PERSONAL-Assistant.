// Smoke-tests the exact artifact that gets uploaded to Vercel.
//
//   node scripts/test-bundle.mjs
//
// It serves dist/api/[...path].js through a plain Node HTTP server and makes
// real requests, so path dispatch, auth and graceful degradation are verified on
// the bundled code rather than only on the modular source.
import http from 'node:http';
import assert from 'node:assert/strict';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const { default: handler } = await import(resolve(root, 'dist/api/[...path].js'));

process.env.JARVIS_API_TOKEN = 'bundle-test-token';
process.env.JARVIS_PROVIDER_ALLOWLIST = 'none';

const server = http.createServer(async (req, res) => {
  const chunks = [];
  for await (const chunk of req) chunks.push(chunk);
  const body = chunks.length ? Buffer.concat(chunks).toString('utf8') : undefined;
  const request = new Request(new URL(req.url, 'http://127.0.0.1'), {
    method: req.method,
    headers: req.headers,
    body,
  });
  const response = await handler(request);
  res.writeHead(response.status, Object.fromEntries(response.headers.entries()));
  res.end(Buffer.from(await response.arrayBuffer()));
});

await new Promise((done) => server.listen(0, '127.0.0.1', done));
const base = `http://127.0.0.1:${server.address().port}`;
const auth = { authorization: 'Bearer bundle-test-token', 'x-device-id': 'device-bundle-01' };

let failures = 0;
async function check(name, fn) {
  try {
    await fn();
    console.log(`✔ ${name}`);
  } catch (error) {
    failures += 1;
    console.error(`✖ ${name}: ${error.message}`);
  }
}

await check('GET /api/health dispatches to the health route', async () => {
  const response = await fetch(`${base}/api/health`);
  assert.equal(response.status, 200);
  const body = await response.json();
  assert.equal(body.ok, true);
  assert.equal(body.service, 'jarvis-backend');
});

await check('trailing slash is tolerated', async () => {
  const response = await fetch(`${base}/api/health/`);
  assert.equal(response.status, 200);
});

await check('unknown endpoint returns 404 with the endpoint list', async () => {
  const response = await fetch(`${base}/api/nope`);
  assert.equal(response.status, 404);
  const body = await response.json();
  assert.ok(body.endpoints.includes('/api/assistant/chat'));
});

await check('nested route requires a token', async () => {
  assert.equal((await fetch(`${base}/api/assistant/tasks`)).status, 401);
  assert.equal((await fetch(`${base}/api/assistant/memory`)).status, 401);
});

await check('authorised request reaches the handler', async () => {
  const response = await fetch(`${base}/api/assistant/tasks`, { headers: auth });
  assert.equal(response.status, 200);
  const body = await response.json();
  assert.ok(Array.isArray(body.tasks));
});

await check('command degrades honestly when no provider is configured', async () => {
  const response = await fetch(`${base}/api/assistant/command`, {
    method: 'POST',
    headers: { ...auth, 'content-type': 'application/json' },
    body: JSON.stringify({ text: 'add a task' }),
  });
  assert.equal(response.status, 200);
  const body = await response.json();
  assert.equal(body.degraded, true);
});

server.close();
console.log(failures === 0 ? '\nBUNDLE OK' : `\n${failures} BUNDLE CHECK(S) FAILED`);
process.exit(failures === 0 ? 0 : 1);

// Local development / integration-test server.
//
// Vercel runs api/**/*.js directly with the Web-standard Request/Response
// signature. This adapter gives the exact same handlers a plain Node HTTP
// server so the backend can be exercised locally without the Vercel CLI:
//
//   node scripts/dev-server.js            # http://127.0.0.1:8787
//   PORT=9000 node scripts/dev-server.js
import http from 'node:http';
import { readFileSync, existsSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');

// Minimal .env loader (no dependency): KEY=value lines, ignores comments.
function loadEnv() {
  for (const name of ['.env.local', '.env']) {
    const file = resolve(root, name);
    if (!existsSync(file)) continue;
    for (const line of readFileSync(file, 'utf8').split('\n')) {
      const match = /^([A-Z0-9_]+)\s*=\s*(.*)$/.exec(line.trim());
      if (match && !process.env[match[1]]) {
        process.env[match[1]] = match[2].replace(/^["']|["']$/g, '');
      }
    }
  }
}
loadEnv();

const routes = {
  '/api/health': await import('../api/health.js'),
  '/api/assistant/status': await import('../api/assistant/status.js'),
  '/api/assistant/chat': await import('../api/assistant/chat.js'),
  '/api/assistant/command': await import('../api/assistant/command.js'),
  '/api/assistant/tasks': await import('../api/assistant/tasks.js'),
  '/api/assistant/memory': await import('../api/assistant/memory.js'),
};

function readBody(req) {
  return new Promise((resolveBody, reject) => {
    const chunks = [];
    let size = 0;
    req.on('data', (chunk) => {
      size += chunk.length;
      if (size > 1024 * 1024) {
        reject(new Error('payload too large'));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on('end', () => resolveBody(Buffer.concat(chunks).toString('utf8')));
    req.on('error', reject);
  });
}

export function createServer() {
  return http.createServer(async (req, res) => {
    try {
      const url = new URL(req.url, `http://${req.headers.host || '127.0.0.1'}`);
      const mod = routes[url.pathname.replace(/\/$/, '')];
      if (!mod) {
        res.writeHead(404, { 'content-type': 'application/json' });
        res.end(JSON.stringify({ error: { code: 'not_found', message: `No route for ${url.pathname}` } }));
        return;
      }
      const hasBody = !['GET', 'HEAD'].includes(req.method);
      const request = new Request(url, {
        method: req.method,
        headers: req.headers,
        body: hasBody ? await readBody(req) : undefined,
      });
      const handler = mod.default || mod[req.method];
      const response = await handler(request);
      const headers = Object.fromEntries(response.headers.entries());
      res.writeHead(response.status, headers);
      if (req.method === 'HEAD') {
        res.end();
        return;
      }
      res.end(Buffer.from(await response.arrayBuffer()));
    } catch (error) {
      res.writeHead(500, { 'content-type': 'application/json' });
      res.end(JSON.stringify({ error: { code: 'adapter_error', message: String(error?.message || error) } }));
    }
  });
}

const isMain = process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  const port = Number(process.env.PORT || 8787);
  const host = process.env.HOST || '0.0.0.0';
  createServer().listen(port, host, () => {
    console.log(`Jarvis backend listening on http://${host}:${port}`);
    console.log(`storage=${process.env.SUPABASE_URL ? 'supabase' : 'memory'} token=${process.env.JARVIS_API_TOKEN ? 'set' : 'MISSING'}`);
  });
}

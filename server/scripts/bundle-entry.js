// Deployment entry point.
//
// Vercel is happiest with one function per route file, which is how the source
// in api/ is organised and tested. For file-based deployment (no git push) the
// whole backend is bundled into a single catch-all function:
//
//   api/[...path].js   <- built by scripts/bundle.mjs from this file
//
// This entry only dispatches on the request path; every handler below is the
// same module the tests exercise.
import health from '../api/health.js';
import status from '../api/assistant/status.js';
import chat from '../api/assistant/chat.js';
import command from '../api/assistant/command.js';
import tasks from '../api/assistant/tasks.js';
import memory from '../api/assistant/memory.js';

const routes = {
  '/api/health': health,
  '/api/assistant/status': status,
  '/api/assistant/chat': chat,
  '/api/assistant/command': command,
  '/api/assistant/tasks': tasks,
  '/api/assistant/memory': memory,
};

function normalise(url) {
  try {
    return new URL(url, 'https://jarvis.local').pathname.replace(/\/+$/, '');
  } catch {
    return url;
  }
}

export default async function handler(request) {
  const path = normalise(request.url);
  const route = routes[path];
  if (!route) {
    return new Response(
      JSON.stringify({
        error: { code: 'not_found', message: `No Jarvis endpoint at ${path}.` },
        endpoints: Object.keys(routes),
      }),
      { status: 404, headers: { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' } },
    );
  }
  return route(request);
}

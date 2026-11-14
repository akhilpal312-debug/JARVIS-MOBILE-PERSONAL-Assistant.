import { json, handleError, methodNotAllowed, logEvent } from '../lib/http.js';
import { configuredProviders } from '../lib/providers.js';
import { storeKind } from '../lib/store.js';

export const config = { runtime: 'nodejs' };

export async function GET() {
  return json({
    ok: true,
    service: 'jarvis-backend',
    version: '1.0.0',
    time: new Date().toISOString(),
    providers: { configured: configuredProviders().length },
    storage: storeKind(),
  });
}

export async function POST() {
  logEvent('health_post_rejected');
  return methodNotAllowed(['GET']);
}

export default async function handler(req) {
  try {
    if (req.method === 'GET') return await GET();
    if (req.method === 'HEAD') return json({ ok: true });
    return methodNotAllowed(['GET']);
  } catch (error) {
    return handleError(error);
  }
}

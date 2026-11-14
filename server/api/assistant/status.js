import { json, handleError, methodNotAllowed, logEvent } from '../../lib/http.js';
import { authorize } from '../../lib/auth.js';
import { describeProviders, configuredProviders } from '../../lib/providers.js';
import { routerState } from '../../lib/router.js';
import { storeKind } from '../../lib/store.js';

export const config = { runtime: 'nodejs' };

async function probe(provider) {
  const started = Date.now();
  try {
    const headers = { accept: 'application/json', 'user-agent': 'Jarvis-Android-Backend/1.0' };
    if (provider.apiKey) headers.authorization = `Bearer ${provider.apiKey}`;
    const response = await fetch(`${provider.baseUrl.replace(/\/$/, '')}/models`, {
      headers,
      signal: AbortSignal.timeout ? AbortSignal.timeout(4500) : undefined,
    });
    return { slug: provider.slug, reachable: response.ok, httpStatus: response.status, latencyMs: Date.now() - started };
  } catch (error) {
    return {
      slug: provider.slug,
      reachable: false,
      httpStatus: null,
      latencyMs: Date.now() - started,
      reason: String(error?.name || 'error'),
    };
  }
}

export async function GET(req) {
  authorize(req);
  const url = new URL(req.url);
  const shouldProbe = url.searchParams.get('probe') === '1';

  const providers = describeProviders();
  let probes = [];
  if (shouldProbe) {
    probes = await Promise.all(configuredProviders().map((provider) => probe(provider)));
    logEvent('provider_probe', { configured: providers.filter((entry) => entry.configured).length });
  }

  return json({
    ok: true,
    version: '1.0.0',
    time: new Date().toISOString(),
    storage: storeKind(),
    providers,
    probes,
    circuitBreakers: routerState(),
    hints: {
      keys: 'Provider keys are server-side environment variables only. The app never receives them.',
      freeTier: 'Free-tier terms are descriptive; re-check each provider pricing page before production use.',
      storage:
        storeKind() === 'memory'
          ? 'Cloud sync uses per-instance memory: set SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY for durable sync. The app keeps working locally without it.'
          : 'Cloud sync is durable (Supabase REST).',
    },
  });
}

export default async function handler(req) {
  try {
    if (req.method === 'GET') return await GET(req);
    return methodNotAllowed(['GET']);
  } catch (error) {
    return handleError(error);
  }
}

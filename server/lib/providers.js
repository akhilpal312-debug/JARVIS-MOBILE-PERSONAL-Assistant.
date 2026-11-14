// Provider registry for the Jarvis backend.
//
// Source of truth for the entries below: https://github.com/mnfst/awesome-free-llm-apis
// (a *directory* of provider APIs — it does not issue credentials). Every entry
// records where the key comes from, which models the directory lists, the
// documented free-tier terms and the known limitations. Nothing here invents an
// endpoint: each base URL is the one published by the provider / directory.
//
// Free-tier terms change. `freeTier` and `rateLimit` are descriptive strings
// copied from the directory as of 2026-11 and must be re-checked against the
// provider's own pricing page before relying on them in production.
//
// All providers except Cloudflare expose an OpenAI-compatible /chat/completions
// surface, which is what lib/router.js speaks. Cloudflare exposes the same shape
// under an account-scoped path.

export const PROVIDERS = [
  {
    slug: 'groq',
    name: 'Groq',
    country: 'US',
    baseUrl: 'https://api.groq.com/openai/v1',
    keyEnv: 'GROQ_API_KEY',
    keyOptional: false,
    docsUrl: 'https://console.groq.com/docs',
    models: ['openai/gpt-oss-120b', 'openai/gpt-oss-20b', 'qwen/qwen3.8-27b'],
    caps: { chat: true, json: true, tools: true, reasoning: true },
    freeTier: 'Free tier, no credit card required. Free-plan prompts are governed by Groq data policies.',
    rateLimit: '30 RPM, 1,000 RPD per listed chat model (directory, 2026-11).',
    priority: 10,
    contextWindow: 131072,
  },
  {
    slug: 'gemini',
    name: 'Google Gemini (OpenAI-compatible)',
    country: 'US',
    baseUrl: 'https://generativelanguage.googleapis.com/v1beta/openai',
    keyEnv: 'GEMINI_API_KEY',
    keyOptional: false,
    docsUrl: 'https://ai.google.dev/gemini-api/docs/openai',
    models: ['gemini-2.5-flash', 'gemini-2.5-flash-lite'],
    caps: { chat: true, json: true, tools: true, reasoning: true },
    freeTier: 'Free tier without a credit card; free-tier prompts may be used to improve Google products.',
    rateLimit: 'Google no longer publishes per-model free-tier limits; check AI Studio quotas.',
    priority: 20,
    contextWindow: 1000000,
  },
  {
    slug: 'mistral',
    name: 'Mistral AI',
    country: 'FR',
    baseUrl: 'https://api.mistral.ai/v1',
    keyEnv: 'MISTRAL_API_KEY',
    keyOptional: false,
    docsUrl: 'https://docs.mistral.ai',
    models: ['mistral-small-latest', 'ministral-8b-latest'],
    caps: { chat: true, json: true, tools: true, reasoning: false },
    freeTier: 'Free mode enabled by default, no credit card. Free-mode prompts may train Mistral models unless opted out.',
    rateLimit: 'Historically ~1 RPS / 500K TPM; the provider now points at the admin Limits page.',
    priority: 30,
    contextWindow: 262144,
  },
  {
    slug: 'nvidia',
    name: 'NVIDIA NIM',
    country: 'US',
    baseUrl: 'https://integrate.api.nvidia.com/v1',
    keyEnv: 'NVIDIA_API_KEY',
    keyOptional: false,
    docsUrl: 'https://build.nvidia.com',
    models: ['openai/gpt-oss-20b', 'nvidia/nemotron-3-ultra-550b-a55b'],
    caps: { chat: true, json: true, tools: true, reasoning: true },
    freeTier: 'Free with NVIDIA Developer Program membership.',
    rateLimit: '40 RPM, 10,000 RPD (directory, 2026-11).',
    priority: 40,
    contextWindow: 131072,
  },
  {
    slug: 'zai',
    name: 'Z AI (Zhipu AI)',
    country: 'CN',
    baseUrl: 'https://api.z.ai/api/paas/v4',
    keyEnv: 'ZAI_API_KEY',
    keyOptional: false,
    docsUrl: 'https://docs.z.ai',
    models: ['glm-4.7-flash', 'glm-4.6v-flash'],
    caps: { chat: true, json: true, tools: false, reasoning: true },
    freeTier: 'Permanent free Flash models, no credit card required.',
    rateLimit: '1 concurrent request on Flash models.',
    priority: 50,
    contextWindow: 204800,
  },
  {
    slug: 'cloudflare',
    name: 'Cloudflare Workers AI',
    country: 'US',
    baseUrlTemplate: 'https://api.cloudflare.com/client/v4/accounts/{account_id}/ai/v1',
    keyEnv: 'CLOUDFLARE_API_TOKEN',
    extraEnv: ['CLOUDFLARE_ACCOUNT_ID'],
    keyOptional: false,
    docsUrl: 'https://developers.cloudflare.com/workers-ai',
    models: ['@cf/meta/llama-3.3-70b-instruct-fp8-fast', '@cf/openai/gpt-oss-120b'],
    caps: { chat: true, json: false, tools: false, reasoning: false },
    freeTier: '10,000 Neurons/day free, no credit card required.',
    rateLimit: 'Shared 10K neurons/day across models.',
    priority: 60,
    contextWindow: 24576,
  },
  {
    slug: 'openrouter',
    name: 'OpenRouter (free pool)',
    country: 'US',
    baseUrl: 'https://openrouter.ai/api/v1',
    keyEnv: 'OPENROUTER_API_KEY',
    keyOptional: false,
    docsUrl: 'https://openrouter.ai/docs',
    models: [
      'google/gemma-4-26b-a4b-it:free',
      'liquid/lfm-2.5-2.6b:free',
      'nvidia/nemotron-3.5-lightning:free',
    ],
    caps: { chat: true, json: true, tools: true, reasoning: false },
    freeTier: 'Models with the :free suffix; free providers may log prompts for training.',
    rateLimit: '20 RPM, 50 RPD across all free models.',
    priority: 70,
    contextWindow: 262144,
  },
  {
    slug: 'llm7',
    name: 'LLM7.io',
    country: 'UK',
    baseUrl: 'https://api.llm7.io/v1',
    keyEnv: 'LLM7_API_KEY',
    keyOptional: true,
    docsUrl: 'https://docs.llm7.io',
    models: ['gpt-oss:20b', 'nemotron-3-nano:30b'],
    caps: { chat: true, json: false, tools: false, reasoning: false },
    freeTier: 'Anonymous access to turbo models with no key; a free token raises limits.',
    rateLimit: 'Free token: 1 RPS, 60 RPM, 250 requests/hour, 100K tokens/24h.',
    priority: 80,
    contextWindow: 128000,
  },
  {
    slug: 'ovh',
    name: 'OVHcloud AI Endpoints (anonymous)',
    country: 'FR',
    baseUrl: 'https://oai.endpoints.kepler.ai.cloud.ovh.net/v1',
    keyEnv: 'OVH_AI_TOKEN',
    keyOptional: true,
    docsUrl: 'https://www.ovhcloud.com/en/public-cloud/ai-endpoints/catalog/',
    models: ['gpt-oss-20b', 'Mistral-Nemo-Instruct-2407'],
    caps: { chat: true, json: false, tools: false, reasoning: false },
    freeTier: 'Permanent free anonymous tier: no account and no key required.',
    rateLimit: '2 RPM per IP per model (anonymous).',
    priority: 90,
    contextWindow: 131072,
  },
];

export function providerBySlug(slug) {
  return PROVIDERS.find((provider) => provider.slug === slug);
}

// Resolves the deployment configuration for one provider, or null when it is not
// usable (no key for a key-required provider, missing account id, allowlist).
export function resolveProvider(provider, env = process.env) {
  const allowlist = String(env.JARVIS_PROVIDER_ALLOWLIST || '')
    .split(',')
    .map((value) => value.trim())
    .filter(Boolean);
  if (allowlist.length > 0 && !allowlist.includes(provider.slug)) return null;

  const raw = provider.keyEnv ? String(env[provider.keyEnv] || '').trim() : '';
  // Placeholder values copied from .env.example must never count as a key.
  const key = /^(paste-|changeme|your-|<)/i.test(raw) ? '' : raw;
  if (!key && !provider.keyOptional) return null;

  // Optional base-URL override, used for a self-hosted proxy or for tests.
  // JARVIS_PROVIDER_BASE_URL_GROQ=https://my-proxy.example/v1
  const overrideEnv = `JARVIS_PROVIDER_BASE_URL_${provider.slug.toUpperCase().replace(/-/g, '_')}`;
  const overrideUrl = String(env[overrideEnv] || '').trim();

  let baseUrl = overrideUrl || provider.baseUrl;
  if (provider.baseUrlTemplate && !overrideUrl) {
    const values = {};
    for (const name of provider.extraEnv || []) {
      const value = String(env[name] || '').trim();
      if (!value) return null;
      values[name] = value;
    }
    baseUrl = provider.baseUrlTemplate.replace(/\{(\w+)\}/g, (_, name) => {
      const envName = Object.keys(values).find((entry) => entry.toLowerCase().includes(name.toLowerCase()));
      return envName ? values[envName] : '';
    });
    if (!baseUrl || baseUrl.includes('{')) return null;
  }

  return {
    ...provider,
    baseUrl,
    apiKey: key || undefined,
    configured: true,
    keyOptional: provider.keyOptional,
  };
}

export function configuredProviders(env = process.env) {
  return PROVIDERS.map((provider) => resolveProvider(provider, env)).filter(Boolean);
}

// Describes each provider without ever exposing key material.
export function describeProviders(env = process.env) {
  return PROVIDERS.map((provider) => {
    const resolved = resolveProvider(provider, env);
    return {
      slug: provider.slug,
      name: provider.name,
      country: provider.country,
      models: provider.models,
      capabilities: provider.caps,
      freeTier: provider.freeTier,
      rateLimit: provider.rateLimit,
      docsUrl: provider.docsUrl,
      keyEnv: provider.keyEnv,
      keyOptional: provider.keyOptional,
      configured: Boolean(resolved),
      status: resolved ? 'available' : provider.keyOptional ? 'unavailable' : 'not_configured',
    };
  });
}

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { describeProviders, configuredProviders, resolveProvider, PROVIDERS } from '../lib/providers.js';
import { buildChain } from '../lib/router.js';

test('registry entries never embed secrets and always document a key env', () => {
  for (const provider of PROVIDERS) {
    assert.ok(provider.slug && provider.name && provider.baseUrl || provider.baseUrlTemplate);
    assert.ok(provider.keyEnv, `${provider.slug} needs keyEnv`);
    assert.ok(provider.models.length > 0);
    assert.ok(provider.freeTier.length > 10);
    assert.ok(!JSON.stringify(provider).match(/sk-[a-zA-Z0-9]{10}/));
  }
});

test('keyless providers are usable without any environment', () => {
  const chain = buildChain({ env: {} });
  assert.ok(chain.some((entry) => entry.slug === 'ovh'), 'anonymous OVH tier must be available');
  assert.ok(!chain.some((entry) => entry.slug === 'groq'), 'groq has no key so it must not appear');
});

test('a configured key adds the provider in priority order', () => {
  const chain = buildChain({ env: { GROQ_API_KEY: 'test-groq-key' } });
  assert.equal(chain[0].slug, 'groq');
});

test('cloudflare requires both token and account id', () => {
  assert.equal(resolveProvider(PROVIDERS.find((p) => p.slug === 'cloudflare'), { CLOUDFLARE_API_TOKEN: 'x' }), null);
  const resolved = resolveProvider(PROVIDERS.find((p) => p.slug === 'cloudflare'), {
    CLOUDFLARE_API_TOKEN: 'x',
    CLOUDFLARE_ACCOUNT_ID: 'acc123',
  });
  assert.match(resolved.baseUrl, /accounts\/acc123\/ai\/v1$/);
});

test('allowlist restricts the chain', () => {
  const chain = buildChain({ env: { JARVIS_PROVIDER_ALLOWLIST: 'ovh' } });
  assert.deepEqual(chain.map((entry) => entry.slug), ['ovh']);
  assert.equal(buildChain({ env: { JARVIS_PROVIDER_ALLOWLIST: 'none' } }).length, 0);
});

test('placeholder keys are treated as unset', () => {
  assert.equal(configuredProviders({ GROQ_API_KEY: 'paste-your-key-here' }).some((p) => p.slug === 'groq'), false);
});

test('JSON-capable providers are preferred for structured commands', () => {
  const chain = buildChain({ needJson: true, env: { LLM7_API_KEY: 'k', GROQ_API_KEY: 'k' } });
  assert.equal(chain[0].slug, 'groq');
  assert.ok(chain[0].caps.json);
});

test('describeProviders reports status without exposing key material', () => {
  const described = describeProviders({ GEMINI_API_KEY: 'super-secret-value' });
  const gemini = described.find((entry) => entry.slug === 'gemini');
  assert.equal(gemini.configured, true);
  assert.equal(gemini.status, 'available');
  assert.ok(!JSON.stringify(described).includes('super-secret-value'));
});

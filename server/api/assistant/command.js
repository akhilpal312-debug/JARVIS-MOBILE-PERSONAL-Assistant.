import { json, readJson, str, handleError, methodNotAllowed, logEvent, HttpError } from '../../lib/http.js';
import { authorize } from '../../lib/auth.js';
import { enforceRateLimit } from '../../lib/ratelimit.js';
import { complete } from '../../lib/router.js';
import { commandPrompt, extractJson, validateCommand } from '../../lib/prompt.js';
import { listTasks, listMemories, upsertTask, upsertMemory, storeKind } from '../../lib/store.js';

export const config = { runtime: 'nodejs' };

export async function POST(req) {
  const { userId } = authorize(req);
  enforceRateLimit(`command:${userId}`, { limit: 20, windowMs: 60_000 });

  const body = await readJson(req);
  const text = str(body.text, { field: 'text', required: true, max: 2000 });
  const persist = body.persist !== false;

  let tasks = [];
  let memories = [];
  try {
    [tasks, memories] = await Promise.all([listTasks(userId), listMemories(userId)]);
  } catch (error) {
    logEvent('context_unavailable', { code: error?.code });
  }

  let result;
  try {
    result = await complete({
      messages: [{ role: 'user', content: commandPrompt({ text, tasks, memories }) }],
      needJson: true,
      maxTokens: 1000,
      temperature: 0.2,
    });
  } catch (error) {
    if (error instanceof HttpError && (error.code === 'no_provider_configured' || error.code === 'all_providers_failed')) {
      logEvent('command_degraded', { code: error.code });
      return json({
        degraded: true,
        reason: error.message,
        command: null,
        attempts: error.details?.attempts || [],
        note: 'The app falls back to its on-device bilingual command parser.',
      });
    }
    throw error;
  }

  const parsed = validateCommand(extractJson(result.text));
  if (!parsed) {
    logEvent('command_unparseable', { provider: result.provider });
    return json({
      degraded: true,
      reason: 'The model reply was not a valid command object.',
      command: null,
      provider: result.provider,
      model: result.model,
    });
  }

  const createdTaskIds = [];
  const createdMemoryIds = [];
  if (persist && storeKind() === 'supabase') {
    for (const task of parsed.tasks) {
      const saved = await upsertTask(userId, task);
      createdTaskIds.push(saved.id);
    }
    for (const memory of parsed.memories) {
      const saved = await upsertMemory(userId, memory);
      createdMemoryIds.push(saved.id);
    }
  }

  logEvent('command_ok', { provider: result.provider, model: result.model, intent: parsed.intent });

  return json({
    command: parsed,
    provider: result.provider,
    providerName: result.providerName,
    model: result.model,
    persisted: persist && storeKind() === 'supabase',
    createdTaskIds,
    createdMemoryIds,
  });
}

export default async function handler(req) {
  try {
    if (req.method === 'POST') return await POST(req);
    return methodNotAllowed(['POST']);
  } catch (error) {
    return handleError(error);
  }
}

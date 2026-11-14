import { json, sse, readJson, str, handleError, methodNotAllowed, logEvent, HttpError } from '../../lib/http.js';
import { authorize } from '../../lib/auth.js';
import { enforceRateLimit } from '../../lib/ratelimit.js';
import { complete } from '../../lib/router.js';
import { systemPrompt } from '../../lib/prompt.js';
import { listTasks, listMemories, storeKind } from '../../lib/store.js';

export const config = { runtime: 'nodejs' };

const ROLES = ['user', 'assistant', 'system'];

function cleanMessages(input) {
  if (!Array.isArray(input) || input.length === 0) {
    throw new HttpError(400, 'invalid_messages', '"messages" must be a non-empty array.');
  }
  return input
    .filter((message) => message && ROLES.includes(message.role) && typeof message.content === 'string')
    .slice(-14)
    .map((message) => ({
      role: message.role === 'system' ? 'user' : message.role,
      content: message.content.trim().slice(0, 4000),
    }))
    .filter((message) => message.content.length > 0);
}

export async function POST(req) {
  const { userId } = authorize(req);
  enforceRateLimit(`chat:${userId}`, { limit: 30, windowMs: 60_000 });

  const body = await readJson(req);
  const messages = cleanMessages(body.messages);
  const language = str(body.language, { field: 'language', max: 20 }) || 'auto';
  const wantsStream = body.stream === true;

  let tasks = [];
  let memories = [];
  if (body.includeContext !== false) {
    try {
      [tasks, memories] = await Promise.all([listTasks(userId), listMemories(userId)]);
    } catch (error) {
      logEvent('context_unavailable', { store: storeKind(), code: error?.code });
    }
  }

  const prompt = systemPrompt({ tasks, memories, language });
  const payload = [{ role: 'system', content: prompt }, ...messages];

  try {
    const result = await complete({ messages: payload, maxTokens: 900, temperature: 0.6 });
    logEvent('chat_ok', { provider: result.provider, model: result.model, store: storeKind() });

    if (wantsStream) {
      const encoder = new TextEncoder();
      const stream = new ReadableStream({
        start(controller) {
          // Chunked delivery of the finished reply: the phone can start speaking
          // the first sentences before the rest has rendered.
          const words = result.text.split(/(\s+)/);
          for (let i = 0; i < words.length; i += 6) {
            controller.enqueue(encoder.encode(`data: ${JSON.stringify({ delta: words.slice(i, i + 6).join('') })}\n\n`));
          }
          controller.enqueue(
            encoder.encode(
              `data: ${JSON.stringify({ done: true, provider: result.provider, model: result.model })}\n\n`,
            ),
          );
          controller.close();
        },
      });
      return sse(stream);
    }

    return json({
      reply: result.text,
      provider: result.provider,
      providerName: result.providerName,
      model: result.model,
      usage: result.usage,
      contextTaskCount: tasks.length,
      contextMemoryCount: memories.length,
    });
  } catch (error) {
    if (error instanceof HttpError && (error.code === 'no_provider_configured' || error.code === 'all_providers_failed')) {
      logEvent('chat_degraded', { code: error.code });
      return json(
        {
          degraded: true,
          reply: null,
          reason: error.message,
          attempts: error.details?.attempts || [],
        },
        200,
      );
    }
    throw error;
  }
}

export default async function handler(req) {
  try {
    if (req.method === 'POST') return await POST(req);
    return methodNotAllowed(['POST']);
  } catch (error) {
    return handleError(error);
  }
}

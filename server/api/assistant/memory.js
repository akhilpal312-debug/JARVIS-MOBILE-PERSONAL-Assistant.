import { json, readJson, str, handleError, methodNotAllowed, logEvent, HttpError } from '../../lib/http.js';
import { authorize } from '../../lib/auth.js';
import { enforceRateLimit } from '../../lib/ratelimit.js';
import { listMemories, upsertMemory, deleteMemory, storeKind } from '../../lib/store.js';

export const config = { runtime: 'nodejs' };

export async function GET(req) {
  const { userId } = authorize(req);
  const memories = await listMemories(userId);
  return json({ memories, storage: storeKind(), count: memories.length });
}

export async function POST(req) {
  const { userId } = authorize(req);
  enforceRateLimit(`memory:${userId}`, { limit: 60, windowMs: 60_000 });
  const body = await readJson(req);
  str(body.content, { field: 'content', required: true, max: 1000 });
  const memory = await upsertMemory(userId, body);
  logEvent('memory_saved', { storage: storeKind() });
  return json({ memory }, 201);
}

export async function DELETE(req) {
  const { userId } = authorize(req);
  enforceRateLimit(`memory:${userId}`, { limit: 60, windowMs: 60_000 });
  const url = new URL(req.url);
  const id = url.searchParams.get('id');
  if (!id) {
    // Deleting everything is an explicit user action from the privacy screen.
    if (url.searchParams.get('all') === '1') {
      const memories = await listMemories(userId);
      for (const memory of memories) await deleteMemory(userId, memory.id);
      logEvent('memory_cleared', { count: memories.length });
      return json({ deleted: true, cleared: memories.length });
    }
    throw new HttpError(400, 'missing_field', '"id" is required (or pass all=1 to clear).');
  }
  await deleteMemory(userId, String(id));
  logEvent('memory_deleted');
  return json({ deleted: true, id: String(id) });
}

export default async function handler(req) {
  try {
    switch (req.method) {
      case 'GET':
        return await GET(req);
      case 'POST':
        return await POST(req);
      case 'DELETE':
        return await DELETE(req);
      default:
        return methodNotAllowed(['GET', 'POST', 'DELETE']);
    }
  } catch (error) {
    return handleError(error);
  }
}

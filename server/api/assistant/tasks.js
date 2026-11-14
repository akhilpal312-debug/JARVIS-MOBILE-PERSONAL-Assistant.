import { json, readJson, str, handleError, methodNotAllowed, logEvent, HttpError } from '../../lib/http.js';
import { authorize } from '../../lib/auth.js';
import { enforceRateLimit } from '../../lib/ratelimit.js';
import {
  listTasks,
  upsertTask,
  deleteTask,
  storeKind,
  TASK_STATUSES,
} from '../../lib/store.js';

export const config = { runtime: 'nodejs' };

export async function GET(req) {
  const { userId } = authorize(req);
  const url = new URL(req.url);
  const status = url.searchParams.get('status') || undefined;
  if (status && !TASK_STATUSES.includes(status)) {
    throw new HttpError(400, 'invalid_status', `status must be one of: ${TASK_STATUSES.join(', ')}.`);
  }
  const tasks = await listTasks(userId, { status });
  return json({ tasks, storage: storeKind(), count: tasks.length });
}

export async function POST(req) {
  const { userId } = authorize(req);
  enforceRateLimit(`tasks:${userId}`, { limit: 120, windowMs: 60_000 });
  const body = await readJson(req);
  str(body.title, { field: 'title', required: true, max: 300 });
  const task = await upsertTask(userId, body);
  logEvent('task_saved', { storage: storeKind(), status: task.status });
  return json({ task }, 201);
}

export async function PATCH(req) {
  const { userId } = authorize(req);
  enforceRateLimit(`tasks:${userId}`, { limit: 120, windowMs: 60_000 });
  const body = await readJson(req);
  const id = str(body.id, { field: 'id', required: true, max: 80 });
  const task = await upsertTask(userId, { ...body, id });
  logEvent('task_updated', { storage: storeKind(), status: task.status });
  return json({ task });
}

export async function DELETE(req) {
  const { userId } = authorize(req);
  enforceRateLimit(`tasks:${userId}`, { limit: 120, windowMs: 60_000 });
  const url = new URL(req.url);
  const id = url.searchParams.get('id') || (await readJson(req, { maxBytes: 4096 })).id;
  if (!id) throw new HttpError(400, 'missing_field', '"id" is required.');
  await deleteTask(userId, String(id));
  logEvent('task_deleted', { storage: storeKind() });
  return json({ deleted: true, id: String(id) });
}

export default async function handler(req) {
  try {
    switch (req.method) {
      case 'GET':
        return await GET(req);
      case 'POST':
        return await POST(req);
      case 'PATCH':
        return await PATCH(req);
      case 'DELETE':
        return await DELETE(req);
      default:
        return methodNotAllowed(['GET', 'POST', 'PATCH', 'DELETE']);
    }
  } catch (error) {
    return handleError(error);
  }
}

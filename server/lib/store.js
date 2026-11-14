// Persistent storage for cloud sync.
//
// Two backends:
//  * supabase  — REST (PostgREST) against SUPABASE_URL + SUPABASE_SERVICE_ROLE_KEY.
//                Rows are always scoped by device_id; the service key never leaves the server.
//  * memory    — per-instance fallback used when no database is configured. It keeps
//                the API functional (the Android app is local-first) but does not
//                survive a cold start; /api/assistant/status reports which is active.
import { HttpError } from './http.js';

const TASKS = 'jarvis_tasks';
const MEMORIES = 'jarvis_memories';

const memory = { tasks: new Map(), memories: new Map() };

const TASK_STATUS = ['pending', 'in_progress', 'scheduled', 'blocked', 'completed'];
const PRIORITIES = ['low', 'medium', 'high', 'critical'];

export function storeKind(env = process.env) {
  const url = String(env.SUPABASE_URL || '').trim();
  const key = String(env.SUPABASE_SERVICE_ROLE_KEY || '').trim();
  return url && key ? 'supabase' : 'memory';
}

function supabaseConfig(env) {
  const url = String(env.SUPABASE_URL || '').trim().replace(/\/$/, '');
  const key = String(env.SUPABASE_SERVICE_ROLE_KEY || '').trim();
  if (!url || !key) return null;
  return { url, key };
}

async function rest(path, { method = 'GET', body, env, fetchImpl = fetch, prefer } = {}) {
  const config = supabaseConfig(env);
  if (!config) throw new HttpError(503, 'store_not_configured', 'Cloud sync is not configured.');
  const headers = {
    apikey: config.key,
    authorization: `Bearer ${config.key}`,
    'content-type': 'application/json',
    accept: 'application/json',
  };
  if (prefer) headers.prefer = prefer;

  const response = await fetchImpl(`${config.url}/rest/v1/${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout ? AbortSignal.timeout(8000) : undefined,
  });
  if (!response.ok) {
    const detail = await response.text().catch(() => '');
    throw new HttpError(502, 'store_error', `Storage request failed (${response.status}).`, {
      detail: detail.slice(0, 300),
    });
  }
  if (response.status === 204) return null;
  const text = await response.text();
  return text ? JSON.parse(text) : null;
}

export function normalizeTask(input, deviceId, existing = null) {
  const now = Date.now();
  // PATCH requests may omit fields; fall back to the stored record.
  const title = String(input.title || existing?.title || '').trim();
  if (!title) throw new HttpError(400, 'invalid_task', 'Task title is required.');
  if (title.length > 300) throw new HttpError(400, 'invalid_task', 'Task title is too long.');

  const status = TASK_STATUS.includes(input.status) ? input.status : existing?.status || 'pending';
  const priority = PRIORITIES.includes(input.priority) ? input.priority : existing?.priority || 'medium';
  const dueAt = Number.isFinite(Number(input.dueAt)) && Number(input.dueAt) > 0 ? Number(input.dueAt) : null;

  return {
    id: String(input.id || existing?.id || `t_${now.toString(36)}_${Math.random().toString(36).slice(2, 8)}`),
    device_id: deviceId,
    title: title.slice(0, 300),
    notes: String(input.notes || existing?.notes || '').slice(0, 2000),
    status,
    priority,
    due_at: dueAt,
    reminder_at:
      Number.isFinite(Number(input.reminderAt)) && Number(input.reminderAt) > 0
        ? Number(input.reminderAt)
        : existing?.reminder_at || null,
    completed_at: status === 'completed' ? existing?.completed_at || now : null,
    evidence: String(input.evidence || existing?.evidence || '').slice(0, 500),
    created_at: existing?.created_at || now,
    updated_at: now,
  };
}

export function normalizeMemory(input, deviceId, existing = null) {
  const content = String(input.content || '').trim();
  if (!content) throw new HttpError(400, 'invalid_memory', 'Memory content is required.');
  const now = Date.now();
  return {
    id: String(input.id || existing?.id || `m_${now.toString(36)}_${Math.random().toString(36).slice(2, 8)}`),
    device_id: deviceId,
    kind: ['preference', 'routine', 'fact', 'goal'].includes(input.kind) ? input.kind : 'fact',
    content: content.slice(0, 1000),
    created_at: existing?.created_at || now,
    updated_at: now,
  };
}

export const TASK_STATUSES = TASK_STATUS;
export const TASK_PRIORITIES = PRIORITIES;

export async function listTasks(deviceId, { env = process.env, fetchImpl = fetch, status } = {}) {
  if (storeKind(env) === 'supabase') {
    const filter = status ? `&status=eq.${encodeURIComponent(status)}` : '';
    const rows = await rest(
      `${TASKS}?device_id=eq.${encodeURIComponent(deviceId)}${filter}&order=updated_at.desc&limit=500`,
      { env, fetchImpl },
    );
    return rows || [];
  }
  return [...memory.tasks.values()].filter((row) => row.device_id === deviceId);
}

export async function upsertTask(deviceId, input, { env = process.env, fetchImpl = fetch } = {}) {
  if (storeKind(env) === 'supabase') {
    const existingRows = input.id
      ? await rest(`${TASKS}?id=eq.${encodeURIComponent(input.id)}&device_id=eq.${encodeURIComponent(deviceId)}&limit=1`, {
          env,
          fetchImpl,
        })
      : null;
    const record = normalizeTask(input, deviceId, existingRows?.[0] || null);
    const rows = await rest(`${TASKS}?on_conflict=id`, {
      method: 'POST',
      body: [record],
      env,
      fetchImpl,
      prefer: 'resolution=merge-duplicates,return=representation',
    });
    return rows?.[0] || record;
  }
  const existing = input.id ? memory.tasks.get(String(input.id)) : null;
  const record = normalizeTask(input, deviceId, existing || null);
  memory.tasks.set(record.id, record);
  return record;
}

export async function deleteTask(deviceId, id, { env = process.env, fetchImpl = fetch } = {}) {
  if (storeKind(env) === 'supabase') {
    await rest(`${TASKS}?id=eq.${encodeURIComponent(id)}&device_id=eq.${encodeURIComponent(deviceId)}`, {
      method: 'DELETE',
      env,
      fetchImpl,
    });
    return { deleted: true };
  }
  memory.tasks.delete(String(id));
  return { deleted: true };
}

export async function listMemories(deviceId, { env = process.env, fetchImpl = fetch } = {}) {
  if (storeKind(env) === 'supabase') {
    const rows = await rest(
      `${MEMORIES}?device_id=eq.${encodeURIComponent(deviceId)}&order=updated_at.desc&limit=500`,
      { env, fetchImpl },
    );
    return rows || [];
  }
  return [...memory.memories.values()].filter((row) => row.device_id === deviceId);
}

export async function upsertMemory(deviceId, input, { env = process.env, fetchImpl = fetch } = {}) {
  if (storeKind(env) === 'supabase') {
    const existingRows = input.id
      ? await rest(
          `${MEMORIES}?id=eq.${encodeURIComponent(input.id)}&device_id=eq.${encodeURIComponent(deviceId)}&limit=1`,
          { env, fetchImpl },
        )
      : null;
    const record = normalizeMemory(input, deviceId, existingRows?.[0] || null);
    const rows = await rest(`${MEMORIES}?on_conflict=id`, {
      method: 'POST',
      body: [record],
      env,
      fetchImpl,
      prefer: 'resolution=merge-duplicates,return=representation',
    });
    return rows?.[0] || record;
  }
  const existing = input.id ? memory.memories.get(String(input.id)) : null;
  const record = normalizeMemory(input, deviceId, existing || null);
  memory.memories.set(record.id, record);
  return record;
}

export async function deleteMemory(deviceId, id, { env = process.env, fetchImpl = fetch } = {}) {
  if (storeKind(env) === 'supabase') {
    await rest(`${MEMORIES}?id=eq.${encodeURIComponent(id)}&device_id=eq.${encodeURIComponent(deviceId)}`, {
      method: 'DELETE',
      env,
      fetchImpl,
    });
    return { deleted: true };
  }
  memory.memories.delete(String(id));
  return { deleted: true };
}

// Test-only helper: clears the in-memory store.
export function resetMemoryStore() {
  memory.tasks.clear();
  memory.memories.clear();
}

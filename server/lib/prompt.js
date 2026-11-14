// Persona, prompt construction and the structured command contract.
//
// The command contract is a small JSON object the Android app applies locally,
// so the phone stays the source of truth for its own task list and can keep
// working when the network or every provider is unavailable.
import { TASK_PRIORITIES, TASK_STATUSES } from './store.js';

export const COMMAND_SCHEMA_HINT = `Reply with ONE JSON object and nothing else:
{
  "reply": string,                 // short spoken answer in the user's language
  "language": "hi" | "en" | "hinglish",
  "intent": "create_tasks" | "complete_task" | "delete_task" | "reschedule_task" | "list_tasks" | "create_memory" | "device_action" | "chat",
  "tasks": [{"title": string, "notes": string, "priority": "low"|"medium"|"high"|"critical",
             "dueAtISO": string|null, "reminderAtISO": string|null}],
  "taskRefs": [{"match": string, "status": "pending"|"in_progress"|"scheduled"|"blocked"|"completed"}],
  "memories": [{"kind": "preference"|"routine"|"fact"|"goal", "content": string}],
  "deviceAction": null | {"type": "open_app"|"open_settings"|"calendar_event"|"dial"|"media",
                          "target": string, "title": string|null, "startAtISO": string|null},
  "needsConfirmation": boolean     // true for irreversible or consequential actions
}`;

export function systemPrompt({ tasks = [], memories = [], language = 'auto', now = new Date() } = {}) {
  const taskLines = tasks
    .slice(0, 25)
    .map((task) => `- [${task.status}] ${task.title}${task.due_at ? ` (due ${new Date(task.due_at).toISOString()})` : ''}`)
    .join('\n');
  const memoryLines = memories
    .slice(0, 25)
    .map((memory) => `- (${memory.kind}) ${memory.content}`)
    .join('\n');

  return [
    'You are Jarvis, the user\'s personal assistant on their Android phone.',
    'You are calm, concise and practical. You never invent facts, and you say plainly when something is not possible, offline, or needs a permission.',
    'You understand and answer in Hindi (Devanagari), English and Hinglish. Always mirror the language and script the user used.',
    'You may create, complete, reschedule or remove tasks only through the JSON contract you are given.',
    'Never claim a device action happened; the phone reports the real result back.',
    'Keep spoken replies under 60 words unless the user asks for detail.',
    `Current time: ${now.toISOString()}. User timezone offset: ${-now.getTimezoneOffset() / 60}h.`,
    language !== 'auto' ? `Preferred language: ${language}.` : '',
    taskLines ? `Open tasks:\n${taskLines}` : 'The user has no open tasks.',
    memoryLines ? `User-approved memories:\n${memoryLines}` : 'No saved memories.',
  ]
    .filter(Boolean)
    .join('\n\n');
}

export function commandPrompt({ text, tasks = [], memories = [], now = new Date() }) {
  return [
    systemPrompt({ tasks, memories, now }),
    'Convert the user\'s instruction into the JSON contract. Break multi-part requests into separate tasks.',
    'Do not duplicate a task that already exists with the same meaning. Use deviceAction only for actions the phone can perform with a standard Android intent.',
    COMMAND_SCHEMA_HINT,
    `User said: ${text}`,
  ].join('\n\n');
}

const ISO = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d{1,3})?)?(Z|[+-]\d{2}:\d{2})?$/;

function toEpoch(value) {
  if (!value || typeof value !== 'string') return null;
  if (!ISO.test(value)) return null;
  const parsed = Date.parse(value);
  return Number.isFinite(parsed) ? parsed : null;
}

// Extracts the first JSON object from a model reply (models sometimes wrap it).
export function extractJson(raw) {
  if (typeof raw !== 'string') return null;
  const trimmed = raw.trim().replace(/^```(?:json)?/i, '').replace(/```$/, '').trim();
  const direct = tryParse(trimmed);
  if (direct) return direct;
  const start = trimmed.indexOf('{');
  const end = trimmed.lastIndexOf('}');
  if (start === -1 || end <= start) return null;
  return tryParse(trimmed.slice(start, end + 1));
}

function tryParse(value) {
  try {
    const parsed = JSON.parse(value);
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : null;
  } catch {
    return null;
  }
}

/**
 * Validates and normalizes a model-produced command object. Anything invalid is
 * dropped rather than applied, so a bad model reply can never corrupt the task list.
 */
export function validateCommand(input) {
  if (!input || typeof input !== 'object') return null;
  const reply = typeof input.reply === 'string' ? input.reply.trim().slice(0, 1200) : '';

  const tasks = Array.isArray(input.tasks)
    ? input.tasks
        .filter((task) => task && typeof task.title === 'string' && task.title.trim())
        .slice(0, 12)
        .map((task) => ({
          title: task.title.trim().slice(0, 300),
          notes: typeof task.notes === 'string' ? task.notes.trim().slice(0, 1000) : '',
          priority: TASK_PRIORITIES.includes(task.priority) ? task.priority : 'medium',
          dueAt: toEpoch(task.dueAtISO),
          reminderAt: toEpoch(task.reminderAtISO),
        }))
    : [];

  const taskRefs = Array.isArray(input.taskRefs)
    ? input.taskRefs
        .filter((ref) => ref && typeof ref.match === 'string' && ref.match.trim())
        .slice(0, 12)
        .map((ref) => ({
          match: ref.match.trim().slice(0, 300),
          status: TASK_STATUSES.includes(ref.status) ? ref.status : 'completed',
        }))
    : [];

  const memories = Array.isArray(input.memories)
    ? input.memories
        .filter((memory) => memory && typeof memory.content === 'string' && memory.content.trim())
        .slice(0, 8)
        .map((memory) => ({
          kind: ['preference', 'routine', 'fact', 'goal'].includes(memory.kind) ? memory.kind : 'fact',
          content: memory.content.trim().slice(0, 500),
        }))
    : [];

  let deviceAction = null;
  if (input.deviceAction && typeof input.deviceAction === 'object') {
    const type = input.deviceAction.type;
    if (['open_app', 'open_settings', 'calendar_event', 'dial', 'media'].includes(type)) {
      deviceAction = {
        type,
        target: String(input.deviceAction.target || '').slice(0, 200),
        title: input.deviceAction.title ? String(input.deviceAction.title).slice(0, 200) : null,
        startAt: toEpoch(input.deviceAction.startAtISO),
      };
    }
  }

  const intents = [
    'create_tasks',
    'complete_task',
    'delete_task',
    'reschedule_task',
    'list_tasks',
    'create_memory',
    'device_action',
    'chat',
  ];
  const intent = intents.includes(input.intent) ? input.intent : 'chat';

  if (!reply && tasks.length === 0 && taskRefs.length === 0 && memories.length === 0 && !deviceAction) {
    return null;
  }

  return {
    reply,
    language: ['hi', 'en', 'hinglish'].includes(input.language) ? input.language : 'auto',
    intent,
    tasks,
    taskRefs,
    memories,
    deviceAction,
    needsConfirmation: Boolean(input.needsConfirmation),
  };
}

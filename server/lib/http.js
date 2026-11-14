// Small HTTP helpers shared by every Vercel function.
// Handlers use the Web-standard Request/Response signature supported by
// Vercel's Node.js runtime, so no framework or runtime dependency is needed.

export class HttpError extends Error {
  constructor(status, code, message, details) {
    super(message);
    this.status = status;
    this.code = code;
    this.details = details;
  }
}

export function json(body, status = 200, headers = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      'content-type': 'application/json; charset=utf-8',
      'cache-control': 'no-store, max-age=0',
      'x-content-type-options': 'nosniff',
      ...headers,
    },
  });
}

export function sse(stream, headers = {}) {
  return new Response(stream, {
    status: 200,
    headers: {
      'content-type': 'text/event-stream; charset=utf-8',
      'cache-control': 'no-store, no-transform',
      connection: 'keep-alive',
      ...headers,
    },
  });
}

export async function readJson(req, { maxBytes = 64 * 1024 } = {}) {
  const raw = await req.text();
  if (raw.length > maxBytes) {
    throw new HttpError(413, 'payload_too_large', 'Request body is too large.');
  }
  if (!raw.trim()) return {};
  try {
    const parsed = JSON.parse(raw);
    if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
      throw new Error('not an object');
    }
    return parsed;
  } catch {
    throw new HttpError(400, 'invalid_json', 'Request body must be a JSON object.');
  }
}

export function str(value, { field, max = 4000, required = false, pattern } = {}) {
  if (value === undefined || value === null) {
    if (required) throw new HttpError(400, 'missing_field', `"${field}" is required.`);
    return undefined;
  }
  if (typeof value !== 'string') {
    throw new HttpError(400, 'invalid_field', `"${field}" must be a string.`);
  }
  const trimmed = value.trim();
  if (required && !trimmed) {
    throw new HttpError(400, 'missing_field', `"${field}" must not be empty.`);
  }
  if (trimmed.length > max) {
    throw new HttpError(400, 'invalid_field', `"${field}" exceeds ${max} characters.`);
  }
  if (pattern && trimmed && !pattern.test(trimmed)) {
    throw new HttpError(400, 'invalid_field', `"${field}" has an unsupported format.`);
  }
  return trimmed;
}

export function oneOf(value, allowed, { field, fallback } = {}) {
  if (value === undefined || value === null || value === '') {
    if (fallback !== undefined) return fallback;
    return undefined;
  }
  if (typeof value !== 'string' || !allowed.includes(value)) {
    throw new HttpError(400, 'invalid_field', `"${field}" must be one of: ${allowed.join(', ')}.`);
  }
  return value;
}

// Structured logging with no prompt text, token or personal content in it.
export function logEvent(event, fields = {}) {
  const safe = {};
  for (const [key, value] of Object.entries(fields)) {
    if (value === undefined) continue;
    if (/token|key|secret|authorization|prompt|message|content/i.test(key)) continue;
    safe[key] = typeof value === 'string' ? value.slice(0, 200) : value;
  }
  console.log(JSON.stringify({ ts: new Date().toISOString(), event, ...safe }));
}

export function handleError(error) {
  if (error instanceof HttpError) {
    return json({ error: { code: error.code, message: error.message, details: error.details } }, error.status);
  }
  logEvent('unhandled_error', { error: error?.name, message: error?.message });
  return json({ error: { code: 'internal_error', message: 'Unexpected server error.' } }, 500);
}

export function methodNotAllowed(allowed) {
  return json(
    { error: { code: 'method_not_allowed', message: `Use ${allowed.join(', ')}.` } },
    405,
    { allow: allowed.join(', ') },
  );
}

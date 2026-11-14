// Device-token authentication.
//
// Ordinary end users never type an API key: the Android app is provisioned with
// a single deployment token (JARVIS_API_TOKEN on Vercel, pasted once into the
// app's settings) and derives a stable device id. Provider keys stay server side.
import { timingSafeEqual } from 'node:crypto';
import { HttpError } from './http.js';

const DEVICE_ID = /^[A-Za-z0-9_-]{8,64}$/;

export function apiTokens(env = process.env) {
  return String(env.JARVIS_API_TOKEN || '')
    .split(',')
    .map((value) => value.trim())
    .filter(Boolean);
}

function safeEqual(a, b) {
  const left = Buffer.from(a);
  const right = Buffer.from(b);
  if (left.length !== right.length) return false;
  return timingSafeEqual(left, right);
}

export function authorize(req, env = process.env) {
  const tokens = apiTokens(env);
  if (tokens.length === 0) {
    throw new HttpError(
      503,
      'server_not_configured',
      'JARVIS_API_TOKEN is not configured on the server.',
    );
  }

  const header = req.headers.get('authorization') || '';
  const match = /^Bearer\s+(.+)$/i.exec(header.trim());
  if (!match) {
    throw new HttpError(401, 'unauthorized', 'Missing bearer token.');
  }

  const presented = match[1].trim();
  // Compare against every configured token without early exit on content.
  let ok = false;
  for (const token of tokens) {
    if (safeEqual(presented, token)) ok = true;
  }
  if (!ok) throw new HttpError(401, 'unauthorized', 'Invalid bearer token.');

  const deviceId = (req.headers.get('x-device-id') || '').trim();
  if (!DEVICE_ID.test(deviceId)) {
    throw new HttpError(400, 'invalid_device_id', 'X-Device-Id must be 8-64 characters of [A-Za-z0-9_-].');
  }

  return { deviceId, userId: deviceId };
}

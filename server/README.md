# Jarvis backend (Vercel)

Zero-dependency Node.js functions using the Web-standard `Request`/`Response`
handler signature supported by Vercel's Node runtime. No framework, no build
step: the files in this directory deploy as-is.

## Endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/health` | Public liveness + configuration summary |
| GET | `/api/assistant/status` | Provider registry, readiness, circuit breakers, storage backend (`?probe=1` also probes `/models` upstream) |
| POST | `/api/assistant/chat` | Conversation. `{messages:[{role,content}], language?, stream?}`. `stream:true` returns SSE chunks |
| POST | `/api/assistant/command` | Structured command `{text}` → validated JSON contract (intent, tasks, taskRefs, memories, deviceAction) |
| GET/POST/PATCH/DELETE | `/api/assistant/tasks` | Cloud-synced task records |
| GET/POST/DELETE | `/api/assistant/memory` | Cloud-synced user-approved memories (`?all=1` clears) |

Every endpoint except `/api/health` requires
`Authorization: Bearer $JARVIS_API_TOKEN` and an `X-Device-Id` header
(8–64 chars of `[A-Za-z0-9_-]`). Rows are scoped to that device id.

## Provider routing

`lib/providers.js` is the registry: provider, models, endpoint, key env var,
capabilities, documented free-tier terms and known limitations, sourced from
[awesome-free-llm-apis](https://github.com/mnfst/awesome-free-llm-apis).

`lib/router.js` picks a capability-aware chain (native JSON mode first for
structured commands), then walks provider → model fallbacks. `429`, `5xx`,
timeouts and empty completions fail over to the next candidate; `401/403` skips
the rest of that provider; two failures open a 30-second circuit breaker for it.
If nothing is configured or everything fails, handlers return HTTP 200 with
`degraded: true` and the phone falls back to its on-device engine — an honest
degradation instead of a fabricated reply.

## Environment

Copy `.env.example` and fill in what you have. Minimum: `JARVIS_API_TOKEN` plus
at least one provider key (or none — the OVHcloud anonymous free tier needs no
key, only 2 requests/minute).

Durable cloud sync needs `SUPABASE_URL` + `SUPABASE_SERVICE_ROLE_KEY` and the
schema in `db/schema.sql`. Without them the API still works and reports
`storage: "memory"`; the app keeps every task locally regardless.

## Local development and tests

```bash
npm test                # 36 tests: HTTP auth/validation, routing/fallback, prompts
npm run dev             # http://127.0.0.1:8787 with the same handlers
curl localhost:8787/api/health
```

Tests run a real HTTP server and a fake OpenAI-compatible provider, so auth,
rate limiting, validation, streaming, device isolation and fallback are
exercised without spending any provider quota.

## Deploy

```bash
npx vercel deploy --prod
# or upload this folder as a project in the Vercel dashboard
```

Set the environment variables in Project → Settings → Environment Variables,
then point the app's **Settings → Jarvis backend** at the deployment URL and
paste the same `JARVIS_API_TOKEN` value the server expects.

-- Jarvis cloud-sync schema (Supabase / Postgres).
-- Apply in the Supabase SQL editor, or: psql "$DATABASE_URL" -f db/schema.sql
--
-- The Android app is local-first. These tables only back optional multi-device
-- sync through /api/assistant/tasks and /api/assistant/memory. Every row is
-- scoped by device_id, which the backend derives from the authenticated
-- X-Device-Id header. The service-role key is used server-side only.

create table if not exists public.jarvis_tasks (
  id           text primary key,
  device_id    text not null,
  title        text not null,
  notes        text not null default '',
  status       text not null default 'pending'
                check (status in ('pending','in_progress','scheduled','blocked','completed')),
  priority     text not null default 'medium'
                check (priority in ('low','medium','high','critical')),
  due_at       bigint,
  reminder_at  bigint,
  completed_at bigint,
  evidence     text not null default '',
  created_at   bigint not null,
  updated_at   bigint not null
);

create index if not exists jarvis_tasks_device_status_idx
  on public.jarvis_tasks (device_id, status, updated_at desc);

create table if not exists public.jarvis_memories (
  id         text primary key,
  device_id  text not null,
  kind       text not null default 'fact'
               check (kind in ('preference','routine','fact','goal')),
  content    text not null,
  created_at bigint not null,
  updated_at bigint not null
);

create index if not exists jarvis_memories_device_idx
  on public.jarvis_memories (device_id, updated_at desc);

-- Only the server (service role) talks to these tables.
alter table public.jarvis_tasks enable row level security;
alter table public.jarvis_memories enable row level security;

comment on table public.jarvis_tasks is 'Jarvis task records synced from the Android app';
comment on table public.jarvis_memories is 'Jarvis user-approved memories synced from the Android app';

-- User accounts for History in a Glimpse (Member 2).
-- Run once in the Supabase SQL editor. Only the backend reads this table,
-- using the secret key, so row level security stays on with no policies.

create table if not exists public.app_users (
    id            bigint generated always as identity primary key,
    username      text not null unique check (username ~ '^[a-z0-9_]{3,30}$'),
    display_name  text not null,
    password_hash text not null,
    role          text not null default 'USER' check (role in ('USER', 'ADMIN')),
    created_at    timestamptz not null default now()
);

alter table public.app_users enable row level security;

-- To make someone an admin after they register:
-- update public.app_users set role = 'ADMIN' where username = 'their_name';

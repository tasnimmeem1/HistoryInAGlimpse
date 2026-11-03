-- User accounts for History in a Glimpse.
-- Run once in the Supabase SQL editor. Only the backend reads this table,
-- using the secret key, so row level security stays on with no policies.

create table if not exists public.app_users (
    id            bigint generated always as identity primary key,
    username      text not null unique check (username ~ '^[a-z0-9_]{3,30}$'),
    display_name  text not null,
    password_hash text,                 -- null for accounts that only use Google sign-in
    google_sub    text unique,          -- Google's id for the user, set by Google sign-in
    role          text not null default 'USER' check (role in ('USER', 'ADMIN')),
    created_at    timestamptz not null default now(),
    check (password_hash is not null or google_sub is not null)
);

alter table public.app_users enable row level security;

-- To make someone an admin after they register:
-- update public.app_users set role = 'ADMIN' where username = 'their_name';

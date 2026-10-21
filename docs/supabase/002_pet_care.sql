-- Twilight Cycle: the shared pet in partner mode (stage 10b).
-- Run once after 001_partner.sql: SQL Editor -> New query -> paste this file -> Run.
-- Safe to run again.
--
-- The partner's phone feeds the pet or gives it a gift; each action is encrypted on that phone
-- with the key from the invitation and waits here until the owner's phone picks it up.

create table if not exists public.partner_pet_care (
    id uuid primary key default gen_random_uuid(),
    link_id uuid not null references public.partner_links (id) on delete cascade,
    sender uuid not null default auth.uid() references auth.users (id) on delete cascade,
    payload text not null
);
create index if not exists partner_pet_care_link on public.partner_pet_care (link_id);

alter table public.partner_pet_care enable row level security;

-- Only the partner of a live link sends care to it.
drop policy if exists "partner sends care" on public.partner_pet_care;
create policy "partner sends care" on public.partner_pet_care
    for insert to authenticated
    with check (
        sender = (select auth.uid())
        and exists (select 1 from public.partner_links l where l.id = link_id and l.partner = (select auth.uid()))
    );

-- Only the owner of that link reads and removes it.
drop policy if exists "owner reads care" on public.partner_pet_care;
create policy "owner reads care" on public.partner_pet_care
    for select to authenticated
    using (exists (select 1 from public.partner_links l where l.id = link_id and l.owner = (select auth.uid())));

drop policy if exists "owner removes care" on public.partner_pet_care;
create policy "owner removes care" on public.partner_pet_care
    for delete to authenticated
    using (exists (select 1 from public.partner_links l where l.id = link_id and l.owner = (select auth.uid())));

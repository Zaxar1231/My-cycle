-- Twilight Cycle: partner mode (stage 9b).
-- Run once in Supabase: SQL Editor -> New query -> paste this file -> Run.
-- Safe to run again: everything is created only if missing.
--
-- The server never sees health data in the clear. The phone that tracks the cycle encrypts
-- what it shares (AES-256-GCM); the key travels to the partner's phone inside the invitation
-- code and is never sent here. Row Level Security additionally limits every row to the two
-- people of one link.

-- An invitation waiting for a partner. Only its owner can see or delete it.
-- Expired rows are removed by accept_partner_invite(), so they do not pile up.
create table if not exists public.partner_invites (
    id uuid primary key,
    owner uuid not null default auth.uid() references auth.users (id) on delete cascade,
    expires_at timestamptz not null default now() + interval '1 day'
);

-- A link between the person who tracks the cycle (owner) and their partner.
-- One partner per owner and one owner per partner. Created only by accept_partner_invite().
create table if not exists public.partner_links (
    id uuid primary key,
    owner uuid not null references auth.users (id) on delete cascade,
    partner uuid not null references auth.users (id) on delete cascade,
    created_at timestamptz not null default now(),
    constraint partner_links_not_self check (owner <> partner)
);
create unique index if not exists partner_links_owner_key on public.partner_links (owner);
create unique index if not exists partner_links_partner_key on public.partner_links (partner);

-- The latest shared snapshot, encrypted on the owner's phone. Removed together with the link.
create table if not exists public.partner_shares (
    link_id uuid primary key references public.partner_links (id) on delete cascade,
    owner uuid not null default auth.uid() references auth.users (id) on delete cascade,
    payload text not null,
    updated_at timestamptz not null default now()
);

alter table public.partner_invites enable row level security;
alter table public.partner_links enable row level security;
alter table public.partner_shares enable row level security;

drop policy if exists "owner manages own invites" on public.partner_invites;
create policy "owner manages own invites" on public.partner_invites
    for all to authenticated
    using (owner = (select auth.uid()))
    with check (owner = (select auth.uid()));

-- Both people see their link and either of them can remove it. There is no insert or update
-- policy: a link can only be made through accept_partner_invite().
drop policy if exists "members read link" on public.partner_links;
create policy "members read link" on public.partner_links
    for select to authenticated
    using ((select auth.uid()) in (owner, partner));

drop policy if exists "members remove link" on public.partner_links;
create policy "members remove link" on public.partner_links
    for delete to authenticated
    using ((select auth.uid()) in (owner, partner));

-- Only the owner of a live link writes its snapshot; both people of that link can read it.
drop policy if exists "owner writes share" on public.partner_shares;
create policy "owner writes share" on public.partner_shares
    for insert to authenticated
    with check (
        owner = (select auth.uid())
        and exists (select 1 from public.partner_links l where l.id = link_id and l.owner = (select auth.uid()))
    );

drop policy if exists "owner updates share" on public.partner_shares;
create policy "owner updates share" on public.partner_shares
    for update to authenticated
    using (owner = (select auth.uid()))
    with check (
        owner = (select auth.uid())
        and exists (select 1 from public.partner_links l where l.id = link_id and l.owner = (select auth.uid()))
    );

drop policy if exists "owner removes share" on public.partner_shares;
create policy "owner removes share" on public.partner_shares
    for delete to authenticated
    using (owner = (select auth.uid()));

drop policy if exists "members read share" on public.partner_shares;
create policy "members read share" on public.partner_shares
    for select to authenticated
    using (exists (
        select 1 from public.partner_links l
        where l.id = link_id and (select auth.uid()) in (l.owner, l.partner)
    ));

-- The partner accepts an invitation: the invitation becomes a link with the same id.
create or replace function public.accept_partner_invite(invite_id uuid)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
    invite_owner uuid;
begin
    if auth.uid() is null then
        raise exception 'not_signed_in';
    end if;
    delete from public.partner_invites where expires_at <= now();
    select owner into invite_owner
    from public.partner_invites
    where id = invite_id and expires_at > now()
    for update;
    if invite_owner is null then
        raise exception 'invite_not_found';
    end if;
    if invite_owner = auth.uid() then
        raise exception 'own_invite';
    end if;
    if exists (
        select 1 from public.partner_links
        where owner in (invite_owner, auth.uid()) or partner in (invite_owner, auth.uid())
    ) then
        raise exception 'already_linked';
    end if;
    insert into public.partner_links (id, owner, partner) values (invite_id, invite_owner, auth.uid());
    delete from public.partner_invites where id = invite_id;
    return invite_id;
end;
$$;

revoke all on function public.accept_partner_invite(uuid) from public, anon;
grant execute on function public.accept_partner_invite(uuid) to authenticated;

-- =====================================================================
-- ЖҰРТ — базовая схема (ТЗ v2.0, разделы 3–9, 12)
-- Принципы:
--   * родство ≠ доступ: доступ даёт только членство в круге/чате;
--   * все проверки — на сервере (RLS + security definer функции);
--   * круги не наследуют доступ друг от друга;
--   * вложение принадлежит одному чату и не переносится (SCC);
--   * новый участник не видит историю до своего вступления.
-- =====================================================================

create extension if not exists pgcrypto;

-- ---------- Типы ----------
create type public.gender as enum ('m', 'f');
create type public.circle_kind as enum ('shanyrak', 'oz', 'nagashy', 'kayin', 'toy');
create type public.circle_role as enum ('creator', 'admin', 'moderator', 'member', 'toy_guest', 'observer');
create type public.conversation_kind as enum ('group', 'pair', 'broadcast');
create type public.message_kind as enum ('text', 'photo', 'video', 'file', 'voice', 'video_note');
create type public.relation_kind as enum ('parent', 'spouse', 'sibling');
create type public.relation_lineage as enum ('biological', 'adoptive', 'step', 'marriage');
create type public.relation_status as enum ('proposed', 'pending', 'confirmed', 'rejected', 'disputed', 'needs_clarification');

-- ---------- Профиль (телефон остаётся в auth.users и наружу не отдаётся) ----------
create table public.profiles (
  id           uuid primary key references auth.users (id) on delete cascade,
  display_name text not null check (char_length(display_name) between 1 and 64),
  avatar_url   text,
  gender       public.gender,
  birth_year   int check (birth_year between 1900 and 2100),
  locale       text not null default 'kk' check (locale in ('kk', 'ru', 'tr', 'uz', 'ky', 'az', 'tk', 'en')),
  country      text not null default 'KZ' check (country ~ '^[A-Z]{2}$'),
  created_at   timestamptz not null default now()
);

-- ---------- Семейное пространство ----------
create table public.family_spaces (
  id         uuid primary key default gen_random_uuid(),
  name       text not null check (char_length(name) between 1 and 80),
  created_by uuid not null references auth.users (id),
  created_at timestamptz not null default now()
);

-- ---------- Граф родства (Kinship Graph) ----------
-- Person: человек в графе; может быть без аккаунта (умерший предок, ребёнок и т.п.)
create table public.persons (
  id              uuid primary key default gen_random_uuid(),
  family_space_id uuid not null references public.family_spaces (id) on delete cascade,
  user_id         uuid references auth.users (id) on delete set null,
  display_name    text not null check (char_length(display_name) between 1 and 64),
  gender          public.gender,
  birth_year      int check (birth_year between 1800 and 2100),
  birth_order     int,                    -- старшинство среди братьев/сестёр, если год неизвестен
  created_by      uuid not null references auth.users (id),
  created_at      timestamptz not null default now(),
  unique (family_space_id, user_id)
);

-- RelationshipAssertion: утверждение о связи. parent: from = родитель, to = ребёнок.
create table public.relationship_assertions (
  id              uuid primary key default gen_random_uuid(),
  family_space_id uuid not null references public.family_spaces (id) on delete cascade,
  from_person     uuid not null references public.persons (id) on delete cascade,
  to_person       uuid not null references public.persons (id) on delete cascade,
  kind            public.relation_kind not null,
  lineage         public.relation_lineage not null default 'biological',
  status          public.relation_status not null default 'pending',
  explanation     text,                   -- «путь родства», показывается пользователю
  created_by      uuid not null references auth.users (id),
  confirmed_by    uuid references auth.users (id),
  confirmed_at    timestamptz,
  created_at      timestamptz not null default now(),
  check (from_person <> to_person),
  unique (from_person, to_person, kind)
);

-- Справочник терминов (версионируется, правится через админку)
create table public.kinship_terms (
  id           bigint generated always as identity primary key,
  locale       text not null,
  code         text not null,             -- машинный код: ake, ana, aga, ini, apke, sinli, ...
  term         text not null,             -- Әке, Ана, ...
  side         text check (side in ('oz', 'nagashy', 'kayin', 'shanyrak', 'other')),
  description  text not null,
  rule_version int not null default 1,
  needs_expert_review boolean not null default true,
  unique (locale, code, rule_version)
);

-- ---------- Круги ----------
create table public.circles (
  id              uuid primary key default gen_random_uuid(),
  family_space_id uuid not null references public.family_spaces (id) on delete cascade,
  kind            public.circle_kind not null,
  title           text not null check (char_length(title) between 1 and 80),
  retention_days  int check (retention_days between 1 and 3650),   -- Қайын: 30 по умолчанию
  event_at        timestamptz,                                      -- Той: дата события
  closes_at       timestamptz,                                      -- Той: когда закрыть
  closed_at       timestamptz,
  created_by      uuid not null references auth.users (id),
  created_at      timestamptz not null default now(),
  check (kind <> 'toy' or closes_at is not null)
);

create table public.circle_members (
  circle_id    uuid not null references public.circles (id) on delete cascade,
  user_id      uuid not null references auth.users (id) on delete cascade,
  role         public.circle_role not null default 'member',
  can_write    boolean not null default true,
  can_attach   boolean not null default true,
  can_invite   boolean not null default false,
  can_pin      boolean not null default false,
  can_moderate boolean not null default false,
  title_here   text,     -- культурная подпись в этом круге: «ана», «жеңге», «құда»
  joined_at    timestamptz not null default now(),
  primary key (circle_id, user_id)
);

-- ---------- Чаты ----------
create table public.conversations (
  id                  uuid primary key default gen_random_uuid(),
  circle_id           uuid not null references public.circles (id) on delete cascade,
  kind                public.conversation_kind not null default 'group',
  title               text not null check (char_length(title) between 1 and 80),
  broadcaster_id      uuid references auth.users (id),   -- рассылка ата
  broadcast_attachments boolean not null default true,
  created_by          uuid not null references auth.users (id),
  created_at          timestamptz not null default now(),
  check (kind <> 'broadcast' or broadcaster_id is not null)
);

create table public.conversation_members (
  conversation_id uuid not null references public.conversations (id) on delete cascade,
  user_id         uuid not null references auth.users (id) on delete cascade,
  joined_at       timestamptz not null default now(),
  last_read_at    timestamptz,
  muted           boolean not null default false,
  primary key (conversation_id, user_id)
);

-- ---------- Вложения (Secure Content Control) ----------
create table public.attachments (
  id              uuid primary key default gen_random_uuid(),
  conversation_id uuid not null references public.conversations (id) on delete cascade,
  uploader_id     uuid not null references auth.users (id),
  storage_path    text not null unique,   -- "<conversation_id>/<uuid>.<ext>" в приватном bucket
  mime_type       text not null,
  size_bytes      bigint not null check (size_bytes > 0 and size_bytes <= 104857600), -- 100 МБ
  duration_ms     int,
  protected       boolean not null default true,
  created_at      timestamptz not null default now(),
  check (split_part(storage_path, '/', 1) = conversation_id::text)
);

-- ---------- Сообщения ----------
-- id генерирует клиент (idempotency key): повторная отправка из офлайн-очереди не создаёт дубликатов.
create table public.messages (
  id              uuid primary key,
  conversation_id uuid not null references public.conversations (id) on delete cascade,
  sender_id       uuid not null references auth.users (id),
  kind            public.message_kind not null default 'text',
  body            text check (char_length(body) <= 8000),
  attachment_id   uuid references public.attachments (id) on delete set null,
  reply_to        uuid references public.messages (id) on delete set null,
  created_at      timestamptz not null default now(),
  edited_at       timestamptz,
  deleted_at      timestamptz,
  check (kind = 'text' or attachment_id is not null or deleted_at is not null),
  check (kind <> 'text' or body is not null or deleted_at is not null)
);
create index messages_conv_time on public.messages (conversation_id, created_at desc);

create table public.message_receipts (
  message_id   uuid not null references public.messages (id) on delete cascade,
  user_id      uuid not null references auth.users (id) on delete cascade,
  delivered_at timestamptz not null default now(),
  read_at      timestamptz,
  primary key (message_id, user_id)
);

-- одна компактная реакция на пользователя
create table public.reactions (
  message_id uuid not null references public.messages (id) on delete cascade,
  user_id    uuid not null references auth.users (id) on delete cascade,
  emoji      text not null check (char_length(emoji) <= 8),
  created_at timestamptz not null default now(),
  primary key (message_id, user_id)
);

-- ---------- Приглашения: персональные, с лимитом срока и использований ----------
create table public.invitations (
  id          uuid primary key default gen_random_uuid(),
  circle_id   uuid not null references public.circles (id) on delete cascade,
  code        text not null unique default upper(encode(gen_random_bytes(5), 'hex')),
  invited_by  uuid not null references auth.users (id),
  invitee_name text,
  role        public.circle_role not null default 'member',
  title_here  text,
  expires_at  timestamptz not null default now() + interval '72 hours',
  max_uses    int not null default 1 check (max_uses between 1 and 50),
  uses        int not null default 0,
  revoked_at  timestamptz,
  created_at  timestamptz not null default now(),
  check (expires_at <= created_at + interval '30 days')
);

create table public.consents (
  user_id    uuid not null references auth.users (id) on delete cascade,
  kind       text not null,          -- privacy_policy, kinship_visibility, ...
  version    text not null,
  granted_at timestamptz not null default now(),
  revoked_at timestamptz,
  primary key (user_id, kind, version)
);

create table public.audit_events (
  id         bigint generated always as identity primary key,
  actor_id   uuid,
  action     text not null,
  object_type text not null,
  object_id  text,
  meta       jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

-- Лист ожидания для сайта
create table public.waitlist (
  id         bigint generated always as identity primary key,
  contact    text not null check (char_length(contact) between 5 and 120),
  country    text,
  locale     text,
  created_at timestamptz not null default now()
);

-- =====================================================================
-- Функции доступа (security definer, чтобы RLS не рекурсировал)
-- =====================================================================
create or replace function public.is_circle_member(c uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from circle_members m where m.circle_id = c and m.user_id = auth.uid());
$$;

create or replace function public.circle_role_of(c uuid)
returns public.circle_role language sql stable security definer set search_path = public as $$
  select m.role from circle_members m where m.circle_id = c and m.user_id = auth.uid();
$$;

create or replace function public.is_circle_admin(c uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select coalesce(public.circle_role_of(c) in ('creator', 'admin'), false);
$$;

create or replace function public.is_space_member(s uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from circle_members m join circles c on c.id = m.circle_id
    where c.family_space_id = s and m.user_id = auth.uid() and m.role <> 'toy_guest'
  );
$$;

-- участник чата И (всё ещё) участник его круга
create or replace function public.is_conversation_member(conv uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from conversation_members cm
    join conversations cv on cv.id = cm.conversation_id
    join circle_members m on m.circle_id = cv.circle_id and m.user_id = cm.user_id
    where cm.conversation_id = conv and cm.user_id = auth.uid()
  );
$$;

create or replace function public.conversation_joined_at(conv uuid)
returns timestamptz language sql stable security definer set search_path = public as $$
  select cm.joined_at from conversation_members cm where cm.conversation_id = conv and cm.user_id = auth.uid();
$$;

-- можно ли писать/прикладывать в чат (рассылка ата, закрытый Той, права участника)
create or replace function public.can_post(conv uuid, with_attachment boolean)
returns boolean language sql stable security definer set search_path = public as $$
  select coalesce((
    select
      c.closed_at is null
      and (c.kind <> 'toy' or c.closes_at > now())
      and m.role <> 'observer'
      and m.can_write
      and (not with_attachment or m.can_attach)
      and (cv.kind <> 'broadcast' or (cv.broadcaster_id = auth.uid() and (not with_attachment or cv.broadcast_attachments)))
    from conversations cv
    join circles c on c.id = cv.circle_id
    join circle_members m on m.circle_id = c.id and m.user_id = auth.uid()
    join conversation_members cm on cm.conversation_id = cv.id and cm.user_id = auth.uid()
    where cv.id = conv
  ), false);
$$;

-- =====================================================================
-- Триггеры целостности
-- =====================================================================

-- Вложение можно использовать только в своём чате (запрет пересылки / выноса в Избранное)
create or replace function public.tg_messages_guard()
returns trigger language plpgsql security definer set search_path = public as $$
declare a_conv uuid;
begin
  if tg_op = 'INSERT' then
    if new.sender_id is distinct from auth.uid() then
      raise exception 'sender must be the current user' using errcode = '42501';
    end if;
    if not public.can_post(new.conversation_id, new.attachment_id is not null) then
      raise exception 'posting is not allowed in this conversation' using errcode = '42501';
    end if;
    new.created_at := now();
    new.edited_at := null;
    new.deleted_at := null;
  end if;

  if new.attachment_id is not null then
    select conversation_id into a_conv from attachments where id = new.attachment_id;
    if a_conv is distinct from new.conversation_id then
      raise exception 'Пересылка запрещена правилами этого чата' using errcode = '42501';
    end if;
  end if;

  if new.reply_to is not null and not exists (
    select 1 from messages r where r.id = new.reply_to and r.conversation_id = new.conversation_id
  ) then
    raise exception 'reply must reference a message of the same conversation';
  end if;

  if tg_op = 'UPDATE' then
    if new.conversation_id <> old.conversation_id or new.sender_id <> old.sender_id
       or new.created_at <> old.created_at or new.kind <> old.kind
       or new.attachment_id is distinct from old.attachment_id and new.deleted_at is null then
      raise exception 'only body can be edited';
    end if;
    if new.body is distinct from old.body and new.deleted_at is null then
      new.edited_at := now();
    end if;
    if new.deleted_at is not null and old.deleted_at is null then
      new.body := null;
      new.attachment_id := null;
      new.deleted_at := now();
    end if;
  end if;
  return new;
end $$;

create trigger messages_guard before insert or update on public.messages
for each row execute function public.tg_messages_guard();

-- Создатель круга автоматически становится его участником
create or replace function public.tg_circle_creator()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  insert into circle_members (circle_id, user_id, role, can_invite, can_pin, can_moderate)
  values (new.id, new.created_by, 'creator', true, true, true)
  on conflict do nothing;
  return new;
end $$;
create trigger circles_creator after insert on public.circles
for each row execute function public.tg_circle_creator();

-- Қайын: 30 дней по умолчанию
create or replace function public.tg_circle_defaults()
returns trigger language plpgsql as $$
begin
  if new.kind = 'kayin' and new.retention_days is null then new.retention_days := 30; end if;
  return new;
end $$;
create trigger circles_defaults before insert on public.circles
for each row execute function public.tg_circle_defaults();

-- Участник чата должен состоять в круге этого чата; гость Той — только в кругах Той
create or replace function public.tg_conv_member_guard()
returns trigger language plpgsql security definer set search_path = public as $$
declare r public.circle_role; k public.circle_kind;
begin
  select m.role, c.kind into r, k
  from conversations cv join circles c on c.id = cv.circle_id
  left join circle_members m on m.circle_id = c.id and m.user_id = new.user_id
  where cv.id = new.conversation_id;
  if r is null then
    raise exception 'user is not a member of the circle' using errcode = '42501';
  end if;
  return new;
end $$;
create trigger conv_members_guard before insert on public.conversation_members
for each row execute function public.tg_conv_member_guard();

create or replace function public.tg_toy_guest_guard()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if new.role = 'toy_guest' and (select kind from circles where id = new.circle_id) <> 'toy' then
    raise exception 'toy guests can join only a Той circle' using errcode = '42501';
  end if;
  return new;
end $$;
create trigger circle_members_toy_guard before insert or update on public.circle_members
for each row execute function public.tg_toy_guest_guard();

-- =====================================================================
-- RPC: сценарии, требующие нескольких шагов
-- =====================================================================

-- Создать семейное пространство + Шаңырақ + общий чат «Үй»
create or replace function public.create_family_space(p_name text, p_my_name text default null)
returns uuid language plpgsql security definer set search_path = public as $$
declare s uuid; c uuid; v uuid; uid uuid := auth.uid();
begin
  if uid is null then raise exception 'not authenticated' using errcode = '42501'; end if;
  insert into family_spaces (name, created_by) values (p_name, uid) returning id into s;
  insert into persons (family_space_id, user_id, display_name, gender, birth_year, created_by)
  select s, uid, coalesce(p_my_name, p.display_name, 'Мен'), p.gender, p.birth_year, uid
  from (select 1) x left join profiles p on p.id = uid;
  insert into circles (family_space_id, kind, title, created_by) values (s, 'shanyrak', 'Шаңырақ', uid) returning id into c;
  insert into conversations (circle_id, kind, title, created_by) values (c, 'group', 'Үй', uid) returning id into v;
  insert into conversation_members (conversation_id, user_id) values (v, uid);
  insert into audit_events (actor_id, action, object_type, object_id) values (uid, 'create', 'family_space', s::text);
  return s;
end $$;

-- Создать круг (только участник пространства; Той требует даты закрытия)
create or replace function public.create_circle(
  p_space uuid, p_kind public.circle_kind, p_title text,
  p_event_at timestamptz default null, p_closes_at timestamptz default null)
returns uuid language plpgsql security definer set search_path = public as $$
declare c uuid; v uuid; uid uuid := auth.uid();
begin
  if not public.is_space_member(p_space) then raise exception 'not a member of the family space' using errcode = '42501'; end if;
  insert into circles (family_space_id, kind, title, event_at, closes_at, created_by)
  values (p_space, p_kind, p_title, p_event_at, coalesce(p_closes_at, case when p_kind = 'toy' then coalesce(p_event_at, now()) + interval '3 days' end), uid)
  returning id into c;
  insert into conversations (circle_id, kind, title, created_by) values (c, 'group', p_title, uid) returning id into v;
  insert into conversation_members (conversation_id, user_id) values (v, uid);
  insert into audit_events (actor_id, action, object_type, object_id, meta)
  values (uid, 'create', 'circle', c::text, jsonb_build_object('kind', p_kind));
  return c;
end $$;

-- Пригласить в круг
create or replace function public.create_invitation(
  p_circle uuid, p_role public.circle_role default 'member', p_title_here text default null,
  p_invitee_name text default null, p_hours int default 72, p_max_uses int default 1)
returns text language plpgsql security definer set search_path = public as $$
declare code text; uid uuid := auth.uid();
begin
  if not exists (select 1 from circle_members where circle_id = p_circle and user_id = uid and (can_invite or role in ('creator','admin'))) then
    raise exception 'no right to invite' using errcode = '42501';
  end if;
  if p_role in ('creator') then raise exception 'cannot invite as creator'; end if;
  insert into invitations (circle_id, invited_by, role, title_here, invitee_name, expires_at, max_uses)
  values (p_circle, uid, p_role, p_title_here, p_invitee_name, now() + make_interval(hours => least(greatest(p_hours, 1), 720)), p_max_uses)
  returning invitations.code into code;
  insert into audit_events (actor_id, action, object_type, object_id) values (uid, 'invite', 'circle', p_circle::text);
  return code;
end $$;

-- Что увидит приглашённый до принятия: кто зовёт, в какой круг
create or replace function public.preview_invitation(p_code text)
returns table (circle_title text, circle_kind public.circle_kind, inviter_name text, role public.circle_role, title_here text, expires_at timestamptz)
language sql stable security definer set search_path = public as $$
  select c.title, c.kind, p.display_name, i.role, i.title_here, i.expires_at
  from invitations i join circles c on c.id = i.circle_id
  left join profiles p on p.id = i.invited_by
  where i.code = upper(p_code) and i.revoked_at is null and i.expires_at > now() and i.uses < i.max_uses and c.closed_at is null;
$$;

-- Принять приглашение: членство в круге + во всех групповых чатах круга (история — с момента вступления)
create or replace function public.accept_invitation(p_code text)
returns uuid language plpgsql security definer set search_path = public as $$
declare i invitations%rowtype; uid uuid := auth.uid();
begin
  if uid is null then raise exception 'not authenticated' using errcode = '42501'; end if;
  select * into i from invitations where code = upper(p_code) for update;
  if not found or i.revoked_at is not null or i.expires_at <= now() or i.uses >= i.max_uses then
    raise exception 'invitation is invalid or expired' using errcode = '42501';
  end if;
  if exists (select 1 from circles where id = i.circle_id and closed_at is not null) then
    raise exception 'circle is closed';
  end if;
  insert into circle_members (circle_id, user_id, role, title_here, can_attach)
  values (i.circle_id, uid, i.role, i.title_here, i.role <> 'observer')
  on conflict (circle_id, user_id) do nothing;
  insert into conversation_members (conversation_id, user_id)
  select cv.id, uid from conversations cv where cv.circle_id = i.circle_id and cv.kind in ('group', 'broadcast')
  on conflict do nothing;
  update invitations set uses = uses + 1 where id = i.id;
  insert into audit_events (actor_id, action, object_type, object_id) values (uid, 'join', 'circle', i.circle_id::text);
  return i.circle_id;
end $$;

-- Приватный чат пары внутри Шаңырақ
create or replace function public.create_pair_conversation(p_circle uuid, p_other uuid, p_title text)
returns uuid language plpgsql security definer set search_path = public as $$
declare v uuid; uid uuid := auth.uid();
begin
  if not public.is_circle_member(p_circle)
     or not exists (select 1 from circle_members where circle_id = p_circle and user_id = p_other) then
    raise exception 'both users must be members of the circle' using errcode = '42501';
  end if;
  insert into conversations (circle_id, kind, title, created_by) values (p_circle, 'pair', p_title, uid) returning id into v;
  insert into conversation_members (conversation_id, user_id) values (v, uid), (v, p_other);
  return v;
end $$;

-- Рассылка ата (пишет только назначенный отправитель)
create or replace function public.create_broadcast(p_circle uuid, p_title text, p_broadcaster uuid, p_attachments boolean default true)
returns uuid language plpgsql security definer set search_path = public as $$
declare v uuid; uid uuid := auth.uid();
begin
  if not public.is_circle_admin(p_circle) then raise exception 'only circle admins can create a broadcast' using errcode = '42501'; end if;
  if not exists (select 1 from circle_members where circle_id = p_circle and user_id = p_broadcaster) then
    raise exception 'broadcaster must be a circle member';
  end if;
  insert into conversations (circle_id, kind, title, broadcaster_id, broadcast_attachments, created_by)
  values (p_circle, 'broadcast', p_title, p_broadcaster, p_attachments, uid) returning id into v;
  insert into conversation_members (conversation_id, user_id)
  select v, m.user_id from circle_members m where m.circle_id = p_circle;
  return v;
end $$;

-- Подтвердить/отклонить/оспорить связь (подтверждает не автор, а вторая сторона или админ)
create or replace function public.set_relationship_status(p_id uuid, p_status public.relation_status)
returns void language plpgsql security definer set search_path = public as $$
declare r relationship_assertions%rowtype; uid uuid := auth.uid();
begin
  select * into r from relationship_assertions where id = p_id for update;
  if not found or not public.is_space_member(r.family_space_id) then raise exception 'not allowed' using errcode = '42501'; end if;
  if p_status = 'confirmed' and r.created_by = uid
     and not exists (select 1 from persons p where p.id in (r.from_person, r.to_person) and p.user_id is null) then
    raise exception 'the other side must confirm' using errcode = '42501';
  end if;
  update relationship_assertions
     set status = p_status,
         confirmed_by = case when p_status = 'confirmed' then uid end,
         confirmed_at = case when p_status = 'confirmed' then now() end
   where id = p_id;
  insert into audit_events (actor_id, action, object_type, object_id, meta)
  values (uid, 'relationship_status', 'relationship', p_id::text, jsonb_build_object('status', p_status));
end $$;

-- Обслуживание: закрыть прошедшие Той, удалить историю Қайын старше срока.
-- Вызывать по расписанию (pg_cron: select cron.schedule('zhurt-retention', '*/30 * * * *', 'select public.run_retention()');)
create or replace function public.run_retention()
returns jsonb language plpgsql security definer set search_path = public as $$
declare closed int; purged int;
begin
  update circles set closed_at = now() where kind = 'toy' and closed_at is null and closes_at <= now();
  get diagnostics closed = row_count;
  delete from messages m using conversations cv, circles c
   where m.conversation_id = cv.id and cv.circle_id = c.id
     and c.retention_days is not null and m.created_at < now() - make_interval(days => c.retention_days);
  get diagnostics purged = row_count;
  delete from attachments a using conversations cv, circles c
   where a.conversation_id = cv.id and cv.circle_id = c.id
     and c.retention_days is not null and a.created_at < now() - make_interval(days => c.retention_days);
  insert into audit_events (action, object_type, meta)
  values ('retention', 'system', jsonb_build_object('toy_closed', closed, 'messages_purged', purged));
  return jsonb_build_object('toy_closed', closed, 'messages_purged', purged);
end $$;
revoke execute on function public.run_retention() from public, anon, authenticated;

-- =====================================================================
-- RLS
-- =====================================================================
alter table public.profiles enable row level security;
alter table public.family_spaces enable row level security;
alter table public.persons enable row level security;
alter table public.relationship_assertions enable row level security;
alter table public.kinship_terms enable row level security;
alter table public.circles enable row level security;
alter table public.circle_members enable row level security;
alter table public.conversations enable row level security;
alter table public.conversation_members enable row level security;
alter table public.attachments enable row level security;
alter table public.messages enable row level security;
alter table public.message_receipts enable row level security;
alter table public.reactions enable row level security;
alter table public.invitations enable row level security;
alter table public.consents enable row level security;
alter table public.audit_events enable row level security;
alter table public.waitlist enable row level security;

-- profiles: свой профиль; профили тех, с кем есть общий круг
create policy profiles_self on public.profiles for all
  using (id = auth.uid()) with check (id = auth.uid());
create policy profiles_shared_circle on public.profiles for select using (
  exists (select 1 from circle_members a join circle_members b on a.circle_id = b.circle_id
          where a.user_id = auth.uid() and b.user_id = profiles.id));

-- family_spaces / persons / relationships: участники пространства (кроме гостей Той)
create policy spaces_read on public.family_spaces for select using (public.is_space_member(id));
create policy spaces_update on public.family_spaces for update using (created_by = auth.uid());

create policy persons_read on public.persons for select using (public.is_space_member(family_space_id));
create policy persons_insert on public.persons for insert
  with check (public.is_space_member(family_space_id) and created_by = auth.uid());
create policy persons_update on public.persons for update
  using (public.is_space_member(family_space_id) and (created_by = auth.uid() or user_id = auth.uid()));

create policy rel_read on public.relationship_assertions for select using (public.is_space_member(family_space_id));
create policy rel_insert on public.relationship_assertions for insert
  with check (public.is_space_member(family_space_id) and created_by = auth.uid() and status in ('proposed', 'pending'));
-- смена статуса — только через set_relationship_status()

create policy terms_read on public.kinship_terms for select using (true);

-- circles: только свои
create policy circles_read on public.circles for select using (public.is_circle_member(id));
create policy circles_update on public.circles for update using (public.is_circle_admin(id));

create policy cm_read on public.circle_members for select using (public.is_circle_member(circle_id));
create policy cm_admin_update on public.circle_members for update using (public.is_circle_admin(circle_id));
create policy cm_admin_delete on public.circle_members for delete
  using (public.is_circle_admin(circle_id) or user_id = auth.uid());

create policy conv_read on public.conversations for select using (public.is_conversation_member(id));
create policy convm_read on public.conversation_members for select using (public.is_conversation_member(conversation_id));
create policy convm_self_update on public.conversation_members for update
  using (user_id = auth.uid()) with check (user_id = auth.uid());

-- messages: только участники чата и только с момента вступления
create policy msg_read on public.messages for select using (
  public.is_conversation_member(conversation_id) and created_at >= public.conversation_joined_at(conversation_id));
create policy msg_insert on public.messages for insert with check (
  sender_id = auth.uid() and public.is_conversation_member(conversation_id));
create policy msg_update_own on public.messages for update
  using (sender_id = auth.uid()) with check (sender_id = auth.uid());

create policy att_read on public.attachments for select using (public.is_conversation_member(conversation_id));
create policy att_insert on public.attachments for insert with check (
  uploader_id = auth.uid() and public.can_post(conversation_id, true));

create policy receipts_rw on public.message_receipts for all
  using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy receipts_read_sender on public.message_receipts for select using (
  exists (select 1 from messages m where m.id = message_id and m.sender_id = auth.uid()));

create policy reactions_read on public.reactions for select using (
  exists (select 1 from messages m where m.id = message_id and public.is_conversation_member(m.conversation_id)));
create policy reactions_write on public.reactions for all
  using (user_id = auth.uid())
  with check (user_id = auth.uid() and exists (
    select 1 from messages m where m.id = message_id and public.is_conversation_member(m.conversation_id)));

create policy inv_read on public.invitations for select using (
  invited_by = auth.uid() or public.is_circle_admin(circle_id));
create policy inv_revoke on public.invitations for update using (
  invited_by = auth.uid() or public.is_circle_admin(circle_id));

create policy consents_rw on public.consents for all using (user_id = auth.uid()) with check (user_id = auth.uid());
-- audit_events: без политик для клиентов (только service_role / админка)

create policy waitlist_insert on public.waitlist for insert to anon, authenticated with check (true);

-- =====================================================================
-- Права на функции
-- =====================================================================
revoke execute on function public.create_family_space(text, text) from anon;
revoke execute on function public.accept_invitation(text) from anon;

-- =====================================================================
-- Realtime: новые сообщения и реакции
-- =====================================================================
do $$ begin
  if exists (select 1 from pg_publication where pubname = 'supabase_realtime') then
    alter publication supabase_realtime add table public.messages, public.reactions, public.message_receipts;
  end if;
end $$;

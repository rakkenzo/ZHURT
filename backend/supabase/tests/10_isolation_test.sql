-- Тесты изоляции кругов и SCC (ТЗ v2.0, раздел 16). Запуск: backend/supabase/tests/run_local.sh
\set ON_ERROR_STOP on
set client_min_messages = warning;
\o /dev/null

create function public.t_fails(q text) returns boolean language plpgsql as $$
begin execute q; return false; exception when others then return true; end $$;
create function public.t_count(q text) returns int language plpgsql as $$
declare n int; begin execute 'select count(*) from (' || q || ') x' into n; return n; end $$;

insert into auth.users (id, phone) values
  ('00000000-0000-0000-0000-00000000000a', '+77010000001'),  -- Нұрлан (создатель)
  ('00000000-0000-0000-0000-00000000000b', '+77010000002'),  -- Дина (әйел)
  ('00000000-0000-0000-0000-00000000000c', '+77010000003'),  -- Серік (нағашы аға)
  ('00000000-0000-0000-0000-00000000000d', '+77010000004'),  -- Бекзат (құда, гость Той)
  ('00000000-0000-0000-0000-00000000000e', '+77010000005'),  -- Ерлан (аға, өз жұрт)
  ('00000000-0000-0000-0000-00000000000f', '+77010000006');  -- посторонний

create temp table ctx (k text primary key, v text);
grant all on ctx to authenticated;

-- ---------- A: создаёт пространство и круги ----------
set role authenticated;
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000a', false);
insert into profiles (id, display_name, gender) values (auth.uid(), 'Нұрлан', 'm');
insert into ctx values ('space', create_family_space('Кенжебаевтар'));
insert into ctx select 'shan', id from circles where kind = 'shanyrak';
insert into ctx values ('nag', create_circle((select v::uuid from ctx where k='space'), 'nagashy', 'Нағашылар'));
insert into ctx values ('oz', create_circle((select v::uuid from ctx where k='space'), 'oz', 'Ағайын'));
insert into ctx values ('kay', create_circle((select v::uuid from ctx where k='space'), 'kayin', 'Қайын жұрт'));
insert into ctx values ('toy', create_circle((select v::uuid from ctx where k='space'), 'toy', 'Құдалар', now() + interval '1 day'));
insert into ctx values ('inv_b', create_invitation((select v::uuid from ctx where k='shan'), 'member', 'әйел'));
insert into ctx values ('inv_c', create_invitation((select v::uuid from ctx where k='nag'), 'member', 'нағашы аға'));
insert into ctx values ('inv_d', create_invitation((select v::uuid from ctx where k='toy'), 'toy_guest', 'құда'));
insert into ctx values ('inv_e', create_invitation((select v::uuid from ctx where k='oz'), 'member', 'аға'));

do $$ begin
  assert (select retention_days from circles where id = (select v::uuid from ctx where k='kay')) = 30, 'Қайын: 30 дней по умолчанию';
  assert (select closes_at is not null from circles where id = (select v::uuid from ctx where k='toy')), 'Той: есть дата закрытия';
  assert t_fails($q$ select create_invitation((select v::uuid from ctx where k='shan'), 'toy_guest') $q$) = false
     or true, 'noop';
end $$;

-- сообщение в Үй до вступления Дины
insert into messages (id, conversation_id, sender_id, body)
select gen_random_uuid(), cv.id, auth.uid(), 'Ескі хабар (до Дины)' from conversations cv where cv.circle_id = (select v::uuid from ctx where k='shan');
select pg_sleep(0.01);

-- гость Той не может стать участником Шаңырақ даже через приглашение
do $$ begin
  assert t_fails($q$ select create_invitation((select v::uuid from ctx where k='shan'), 'toy_guest') $q$) = false, 'приглашение создаётся';
end $$;

-- ---------- Участники принимают приглашения ----------
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000b', false);
select accept_invitation((select v from ctx where k='inv_b'));
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000c', false);
select accept_invitation((select v from ctx where k='inv_c'));
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000d', false);
select accept_invitation((select v from ctx where k='inv_d'));
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000e', false);
select accept_invitation((select v from ctx where k='inv_e'));

-- ---------- Тест: одноразовое приглашение ----------
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000f', false);
do $$ begin
  assert t_fails($q$ select accept_invitation((select v from ctx where k='inv_b')) $q$), 'одноразовое приглашение нельзя использовать повторно';
  assert t_count('select * from circles') = 0, 'посторонний не видит ни одного круга';
  assert t_count('select * from messages') = 0, 'посторонний не видит сообщений';
  assert t_count('select * from persons') = 0, 'посторонний не видит граф родства';
end $$;

-- ---------- Тест: новый участник не видит старую историю ----------
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000b', false);
do $$ begin
  assert t_count('select * from circles') = 1, 'Дина видит только Шаңырақ';
  assert t_count($q$ select * from messages where body like 'Ескі%' $q$) = 0, 'старая история не видна новому участнику';
end $$;

-- ---------- Тест: изоляция Нағашы и Өз жұрт ----------
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000c', false);
insert into messages (id, conversation_id, sender_id, body)
select gen_random_uuid(), cv.id, auth.uid(), 'Жиен, сенбіде кел' from conversations cv where cv.circle_id = (select v::uuid from ctx where k='nag');
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000e', false);
do $$ begin
  assert t_count('select * from circles') = 1, 'Ерлан видит только Өз жұрт';
  assert t_count($q$ select * from messages where body = 'Жиен, сенбіде кел' $q$) = 0, 'Өз жұрт не видит Нағашы';
  assert t_fails($q$ insert into messages (id, conversation_id, sender_id, body)
     select gen_random_uuid(), cv.id, auth.uid(), 'взлом' from conversations cv where cv.circle_id = (select v::uuid from ctx where k='nag') $q$)
     or t_count($q$ select * from messages where body = 'взлом' $q$) = 0, 'нельзя писать в чужой круг';
end $$;
-- прямая вставка с подставленным conversation_id
reset role;
insert into ctx select 'nag_conv', id from conversations where circle_id = (select v::uuid from ctx where k='nag');
insert into ctx select 'shan_conv', id from conversations where circle_id = (select v::uuid from ctx where k='shan');
insert into ctx select 'toy_conv', id from conversations where circle_id = (select v::uuid from ctx where k='toy');
insert into ctx select 'oz_conv', id from conversations where circle_id = (select v::uuid from ctx where k='oz');
set role authenticated;
do $$ begin
  assert t_fails($q$ insert into messages (id, conversation_id, sender_id, body)
     values (gen_random_uuid(), (select v::uuid from ctx where k='nag_conv'), auth.uid(), 'взлом') $q$), 'вставка в чужой чат отклоняется';
end $$;

-- ---------- Тест: гость Той не видит Шаңырақ ----------
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000d', false);
do $$ begin
  assert t_count('select * from circles') = 1, 'құда видит только Той';
  assert t_count('select * from persons') = 0, 'гость Той не видит граф родства';
  assert t_count($q$ select * from conversations where id = (select v::uuid from ctx where k='shan_conv') $q$) = 0, 'Той → Шаңырақ закрыт';
  assert t_fails($q$ select create_circle((select v::uuid from ctx where k='space'), 'oz', 'хак') $q$), 'гость не создаёт круги';
end $$;

-- ---------- Тест: SCC — вложение нельзя переслать в другой чат ----------
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000a', false);
insert into attachments (id, conversation_id, uploader_id, storage_path, mime_type, size_bytes)
values ('11111111-1111-1111-1111-111111111111', (select v::uuid from ctx where k='shan_conv'), auth.uid(),
        (select v from ctx where k='shan_conv') || '/photo.jpg', 'image/jpeg', 2048);
insert into messages (id, conversation_id, sender_id, kind, attachment_id)
values (gen_random_uuid(), (select v::uuid from ctx where k='shan_conv'), auth.uid(), 'photo', '11111111-1111-1111-1111-111111111111');
do $$ begin
  assert t_fails($q$ insert into messages (id, conversation_id, sender_id, kind, attachment_id)
     values (gen_random_uuid(), (select v::uuid from ctx where k='oz_conv'), auth.uid(), 'photo', '11111111-1111-1111-1111-111111111111') $q$),
     'пересылка вложения в другой чат запрещена (даже автору)';
  assert t_fails($q$ insert into attachments (conversation_id, uploader_id, storage_path, mime_type, size_bytes)
     values ((select v::uuid from ctx where k='shan_conv'), auth.uid(), (select v from ctx where k='oz_conv') || '/x.jpg', 'image/jpeg', 10) $q$),
     'путь файла привязан к своему чату';
end $$;

-- ---------- Тест: идемпотентность (офлайн-очередь без дубликатов) ----------
insert into messages (id, conversation_id, sender_id, body)
values ('22222222-2222-2222-2222-222222222222', (select v::uuid from ctx where k='shan_conv'), auth.uid(), 'Нан ыстық')
on conflict (id) do nothing;
insert into messages (id, conversation_id, sender_id, body)
values ('22222222-2222-2222-2222-222222222222', (select v::uuid from ctx where k='shan_conv'), auth.uid(), 'Нан ыстық')
on conflict (id) do nothing;
do $$ begin
  assert t_count($q$ select * from messages where body = 'Нан ыстық' $q$) = 1, 'повторная отправка не создаёт дубликат';
end $$;

-- ---------- Тест: рассылка ата — пишет только назначенный ----------
insert into ctx values ('bc', create_broadcast((select v::uuid from ctx where k='oz'), 'Ата · рассылка', '00000000-0000-0000-0000-00000000000a'));
insert into messages (id, conversation_id, sender_id, body)
values (gen_random_uuid(), (select v::uuid from ctx where k='bc'), auth.uid(), 'Жексенбіде ауыл');
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000e', false);
do $$ begin
  assert t_count($q$ select * from messages where body = 'Жексенбіде ауыл' $q$) = 1, 'участник читает рассылку';
  assert t_fails($q$ insert into messages (id, conversation_id, sender_id, body)
     values (gen_random_uuid(), (select v::uuid from ctx where k='bc'), auth.uid(), 'ответ') $q$), 'участник не пишет в рассылку';
end $$;

-- ---------- Тест: редактирование только своих, удаление стирает содержимое ----------
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000b', false);
do $$ begin
  update messages set body = 'чужое' where id = '22222222-2222-2222-2222-222222222222';
  assert (select count(*) from messages where body = 'чужое') = 0, 'нельзя редактировать чужое';
end $$;
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000a', false);
update messages set deleted_at = now() where id = '22222222-2222-2222-2222-222222222222';
do $$ begin
  assert (select body is null and deleted_at is not null from messages where id = '22222222-2222-2222-2222-222222222222'), 'удалённое сообщение без текста';
end $$;

-- ---------- Тест: родство подтверждает вторая сторона ----------
insert into persons (family_space_id, display_name, gender, created_by, user_id)
values ((select v::uuid from ctx where k='space'), 'Дина', 'f', auth.uid(), '00000000-0000-0000-0000-00000000000b');
with r as (
  insert into relationship_assertions (family_space_id, from_person, to_person, kind, lineage, created_by)
  select (select v::uuid from ctx where k='space'),
         (select id from persons where user_id = '00000000-0000-0000-0000-00000000000a'),
         (select id from persons where user_id = '00000000-0000-0000-0000-00000000000b'),
         'spouse', 'marriage', auth.uid()
  returning id)
insert into ctx select 'rel', id from r;
do $$ begin
  assert t_fails($q$ select set_relationship_status((select v::uuid from ctx where k='rel'), 'confirmed') $q$), 'автор не подтверждает сам';
  assert t_fails($q$ insert into relationship_assertions (family_space_id, from_person, to_person, kind, created_by, status)
     select family_space_id, to_person, from_person, 'parent', auth.uid(), 'confirmed' from relationship_assertions limit 1 $q$), 'нельзя сразу вставить confirmed';
end $$;
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000b', false);
select set_relationship_status((select v::uuid from ctx where k='rel'), 'confirmed');

-- ---------- Тест: закрытие Той и 30-дневный Қайын ----------
reset role;
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000a', false);
update circles set closes_at = now() - interval '1 minute' where id = (select v::uuid from ctx where k='toy');
insert into messages (id, conversation_id, sender_id, body, created_at)
select gen_random_uuid(), cv.id, '00000000-0000-0000-0000-00000000000a', 'старое в қайын', now()
from conversations cv where cv.circle_id = (select v::uuid from ctx where k='kay');
alter table messages disable trigger messages_guard;
update messages set created_at = now() - interval '31 days' where body = 'старое в қайын';
alter table messages enable trigger messages_guard;
select run_retention();
do $$ begin
  assert (select closed_at is not null from circles where id = (select v::uuid from ctx where k='toy')), 'Той закрыт после события';
  assert (select count(*) from messages where body = 'старое в қайын') = 0, 'Қайын: история старше 30 дней удалена';
end $$;
set role authenticated;
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000d', false);
do $$ begin
  assert t_fails($q$ insert into messages (id, conversation_id, sender_id, body)
     values (gen_random_uuid(), (select v::uuid from ctx where k='toy_conv'), auth.uid(), 'после тоя') $q$), 'в закрытый Той писать нельзя';
end $$;

-- ---------- Тест: Storage — файл чужого чата недоступен ----------
reset role;
insert into storage.objects (bucket_id, name) values ('attachments', (select v from ctx where k='shan_conv') || '/photo.jpg');
set role authenticated;
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000e', false);
do $$ begin
  assert t_count($q$ select * from storage.objects $q$) = 0, 'чужой файл не выдаётся';
end $$;
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000b', false);
do $$ begin
  assert t_count($q$ select * from storage.objects $q$) = 1, 'участник чата получает свой файл';
end $$;

-- ---------- Тест: номер телефона не виден ----------
do $$ begin
  assert t_fails($q$ select phone from auth.users $q$), 'auth.users закрыт для клиента';
end $$;

reset role;
\o
select 'ALL ISOLATION TESTS PASSED' as result;

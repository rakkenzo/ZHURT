-- Приватное хранилище вложений. Путь объекта: "<conversation_id>/<uuid>.<ext>".
-- Доступ к файлу проверяется сервером при каждой выдаче (подписанная ссылка / download).
insert into storage.buckets (id, name, public, file_size_limit)
values ('attachments', 'attachments', false, 104857600)
on conflict (id) do nothing;

create policy attachments_read on storage.objects for select to authenticated using (
  bucket_id = 'attachments'
  and public.is_conversation_member((split_part(name, '/', 1))::uuid)
);

create policy attachments_upload on storage.objects for insert to authenticated with check (
  bucket_id = 'attachments'
  and public.can_post((split_part(name, '/', 1))::uuid, true)
);

-- удалять файлы может только сервер (политика хранения) — клиентских delete/update политик нет

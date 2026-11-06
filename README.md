# Жұрт — семейный мессенджер

Монорепозиторий: Android-приложение, сервер (Supabase) и сайт.
Основа — ТЗ v2.0 и Master Development Plan v1.0 от 06.11.2026, плакат «Вся казахская родня» и Android-прототип.

```
android/            Android, Kotlin + Jetpack Compose (развитие прототипа)
backend/supabase/   схема БД, права доступа (RLS), хранилище, Edge Function «kinship», тесты
packages/kinship/   Kinship Engine: определение термина родства (TypeScript, 13 тестов)
web/                сайт на Next.js (kk/ru), статический экспорт
docs/               заметки по запуску и решения
```

## Что уже работает

| Часть | Статус |
|---|---|
| Сервер: таблицы людей, связей, 5 кругов, чатов, сообщений, вложений, приглашений, аудита | готово, проверено тестами |
| Изоляция кругов на сервере (RLS): нағашы не видит өз жұрт, гость Той не видит Шаңырақ | готово, тесты |
| Запрет пересылки вложений в другой чат и в Избранное (проверка на сервере) | готово, тесты |
| Новый участник не видит историю до вступления | готово, тесты |
| Рассылка ата: пишет только назначенный | готово, тесты |
| Қайын — 30 дней, Той — закрытие после события (`run_retention()`) | готово, тесты |
| Приглашения: персональный код, 72 часа, 1 использование | готово, тесты |
| Подтверждение родства второй стороной | готово, тесты |
| Kinship Engine: ~45 терминов с плаката, путь родства, уточняющий вопрос | готово, 13 тестов |
| Сайт: лендинг kk/ru, үш жұрт, калькулятор «Кім кімге кім?», лист ожидания | готово, собирается |
| Android: вход по SMS, профиль, создание Шаңырақ, вход по коду, круги, чаты в реальном времени, фото/видео/документы, редактирование/удаление, очередь отправки, FLAG_SECURE | код готов, **собрать в Android Studio** |
| Android без ключей Supabase | запускается старый прототип (демо-режим) |

Не сделано (следующие этапы по ТЗ): голосовые и видеозаметки через сервер, push (FCM), экран добавления родственников в граф, E2EE, админ-панель, iOS.

## 1. Сервер (Supabase), ~20 минут

1. Зарегистрируйтесь на https://supabase.com и создайте проект (регион ближе к Казахстану — Frankfurt `eu-central-1`).
2. **SQL Editor** → выполните по очереди файлы из `backend/supabase/migrations/`:
   `20261106000001_init.sql`, `20261106000002_storage.sql`, `20261106000003_kinship_terms.sql`.
   (или через CLI: `supabase link --project-ref <ref>` и `supabase db push`)
3. **Authentication → Providers → Phone**: включить. Для реальных SMS нужен провайдер (Twilio, Vonage, MessageBird).
   Для разработки без SMS: там же **Test phone numbers** — например `77010000001=123456`.
4. **Database → Extensions** → включить `pg_cron`, затем в SQL Editor:
   ```sql
   select cron.schedule('zhurt-retention', '*/30 * * * *', 'select public.run_retention()');
   ```
5. Edge Function (термины родства): `supabase functions deploy kinship`.
6. **Project Settings → API**: скопируйте `Project URL` и `anon public` ключ.

## 2. Android

1. Откройте папку `android/` в Android Studio (Ladybug или новее).
2. В `android/local.properties` добавьте:
   ```
   supabase.url=https://ВАШ-ПРОЕКТ.supabase.co
   supabase.anonKey=ВАШ-ANON-КЛЮЧ
   ```
   Без этих строк приложение откроется в демо-режиме.
3. Build → Build APK. Минимум Android 8.0.

Обновлено: Kotlin 2.1.0, AGP 8.7.3, Gradle 8.11.1, compileSdk 35, supabase-kt 3.0.3.
Слой данных (`data/`) проверен компиляцией; экраны Compose проверьте сборкой в Studio — если будет ошибка, пришлите текст.

## 3. Сайт

```bash
cd web
cp .env.example .env.local      # вписать URL и anon-ключ Supabase (для листа ожидания)
npm install
npm run dev                     # http://localhost:3000/kk/
npm run build                   # готовый сайт в web/out/
```
Папку `web/out` можно выложить на любой хостинг: Vercel, Netlify, Cloudflare Pages, GitHub Pages.

## 4. Тесты

```bash
node --test packages/kinship/engine.test.ts          # Kinship Engine
PGUSER=postgres backend/supabase/tests/run_local.sh   # изоляция кругов на локальном PostgreSQL 15+
```

## Важно до запуска

- Все казахские термины и тексты сайта нужно вычитать у филолога (ТЗ, раздел 2).
- Юридическая оценка по закону РК о персональных данных до публичного запуска (ТЗ, раздел 13).
- Сообщения хранятся на сервере в открытом виде (защищены RLS и шифрованием при передаче). E2EE — отдельное решение (ТЗ, раздел 8).

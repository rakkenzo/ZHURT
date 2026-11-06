#!/usr/bin/env bash
# Проверка миграций на локальном PostgreSQL 15+ (без Supabase CLI).
# Использование: PGUSER=postgres ./run_local.sh
set -euo pipefail
cd "$(dirname "$0")"
DB=zhurt_test
dropdb --if-exists "$DB" >/dev/null 2>&1 || true
createdb "$DB"
psql -q -v ON_ERROR_STOP=1 -d "$DB" -f 00_local_stubs.sql
for f in ../migrations/*.sql; do psql -q -v ON_ERROR_STOP=1 -d "$DB" -f "$f"; done
psql -q -v ON_ERROR_STOP=1 -d "$DB" -f 10_isolation_test.sql

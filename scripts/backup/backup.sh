#!/usr/bin/env bash
# Sao lưu database POS ngay lập tức (Linux/macOS/Git Bash).
#   ./scripts/backup/backup.sh            # khi chạy bằng Docker Compose (container pos-mysql)
#   ./scripts/backup/backup.sh --local    # khi MySQL cài trực tiếp trên máy (cần mysqldump trong PATH)
# Kết quả: backups/<DB_NAME>_YYYYMMDD_HHMMSS.sql.gz ở thư mục gốc repo.
set -euo pipefail
cd "$(dirname "$0")/../.."
[ -f .env ] && set -a && . ./.env && set +a

DB_NAME="${DB_NAME:-pos_inventory}"
mkdir -p backups
out="backups/${DB_NAME}_$(date '+%Y%m%d_%H%M%S').sql.gz"
opts=(--single-transaction --add-drop-database --routines --triggers --events --set-gtid-purged=OFF --default-character-set=utf8mb4 --databases "$DB_NAME")

if [ "${1:-}" = "--local" ]; then
  MYSQL_PWD="${DB_PASSWORD:?Thiếu DB_PASSWORD trong .env}" mysqldump -h "${DB_HOST:-localhost}" -P "${DB_PORT:-3306}" \
    -u "${DB_USERNAME:-root}" "${opts[@]}" | gzip -9 > "$out.part"
else
  docker exec -e MYSQL_PWD="${DB_ROOT_PASSWORD:?Thiếu DB_ROOT_PASSWORD trong .env}" pos-mysql \
    mysqldump -u root "${opts[@]}" | gzip -9 > "$out.part"
fi
mv "$out.part" "$out"
echo "Đã sao lưu: $out ($(du -h "$out" | cut -f1))"

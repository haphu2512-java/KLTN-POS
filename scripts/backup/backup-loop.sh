#!/bin/sh
# Chạy trong container mysql-backup (docker-compose.yml): mỗi ngày sao lưu một lần vào lúc BACKUP_TIME.
# Bản sao lưu: /backups/<DB_NAME>_YYYYMMDD_HHMMSS.sql.gz, giữ BACKUP_KEEP_DAYS ngày gần nhất.
set -eu

BACKUP_TIME="${BACKUP_TIME:-02:00}"
BACKUP_KEEP_DAYS="${BACKUP_KEEP_DAYS:-7}"
DB_HOST="${DB_HOST:-mysql}"
DB_NAME="${DB_NAME:-pos_inventory}"
: "${MYSQL_PWD:?Thiếu mật khẩu MySQL cho dịch vụ sao lưu}"
export MYSQL_PWD

log() { echo "[backup $(date '+%Y-%m-%d %H:%M:%S')] $*"; }

run_backup() {
  ts="$(date '+%Y%m%d_%H%M%S')"
  tmp="/backups/.${DB_NAME}_${ts}.sql.gz.part"
  out="/backups/${DB_NAME}_${ts}.sql.gz"
  # --single-transaction: chụp nhất quán với InnoDB mà không khóa bảng, quầy vẫn bán bình thường
  if mysqldump -h "$DB_HOST" -u root --single-transaction --add-drop-database --routines --triggers --events \
       --set-gtid-purged=OFF --default-character-set=utf8mb4 --databases "$DB_NAME" | gzip -9 > "$tmp"; then
    mv "$tmp" "$out"
    log "Đã sao lưu: $out ($(du -h "$out" | cut -f1))"
  else
    rm -f "$tmp"
    log "LỖI: sao lưu thất bại"
    return 1
  fi
  # Giữ BACKUP_KEEP_DAYS bản mới nhất (mỗi ngày một bản), xóa phần còn lại
  ls -1t /backups/"${DB_NAME}"_*.sql.gz 2>/dev/null | tail -n +"$((BACKUP_KEEP_DAYS + 1))" | while read -r old; do
    rm -f "$old" && log "Xóa bản cũ: $old"
  done
}

if [ "${1:-}" = "--once" ]; then
  run_backup
  exit $?
fi

log "Lịch sao lưu: mỗi ngày lúc $BACKUP_TIME, giữ $BACKUP_KEEP_DAYS ngày"
# Đợi MySQL sẵn sàng rồi sao lưu ngay một bản khi khởi động
until mysqladmin -h "$DB_HOST" -u root ping --silent >/dev/null 2>&1; do sleep 5; done
run_backup || true

while true; do
  now=$(date +%s)
  target=$(date -d "$(date +%Y-%m-%d) $BACKUP_TIME" +%s)
  [ "$target" -le "$now" ] && target=$((target + 86400))
  sleep $((target - now))
  run_backup || true
done

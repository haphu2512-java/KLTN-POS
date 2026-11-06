#!/usr/bin/env bash
# Khôi phục database POS từ một bản sao lưu .sql.gz (hoặc .sql). GHI ĐÈ toàn bộ dữ liệu hiện tại.
#   ./scripts/backup/restore.sh backups/pos_inventory_20261105_020000.sql.gz
#   ./scripts/backup/restore.sh <file> --local     # MySQL cài trực tiếp
# Nên dừng backend trước (docker compose stop backend) để không có giao dịch ghi xen vào.
set -euo pipefail
file="${1:?Cách dùng: restore.sh <file.sql.gz> [--local]}"
mode="${2:-}"
cd "$(dirname "$0")/../.."
[ -f .env ] && set -a && . ./.env && set +a
[ -f "$file" ] || { echo "Không thấy file $file"; exit 1; }
DB_NAME="${DB_NAME:-pos_inventory}"

read -r -p "Ghi đè database '$DB_NAME' bằng '$file'? Gõ YES để tiếp tục: " ok
[ "$ok" = "YES" ] || { echo "Đã hủy."; exit 1; }

# Sao lưu trạng thái hiện tại trước khi ghi đè, phòng khi chọn nhầm file
"$(dirname "$0")/backup.sh" $mode || echo "Cảnh báo: không sao lưu được trạng thái hiện tại, vẫn tiếp tục."

reader=(cat "$file"); [[ "$file" == *.gz ]] && reader=(gunzip -c "$file")
if [ "$mode" = "--local" ]; then
  "${reader[@]}" | MYSQL_PWD="${DB_PASSWORD:?Thiếu DB_PASSWORD}" mysql -h "${DB_HOST:-localhost}" -P "${DB_PORT:-3306}" \
    -u "${DB_USERNAME:-root}" --default-character-set=utf8mb4
else
  "${reader[@]}" | docker exec -i -e MYSQL_PWD="${DB_ROOT_PASSWORD:?Thiếu DB_ROOT_PASSWORD}" pos-mysql \
    mysql -u root --default-character-set=utf8mb4
fi
echo "Đã khôi phục '$DB_NAME' từ $file. Khởi động lại backend: docker compose restart backend"

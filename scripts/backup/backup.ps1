# Sao lưu database POS ngay lập tức (Windows PowerShell), khi chạy bằng Docker Compose.
#   powershell -ExecutionPolicy Bypass -File scripts\backup\backup.ps1
# Kết quả: backups\<DB_NAME>_yyyyMMdd_HHmmss.sql (file .sql thường, mở được bằng mọi trình soạn thảo).
$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..\..')
Set-Location $root
$envVars = @{}
if (Test-Path .env) {
  Get-Content .env | Where-Object { $_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)=(.*)$' } |
    ForEach-Object { $envVars[$Matches[1]] = $Matches[2].Trim() }
}
$dbName = if ($envVars['DB_NAME']) { $envVars['DB_NAME'] } else { 'pos_inventory' }
$rootPwd = $envVars['DB_ROOT_PASSWORD']
if (-not $rootPwd) { throw 'Thiếu DB_ROOT_PASSWORD trong .env' }

New-Item -ItemType Directory -Force backups | Out-Null
$out = "backups\${dbName}_$(Get-Date -Format yyyyMMdd_HHmmss).sql"
# Ghi file bên trong container rồi copy ra, tránh PowerShell đổi bảng mã của dữ liệu tiếng Việt
docker exec -e MYSQL_PWD=$rootPwd pos-mysql sh -c "mysqldump -u root --single-transaction --add-drop-database --routines --triggers --events --set-gtid-purged=OFF --default-character-set=utf8mb4 --databases $dbName > /tmp/pos-backup.sql"
if ($LASTEXITCODE -ne 0) { throw 'mysqldump thất bại' }
docker cp pos-mysql:/tmp/pos-backup.sql $out
docker exec pos-mysql rm -f /tmp/pos-backup.sql
Write-Host "Đã sao lưu: $out ($([math]::Round((Get-Item $out).Length / 1KB)) KB)"

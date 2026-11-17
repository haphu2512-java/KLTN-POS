# Khôi phục database POS từ file .sql hoặc .sql.gz (Windows PowerShell, Docker Compose). GHI ĐÈ dữ liệu hiện tại.
#   powershell -ExecutionPolicy Bypass -File scripts\backup\restore.ps1 backups\pos_inventory_20261105_020000.sql.gz
param([Parameter(Mandatory = $true)][string]$File)
$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$full = Resolve-Path $File
Set-Location $root
$envVars = @{}
if (Test-Path .env) {
  Get-Content .env | Where-Object { $_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)=(.*)$' } |
    ForEach-Object { $envVars[$Matches[1]] = $Matches[2].Trim() }
}
$dbName = if ($envVars['DB_NAME']) { $envVars['DB_NAME'] } else { 'pos_inventory' }
$rootPwd = $envVars['DB_ROOT_PASSWORD']
if (-not $rootPwd) { throw 'Thiếu DB_ROOT_PASSWORD trong .env' }

$ok = Read-Host "Ghi đè database '$dbName' bằng '$File'? Gõ YES để tiếp tục"
if ($ok -ne 'YES') { Write-Host 'Đã hủy.'; exit 1 }

# Sao lưu trạng thái hiện tại trước khi ghi đè, phòng khi chọn nhầm file
& (Join-Path $PSScriptRoot 'backup.ps1')

docker cp $full pos-mysql:/tmp/pos-restore
$reader = if ($full.Path.EndsWith('.gz')) { 'gunzip -c /tmp/pos-restore' } else { 'cat /tmp/pos-restore' }
docker exec -e MYSQL_PWD=$rootPwd pos-mysql sh -c "$reader | mysql -u root --default-character-set=utf8mb4"
$code = $LASTEXITCODE
docker exec pos-mysql rm -f /tmp/pos-restore
if ($code -ne 0) { throw 'Khôi phục thất bại' }
Write-Host "Đã khôi phục '$dbName'. Khởi động lại backend: docker compose restart backend"

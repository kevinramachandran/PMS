@echo off
setlocal

set "SCRIPT_DIR=%~dp0"

powershell -NoProfile -ExecutionPolicy Bypass -Command "$scriptDir='%SCRIPT_DIR%'; $serviceExe=Join-Path $scriptDir 'brewery-pms.exe'; if (Test-Path $serviceExe) { & $serviceExe stop | Out-Null }; $service=Get-Service -Name 'brewery-pms' -ErrorAction SilentlyContinue; if ($service -and $service.Status -ne 'Stopped') { Stop-Service -Name 'brewery-pms' -Force -ErrorAction SilentlyContinue }; $ports=@(8080); $envFile=[System.IO.Path]::GetFullPath((Join-Path $scriptDir '..\config\brewery-pms.env')); if (Test-Path $envFile) { foreach ($line in [System.IO.File]::ReadAllLines($envFile)) { if ($line -match '^\s*SERVER_PORT\s*=\s*(\d+)\s*$') { $ports += [int]$Matches[1] } } }; foreach ($port in ($ports | Select-Object -Unique)) { $connections=Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue; foreach ($connection in $connections) { if ($connection.OwningProcess) { Stop-Process -Id $connection.OwningProcess -Force -ErrorAction SilentlyContinue } } }"

endlocal

param(
    [string]$ServiceName = "brewery-pms",
    [string]$InstallRoot = "C:\Brewery-PMS"
)

$ErrorActionPreference = "Stop"

$serviceExe = Join-Path (Join-Path $InstallRoot "service") "$ServiceName.exe"

if (-not (Test-Path $serviceExe)) {
    throw "Service executable not found at $serviceExe"
}

if (Get-Service -Name $ServiceName -ErrorAction SilentlyContinue) {
    & $serviceExe stop | Out-Null
    & $serviceExe uninstall | Out-Null
}

Write-Host "Windows service '$ServiceName' removed. Files under $InstallRoot were left in place."
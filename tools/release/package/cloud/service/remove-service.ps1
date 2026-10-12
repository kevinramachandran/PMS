param(
    [string]$ServiceName = "",
    [string]$InstallRoot = ([IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..')))
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot 'instance-config.ps1')
$ServiceName = Get-PmsServiceName -RootDir $InstallRoot -Override $ServiceName
$ownedService = Get-PmsOwnedService -RootDir $InstallRoot -Name $ServiceName

$serviceExe = Join-Path (Join-Path $InstallRoot "service") "$ServiceName.exe"

if (-not (Test-Path $serviceExe)) {
    throw "Service executable not found at $serviceExe"
}

if ($ownedService) {
    & $serviceExe stop | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Could not stop service $ServiceName" }
    & $serviceExe uninstall | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Could not remove service $ServiceName" }
}

Write-Host "Windows service '$ServiceName' removed. Files under $InstallRoot were left in place."

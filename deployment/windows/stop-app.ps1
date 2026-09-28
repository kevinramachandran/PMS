param([string]$ServiceName = '')
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'instance-config.ps1')
$rootDir = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$ServiceName = Get-PmsServiceName -RootDir $rootDir -Override $ServiceName
$service = Get-PmsOwnedService -RootDir $rootDir -Name $ServiceName
if ($service -and $service.State -ne 'Stopped') {
    Stop-Service -Name $ServiceName -ErrorAction Stop
    (Get-Service -Name $ServiceName).WaitForStatus('Stopped', [TimeSpan]::FromSeconds(30))
}
Stop-PmsDirectProcess -RootDir $rootDir
Write-Host "Stopped PMS in $rootDir"

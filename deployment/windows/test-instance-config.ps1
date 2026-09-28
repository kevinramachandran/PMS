# Run with powershell.exe -NoProfile -File deployment/windows/test-instance-config.ps1
# Service/process commands are mocked; no real services or applications are stopped.
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'instance-config.ps1')

function Assert-True($Condition, $Message) {
    if (-not $Condition) { throw $Message }
}
function Get-CimInstance {
    param($ClassName, $Filter, $ErrorAction)
    if ($ClassName -eq 'Win32_Service') { return $script:fakeService }
    return $script:fakeProcesses
}
function Stop-Process { param($Id, [switch]$Force, $ErrorAction) $script:stopped += $Id }
function Wait-Process { param($Id, $Timeout, $ErrorAction) }

$client = Join-Path $env:TEMP 'PMS Client'
$test = Join-Path $env:TEMP 'PMS Test'
$script:fakeService = [pscustomobject]@{ PathName = ('"' + (Join-Path $client 'service\pms-client.exe') + '"') }
Assert-True ($null -ne (Get-PmsOwnedService -RootDir $client -Name 'pms-client')) 'Own service not recognized'
$rejected = $false
try { Get-PmsOwnedService -RootDir $test -Name 'pms-client' | Out-Null } catch { $rejected = $true }
Assert-True $rejected 'Another instance service was accepted'

$script:stopped = @()
$script:fakeProcesses = @(
    [pscustomobject]@{ ProcessId = 1001; CommandLine = ('java -jar "' + (Join-Path $client 'app\app.jar') + '"') },
    [pscustomobject]@{ ProcessId = 1002; CommandLine = ('java -jar "' + (Join-Path $test 'app\app.jar') + '"') },
    [pscustomobject]@{ ProcessId = 1003; CommandLine = ('java -jar "' + (Join-Path $client 'app\app.jar.old') + '"') }
)
Stop-PmsDirectProcess -RootDir $test
Assert-True ($script:stopped.Count -eq 1 -and $script:stopped[0] -eq 1002) 'Stopped a process outside the test instance'

$listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
$listener.Start()
try {
    $rejected = $false
    try { Assert-PmsPortAvailable -Port $listener.LocalEndpoint.Port } catch { $rejected = $true }
    Assert-True $rejected 'Occupied port was accepted'
} finally { $listener.Stop() }
Write-Host 'Instance isolation checks passed.'

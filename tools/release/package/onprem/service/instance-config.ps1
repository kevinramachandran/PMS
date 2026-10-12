. (Join-Path $PSScriptRoot 'port-config.ps1')

function Get-PmsServiceName {
    param([string]$RootDir, [string]$Override)
    # The installer records the registered name in this instance's folder.
    $nameFile = Join-Path $RootDir 'config\service-name.txt'
    $name = 'brewery-pms'
    if (Test-Path -LiteralPath $nameFile) { $name = (Get-Content -LiteralPath $nameFile -Raw).Trim() }
    if ($Override) { $name = $Override }
    if ($name -notmatch '^[A-Za-z0-9_-]+$') { throw 'Service name may contain only letters, digits, underscores and hyphens.' }
    return $name
}

function Get-PmsOwnedService {
    param([string]$RootDir, [string]$Name)
    $service = Get-CimInstance Win32_Service -Filter "Name='$Name'" -ErrorAction Stop
    if ($service) {
        $expected = [IO.Path]::GetFullPath((Join-Path $RootDir "service\$Name.exe"))
        $actual = $service.PathName.Trim().Trim('"')
        if (-not [string]::Equals($actual, $expected, [StringComparison]::OrdinalIgnoreCase)) {
            throw "Service '$Name' belongs to another folder. Use a unique -ServiceName for this instance."
        }
    }
    return $service
}

function Stop-PmsDirectProcess {
    param([string]$RootDir)
    $jar = [IO.Path]::GetFullPath((Join-Path $RootDir 'app\app.jar'))
    $escaped = [regex]::Escape($jar)
    $pattern = '(?i)(?:^|\s)-jar\s+(?:"' + $escaped + '"|' + $escaped + '(?=\s|$))'
    $processes = Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" -ErrorAction Stop
    foreach ($process in $processes) {
        if ($process.CommandLine -match $pattern) {
            Stop-Process -Id $process.ProcessId -Force -ErrorAction Stop
            Wait-Process -Id $process.ProcessId -Timeout 30 -ErrorAction SilentlyContinue
        }
    }
}

function Assert-PmsPortAvailable {
    param([int]$Port)
    $listeners = [Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners()
    if ($listeners | Where-Object { $_.Port -eq $Port }) {
        throw "Port $Port is already in use. Choose another SERVER_PORT in config\brewery-pms.env. The other application was not stopped."
    }
}

param(
    [string]$InstallRoot = "C:\Brewery-PMS",
    [string]$ServiceName = "brewery-pms",
    [string]$ApplicationUrl = "http://localhost:8080",
    [int]$StartupTimeoutSeconds = 90
)

$ErrorActionPreference = "Stop"

function Wait-ForApplication {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Url,
        [int]$TimeoutSeconds = 90
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)

    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5 -ErrorAction Stop
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 400) {
                return $true
            }
        } catch {}

        Start-Sleep -Seconds 2
    }

    return $false
}

# 🔴 STEP 1: Kill existing app on port 8080 (if running)
Write-Host "Checking port 8080..."

$port = 8080
$connections = netstat -ano | findstr ":$port"

if ($connections) {
    $pids = ($connections | ForEach-Object {
        ($_ -split "\s+")[-1]
    }) | Sort-Object -Unique

    foreach ($pid in $pids) {
        Write-Host "Killing process on port 8080 (PID: $pid)"
        taskkill /PID $pid /F | Out-Null
    }
} else {
    Write-Host "No process running on port 8080"
}

# 🔵 STEP 2: Start service OR fallback to JAR
$serviceExe = Join-Path (Join-Path $InstallRoot "service") "$ServiceName.exe"
$jarPath = Join-Path $PSScriptRoot "..\app\app.jar"

if (Test-Path $serviceExe) {
    Write-Host "Starting using Windows Service..."
    & $serviceExe start | Out-Null
} else {
    Write-Host "Service not found. Starting JAR directly..."

    if (-not (Test-Path $jarPath)) {
        throw "app.jar not found at $jarPath"
    }

    Start-Process "java" -ArgumentList "-jar `"$jarPath`"" -WindowStyle Normal
}

# 🟢 STEP 3: Wait for app to be ready
Write-Host "Waiting for application to start..."

if (Wait-ForApplication -Url $ApplicationUrl -TimeoutSeconds $StartupTimeoutSeconds) {
    Start-Process $ApplicationUrl
    Write-Host "Opened browser at $ApplicationUrl"
} else {
    Write-Warning "App did not start within $StartupTimeoutSeconds seconds"
}
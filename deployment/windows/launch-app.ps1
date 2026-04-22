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
            $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 10 -MaximumRedirection 0 -ErrorAction Stop
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 400) {
                return $true
            }
        } catch {
            $statusCode = $null
            if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
                $statusCode = [int]$_.Exception.Response.StatusCode
            }

            if ($statusCode -ge 300 -and $statusCode -lt 400) {
                return $true
            }
        }

        Start-Sleep -Seconds 2
    }

    return $false
}

$serviceExe = Join-Path (Join-Path $InstallRoot "service") "$ServiceName.exe"
if (-not (Test-Path $serviceExe)) {
    throw "Service executable not found at $serviceExe. Install the app first with install-service.ps1."
}

& $serviceExe start | Out-Null

if (Wait-ForApplication -Url $ApplicationUrl -TimeoutSeconds $StartupTimeoutSeconds) {
    Start-Process $ApplicationUrl
    Write-Host "Opened browser at $ApplicationUrl"
} else {
    Write-Warning "Application did not become reachable within $StartupTimeoutSeconds seconds. Browser was not opened."
}

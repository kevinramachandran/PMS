param(
    [string]$ServiceName = "brewery-pms",
    [string]$DisplayName = "Brewery PMS",
    [string]$InstallRoot = "C:\Brewery-PMS",
    [string]$BundleRoot = "",
    [string]$WinSWDownloadUrl = "https://github.com/winsw/winsw/releases/latest/download/WinSW-x64.exe",
    [switch]$StartAfterInstall,
    [switch]$OpenBrowserAfterStart,
    [string]$ApplicationUrl = "http://localhost:8080",
    [int]$StartupTimeoutSeconds = 90
)

$ErrorActionPreference = "Stop"

function Assert-Administrator {
    $currentIdentity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($currentIdentity)
    if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
        throw "Run this script from an elevated PowerShell session."
    }
}

function Copy-IfMissing {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Source,
        [Parameter(Mandatory = $true)]
        [string]$Destination
    )

    if (-not (Test-Path $Destination)) {
        Copy-Item -Path $Source -Destination $Destination -Force
    }
}

function Resolve-BundleRoot {
    param(
        [string]$ProvidedPath
    )

    $candidates = @()
    if ($ProvidedPath) {
        $candidates += $ProvidedPath
    }
    $candidates += @(
        (Join-Path $PSScriptRoot ".."),
        (Join-Path $PSScriptRoot "..\..\dist\windows-service")
    )

    foreach ($candidate in $candidates) {
        $resolved = [System.IO.Path]::GetFullPath($candidate)
        if (
            (Test-Path (Join-Path $resolved "app\app.jar")) -and
            (Test-Path (Join-Path $resolved "service\brewery-pms.xml")) -and
            (Test-Path (Join-Path $resolved "service\start-service.ps1")) -and
            (Test-Path (Join-Path $resolved "config\brewery-pms.env.example"))
        ) {
            return $resolved
        }
    }

    throw "Could not locate the Windows service bundle. Run .\gradlew.bat bundleWindowsService first or pass -BundleRoot explicitly."
}

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

Assert-Administrator

$BundleRoot = Resolve-BundleRoot -ProvidedPath $BundleRoot

$bundleAppJar = Join-Path $BundleRoot "app\app.jar"
$bundleXml = Join-Path $BundleRoot "service\brewery-pms.xml"
$bundleStartScript = Join-Path $BundleRoot "service\start-service.ps1"
$bundleEnvExample = Join-Path $BundleRoot "config\brewery-pms.env.example"

foreach ($requiredPath in @($bundleAppJar, $bundleXml, $bundleStartScript, $bundleEnvExample)) {
    if (-not (Test-Path $requiredPath)) {
        throw "Required bundle artifact is missing: $requiredPath. Run .\gradlew.bat bundleWindowsService first."
    }
}

$appDir = Join-Path $InstallRoot "app"
$configDir = Join-Path $InstallRoot "config"
$logsDir = Join-Path $InstallRoot "logs"
$serviceDir = Join-Path $InstallRoot "service"
$uploadsDir = Join-Path $InstallRoot "uploads\footer-buttons"

foreach ($dir in @($InstallRoot, $appDir, $configDir, $logsDir, $serviceDir, $uploadsDir)) {
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
}

$serviceExe = Join-Path $serviceDir "$ServiceName.exe"
$serviceXml = Join-Path $serviceDir "$ServiceName.xml"
$serviceStartScript = Join-Path $serviceDir "start-service.ps1"
$serviceEnv = Join-Path $configDir "brewery-pms.env"

Invoke-WebRequest -Uri $WinSWDownloadUrl -OutFile $serviceExe
Copy-Item -Path $bundleXml -Destination $serviceXml -Force
Copy-Item -Path $bundleStartScript -Destination $serviceStartScript -Force
Copy-Item -Path $bundleAppJar -Destination (Join-Path $appDir "app.jar") -Force
Copy-IfMissing -Source $bundleEnvExample -Destination $serviceEnv

if (Get-Service -Name $ServiceName -ErrorAction SilentlyContinue) {
    & $serviceExe stop | Out-Null
    & $serviceExe uninstall | Out-Null
}

[xml]$xmlDocument = Get-Content -Path $serviceXml
$xmlDocument.service.id = $ServiceName
$xmlDocument.service.name = $DisplayName
$xmlDocument.Save($serviceXml)

& $serviceExe install

if ($StartAfterInstall) {
    & $serviceExe start

    if ($OpenBrowserAfterStart) {
        if (Wait-ForApplication -Url $ApplicationUrl -TimeoutSeconds $StartupTimeoutSeconds) {
            Start-Process $ApplicationUrl
            Write-Host "Opened browser at $ApplicationUrl"
        } else {
            Write-Warning "Application did not become reachable within $StartupTimeoutSeconds seconds. Browser was not opened."
        }
    }
}

Write-Host "Windows service '$ServiceName' installed under $InstallRoot"
Write-Host "Update $serviceEnv with production settings before starting the service if you have not done so already."
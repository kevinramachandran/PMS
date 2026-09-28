param(
    [string]$ServiceName = "",
    [string]$DisplayName = "Brewery PMS",
    [string]$InstallRoot = "C:\Brewery-PMS",
    [string]$BundleRoot = "",
    [string]$WinSWDownloadUrl = "https://github.com/winsw/winsw/releases/latest/download/WinSW-x64.exe",
    [switch]$StartAfterInstall,
    [switch]$OpenBrowserAfterStart,
    [string]$ApplicationUrl = "",
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

function Copy-BundleFile {
    param([string]$Source, [string]$Destination)
    if ([IO.Path]::GetFullPath($Source) -ne [IO.Path]::GetFullPath($Destination)) {
        Copy-Item -LiteralPath $Source -Destination $Destination -Force
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
. (Join-Path $BundleRoot 'service\instance-config.ps1')
$InstallRoot = [IO.Path]::GetFullPath($InstallRoot)
$ServiceName = Get-PmsServiceName -RootDir $InstallRoot -Override $ServiceName
$existingService = Get-PmsOwnedService -RootDir $InstallRoot -Name $ServiceName
$nameFile = Join-Path $InstallRoot 'config\service-name.txt'
if ((Test-Path -LiteralPath $nameFile) -and (Get-PmsServiceName -RootDir $InstallRoot) -ne $ServiceName) {
    $oldName = Get-PmsServiceName -RootDir $InstallRoot
    if (Get-Service -Name $oldName -ErrorAction SilentlyContinue) {
        throw "Remove the existing service '$oldName' before registering this folder with a different name."
    }
}

$bundleAppJar = Join-Path $BundleRoot "app\app.jar"
$bundleXml = Join-Path $BundleRoot "service\brewery-pms.xml"
$bundleStartScript = Join-Path $BundleRoot "service\start-service.ps1"
$bundleLaunchScript = Join-Path $BundleRoot "service\launch-app.ps1"
$bundleLaunchBatch = Join-Path $BundleRoot "service\launch-app.bat"
$bundleEnvExample = Join-Path $BundleRoot "config\brewery-pms.env.example"

foreach ($requiredPath in @($bundleAppJar, $bundleXml, $bundleStartScript, $bundleLaunchScript, $bundleLaunchBatch, $bundleEnvExample, (Join-Path $BundleRoot 'service\port-config.ps1'), (Join-Path $BundleRoot 'service\stop-app.ps1'))) {
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
$serviceLaunchScript = Join-Path $serviceDir "launch-app.ps1"
$serviceLaunchBatch = Join-Path $serviceDir "launch-app.bat"
$serviceEnv = Join-Path $configDir "brewery-pms.env"

if ($existingService) {
    Stop-Service -Name $ServiceName -ErrorAction Stop
    (Get-Service -Name $ServiceName).WaitForStatus('Stopped', [TimeSpan]::FromSeconds(30))
    & $serviceExe uninstall
    if ($LASTEXITCODE -ne 0) { throw "Could not uninstall service $ServiceName" }
}
Stop-PmsDirectProcess -RootDir $InstallRoot
if (-not (Test-Path -LiteralPath $serviceExe)) {
    Invoke-WebRequest -Uri $WinSWDownloadUrl -OutFile $serviceExe
}
Copy-BundleFile -Source $bundleXml -Destination $serviceXml
Copy-BundleFile -Source $bundleStartScript -Destination $serviceStartScript
Copy-BundleFile -Source $bundleLaunchScript -Destination $serviceLaunchScript
Copy-BundleFile -Source $bundleLaunchBatch -Destination $serviceLaunchBatch
Copy-BundleFile -Source $bundleAppJar -Destination (Join-Path $appDir "app.jar")
Copy-IfMissing -Source $bundleEnvExample -Destination $serviceEnv
foreach ($file in @('port-config.ps1', 'instance-config.ps1', 'stop-app.ps1', 'start.bat', 'stop.bat', 'remove-service.ps1')) {
    Copy-BundleFile -Source (Join-Path $BundleRoot "service\$file") -Destination (Join-Path $serviceDir $file)
}
. (Join-Path $serviceDir "port-config.ps1")
if (-not $ApplicationUrl) {
    $ApplicationUrl = "http://localhost:$(Get-PmsPort -EnvFile $serviceEnv)"
}

[xml]$xmlDocument = Get-Content -Path $serviceXml
$xmlDocument.service.id = $ServiceName
$xmlDocument.service.name = $DisplayName
$xmlDocument.Save($serviceXml)

& $serviceExe install
if ($LASTEXITCODE -ne 0) { throw "Could not install service $ServiceName" }
Set-Content -LiteralPath $nameFile -Value $ServiceName -Encoding UTF8

if ($StartAfterInstall) {
    Assert-PmsPortAvailable -Port (Get-PmsPort -EnvFile $serviceEnv)
    & $serviceExe start
    if ($LASTEXITCODE -ne 0) { throw "Could not start service $ServiceName" }

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

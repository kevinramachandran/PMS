param(
    [string]$InstallRoot = "C:\Brewery-PMS",
    [string]$ServiceName = "",
    [string]$ApplicationUrl = "",
    [int]$StartupTimeoutSeconds = 420
)

$ErrorActionPreference = "Stop"

function Write-StartupLog {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Message
    )

    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    Add-Content -Path $script:StartupLogFile -Value "[$timestamp] $Message" -Encoding UTF8
}

function Read-EnvFile {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    foreach ($line in [System.IO.File]::ReadAllLines($Path)) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith("#")) {
            continue
        }

        $separatorIndex = $trimmed.IndexOf("=")
        if ($separatorIndex -lt 1) {
            continue
        }

        $name = $trimmed.Substring(0, $separatorIndex).Trim()
        $value = $trimmed.Substring($separatorIndex + 1).Trim()

        if ($value.Length -ge 2) {
            $quotedWithDouble = $value.StartsWith('"') -and $value.EndsWith('"')
            $quotedWithSingle = $value.StartsWith("'") -and $value.EndsWith("'")
            if ($quotedWithDouble -or $quotedWithSingle) {
                $value = $value.Substring(1, $value.Length - 2)
            }
        }

        [System.Environment]::SetEnvironmentVariable($name, $value)
        Set-Item -Path "Env:$name" -Value $value
    }
}

function Repair-EnvFilePaths {
    param(
        [Parameter(Mandatory = $true)]
        [string]$EnvFile,
        [Parameter(Mandatory = $true)]
        [string]$RootDir
    )

    if (-not (Test-Path $EnvFile)) {
        return
    }

    $content = Get-Content -Path $EnvFile -Raw
    $updated = $content.Replace("C:\Brewery-PMS", $RootDir)
    if ($updated -ne $content) {
        Set-Content -Path $EnvFile -Value $updated -Encoding UTF8
        Write-StartupLog "Updated default C:\Brewery-PMS paths in $EnvFile to $RootDir"
    }
}

function Resolve-JavaPath {
    if ($env:JAVA_HOME) {
        $javaFromHome = Join-Path $env:JAVA_HOME "bin\java.exe"
        if (Test-Path $javaFromHome) {
            return $javaFromHome
        }
    }

    $javaCommand = Get-Command java -ErrorAction SilentlyContinue
    if ($null -ne $javaCommand) {
        return $javaCommand.Source
    }

    throw "Java 17 or newer was not found. Set JAVA_HOME or install a JDK/JRE that provides java.exe."
}

function Quote-ProcessArgument {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Value
    )

    return '"' + $Value.Replace('"', '\"') + '"'
}

function Get-DirectoryFileUri {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    $resolved = [System.IO.Path]::GetFullPath($Path)
    if (-not $resolved.EndsWith([System.IO.Path]::DirectorySeparatorChar)) {
        $resolved += [System.IO.Path]::DirectorySeparatorChar
    }

    return ([System.Uri]$resolved).AbsoluteUri
}

function Ensure-EnvFile {
    param(
        [Parameter(Mandatory = $true)]
        [string]$EnvFile,
        [Parameter(Mandatory = $true)]
        [string]$ExampleFile,
        [Parameter(Mandatory = $true)]
        [string]$RootDir
    )

    if (Test-Path $EnvFile) {
        return
    }

    if (Test-Path $ExampleFile) {
        $content = Get-Content -Path $ExampleFile -Raw
        $content = $content.Replace("C:\Brewery-PMS", $RootDir)
        Set-Content -Path $EnvFile -Value $content -Encoding UTF8
        return
    }

    $logFile = Join-Path (Join-Path $RootDir "logs") "brewery-pms.log"
    $uploadDir = Join-Path (Join-Path $RootDir "uploads") "footer-buttons"
    @"
# Auto-created by service\start.bat. Edit these values for the target VM.

SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=165
APP_TIMEZONE=UTC

DB_HOST=localhost
DB_PORT=3306
DB_NAME=brewery_pms
DB_USERNAME=root
DB_PASSWORD=Password@123

LOG_LEVEL_ROOT=INFO
LOG_LEVEL_WEB=INFO
LOG_LEVEL_SQL=WARN
JPA_SHOW_SQL=false
LOG_FILE=$logFile

UPLOAD_DIR=$uploadDir
APP_EMAIL_CONFIG_SECRET=change-this-to-a-long-random-value
APP_EMAIL_CONFIG_TEST_RATE_LIMIT_MS=5000
"@ | Set-Content -Path $EnvFile -Encoding UTF8
}

function Wait-ForApplication {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Url,
        [int]$TimeoutSeconds = 90
    )

    $spinner = @("|", "/", "-", "\")
    $spinnerIndex = 0
    $startedAt = Get-Date
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)

    while ((Get-Date) -lt $deadline) {
        $elapsed = [int]((Get-Date) - $startedAt).TotalSeconds
        $frame = $spinner[$spinnerIndex % $spinner.Length]
        Write-Host -NoNewline ("`rWaiting for application to start {0} {1}s" -f $frame, $elapsed)
        $spinnerIndex++

        try {
            $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 400) {
                Write-Host ("`rApplication started successfully in {0}s.           " -f $elapsed)
                return $true
            }
        } catch {}

        Start-Sleep -Milliseconds 500
    }

    Write-Host ("`rApplication startup timed out after {0}s.          " -f $TimeoutSeconds)
    return $false
}

function Test-DatabaseAuthenticationFailure {
    param([string[]]$LogPaths)

    foreach ($path in $LogPaths) {
        if (Test-Path $path) {
            $logText = Get-Content -Path $path -Raw -ErrorAction SilentlyContinue
            if ($logText -match "Access denied for user .*using password") {
                return $true
            }
        }
    }

    return $false
}

$rootDir = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$configDir = Join-Path $rootDir "config"
$envFile = Join-Path $configDir "brewery-pms.env"
$envExampleFile = Join-Path $configDir "brewery-pms.env.example"
$logsDir = Join-Path $rootDir "logs"
$script:StartupLogFile = Join-Path $logsDir "startup.log"

New-Item -ItemType Directory -Force -Path $configDir | Out-Null
New-Item -ItemType Directory -Force -Path $logsDir | Out-Null
Write-StartupLog "Start button launched from $PSScriptRoot"
Write-StartupLog "Resolved application root as $rootDir"
Ensure-EnvFile -EnvFile $envFile -ExampleFile $envExampleFile -RootDir $rootDir
Repair-EnvFilePaths -EnvFile $envFile -RootDir $rootDir

if (Test-Path $envFile) {
    Read-EnvFile -Path $envFile
    Write-StartupLog "Loaded environment file $envFile"
}

. (Join-Path $PSScriptRoot "port-config.ps1")
$port = Get-PmsPort -EnvFile $envFile
$env:SERVER_PORT = [string]$port
if (-not $ApplicationUrl) {
    $ApplicationUrl = "http://localhost:$port"
}

. (Join-Path $PSScriptRoot "instance-config.ps1")
$ServiceName = Get-PmsServiceName -RootDir $rootDir -Override $ServiceName
$ownedService = Get-PmsOwnedService -RootDir $rootDir -Name $ServiceName
if ($ownedService -and $ownedService.State -ne 'Stopped') {
    Stop-Service -Name $ServiceName -ErrorAction Stop
    (Get-Service -Name $ServiceName).WaitForStatus('Stopped', [TimeSpan]::FromSeconds(30))
}
Stop-PmsDirectProcess -RootDir $rootDir
Assert-PmsPortAvailable -Port $port

# Start the current release only. Do not fall back to an older installed release.
$serviceExe = Join-Path (Join-Path $rootDir "service") "$ServiceName.exe"
$jarPath = Join-Path $rootDir "app\app.jar"
$installedService = Get-Service -Name $ServiceName -ErrorAction SilentlyContinue

if ((Test-Path $serviceExe) -and $installedService) {
    Write-Host "Starting using Windows Service..."
    Write-StartupLog "Starting Windows service using $serviceExe"
    & $serviceExe start 2>&1 | ForEach-Object { Write-StartupLog $_ }
} else {
    Write-Host "Windows Service is not installed. Starting PMS -4 directly..."
    Write-StartupLog "Windows service is not installed. Starting PMS -4 directly from $jarPath"

    if (-not (Test-Path $jarPath)) {
        throw "app.jar not found at $jarPath"
    }

    if (-not $env:LOG_FILE) {
        $env:LOG_FILE = Join-Path $logsDir "brewery-pms.log"
    }

    $javaExe = Resolve-JavaPath
    $stdoutLog = Join-Path $logsDir "java-stdout.log"
    $stderrLog = Join-Path $logsDir "java-stderr.log"
    Write-StartupLog "Using Java executable $javaExe"
    Write-StartupLog "Spring log file is $env:LOG_FILE"
    $configLocation = Get-DirectoryFileUri -Path $configDir
    $javaArguments = @(
        "-jar",
        (Quote-ProcessArgument -Value $jarPath),
        (Quote-ProcessArgument -Value "--spring.config.additional-location=$configLocation")
    )
    Start-Process -FilePath $javaExe -ArgumentList $javaArguments -WorkingDirectory $rootDir -WindowStyle Hidden -RedirectStandardOutput $stdoutLog -RedirectStandardError $stderrLog
}

# STEP 3: Wait for app to be ready

if (Wait-ForApplication -Url $ApplicationUrl -TimeoutSeconds $StartupTimeoutSeconds) {
    Start-Process $ApplicationUrl
    Write-Host "Opened browser at $ApplicationUrl"
    Write-StartupLog "Application is reachable. Opened browser at $ApplicationUrl"
} else {
    $logPaths = @($env:LOG_FILE, (Join-Path $logsDir "java-stderr.log")) | Where-Object { $_ }
    if (Test-DatabaseAuthenticationFailure -LogPaths $logPaths) {
        $message = "MySQL rejected the configured database login. Edit config\brewery-pms.env (DB_USERNAME and DB_PASSWORD) to match the MySQL account on this computer, then start PMS again."
        Write-Host "`n$message" -ForegroundColor Yellow
        Write-StartupLog $message
    } else {
        Write-Warning "App did not start within $StartupTimeoutSeconds seconds"
        Write-StartupLog "Application did not start within $StartupTimeoutSeconds seconds. Check $env:LOG_FILE and logs\java-stderr.log"
    }
}

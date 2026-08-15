param()

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
# Auto-created by the Brewery PMS service. Edit these values for the target VM.

SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8080
APP_TIMEZONE=UTC

DB_URL=jdbc:mysql://localhost:3306/brewery_pms?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
DB_USERNAME=root
DB_PASSWORD=Password@123
APP_INTERNAL_SIVA_PASSWORD=Siva@2026

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

$rootDir = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$configDir = Join-Path $rootDir "config"
$envFile = Join-Path $configDir "brewery-pms.env"
$envExampleFile = Join-Path $configDir "brewery-pms.env.example"
$appJar = Join-Path $rootDir "app\app.jar"
$logsDir = Join-Path $rootDir "logs"
$uploadsDir = Join-Path $rootDir "uploads\footer-buttons"
$script:StartupLogFile = Join-Path $logsDir "startup.log"

New-Item -ItemType Directory -Force -Path $configDir | Out-Null
New-Item -ItemType Directory -Force -Path $logsDir | Out-Null
Write-StartupLog "Windows service startup launched from $PSScriptRoot"
Write-StartupLog "Resolved application root as $rootDir"
Ensure-EnvFile -EnvFile $envFile -ExampleFile $envExampleFile -RootDir $rootDir
Repair-EnvFilePaths -EnvFile $envFile -RootDir $rootDir

if (Test-Path $envFile) {
    Read-EnvFile -Path $envFile
    Write-StartupLog "Loaded environment file $envFile"
}

if (-not (Test-Path $appJar)) {
    throw "Application JAR not found at $appJar"
}

New-Item -ItemType Directory -Force -Path $logsDir | Out-Null
New-Item -ItemType Directory -Force -Path $uploadsDir | Out-Null

if (-not $env:LOG_FILE) {
    $env:LOG_FILE = Join-Path $logsDir "brewery-pms.log"
}

if (-not $env:UPLOAD_DIR) {
    $env:UPLOAD_DIR = $uploadsDir
}

$javaExe = Resolve-JavaPath
$configLocation = Get-DirectoryFileUri -Path $configDir
Write-StartupLog "Using Java executable $javaExe"
Write-StartupLog "Spring log file is $env:LOG_FILE"
$javaArgs = @(
    "-jar"
    $appJar
    "--spring.config.additional-location=$configLocation"
)

& $javaExe @javaArgs
exit $LASTEXITCODE

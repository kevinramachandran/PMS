param()

$ErrorActionPreference = "Stop"

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

    throw "Java 21 was not found. Set JAVA_HOME or install a JDK/JRE that provides java.exe."
}

$rootDir = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$configDir = Join-Path $rootDir "config"
$envFile = Join-Path $configDir "brewery-pms.env"
$appJar = Join-Path $rootDir "app\app.jar"
$logsDir = Join-Path $rootDir "logs"
$uploadsDir = Join-Path $rootDir "uploads\footer-buttons"

if (Test-Path $envFile) {
    Read-EnvFile -Path $envFile
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
$javaArgs = @(
    "-jar"
    $appJar
    "--spring.config.additional-location=file:$configDir/"
)

& $javaExe @javaArgs
exit $LASTEXITCODE
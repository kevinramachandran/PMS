param([Parameter(Mandatory=$true)][string]$Root, [Parameter(Mandatory=$true)][string]$Metadata)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
Push-Location -LiteralPath $Root
$previousMetadata=$env:PMS_RELEASE_METADATA
try {
    $env:PMS_RELEASE_METADATA=$Metadata
    # Separate clean from build: never let clean race packaging tasks.
    & .\gradlew.bat clean --no-daemon
    if($LASTEXITCODE -ne 0){throw 'Gradle clean failed'}
    & .\gradlew.bat test bootJar pmsReleaseMetadata -I (Join-Path $PSScriptRoot 'metadata.gradle') --no-daemon --rerun-tasks
    if($LASTEXITCODE -ne 0){throw 'Gradle full tests/build failed'}
    $tests=@(Get-ChildItem -LiteralPath 'src/test/js' -Filter '*.test.cjs' | ForEach-Object {$_.FullName})
    if(!$tests.Count){throw 'No frontend regression tests found'}
    & node --test @tests
    if($LASTEXITCODE -ne 0){throw 'Frontend tests failed'}
} finally {$env:PMS_RELEASE_METADATA=$previousMetadata;Pop-Location}

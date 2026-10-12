param(
    [Parameter(Mandatory=$true)][ValidateSet('onprem','cloud')][string]$Variant,
    [Parameter(Mandatory=$true)][string]$Metadata,
    [Parameter(Mandatory=$true)][string]$Output,
    [Parameter(Mandatory=$true)][string]$Checks,
    [Parameter(Mandatory=$true)][ValidatePattern('^[a-f0-9]{40}$')][string]$Commit,
    [Parameter(Mandatory=$true)][ValidatePattern('^[A-Za-z0-9][A-Za-z0-9._-]{0,79}$')][string]$ReleaseId
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$build=Get-Content -LiteralPath $Metadata -Raw | ConvertFrom-Json
$baseline=Join-Path $PSScriptRoot ('package/'+$Variant)
$pinned=(Get-Content -LiteralPath (Join-Path $PSScriptRoot 'package-hashes.json') -Raw | ConvertFrom-Json).$Variant
$names=@('README.md','config/brewery-pms.env.example','service/brewery-pms.xml','service/install-service.ps1','service/instance-config.ps1','service/launch-app.bat','service/launch-app.ps1','service/port-config.ps1','service/remove-service.ps1','service/start-service.ps1','service/start.bat','service/stop-app.ps1','service/stop.bat')
function EntryHash($entry) {
    if(!$entry){throw 'Missing archive entry'}
    $stream=$entry.Open();$sha=[Security.Cryptography.SHA256]::Create()
    try {return [BitConverter]::ToString($sha.ComputeHash($stream)).Replace('-','').ToLowerInvariant()}
    finally {$stream.Dispose();$sha.Dispose()}
}
function EntryText($entry) {
    if(!$entry){throw 'Missing archive text entry'}
    $reader=New-Object IO.StreamReader($entry.Open())
    try {return $reader.ReadToEnd()} finally {$reader.Dispose()}
}
foreach($name in $names) {
    $file=Join-Path $baseline $name
    if((Get-FileHash -LiteralPath $file).Hash.ToLowerInvariant() -ne $pinned.$name){throw "Standard template changed: $Variant/$name"}
}
$actualNames=@(Get-ChildItem -LiteralPath $baseline -Recurse -File | ForEach-Object {$_.FullName.Substring($baseline.Length+1).Replace('\','/')})
if(@(Compare-Object $names $actualNames).Count){throw 'Unexpected package template files'}
$testCount=0;$skipped=0;$suites=@();$skippedNames=@()
$allowedSkips=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'allowed-test-skips.json') -Raw | ConvertFrom-Json
foreach($result in Get-ChildItem -LiteralPath $build.testResults -Filter 'TEST-*.xml') {
    [xml]$xml=Get-Content -LiteralPath $result.FullName -Raw
    $suite=$xml.testsuite
    if([int]$suite.failures -or [int]$suite.errors){throw "Failed tests: $($suite.name)"}
    $testCount += [int]$suite.tests;$skipped += [int]$suite.skipped
    $suites += [string]$suite.name
    if($suite.name -match '\.(CloudSyncServiceTest|DataSyncServiceTest)$' -and [int]$suite.tests -eq 0){throw 'Required sync tests are empty'}
    foreach($case in $xml.SelectNodes('//testcase[skipped]')) {
        $key=[string]$suite.name+'::'+[string]$case.name
        if($key -notin @($allowedSkips.PSObject.Properties.Name)){throw "Unreviewed skipped test: $key"}
        $skippedNames += $key
    }
}
if($testCount -le 0 -or 'org.example.service.CloudSyncServiceTest' -notin $suites -or 'org.example.service.DataSyncServiceTest' -notin $suites){throw 'Required full sync regression results missing'}
$pageSuite=if($Variant -eq 'cloud'){'org.example.service.CloudPagesTest'}else{'org.example.service.LocalPagesTest'}
if($pageSuite -notin $suites){throw 'Variant page-rendering tests missing'}
$bytecode=(& $build.javap -p -c -v -classpath $build.classes org.example.service.CloudSyncService) -join "`n"
if($LASTEXITCODE -ne 0){throw 'Cannot inspect compiled sync service'}
$execution=[regex]::Match($bytecode,'(?s)private void execute\(org\.example\.entity\.SyncConfiguration, java\.util\.List<java\.lang\.String>\);(.*?)\n  (?:private|public|protected|java|static)').Groups[1].Value
if(!$execution.Contains('applyJsonSnapshots') -or $execution -match 'importCsvForSync|processFiles|Files\.write' -or !$bytecode.Contains('/api/cloud-sync/data/')){throw 'Compiled normal sync flow is not the expected JSON/API implementation'}
$jar=[IO.Compression.ZipFile]::OpenRead($build.jar)
$resourceCount=0;$classCount=0
try {
    foreach($group in @(@{Root=$build.resources;Kind='resource'},@{Root=$build.classes;Kind='class'})) {
        foreach($file in Get-ChildItem -LiteralPath $group.Root -Recurse -File) {
            $relative=$file.FullName.Substring($group.Root.Length+1).Replace('\','/')
            if((EntryHash $jar.GetEntry('BOOT-INF/classes/'+$relative)) -ne (Get-FileHash -LiteralPath $file.FullName).Hash.ToLowerInvariant()){throw "JAR does not match build: $relative"}
            if($group.Kind -eq 'class'){$classCount++}else{$resourceCount++}
        }
    }
    foreach($entry in $jar.Entries | Where-Object {$_.FullName -match '^BOOT-INF/classes/.*\.class$' -and $_.FullName -notmatch '\$'}) {
        $source=$entry.FullName.Substring('BOOT-INF/classes/'.Length) -replace '\.class$','.java'
        if(!(Test-Path -LiteralPath (Join-Path $build.javaSources $source))){throw "Foreign class in JAR: $source"}
    }
    foreach($name in @('org/example/service/DataSyncService$JsonSyncExport.class','org/example/entity/SyncRecordMapping.class','org/example/entity/SyncPushRequest.class','static/js/mobile-responsive.js','static/css/mobile-responsive.css')) {
        if(!$jar.GetEntry('BOOT-INF/classes/'+$name)){throw "Required release feature missing: $name"}
    }
    if($Variant -eq 'cloud' -and !$jar.GetEntry('BOOT-INF/classes/static/css/report-layout.css')){throw 'Cloud register layout missing'}
    foreach($page in @('gemba-walk-config','gemba-kaizen-config','abnormality-reporting-config','process-confirmation-config')) {
        $html=EntryText $jar.GetEntry('BOOT-INF/classes/templates/'+$page+'.html')
        if(!$html.Contains('mobile-responsive.js')){throw "Mobile integration missing: $page"}
        if($Variant -eq 'cloud' -and (!$html.Contains('pms-mobile-scroll-only') -or !$html.Contains('report-layout.css'))){throw "Cloud register layout integration missing: $page"}
    }
} finally {$jar.Dispose()}
if(Test-Path -LiteralPath $Output){throw 'Refusing to overwrite an existing release archive'}
$jarHash=(Get-FileHash -LiteralPath $build.jar).Hash.ToLowerInvariant()
$readme=[IO.File]::ReadAllText((Join-Path $baseline 'README.md'))
$readme=[regex]::Replace($readme,'2026-10-12',[DateTime]::UtcNow.ToString('yyyy-MM-dd'))
$readme += "`n`n## Build identity`n`nRelease: $ReleaseId`nVariant: $Variant`nSource commit: $Commit`nApplication SHA-256: $jarHash`n"
$zip=[IO.Compression.ZipFile]::Open($Output,[IO.Compression.ZipArchiveMode]::Create)
try {
    foreach($name in $names) {
        if($name -eq 'README.md') {
            $readmeEntry=$zip.CreateEntry('README.md');$readmeWriter=New-Object IO.StreamWriter($readmeEntry.Open())
            try {$readmeWriter.Write($readme)} finally {$readmeWriter.Dispose()}
        } else {[IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,(Join-Path $baseline $name),$name,[IO.Compression.CompressionLevel]::Optimal)|Out-Null}
    }
    [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,$build.jar,'app/app.jar',[IO.Compression.CompressionLevel]::Optimal)|Out-Null
    $entry=$zip.CreateEntry('SHA256SUMS.txt');$writer=New-Object IO.StreamWriter($entry.Open())
    try {$writer.Write($jarHash+'  app/app.jar'+[Environment]::NewLine)} finally {$writer.Dispose()}
} finally {$zip.Dispose()}
$zip=[IO.Compression.ZipFile]::OpenRead($Output)
try {
    $expected=@($names)+@('app/app.jar','SHA256SUMS.txt')
    if($zip.Entries.Count -ne 15 -or @(Compare-Object $expected @($zip.Entries.FullName)).Count){throw 'Standard ZIP layout changed'}
    if((EntryHash $zip.GetEntry('app/app.jar')) -ne $jarHash){throw 'Packaged JAR checksum mismatch'}
    if((EntryText $zip.GetEntry('SHA256SUMS.txt')).Trim() -ne ($jarHash+'  app/app.jar')){throw 'SHA256SUMS mismatch'}
    foreach($name in $names | Where-Object {$_ -ne 'README.md'}) {if((EntryHash $zip.GetEntry($name)) -ne $pinned.$name){throw "Packaged standard file changed: $name"}}
    if((EntryText $zip.GetEntry('README.md')) -ne $readme){throw 'Build identity missing from package README'}
} finally {$zip.Dispose()}
@{variant=$Variant;jarSha256=$jarHash;javaTests=$testCount;javaSkipped=$skipped;skippedTests=$skippedNames;resources=$resourceCount;classes=$classCount;zipEntries=15;syncTransport='JSON/API';standardFiles='service/config unchanged'} | ConvertTo-Json | Set-Content -LiteralPath $Checks -Encoding UTF8
Write-Output "Verified ${Variant}: $testCount Java tests, $resourceCount resources, $classCount classes, 15 standard ZIP entries."

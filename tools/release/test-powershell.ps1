$ErrorActionPreference='Stop'
foreach($file in Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.ps1') {
    $tokens=$null;$errors=$null
    [Management.Automation.Language.Parser]::ParseFile($file.FullName,[ref]$tokens,[ref]$errors)|Out-Null
    if($errors.Count){throw "Invalid PowerShell in $($file.Name): $($errors -join ', ')"}
}
Write-Output 'Release PowerShell scripts parse successfully.'

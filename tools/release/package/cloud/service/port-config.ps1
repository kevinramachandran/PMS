function Get-PmsPort {
    param([string]$EnvFile)

    $value = $env:SERVER_PORT
    if ($EnvFile -and (Test-Path -LiteralPath $EnvFile)) {
        foreach ($line in [System.IO.File]::ReadAllLines($EnvFile)) {
            if ($line -match '^\s*SERVER_PORT\s*=(.*)$') {
                $value = $Matches[1].Trim().Trim('"').Trim("'")
            }
        }
    }
    if ([string]::IsNullOrWhiteSpace($value)) { $value = '165' }
    $port = 0
    if (-not [int]::TryParse($value, [ref]$port) -or $port -lt 1 -or $port -gt 65535) {
        throw "Invalid SERVER_PORT '$value'. Set a port from 1 to 65535 in config\brewery-pms.env."
    }
    return $port
}

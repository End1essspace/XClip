$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path "$PSScriptRoot\..").Path
Push-Location $repoRoot
try {
    .\gradlew.bat clean m7LargeDataGate --no-daemon
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    Write-Host "M7.3 evidence: app\build\reports\m7-large-data"
}
finally {
    Pop-Location
}

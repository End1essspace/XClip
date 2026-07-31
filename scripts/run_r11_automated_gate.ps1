param(
    [switch]$PackageMsi
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $repoRoot

$latestPointer = Join-Path $repoRoot "artifacts\r11\latest.txt"
if (Test-Path $latestPointer -PathType Leaf) {
    $evidenceDirectory = (Get-Content $latestPointer -Raw).Trim()
}

if ([string]::IsNullOrWhiteSpace($evidenceDirectory) -or -not (Test-Path $evidenceDirectory -PathType Container)) {
    & (Join-Path $PSScriptRoot "start_r11_manual_validation.ps1")
    $evidenceDirectory = (Get-Content $latestPointer -Raw).Trim()
}

$logPath = Join-Path $evidenceDirectory "R11_AUTOMATED_GATE.txt"
$passMarker = Join-Path $evidenceDirectory "automated-gate.pass"
Remove-Item $passMarker -Force -ErrorAction SilentlyContinue

Start-Transcript -Path $logPath -Force | Out-Null
try {
    Write-Host "R11 evidence: $evidenceDirectory"

    & .\gradlew.bat clean r11AutomatedGate build --no-daemon
    if ($LASTEXITCODE -ne 0) { throw "Gradle R11 gate failed with exit code $LASTEXITCODE." }

    & git diff --check
    if ($LASTEXITCODE -ne 0) { throw "git diff --check failed with exit code $LASTEXITCODE." }

    if ($PackageMsi) {
        & .\gradlew.bat packageMsi --no-daemon
        if ($LASTEXITCODE -ne 0) { throw "MSI packaging failed with exit code $LASTEXITCODE." }

        $msi = Get-ChildItem -Path (Join-Path $repoRoot "app\build\installer") -Filter *.msi -File -Recurse |
            Sort-Object LastWriteTimeUtc -Descending |
            Select-Object -First 1
        if ($null -eq $msi) { throw "No MSI was produced under app\build\installer." }

        $hash = (Get-FileHash -Path $msi.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        @(
            "Path=$($msi.FullName)"
            "Size=$($msi.Length)"
            "SHA256=$hash"
            "ValidatedAt=$(Get-Date -Format o)"
        ) | Set-Content -Path (Join-Path $evidenceDirectory "package-msi.txt") -Encoding UTF8
    }

    @(
        "R11_AUTOMATED_GATE_OK"
        "CompletedAt=$(Get-Date -Format o)"
        "Commit=$((& git rev-parse HEAD).Trim())"
        "PackageMsi=$PackageMsi"
    ) | Set-Content -Path $passMarker -Encoding UTF8

    Write-Host "R11_AUTOMATED_GATE_OK"
} finally {
    Stop-Transcript | Out-Null
}

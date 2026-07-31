param()

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $repoRoot

$latestPointer = Join-Path $repoRoot "artifacts\r11\latest.txt"
if (-not (Test-Path $latestPointer -PathType Leaf)) {
    throw "No R11 evidence run found. Run scripts\start_r11_manual_validation.ps1 first."
}

$evidenceDirectory = (Get-Content $latestPointer -Raw).Trim()
if (-not (Test-Path $evidenceDirectory -PathType Container)) {
    throw "R11 evidence directory does not exist: $evidenceDirectory"
}

$matrixPath = Join-Path $repoRoot "docs\R11_REGRESSION_MATRIX.csv"
$screenshotSetPath = Join-Path $repoRoot "docs\R11_SCREENSHOT_SET.csv"
$resultPath = Join-Path $evidenceDirectory "R11_REGRESSION_RESULTS.csv"
$screenshotDirectory = Join-Path $evidenceDirectory "screenshots"

foreach ($requiredFile in @(
    $matrixPath,
    $screenshotSetPath,
    $resultPath,
    (Join-Path $evidenceDirectory "environment.txt"),
    (Join-Path $evidenceDirectory "R11_AUTOMATED_GATE.txt"),
    (Join-Path $evidenceDirectory "automated-gate.pass"),
    (Join-Path $evidenceDirectory "package-msi.txt")
)) {
    if (-not (Test-Path $requiredFile -PathType Leaf)) {
        throw "Missing R11 evidence file: $requiredFile"
    }
}

$canonical = Import-Csv $matrixPath
$results = Import-Csv $resultPath
if ($canonical.Count -ne 38) { throw "Canonical matrix must contain 38 rows." }
if ($results.Count -ne 38) { throw "Results must contain 38 rows, found $($results.Count)." }

$canonicalIds = @($canonical | ForEach-Object { $_.Id })
$resultIds = @($results | ForEach-Object { $_.Id })
if (($canonicalIds -join "|") -ne ($resultIds -join "|")) {
    throw "Result IDs or ordering do not match the canonical R11 matrix."
}

$failed = @($results | Where-Object { $_.Status.Trim().ToUpperInvariant() -ne "PASS" })
if ($failed.Count -gt 0) {
    $ids = ($failed | ForEach-Object { "$($_.Id)=$($_.Status)" }) -join ", "
    throw "All R11 rows must be PASS. Remaining: $ids"
}

$requiredScreenshots = Import-Csv $screenshotSetPath
foreach ($shot in $requiredScreenshots) {
    $path = Join-Path $screenshotDirectory $shot.Filename
    if (-not (Test-Path $path -PathType Leaf)) {
        throw "Missing required screenshot: $path"
    }
    if ((Get-Item $path).Length -le 0) {
        throw "Empty screenshot file: $path"
    }
}

$packageMetadata = Get-Content (Join-Path $evidenceDirectory "package-msi.txt") -Raw
if ($packageMetadata -notmatch '(?m)^SHA256=[0-9a-f]{64}$') {
    throw "package-msi.txt does not contain a valid lowercase SHA-256."
}

Write-Host "R11_EVIDENCE_OK: $evidenceDirectory"
Write-Host "Cases: 38 PASS"
Write-Host "Screenshots: $($requiredScreenshots.Count)"

param(
    [Parameter(Mandatory = $true)]
    [string]$EvidenceDirectory
)

$ErrorActionPreference = "Stop"
$directory = (Resolve-Path $EvidenceDirectory).Path
$requiredFiles = @(
    "results.csv",
    "environment.txt",
    "matrix-reference.csv",
    "instructions.md"
)
foreach ($requiredFile in $requiredFiles) {
    $requiredPath = Join-Path $directory $requiredFile
    if (-not (Test-Path $requiredPath -PathType Leaf)) {
        throw "Missing M8 evidence file: $requiredPath"
    }
}

$resultsPath = Join-Path $directory "results.csv"

$rows = @(Import-Csv $resultsPath)
if ($rows.Count -ne 18) { throw "Expected 18 M8 cases, found $($rows.Count)" }

$expected = 1..18 | ForEach-Object { "M8-{0:D3}" -f $_ }
$actual = @($rows | ForEach-Object { $_.CaseId })
if (($actual -join "|") -ne ($expected -join "|")) { throw "M8 case IDs are missing, duplicated, or out of order" }

$failed = @($rows | Where-Object { ([string]$_.Status).Trim().ToUpperInvariant() -ne "PASS" })
if ($failed.Count -gt 0) { throw "M8 cases not marked PASS: $($failed.CaseId -join ', ')" }

$missingNotes = @($rows | Where-Object { [string]::IsNullOrWhiteSpace($_.Notes) -and [string]::IsNullOrWhiteSpace($_.Evidence) })
if ($missingNotes.Count -gt 0) { throw "M8 cases need Notes or Evidence: $($missingNotes.CaseId -join ', ')" }

$pass = Join-Path $directory "PASS.txt"
@(
    "M8_WINDOWS_LIFECYCLE_PASS"
    "ValidatedAt=$(Get-Date -Format o)"
    "Cases=18"
) | Set-Content -Path $pass -Encoding UTF8

Write-Host "M8_WINDOWS_LIFECYCLE_EVIDENCE_OK=$directory"

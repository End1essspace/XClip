param()

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $repoRoot

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$evidenceDirectory = Join-Path $repoRoot "artifacts\r11\$stamp"
$screenshotDirectory = Join-Path $evidenceDirectory "screenshots"
$matrixPath = Join-Path $repoRoot "docs\R11_REGRESSION_MATRIX.csv"
$resultPath = Join-Path $evidenceDirectory "R11_REGRESSION_RESULTS.csv"
$metadataPath = Join-Path $evidenceDirectory "environment.txt"

if (-not (Test-Path $matrixPath -PathType Leaf)) {
    throw "Missing canonical R11 matrix: $matrixPath"
}

New-Item -ItemType Directory -Path $screenshotDirectory -Force | Out-Null

$rows = Import-Csv $matrixPath
if ($rows.Count -ne 38) {
    throw "Expected 38 R11 regression rows, found $($rows.Count)."
}

$rows | ForEach-Object {
    [pscustomobject]@{
        Id = $_.Id
        Area = $_.Area
        Scenario = $_.Scenario
        Expected = $_.Expected
        RequiredEvidence = $_.RequiredEvidence
        Status = "PENDING"
        Notes = ""
        Evidence = ""
    }
} | Export-Csv -Path $resultPath -NoTypeInformation -Encoding UTF8

$commit = (& git rev-parse HEAD 2>$null)
$branch = (& git branch --show-current 2>$null)
$javaCommand = Get-Command java -ErrorAction SilentlyContinue
if ($null -eq $javaCommand) {
    $javaVersion = "java command not found on PATH"
} else {
    try {
        # Java 17+ writes --version to stdout. Using -version would write to
        # stderr and PowerShell 5.1 can promote that output to NativeCommandError
        # while $ErrorActionPreference is Stop.
        $javaVersion = (& $javaCommand.Source --version | Out-String).Trim()
    } catch {
        $javaVersion = "Unable to query Java version: $($_.Exception.Message)"
    }
}
$os = Get-CimInstance Win32_OperatingSystem
$video = Get-CimInstance Win32_VideoController | Select-Object Name, CurrentHorizontalResolution, CurrentVerticalResolution, CurrentRefreshRate

@(
    "R11 validation started: $(Get-Date -Format o)"
    "Repository: $repoRoot"
    "Commit: $commit"
    "Branch: $branch"
    "Windows: $($os.Caption) $($os.Version) build $($os.BuildNumber)"
    "Java:"
    $javaVersion
    "Displays:"
    ($video | Format-Table -AutoSize | Out-String).TrimEnd()
) | Set-Content -Path $metadataPath -Encoding UTF8

Set-Content -Path (Join-Path $repoRoot "artifacts\r11\latest.txt") -Value $evidenceDirectory -Encoding UTF8

Write-Host "R11_MANUAL_VALIDATION_STARTED: $evidenceDirectory"
Write-Host "Results: $resultPath"
Write-Host "Screenshots: $screenshotDirectory"

param(
    [string]$EvidenceRoot = "$env:USERPROFILE\Desktop"
)

$ErrorActionPreference = "Stop"
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$evidence = Join-Path $EvidenceRoot "XClip_M8_WINDOWS_LIFECYCLE_$stamp"
New-Item -ItemType Directory -Path $evidence -Force | Out-Null

$matrixPath = Join-Path $PSScriptRoot "..\docs\M8_WINDOWS_LIFECYCLE_MATRIX.csv"
$rows = Import-Csv $matrixPath | ForEach-Object {
    [pscustomobject]@{
        CaseId = $_.CaseId
        Scenario = $_.Scenario
        Status = "PENDING"
        Notes = ""
        Evidence = ""
    }
}
$results = Join-Path $evidence "results.csv"
$rows | Export-Csv -Path $results -NoTypeInformation -Encoding UTF8

$environment = Join-Path $evidence "environment.txt"
@(
    "CreatedAt=$(Get-Date -Format o)"
    "ComputerName=$env:COMPUTERNAME"
    "UserName=$env:USERNAME"
    "Windows=$([System.Environment]::OSVersion.VersionString)"
    "PowerShell=$($PSVersionTable.PSVersion)"
    "Repository=$((Resolve-Path (Join-Path $PSScriptRoot '..')).Path)"
) | Set-Content -Path $environment -Encoding UTF8

Copy-Item -Path $matrixPath -Destination (Join-Path $evidence "matrix-reference.csv")
Copy-Item -Path (Join-Path $PSScriptRoot "..\docs\M8_WINDOWS_LIFECYCLE.md") -Destination (Join-Path $evidence "instructions.md")

Write-Host "M8_EVIDENCE_DIRECTORY=$evidence"
Write-Host "Edit results.csv after each packaged lifecycle case."

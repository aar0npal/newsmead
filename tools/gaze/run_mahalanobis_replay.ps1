<#
.SYNOPSIS
Replay a baseline and recorded RDF session with the shared P90/P95/P99 core.
.DESCRIPTION
Writes updates/episodes CSV files and replay_summary.json. Requires an explicit
baseline_profile.config record and baseline_vector records. This exports model
decisions only; independent labels and held-out evaluation remain separate work.
.EXAMPLE
./tools/gaze/run_mahalanobis_replay.ps1 -BaselineLog baseline.jsonl -InputLog reading.jsonl -OutputDirectory output/replay
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$BaselineLog,
    [Parameter(Mandatory = $true)][string]$InputLog,
    [Parameter(Mandatory = $true)][string]$OutputDirectory
)
$ErrorActionPreference = 'Stop'
$baselinePath = (Resolve-Path -LiteralPath $BaselineLog).Path
$inputPath = (Resolve-Path -LiteralPath $InputLog).Path
if (-not (Test-Path -LiteralPath $baselinePath -PathType Leaf)) { throw 'BaselineLog must be a file.' }
if (-not (Test-Path -LiteralPath $inputPath -PathType Leaf)) { throw 'InputLog must be a file.' }
$outputPath = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutputDirectory)
$repositoryPath = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$gradlePath = Join-Path $repositoryPath 'gradlew.bat'
Push-Location -LiteralPath $repositoryPath
try {
    & $gradlePath --offline :app:mahalanobisReplay "-PbaselineLog=$baselinePath" "-PinputLog=$inputPath" "-PreplayOutput=$outputPath"
    if ($LASTEXITCODE -ne 0) { throw "Replay failed with exit code $LASTEXITCODE" }
} finally {
    Pop-Location
}

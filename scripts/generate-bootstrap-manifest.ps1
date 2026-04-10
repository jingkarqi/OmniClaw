[CmdletBinding()]
param(
    [string]$BootstrapDirectory = "runtime/payloads/src/main/assets/bootstrap",
    [string]$RootFsFileName = "debian-rootfs.tar.xz",
    [string]$RuntimeArchiveFileName = "openclaw-2026.3.13.tgz"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$bootstrapPath = Join-Path $repoRoot $BootstrapDirectory

$requiredFiles = @($RootFsFileName, $RuntimeArchiveFileName)
$payloadEntries = foreach ($fileName in $requiredFiles) {
    $filePath = Join-Path $bootstrapPath $fileName
    if (-not (Test-Path -LiteralPath $filePath)) {
        throw "Missing required payload file: $filePath"
    }

    $fileInfo = Get-Item -LiteralPath $filePath
    $hash = Get-FileHash -LiteralPath $filePath -Algorithm SHA256

    [PSCustomObject]@{
        fileName = $fileInfo.Name
        sha256 = $hash.Hash.ToLowerInvariant()
        sizeBytes = $fileInfo.Length
    }
}

$manifest = [PSCustomObject]@{
    schemaVersion = 1
    payloads = $payloadEntries
}

$manifestPath = Join-Path $bootstrapPath "manifest.json"
$manifest | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $manifestPath

Write-Host "Generated $manifestPath"

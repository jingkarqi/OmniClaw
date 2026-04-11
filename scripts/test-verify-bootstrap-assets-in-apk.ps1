[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$scriptPath = Join-Path $repoRoot "scripts/verify-bootstrap-assets-in-apk.ps1"

$requiredPayloads = @(
    @{
        fileName = "debian-rootfs.tar.xz"
        content = "rootfs-payload"
    },
    @{
        fileName = "openclaw-2026.3.13.tgz"
        content = "runtime-payload"
    }
)

function Get-Sha256Hex {
    param(
        [Parameter(Mandatory = $true)]
        [byte[]]$Bytes
    )

    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try {
        $hashBytes = $sha256.ComputeHash($Bytes)
    }
    finally {
        $sha256.Dispose()
    }

    return ([System.BitConverter]::ToString($hashBytes).Replace("-", "").ToLowerInvariant())
}

function New-TestApk {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,

        [switch]$CorruptManifestSize,

        [switch]$CorruptPayloadContent
    )

    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem

    $manifestPayloads = foreach ($payload in $requiredPayloads) {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($payload.content)
        [PSCustomObject]@{
            fileName = $payload.fileName
            sha256 = Get-Sha256Hex -Bytes $bytes
            sizeBytes = $bytes.LongLength
        }
    }

    if ($CorruptManifestSize) {
        $manifestPayloads[0].sizeBytes++
    }

    $manifest = [PSCustomObject]@{
        schemaVersion = 1
        payloads = $manifestPayloads
    }
    $manifestJson = $manifest | ConvertTo-Json -Depth 4

    if (Test-Path -LiteralPath $Path) {
        Remove-Item -LiteralPath $Path -Force
    }

    $zipArchive = [System.IO.Compression.ZipFile]::Open(
        $Path,
        [System.IO.Compression.ZipArchiveMode]::Create
    )

    try {
        $manifestEntry = $zipArchive.CreateEntry("assets/bootstrap/manifest.json")
        $manifestWriter = [System.IO.StreamWriter]::new(
            $manifestEntry.Open(),
            [System.Text.UTF8Encoding]::new($false)
        )
        try {
            $manifestWriter.Write($manifestJson)
        }
        finally {
            $manifestWriter.Dispose()
        }

        foreach ($payload in $requiredPayloads) {
            $entry = $zipArchive.CreateEntry("assets/bootstrap/$($payload.fileName)")
            $entryStream = $entry.Open()
            try {
                $content = if ($CorruptPayloadContent -and $payload.fileName -eq "openclaw-2026.3.13.tgz") {
                    "runtime-payloae"
                }
                else {
                    $payload.content
                }

                $bytes = [System.Text.Encoding]::UTF8.GetBytes($content)
                $entryStream.Write($bytes, 0, $bytes.Length)
            }
            finally {
                $entryStream.Dispose()
            }
        }
    }
    finally {
        $zipArchive.Dispose()
    }
}

function Assert-ExitCodeZero {
    param(
        [Parameter(Mandatory = $true)]
        [int]$ExitCode,

        [Parameter(Mandatory = $true)]
        [string]$Scenario
    )

    if ($ExitCode -ne 0) {
        throw "$Scenario failed unexpectedly with exit code $ExitCode."
    }
}

function Assert-ExitCodeNonZero {
    param(
        [Parameter(Mandatory = $true)]
        [int]$ExitCode,

        [Parameter(Mandatory = $true)]
        [string]$Scenario
    )

    if ($ExitCode -eq 0) {
        throw "$Scenario was expected to fail but exited with code 0."
    }
}

$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("verify-bootstrap-assets-" + [System.Guid]::NewGuid().ToString("N"))
[System.IO.Directory]::CreateDirectory($tempRoot) | Out-Null

try {
    $validApkPath = Join-Path $tempRoot "valid.apk"
    New-TestApk -Path $validApkPath
    & pwsh -NoProfile -File $scriptPath -ApkPath $validApkPath
    Assert-ExitCodeZero -ExitCode $LASTEXITCODE -Scenario "Valid APK verification"

    $invalidSizeApkPath = Join-Path $tempRoot "invalid-size.apk"
    New-TestApk -Path $invalidSizeApkPath -CorruptManifestSize
    & pwsh -NoProfile -File $scriptPath -ApkPath $invalidSizeApkPath 2>$null
    Assert-ExitCodeNonZero -ExitCode $LASTEXITCODE -Scenario "Manifest size mismatch verification"

    $invalidHashApkPath = Join-Path $tempRoot "invalid-hash.apk"
    New-TestApk -Path $invalidHashApkPath -CorruptPayloadContent
    & pwsh -NoProfile -File $scriptPath -ApkPath $invalidHashApkPath 2>$null
    Assert-ExitCodeNonZero -ExitCode $LASTEXITCODE -Scenario "Payload hash mismatch verification"

    Write-Host "All APK bootstrap asset verification scenarios passed."
}
finally {
    if (Test-Path -LiteralPath $tempRoot) {
        Remove-Item -LiteralPath $tempRoot -Recurse -Force
    }
}

[CmdletBinding()]
param(
    [string]$ApkPath = "app/build/outputs/apk/debug/app-debug.apk"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$requiredPayloadFileNames = @(
    "debian-rootfs.tar.xz",
    "openclaw-2026.3.13.tgz"
)
$manifestEntryPath = "assets/bootstrap/manifest.json"
$supportedSchemaVersion = 1
$sha256Pattern = "^[0-9a-f]{64}$"

function Resolve-ApkLiteralPath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    $candidatePath = if ([System.IO.Path]::IsPathRooted($Path)) {
        $Path
    }
    else {
        Join-Path $repoRoot $Path
    }

    if (-not (Test-Path -LiteralPath $candidatePath -PathType Leaf)) {
        throw "APK file was not found: $candidatePath"
    }

    return (Resolve-Path -LiteralPath $candidatePath).Path
}

function Get-RequiredZipEntry {
    param(
        [Parameter(Mandatory = $true)]
        [System.IO.Compression.ZipArchive]$Archive,

        [Parameter(Mandatory = $true)]
        [string]$EntryPath
    )

    $entry = $Archive.GetEntry($EntryPath)
    if ($null -eq $entry) {
        throw "Required APK entry is missing: $EntryPath"
    }

    return $entry
}

function Read-ZipEntryText {
    param(
        [Parameter(Mandatory = $true)]
        [System.IO.Compression.ZipArchiveEntry]$Entry
    )

    $stream = $Entry.Open()
    try {
        $reader = [System.IO.StreamReader]::new(
            $stream,
            [System.Text.UTF8Encoding]::new($false),
            $true
        )
        try {
            return $reader.ReadToEnd()
        }
        finally {
            $reader.Dispose()
        }
    }
    finally {
        $stream.Dispose()
    }
}

function Get-StreamSha256Hex {
    param(
        [Parameter(Mandatory = $true)]
        [System.IO.Stream]$Stream
    )

    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try {
        $hashBytes = $sha256.ComputeHash($Stream)
    }
    finally {
        $sha256.Dispose()
    }

    return ([System.BitConverter]::ToString($hashBytes).Replace("-", "").ToLowerInvariant())
}

function Get-ManifestPayloadIndex {
    param(
        [Parameter(Mandatory = $true)]
        [hashtable]$Manifest
    )

    if (-not $Manifest.ContainsKey("schemaVersion")) {
        throw "Bootstrap manifest is missing schemaVersion."
    }
    if ([int]$Manifest["schemaVersion"] -ne $supportedSchemaVersion) {
        throw "Bootstrap manifest schema version $($Manifest["schemaVersion"]) is not supported."
    }
    if (-not $Manifest.ContainsKey("payloads") -or $null -eq $Manifest["payloads"]) {
        throw "Bootstrap manifest is missing payloads."
    }

    $payloadEntries = @($Manifest["payloads"])
    if ($payloadEntries.Count -eq 0) {
        throw "Bootstrap manifest must declare payload entries."
    }

    $payloadIndex = @{}
    foreach ($payloadEntry in $payloadEntries) {
        if ($null -eq $payloadEntry) {
            throw "Bootstrap manifest contains a null payload entry."
        }
        if (-not $payloadEntry.ContainsKey("fileName") -or [string]::IsNullOrWhiteSpace([string]$payloadEntry["fileName"])) {
            throw "Bootstrap manifest contains a payload entry without fileName."
        }

        $fileName = [string]$payloadEntry["fileName"]
        if ($payloadIndex.ContainsKey($fileName)) {
            throw "Bootstrap manifest contains duplicate payload entries for $fileName."
        }

        $payloadIndex[$fileName] = $payloadEntry
    }

    return $payloadIndex
}

function Test-ManifestPayloadEntry {
    param(
        [Parameter(Mandatory = $true)]
        [System.IO.Compression.ZipArchive]$Archive,

        [Parameter(Mandatory = $true)]
        [hashtable]$PayloadEntry,

        [Parameter(Mandatory = $true)]
        [string]$FileName
    )

    if (-not $PayloadEntry.ContainsKey("sizeBytes")) {
        throw "Bootstrap manifest entry for $FileName is missing sizeBytes."
    }
    if (-not $PayloadEntry.ContainsKey("sha256")) {
        throw "Bootstrap manifest entry for $FileName is missing sha256."
    }

    $expectedSize = [long]$PayloadEntry["sizeBytes"]
    if ($expectedSize -le 0) {
        throw "Bootstrap manifest entry for $FileName must declare a positive sizeBytes."
    }

    $expectedSha256 = ([string]$PayloadEntry["sha256"]).ToLowerInvariant()
    if ($expectedSha256 -notmatch $sha256Pattern) {
        throw "Bootstrap manifest entry for $FileName has an invalid sha256 digest."
    }

    $assetEntryPath = "assets/bootstrap/$FileName"
    $assetEntry = Get-RequiredZipEntry -Archive $Archive -EntryPath $assetEntryPath

    if ($assetEntry.Length -ne $expectedSize) {
        throw "Payload $FileName size mismatch: manifest=$expectedSize apk=$($assetEntry.Length)."
    }

    $assetStream = $assetEntry.Open()
    try {
        $actualSha256 = Get-StreamSha256Hex -Stream $assetStream
    }
    finally {
        $assetStream.Dispose()
    }

    if ($actualSha256 -ne $expectedSha256) {
        throw "Payload $FileName sha256 mismatch: manifest=$expectedSha256 apk=$actualSha256."
    }

    return [PSCustomObject]@{
        fileName = $FileName
        sizeBytes = $expectedSize
        sha256 = $actualSha256
    }
}

try {
    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem

    $resolvedApkPath = Resolve-ApkLiteralPath -Path $ApkPath
    $zipArchive = [System.IO.Compression.ZipFile]::OpenRead($resolvedApkPath)
    try {
        $manifestEntry = Get-RequiredZipEntry -Archive $zipArchive -EntryPath $manifestEntryPath
        $manifestText = Read-ZipEntryText -Entry $manifestEntry

        try {
            $manifest = ConvertFrom-Json -InputObject $manifestText -AsHashtable -Depth 8
        }
        catch {
            throw "Failed to parse bootstrap manifest at $manifestEntryPath."
        }

        $payloadIndex = Get-ManifestPayloadIndex -Manifest $manifest

        $verifiedPayloads = foreach ($requiredPayloadFileName in $requiredPayloadFileNames) {
            if (-not $payloadIndex.ContainsKey($requiredPayloadFileName)) {
                throw "Bootstrap manifest is missing payload entry for $requiredPayloadFileName."
            }

            Test-ManifestPayloadEntry `
                -Archive $zipArchive `
                -PayloadEntry $payloadIndex[$requiredPayloadFileName] `
                -FileName $requiredPayloadFileName
        }
    }
    finally {
        if ($null -ne $zipArchive) {
            $zipArchive.Dispose()
        }
    }

    Write-Host "Verified bootstrap assets in $resolvedApkPath"
    foreach ($verifiedPayload in $verifiedPayloads) {
        Write-Host "OK  $($verifiedPayload.fileName) sizeBytes=$($verifiedPayload.sizeBytes) sha256=$($verifiedPayload.sha256)"
    }
}
catch {
    $message = if ($_.Exception -and $_.Exception.Message) {
        $_.Exception.Message
    }
    else {
        $_.ToString()
    }

    Write-Error $message
    exit 1
}

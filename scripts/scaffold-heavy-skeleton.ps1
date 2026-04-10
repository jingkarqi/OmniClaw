[CmdletBinding()]
param(
    [switch]$Force
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $repoRoot

$modules = @(
    @{ ModulePath = ":core:common"; Dir = "core/common"; Namespace = "com.sora.omniclaw.core.common" },
    @{ ModulePath = ":core:model"; Dir = "core/model"; Namespace = "com.sora.omniclaw.core.model" },
    @{ ModulePath = ":core:storage"; Dir = "core/storage"; Namespace = "com.sora.omniclaw.core.storage" },
    @{ ModulePath = ":bridge:api"; Dir = "bridge/api"; Namespace = "com.sora.omniclaw.bridge.api" },
    @{ ModulePath = ":bridge:impl"; Dir = "bridge/impl"; Namespace = "com.sora.omniclaw.bridge.impl" },
    @{ ModulePath = ":runtime:api"; Dir = "runtime/api"; Namespace = "com.sora.omniclaw.runtime.api" },
    @{ ModulePath = ":runtime:impl"; Dir = "runtime/impl"; Namespace = "com.sora.omniclaw.runtime.impl" },
    @{ ModulePath = ":runtime:payloads"; Dir = "runtime/payloads"; Namespace = "com.sora.omniclaw.runtime.payloads" },
    @{ ModulePath = ":domain:runtime"; Dir = "domain/runtime"; Namespace = "com.sora.omniclaw.domain.runtime" },
    @{ ModulePath = ":domain:bridge"; Dir = "domain/bridge"; Namespace = "com.sora.omniclaw.domain.bridge" },
    @{ ModulePath = ":domain:provider"; Dir = "domain/provider"; Namespace = "com.sora.omniclaw.domain.provider" },
    @{ ModulePath = ":feature:home"; Dir = "feature/home"; Namespace = "com.sora.omniclaw.feature.home" },
    @{ ModulePath = ":feature:provider"; Dir = "feature/provider"; Namespace = "com.sora.omniclaw.feature.provider" },
    @{ ModulePath = ":feature:runtime"; Dir = "feature/runtime"; Namespace = "com.sora.omniclaw.feature.runtime" },
    @{ ModulePath = ":feature:permissions"; Dir = "feature/permissions"; Namespace = "com.sora.omniclaw.feature.permissions" },
    @{ ModulePath = ":service:host"; Dir = "service/host"; Namespace = "com.sora.omniclaw.service.host" },
    @{ ModulePath = ":testing:fake"; Dir = "testing/fake"; Namespace = "com.sora.omniclaw.testing.fake" }
)

function Write-TextFile {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,

        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [string]$Content
    )

    if ((Test-Path -LiteralPath $Path) -and -not $Force) {
        Write-Host "Skipping existing file: $Path"
        return
    }

    $parent = Split-Path -Parent $Path
    if ($parent) {
        New-Item -ItemType Directory -Path $parent -Force | Out-Null
    }

    Set-Content -LiteralPath $Path -Value $Content
    Write-Host "Wrote $Path"
}

foreach ($module in $modules) {
    $moduleRoot = Join-Path $repoRoot $module.Dir
    $manifestPath = Join-Path $moduleRoot "src/main/AndroidManifest.xml"
    $buildFilePath = Join-Path $moduleRoot "build.gradle.kts"

    $buildFileContent = @"
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "$($module.Namespace)"

    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 28
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
"@

    Write-TextFile -Path $buildFilePath -Content $buildFileContent
    Write-TextFile -Path $manifestPath -Content @'
<?xml version="1.0" encoding="utf-8"?>
<manifest />
'@
}

$payloadBootstrapDir = Join-Path $repoRoot "runtime/payloads/src/main/assets/bootstrap"
New-Item -ItemType Directory -Path $payloadBootstrapDir -Force | Out-Null
Write-TextFile -Path (Join-Path $payloadBootstrapDir ".gitkeep") -Content ""

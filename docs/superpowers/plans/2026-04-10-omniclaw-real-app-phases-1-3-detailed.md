# OmniClaw Real App Phases 1-3 Detailed Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Status update (2026-04-11):** Tasks 1-8 are complete in the repository. Task 9 remains open as the integration checkpoint for Phases 1-3, and Phase 4+ work remains open. The summary below is now the live status source; the detailed task checklists are preserved as implementation history and should not be used as the current baseline on their own.

**Goal:** Replace the current placeholder bootstrap inputs and stub runtime integration with a real runtime installation and startup path that consumes provider export metadata, materializes secret-bearing runtime config only at start time, and can support the first truly usable host workflow.

**Architecture:** Execute the work in three dependency-ordered layers. First, make the packaged payload inputs real and verifiable. Second, replace `StubRuntimeManager` with a real install/start/stop/observe implementation that owns filesystem layout, extraction, launch, and diagnostics. Third, persist non-secret provider export metadata and materialize secret-bearing `openclaw.json5` only during runtime start so a real runtime launch has valid inputs. Keep `app` and `feature:*` unchanged unless runtime or provider integration requires new surfaced state.

**Tech Stack:** Kotlin, Android app-private storage, packaged assets in `assets/bootstrap/`, foreground service, PowerShell manifest generation, Android Keystore, DataStore, coroutines and Flow, process and filesystem management in `runtime:impl`.

---

## Scope And Boundaries

- This plan covers only the path from packaged payloads to a real runtime start with real provider config.
- This plan does not include replacing the bridge stub with a full real UDS server.
- This plan does not redesign `feature:*` modules; UI changes should be limited to exposing newly available runtime state and errors only when required.
- This plan does not change the `assets/bootstrap/` contract introduced by the heavy skeleton.

## Completion Status

- [x] Task 1: real bootstrap archives replaced and APK-level asset verification added.
- [x] Task 2: runtime-side payload validation is implemented and covered by tests.
- [x] Task 3: runtime workspace layout and persisted install-state model are implemented.
- [x] Task 4: bootstrap extraction and installation are implemented, idempotent, and tested.
- [x] Task 5: `RealRuntimeManager`, runtime process launch, health checks, and log capture are wired into `app`.
- [x] Task 6: domain and foreground-service behavior now reflect real runtime install/start/stop/recovery semantics.
- [x] Task 7: non-secret `ProviderRuntimeExport` metadata and start-time `openclaw.json5` materialization are implemented.
- [x] Task 8: provider export/save wiring, readiness semantics, and `openclaw.json5` cleanup on stop or failure are implemented.
- [ ] Task 9: keep open for a later end-to-end integration checkpoint once the real local bridge work starts.

## Current Baseline Assumptions

- `runtime/payloads/src/main/assets/bootstrap/` exists and is already merged into the APK.
- [`scripts/generate-bootstrap-manifest.ps1`](/E:/GitHub/OmniClaw/scripts/generate-bootstrap-manifest.ps1) is the manifest-generation entry point.
- [`runtime/impl/src/main/java/com/sora/omniclaw/runtime/impl/AssetPayloadLocator.kt`](/E:/GitHub/OmniClaw/runtime/impl/src/main/java/com/sora/omniclaw/runtime/impl/AssetPayloadLocator.kt) is the only manifest lookup path.
- [`runtime/impl/src/main/java/com/sora/omniclaw/runtime/impl/RealRuntimeManager.kt`](/E:/GitHub/OmniClaw/runtime/impl/src/main/java/com/sora/omniclaw/runtime/impl/RealRuntimeManager.kt) is now the active runtime manager implementation.
- [`domain/provider`]( /E:/GitHub/OmniClaw/domain/provider ) and [`core/storage`]( /E:/GitHub/OmniClaw/core/storage ) now provide the saved draft, non-secret runtime-export metadata, and secret availability used by runtime start gating.

## Live Status Clarification

- `ProviderRuntimeExport` is a non-secret metadata record only; it does not carry the provider API key.
- `SecretStore` is read only when `RealRuntimeManager` materializes `runtime/config/openclaw.json5` during runtime start.
- Provider readiness and host start gating require both export-metadata readiness and secret availability.
- `openclaw.json5` is removed when runtime stop succeeds and when runtime start fails or aborts.
- Task 1-8 is complete; Task 9 remains open as a later integration checkpoint after more bridge work exists.

## File Structure Map

- `runtime/payloads/src/main/assets/bootstrap/`: fixed source of bundled bootstrap assets.
- `scripts/generate-bootstrap-manifest.ps1`: manifest generator for asset hashes and sizes.
- `runtime/api/src/main/java/com/sora/omniclaw/runtime/api/RuntimeManager.kt`: runtime contract surface; extend only if the real host path needs additional observable state or operations.
- `runtime/impl/src/main/java/com/sora/omniclaw/runtime/impl/AssetPayloadLocator.kt`: manifest reader; keep path stable and extend validation carefully.
- `runtime/impl/src/main/java/com/sora/omniclaw/runtime/impl/`: primary home for payload validation, extraction, directories, launcher, process state, diagnostics, and log collection.
- `domain/runtime/src/main/java/com/sora/omniclaw/domain/runtime/`: host lifecycle coordination; keep logic against runtime abstractions where possible.
- `domain/provider/src/main/java/com/sora/omniclaw/domain/provider/`: provider export orchestration and save/read flow adjustments.
- `core/storage/src/main/java/com/sora/omniclaw/core/storage/`: source of persisted config and secrets; extend only if runtime export needs new storage-level contracts.
- `service/host/src/main/java/com/sora/omniclaw/service/host/`: lifecycle shell; should consume the upgraded runtime behavior, not reimplement it.

## Phase 1: Real Payload Intake

### Task 1: Replace Placeholder Bootstrap Archives With Real Inputs

**Files:**
- Modify: `runtime/payloads/src/main/assets/bootstrap/debian-rootfs.tar.xz`
- Modify: `runtime/payloads/src/main/assets/bootstrap/openclaw-2026.3.13.tgz`
- Modify: `runtime/payloads/src/main/assets/bootstrap/manifest.json`
- Modify if needed: `scripts/generate-bootstrap-manifest.ps1`

- [ ] Acquire the exact rootfs bundle and runtime archive chosen for the app bootstrap contract.
- [ ] Confirm both files match the fixed file names already used by the app skeleton.
- [ ] Replace the placeholder archives in `runtime/payloads/src/main/assets/bootstrap/`.
- [ ] Re-run `.\scripts\generate-bootstrap-manifest.ps1`.
- [ ] Inspect the resulting `manifest.json` and confirm both entries have non-placeholder sizes and fresh SHA-256 hashes.
- [ ] Run `.\gradlew.bat clean :app:assembleDebug`.
- [ ] Inspect the built APK and confirm all three files are present under `assets/bootstrap/`.
- [ ] Commit the real bootstrap payload replacement separately from runtime-implementation code.

**Verification:**
- `.\scripts\generate-bootstrap-manifest.ps1`
- `.\gradlew.bat clean :app:assembleDebug`
- APK inspection for `assets/bootstrap/debian-rootfs.tar.xz`, `assets/bootstrap/openclaw-2026.3.13.tgz`, and `assets/bootstrap/manifest.json`
- Expected: the APK carries the real bootstrap assets and the manifest matches them.

### Task 2: Add Runtime-Side Payload Validation

**Files:**
- Create: `runtime/impl/src/main/java/com/sora/omniclaw/runtime/impl/BootstrapPayloadValidator.kt`
- Modify: `runtime/impl/src/main/java/com/sora/omniclaw/runtime/impl/AssetPayloadLocator.kt`
- Modify: `runtime/impl/src/main/java/com/sora/omniclaw/runtime/impl/StubRuntimeManager.kt` or replace it in later tasks
- Create: `runtime/impl/src/test/java/com/sora/omniclaw/runtime/impl/BootstrapPayloadValidatorTest.kt`

- [ ] Add a runtime-side validator that checks `manifest.json` presence, required filenames, non-empty entries, and hash or size consistency assumptions available at load time.
- [ ] Keep the validator separate from the manifest reader so asset-path logic and payload-validation logic do not collapse into one file.
- [ ] Return structured runtime errors for missing, malformed, or incomplete payload sets.
- [ ] Add unit tests for missing manifest, missing entry, duplicated entry, and malformed manifest cases.
- [ ] Run the focused runtime-impl test slice before moving on to extraction work.
- [ ] Commit payload validation as a separate step.

**Verification:**
- `.\gradlew.bat :runtime:impl:testDebugUnitTest`
- Expected: payload validation errors are explicit, deterministic, and covered by unit tests.

## Phase 2: Real Runtime Host Implementation

### Task 3: Define Runtime Workspace Layout And Install-State Model

**Files:**
- Create: `runtime/impl/src/main/java/com/sora/omniclaw/runtime/impl/RuntimeDirectories.kt`
- Create: `runtime/impl/src/main/java/com/sora/omniclaw/runtime/impl/RuntimeInstallState.kt`
- Create: `runtime/impl/src/main/java/com/sora/omniclaw/runtime/impl/RuntimeInstallStateStore.kt`
- Create: `runtime/impl/src/test/java/com/sora/omniclaw/runtime/impl/RuntimeDirectoriesTest.kt`
- Create: `runtime/impl/src/test/java/com/sora/omniclaw/runtime/impl/RuntimeInstallStateStoreTest.kt`

- [ ] Define a single authoritative workspace layout for payload staging, extracted rootfs, extracted runtime files, generated config, logs, and temp files.
- [ ] Define the persisted install-state model needed to distinguish “not installed,” “installing,” “installed,” “corrupt,” and “upgrade required.”
- [ ] Keep install state independent from UI-only state so service recreation can recover from disk.
- [ ] Add unit tests for path derivation and install-state transitions.
- [ ] Commit the workspace and install-state foundation before process-launch logic.

**Verification:**
- `.\gradlew.bat :runtime:impl:testDebugUnitTest`
- Expected: runtime workspace paths and persisted install states are deterministic and covered by tests.

### Task 4: Implement Bootstrap Extraction And Installation

**Files:**
- Create: `runtime/impl/src/main/java/com/sora/omniclaw/runtime/impl/BootstrapInstaller.kt`
- Create: `runtime/impl/src/main/java/com/sora/omniclaw/runtime/impl/ArchiveExtractor.kt`
- Modify: `runtime/impl/src/main/java/com/sora/omniclaw/runtime/impl/AssetPayloadLocator.kt`
- Modify: `runtime/impl/src/main/java/com/sora/omniclaw/runtime/impl/StubRuntimeManager.kt` or replace with a real manager shell
- Create: `runtime/impl/src/test/java/com/sora/omniclaw/runtime/impl/BootstrapInstallerTest.kt`

- [ ] Implement extraction of the two bundled payloads into the runtime workspace using a single installation entry point.
- [ ] Ensure installation is idempotent and can detect partially extracted state.
- [ ] Record install progress and terminal state in the persisted install-state store.
- [ ] Fail fast on missing assets, extraction errors, and mismatched target layout.
- [ ] Add tests for clean install, re-install on existing valid state, and partial-install recovery.
- [ ] Commit extraction and installation separately from launch behavior.

**Verification:**
- `.\gradlew.bat :runtime:impl:testDebugUnitTest`
- Manual install-path verification through app-private storage on a debug build
- Expected: the runtime workspace can be populated from bundled assets in a controlled, repeatable way.

### Task 5: Replace `StubRuntimeManager` With A Real Lifecycle Manager

**Files:**
- Modify or replace: `runtime/impl/src/main/java/com/sora/omniclaw/runtime/impl/StubRuntimeManager.kt`
- Create: `runtime/impl/src/main/java/com/sora/omniclaw/runtime/impl/RealRuntimeManager.kt`
- Create: `runtime/impl/src/main/java/com/sora/omniclaw/runtime/impl/RuntimeProcessLauncher.kt`
- Create: `runtime/impl/src/main/java/com/sora/omniclaw/runtime/impl/RuntimeHealthChecker.kt`
- Create: `runtime/impl/src/main/java/com/sora/omniclaw/runtime/impl/RuntimeLogCollector.kt`
- Create: `runtime/impl/src/test/java/com/sora/omniclaw/runtime/impl/RealRuntimeManagerTest.kt`

- [ ] Swap the app graph from the stub manager to the new real manager only after the real manager can install, start, stop, and observe runtime state.
- [ ] Implement real start behavior: ensure installed assets exist, ensure generated config exists, launch the runtime process, and update observable state.
- [ ] Implement real stop behavior: attempt graceful stop, handle timeout, and cleanly update persisted and observable state.
- [ ] Implement health checks that can distinguish “starting,” “running,” “stopped,” and “error” using real process or filesystem evidence.
- [ ] Collect recent logs or boot output so diagnostics and UI can expose failure reasons.
- [ ] Add tests for startup preconditions, install-before-start behavior, launch failure handling, and stop behavior.
- [ ] Commit the runtime-manager replacement as its own integration step.

**Verification:**
- `.\gradlew.bat :runtime:impl:testDebugUnitTest :domain:runtime:testDebugUnitTest :app:assembleDebug`
- Device or emulator smoke verification that start and stop now map to real runtime behavior rather than stub-only state changes
- Expected: the app can install and launch the real runtime host path.

### Task 6: Surface Real Runtime State Through Domain And Service

**Files:**
- Modify: `domain/runtime/src/main/java/com/sora/omniclaw/domain/runtime/ObserveHostOverviewUseCase.kt`
- Modify: `domain/runtime/src/main/java/com/sora/omniclaw/domain/runtime/StartHostUseCase.kt`
- Modify: `domain/runtime/src/main/java/com/sora/omniclaw/domain/runtime/StopHostUseCase.kt`
- Modify: `service/host/src/main/java/com/sora/omniclaw/service/host/HostForegroundService.kt`
- Modify: `app/src/main/java/com/sora/omniclaw/AppGraph.kt`
- Modify tests under `domain/runtime/src/test/` and `service/host/src/test/`

- [ ] Update runtime-domain logic to reflect the real install and launch semantics rather than the old stub assumptions.
- [ ] Ensure service restart paths preserve desired host state and correctly re-enter runtime start or recovery logic.
- [ ] Ensure the home overview exposes real install-required, configuration-invalid, degraded, and error cases from the real manager.
- [ ] Re-run service and domain tests after runtime-manager replacement.
- [ ] Commit the domain and service adaptation after the runtime manager itself is stable.

**Verification:**
- `.\gradlew.bat :service:host:testDebugUnitTest :domain:runtime:testDebugUnitTest :app:assembleDebug`
- Expected: foreground-service lifecycle and home-overview state reflect the real runtime path.

## Phase 3: Real Provider Config Export

> **Archival note:** The task checklists below are preserved from the pre-completion implementation plan. Use the completion summary and live-status clarification above for the current repository baseline.

### Task 7: Define Runtime-Facing Provider Export Metadata Contract

**Files:**
- Create: `runtime/impl/src/main/java/com/sora/omniclaw/runtime/impl/RuntimeProviderConfigWriter.kt`
- Create: `runtime/impl/src/main/java/com/sora/omniclaw/runtime/impl/RuntimeProviderConfig.kt`
- Modify if needed: `core/model/src/main/java/com/sora/omniclaw/core/model/ProviderConfigDraft.kt`
- Create: `runtime/impl/src/test/java/com/sora/omniclaw/runtime/impl/RuntimeProviderConfigWriterTest.kt`

- [ ] Define the exact `ProviderRuntimeExport` metadata shape and the runtime config file shape and target location inside the runtime workspace.
- [ ] Keep Android-side provider editing models separate from both the stored export metadata and the secret-bearing runtime config file.
- [ ] Ensure secret material is read from `SecretStore` only when materializing `openclaw.json5` during runtime start and is not reintroduced into long-lived UI state.
- [ ] Add tests for valid export, missing secret, missing required fields, and overwrite behavior.
- [ ] Commit the export contract before wiring it into runtime start.

**Verification:**
- `.\gradlew.bat :runtime:impl:testDebugUnitTest`
- Expected: non-secret export metadata is valid on save, and runtime config materialization succeeds only when export metadata plus secret availability are both present.

### Task 8: Wire Provider Export Metadata Into Save And Start Paths

**Files:**
- Modify: `domain/provider/src/main/java/com/sora/omniclaw/domain/provider/SaveProviderConfigUseCase.kt`
- Modify: `domain/provider/src/main/java/com/sora/omniclaw/domain/provider/ObserveProviderConfigUseCase.kt`
- Modify: `domain/runtime/src/main/java/com/sora/omniclaw/domain/runtime/StartHostUseCase.kt`
- Modify: `runtime/impl/src/main/java/com/sora/omniclaw/runtime/impl/RealRuntimeManager.kt`
- Modify tests under `domain/provider/src/test/`, `domain/runtime/src/test/`, and `runtime/impl/src/test/`

- [ ] Apply one consistent rule: write non-secret export metadata on successful save, then materialize `openclaw.json5` from export metadata plus `SecretStore` during runtime start.
- [ ] Ensure host startup refuses to proceed if export metadata is missing or invalid, or if the secret is unavailable, even when Android-side draft fields look complete.
- [ ] Ensure provider-ready UI state remains driven by export-metadata readiness plus secret availability.
- [ ] Add tests that distinguish storage success from metadata export failure, metadata export success from config-materialization failure, and config materialization success from runtime launch failure.
- [ ] Commit provider-export wiring separately from any later bridge work.

**Verification:**
- `.\gradlew.bat :domain:provider:testDebugUnitTest :domain:runtime:testDebugUnitTest :runtime:impl:testDebugUnitTest`
- Expected: provider save, provider observe, and host start all reflect metadata export readiness, secret availability, and start-time config materialization requirements.

### Task 9: End-To-End Verification For Phases 1-3

**Files:**
- Modify as needed: `app/src/main/java/com/sora/omniclaw/AppGraph.kt`
- Modify as needed: `app/src/main/java/com/sora/omniclaw/navigation/OmniClawNavHost.kt`
- Create if needed: `app/src/androidTest/java/com/sora/omniclaw/RealRuntimeBootstrapSmokeTest.kt`
- Update docs if required under `docs/superpowers/plans/` or runtime-related notes

- [ ] Build a smoke path that verifies: packaged assets present, install path succeeds, provider config saves, config export occurs, runtime start succeeds, runtime stop succeeds.
- [ ] Re-run the full app assembly plus all slices touched by phases 1-3.
- [ ] Confirm the APK still packages the runtime payload assets after all runtime-manager changes.
- [ ] Confirm the UI exposes meaningful state changes during install, launch, and configuration failure scenarios.
- [ ] Commit the phase-1-to-phase-3 integration checkpoint only after all verifications are green.

**Verification:**
- `.\scripts\generate-bootstrap-manifest.ps1`
- `.\gradlew.bat :service:host:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug :domain:provider:testDebugUnitTest :domain:runtime:testDebugUnitTest :runtime:impl:testDebugUnitTest`
- `.\gradlew.bat clean :app:assembleDebug`
- APK inspection for `assets/bootstrap/*`
- Expected: the host can package, install, configure, start, and stop a real runtime using real provider input.

## Execution Order

- [x] Complete Task 1 before changing runtime validation logic.
- [x] Complete Task 2 before implementing extraction and install-state persistence.
- [x] Complete Task 3 before implementing the installer.
- [x] Complete Task 4 before replacing `StubRuntimeManager`.
- [x] Complete Task 5 before adapting domain and service behavior.
- [x] Complete Task 6 before wiring provider export into runtime startup.
- [x] Complete Task 7 before Task 8.
- [x] Complete Task 8 before integration verification in Task 9.

## Risks To Watch

- Real payload archives may force a rename or versioning decision that conflicts with the current fixed bootstrap file-name contract.
- Extraction and launch behavior may be correct in isolation but still fail under service recreation unless persisted host state is treated as authoritative.
- Provider export can accidentally reintroduce secret leakage if runtime-facing config generation is not carefully scoped.
- Replacing the stub manager too early can destabilize `app` and `service:host` before install and config foundations are sound.
- Integration work must not quietly mix “real runtime” and “stub bridge” assumptions into one ambiguous state model.

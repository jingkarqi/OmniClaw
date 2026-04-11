# OmniClaw Real App Tasks 1-8 Execution Plan

> Execution plan for `codex/real-app-task1-8`. Follow dependency order strictly and keep milestone commits small and reviewable.

## Phase A: Design And Baseline

- [x] Create isolated worktree `C:\Users\SoraE\.config\superpowers\worktrees\OmniClaw\real-app-task1-8`
- [x] Create branch `codex/real-app-task1-8`
- [x] Run baseline verification on the untouched branch tip
- [x] Write the branch design document
- [x] Write this execution plan
- [ ] Commit the design and plan docs

## Phase B: Finish Task 1-4 Foundations

### Task 1

- [ ] Add reusable APK-level packaging verification so bootstrap asset presence is asserted from the built APK, not only by manual inspection.
- [ ] Keep the `assets/bootstrap/` contract unchanged.

### Task 2

- [ ] Expand `BootstrapPayloadValidatorTest` to cover remaining malformed and incomplete manifest cases.
- [ ] Confirm runtime-facing payload-validation failures stay explicit and deterministic.

### Task 3

- [ ] Re-check `RuntimeDirectories` and `RuntimeInstallStateStore` against the detailed plan and patch any missing state/path gaps needed by later launch work.
- [ ] Add or adjust tests for any new persisted state files introduced for the real runtime path.

### Task 4

- [ ] Patch any installer/layout gaps that block real runtime launch.
- [ ] Keep install idempotence, partial recovery, and symlink-safe layout validation green.
- [ ] Re-run `:runtime:impl:testDebugUnitTest`.
- [ ] Commit the Task 1-4 completion slice.

## Phase C: Implement Task 5 Real Runtime Lifecycle

- [ ] Add Android payload-source glue for `BootstrapInstaller`.
- [ ] Add `RuntimeLaunchStateStore`.
- [ ] Add `RuntimeProcessLauncher`.
- [ ] Add `RuntimeHealthChecker`.
- [ ] Add `RuntimeLogCollector`.
- [ ] Add `RealRuntimeManager`.
- [ ] Replace `StubRuntimeManager` in `AppGraph` only after `RealRuntimeManager` tests are green.
- [ ] Add or update unit tests for install-before-start, launch failure, stop behavior, and state restoration.
- [ ] Run `:runtime:impl:testDebugUnitTest :domain:runtime:testDebugUnitTest :app:assembleDebug`.
- [ ] Commit the real runtime-manager slice.

## Phase D: Implement Task 6 Domain And Service Adaptation

- [ ] Update `ObserveHostOverviewUseCase` to reflect real runtime states.
- [ ] Update `StartHostUseCase` to cooperate with real runtime semantics.
- [ ] Update `StopHostUseCase` if needed for graceful stop and service semantics.
- [ ] Update `HostForegroundService` only where lifecycle behavior genuinely changes.
- [ ] Update app wiring and tests.
- [ ] Run `:service:host:testDebugUnitTest :domain:runtime:testDebugUnitTest :app:assembleDebug`.
- [ ] Commit the domain/service adaptation slice.

## Phase E: Implement Task 7 Provider Export Contract

- [ ] Add `RuntimeProviderConfig`.
- [ ] Add `RuntimeProviderConfigWriter`.
- [ ] Define the stable generated-config filename and workspace location.
- [ ] Add tests for valid export, missing secret, missing fields, and overwrite behavior.
- [ ] Run `:runtime:impl:testDebugUnitTest`.
- [ ] Commit the provider-export contract slice.

## Phase F: Implement Task 8 Save And Start Wiring

- [ ] Wire export into successful provider saves.
- [ ] Make runtime start re-export when generated config is missing or stale.
- [ ] Make start gating depend on real export readiness, not only form completeness.
- [ ] Surface export-specific failure semantics through domain/runtime and provider flows.
- [ ] Update tests in `domain/provider`, `domain/runtime`, and `runtime:impl`.
- [ ] Run `:domain:provider:testDebugUnitTest :domain:runtime:testDebugUnitTest :runtime:impl:testDebugUnitTest`.
- [ ] Commit the provider wiring slice.

## Phase G: Integration Verification

- [ ] Re-run the full verification command:
  - `.\gradlew.bat :service:host:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug :domain:provider:testDebugUnitTest :domain:runtime:testDebugUnitTest :runtime:impl:testDebugUnitTest`
- [ ] Re-check the debug APK for all bootstrap assets under `assets/bootstrap/`.
- [ ] Add or update any small integration smoke coverage needed for the phases 1-3 checkpoint.
- [ ] Commit the final integration checkpoint if code changes were required.

## Agent Usage Rules

- [ ] Prefer narrow, independent subagent tasks.
- [ ] Use `gpt-5.4` with `xhigh` for implementation-oriented work.
- [ ] Use `gpt-5.3-codex` with `xhigh` for review, debugging, and gap analysis.
- [ ] Close subagents immediately after they explicitly return `DONE`.

## Guardrails

- [ ] Do not develop directly on `master`.
- [ ] Do not pull bridge Phase 4 work into this branch.
- [ ] Do not leak provider secrets into long-lived UI state or persisted draft storage.
- [ ] Do not claim success without re-running the required verification slice.

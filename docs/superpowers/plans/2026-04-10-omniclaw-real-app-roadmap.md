# OmniClaw Real App Roadmap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move OmniClaw from the completed heavy skeleton to a genuinely usable Android host app that can package real runtime assets, start and stop a real OpenClaw runtime, expose a real local bridge, and support recovery, diagnostics, and user-facing operational flows.

**Architecture:** Keep the current multi-module boundary intact and upgrade it in place: replace placeholder payloads with real packaged assets, replace stub runtime and bridge implementations with real host integrations, keep `app` as the only composition root, keep `service:host` as the only long-lived lifecycle shell, and keep `feature:*` modules presentational. Deliver real usability by closing the runtime, bridge, permissions, diagnostics, and release-validation gaps in dependency order.

**Tech Stack:** Kotlin, Android Gradle Plugin, Jetpack Compose, Navigation Compose, DataStore Preferences, Android Keystore, foreground service, packaged bootstrap assets, local UDS bridge, coroutines and Flow, PowerShell asset scripts.

---

## Current Baseline

- The heavy skeleton is in place through Task 7.
- `app` has a real `Application`, `AppGraph`, navigation shell, and service launcher wiring.
- `service:host` has a real foreground-service skeleton and start/stop entry points.
- `core:storage` persists provider config and secrets.
- `runtime:payloads` is packaged into the APK and exposes the fixed `assets/bootstrap/` path.
- `runtime:impl` is still driven by `StubRuntimeManager`.
- `bridge:impl` is still driven by stub bridge and stub device gateway implementations.
- The current payload archives are placeholder bootstrap artifacts, not production runtime assets.

## Definition Of “Real Usable”

The app should count as genuinely usable only when all of the following are true:

- A user can install the APK and complete the required setup flow without developer tools.
- Real runtime payloads are bundled, validated, and unpacked correctly.
- Provider configuration saved in Android is exported into the runtime’s real configuration surface.
- The host can start, stop, and recover a real OpenClaw runtime instead of switching stub state only.
- The runtime can connect to a real local bridge and successfully call at least one real Android-backed capability.
- The UI exposes real state, real failures, and actionable recovery or diagnostics paths.

## Phase 1: Replace Placeholder Payloads With Real Bootstrap Inputs

**Objective**

- Convert `runtime:payloads` from a packaging skeleton into a trustworthy source of real runtime inputs.

**Primary Work**

- Replace placeholder `debian-rootfs.tar.xz` with the real root filesystem bundle.
- Replace placeholder `openclaw-2026.3.13.tgz` with the real packaged runtime archive currently chosen for the bootstrap contract.
- Keep the `assets/bootstrap/` directory and file naming contract stable.
- Regenerate `manifest.json` from the actual bundled files and verify the hashes match.
- Add packaging verification so asset presence is checked from the built APK, not only from the source tree.

**Modules Most Affected**

- `runtime:payloads`
- `runtime:impl`
- `scripts/`

**Exit Criteria**

- The APK contains real runtime assets under `assets/bootstrap/`.
- `manifest.json` contains correct hashes and sizes for both required files.
- Runtime payload loading reads real bundled inputs without relying on placeholders.

## Phase 2: Turn `runtime:impl` Into A Real Runtime Host

**Objective**

- Replace the stub runtime state machine with a real host integration that installs, starts, stops, and observes a real OpenClaw runtime.

**Primary Work**

- Define the real runtime workspace layout under app-managed storage.
- Implement payload validation, unpacking, and install-state tracking.
- Implement runtime launch, stop, health check, and log capture behavior.
- Persist install and launch state so UI and service can recover after process death.
- Surface runtime failures as structured errors instead of generic fallback state.

**Modules Most Affected**

- `runtime:api`
- `runtime:impl`
- `domain:runtime`
- `service:host`
- `app`

**Exit Criteria**

- `RuntimeManager.start()` launches a real runtime process and can prove it is alive.
- `RuntimeManager.stop()` terminates the real runtime cleanly or escalates to a controlled force-stop path.
- The runtime’s current status and diagnostics survive app or service recreation.

## Phase 3: Export Real Provider Configuration Into Runtime Startup

**Objective**

- Make saved provider configuration operational by exporting it into the runtime’s real configuration input before launch.

**Primary Work**

- Build a runtime-facing config export format from `ProviderConfigStore` and `SecretStore`.
- Write exported config into the runtime workspace in a predictable location.
- Define when config is regenerated: initial install, explicit save, and before runtime start.
- Distinguish between storage success, config export failure, and runtime consumption failure.
- Keep UI truth sourced through domain use cases instead of raw storage reads.

**Modules Most Affected**

- `domain:provider`
- `domain:runtime`
- `runtime:impl`
- `core:storage`

**Exit Criteria**

- Saving provider config changes the next runtime launch behavior for real.
- Start gating uses real exported config readiness, not only Android-side form completeness.
- Runtime diagnostics can identify configuration export failures separately from runtime boot failures.

## Phase 4: Replace Stub Bridge With A Real Local Bridge Server

**Objective**

- Turn the current bridge skeleton into a real local server that a real runtime can connect to and use.

**Primary Work**

- Implement the local socket server lifecycle.
- Implement request decoding, routing, and response encoding.
- Establish the minimum supported capability set for first real usability.
- Return structured “unsupported” results for everything outside the initial capability set.
- Expose bridge lifecycle, health, and recent-failure state to the UI and diagnostics layer.

**Modules Most Affected**

- `bridge:api`
- `bridge:impl`
- `domain:bridge`
- `domain:runtime`
- `service:host`

**Exit Criteria**

- A real runtime can connect to the local bridge endpoint.
- At least one Android-backed capability executes successfully end to end.
- Bridge failures are visible and diagnosable from the app.

## Phase 5: Complete Permissions, Foreground Service, And Recovery Semantics

**Objective**

- Upgrade permissions and lifecycle handling from presentation-level skeletons into reliable operating behavior.

**Primary Work**

- Add executable permission and settings-intent flows for every required capability in the first usable slice.
- Make service restart, host expectation state, and runtime recovery behavior consistent across app death and service recreation.
- Define recovery rules for partially installed runtime state, missing permissions, stale bridge state, and orphaned runtime processes.
- Ensure UI, service, and runtime state remain synchronized after background transitions and restarts.

**Modules Most Affected**

- `feature:permissions`
- `service:host`
- `domain:runtime`
- `domain:bridge`
- `app`

**Exit Criteria**

- Required permissions are not only displayed but can be meaningfully resolved by the user.
- The service can recover its desired host state after recreation.
- The app no longer depends on fresh in-memory graph construction to reflect operational state.

## Phase 6: Upgrade The Four Screens Into Operational UI

**Objective**

- Make the existing screens usable for real operator workflows instead of skeleton demonstrations.

**Primary Work**

- Show real install state, launch state, diagnostics, and recent errors.
- Add save feedback, reset behavior, and current effective-provider visibility on the provider screen.
- Add repair and diagnostics entry points to the runtime screen.
- Improve permission screen explanations and action affordances.
- Ensure the home screen summarizes real readiness across provider, runtime, bridge, and permissions.

**Modules Most Affected**

- `feature:home`
- `feature:provider`
- `feature:runtime`
- `feature:permissions`
- `app`

**Exit Criteria**

- A user can complete setup, start the host, inspect failures, and stop the host without external tooling.
- The screens expose state transitions clearly and consistently.

## Phase 7: Add Real Diagnostics And Repair Workflows

**Objective**

- Make failures actionable and recoverable without reinstalling the app by default.

**Primary Work**

- Build diagnostics collection for payload state, runtime install state, runtime logs, bridge health, permissions, and provider export state.
- Add repair flows for common recoverable failures such as missing generated config, stale extracted assets, or stuck processes.
- Classify failures into recoverable, user-actionable, and fatal categories.
- Present diagnostics and repair outcomes in the runtime UI.

**Modules Most Affected**

- `domain:runtime`
- `runtime:impl`
- `feature:runtime`
- `core:common`

**Exit Criteria**

- The app can produce a useful diagnostic snapshot.
- Common host failures can be repaired without uninstalling the app.

## Phase 8: Reach Release-Level Verification On Real Devices

**Objective**

- Prove the host works outside the development loop and establish release gates that reflect real-world use.

**Primary Work**

- Validate install, first run, configuration, runtime start, runtime stop, and restart on real target devices.
- Validate large bundled asset packaging and install behavior across fresh install and upgrade paths.
- Add integration and end-to-end verification for runtime lifecycle and bridge connectivity.
- Remove or clearly fence any remaining skeleton-only shortcuts.
- Document release blockers, supported devices, and known limitations.

**Modules Most Affected**

- `app`
- `service:host`
- `runtime:impl`
- `bridge:impl`
- `runtime:payloads`

**Exit Criteria**

- The app can be installed and operated by a non-developer through a real workflow.
- Verification no longer depends on placeholder runtime inputs or stub integrations.

## Recommended Execution Order

- [ ] Finish Phase 1 before replacing `StubRuntimeManager`.
- [ ] Finish Phase 2 before claiming real runtime usability.
- [ ] Finish Phase 3 before widening bridge capability work.
- [ ] Finish Phase 4 before calling the host-runtime integration end to end.
- [ ] Finish Phase 5 before treating service behavior as production-ready.
- [ ] Finish Phase 6 before treating the app as user-operable.
- [ ] Finish Phase 7 before broad real-device validation.
- [ ] Finish Phase 8 before any release-style distribution.

## Risks To Watch

- Placeholder payloads can create false-positive “payload available” state unless clearly fenced or replaced.
- Service restart behavior must not diverge from actual host desired state.
- Runtime installation, configuration export, and bridge startup must not become three separate sources of truth.
- `feature:*` modules must remain consumers of domain state, not owners of runtime or bridge logic.
- Asset size, extraction cost, and process lifetime constraints may become Android-platform blockers even when the code is correct.

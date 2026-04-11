# OmniClaw Real App Tasks 1-8 Completion Design

> This design is the implementation contract for `codex/real-app-task1-8`. It refines the roadmap in [`2026-04-10-omniclaw-real-app-roadmap.md`](./2026-04-10-omniclaw-real-app-roadmap.md) and the dependency-ordered detail plan in [`2026-04-10-omniclaw-real-app-phases-1-3-detailed.md`](./2026-04-10-omniclaw-real-app-phases-1-3-detailed.md).
>
> **Status:** Historical design record for the completed Task 1-8 branch work on 2026-04-11. Baseline assumptions, proposed filenames, and sequencing below describe pre-completion intent and may differ from the final repository behavior. Use the updated roadmap, phases, and progress documents for the current repository baseline.

## Goal

Complete Tasks 1-8 in strict dependency order by:

- finishing the remaining payload-validation and install-foundation gaps,
- replacing `StubRuntimeManager` with a real runtime host path,
- persisting non-secret provider export metadata and materializing runtime config at start time, and
- wiring start gating and surfaced state around that real runtime path.

## Non-Goals

- Do not replace the stub bridge with a real UDS bridge in this branch.
- Do not redesign the feature modules into a new presentation architecture.
- Do not widen the public app graph beyond what the runtime seam genuinely needs.

## Baseline Facts

- `runtime:payloads` already bundles real bootstrap artifacts under `assets/bootstrap/`.
- `runtime:impl` already contains `BootstrapPayloadValidator`, `RuntimeDirectories`, `RuntimeInstallState`, `RuntimeInstallStateStore`, `ArchiveExtractor`, and `BootstrapInstaller`.
- `app` still wires `RuntimeManager` to `StubRuntimeManager`.
- `StartHostUseCase` still gates startup on permissions plus Android-side provider completeness only.
- The bundled runtime archive contains `openclaw.mjs` and `package.json` with `bin.openclaw = openclaw.mjs`.
- The bundled rootfs contains `installed-rootfs/debian/usr/bin/node`, `installed-rootfs/debian/usr/bin/npm`, `installed-rootfs/debian/usr/bin/nodejs`, and `proot-distro/debian.sh`.
- The stable executable entry appears to be the rootfs-global `installed-rootfs/debian/usr/bin/openclaw`; the extracted `openclaw-2026.3.13-1/` tree looks like a source/workspace payload, not the direct exec target.

## Design Principles

- Keep `app` as the only composition root.
- Keep `RuntimeManager` as the main seam consumed by domain and UI code.
- Treat disk state as authoritative for install and recovery behavior.
- Keep bridge assumptions minimal until Phase 4; the runtime only needs enough startup context to boot successfully.
- Export provider secrets only at runtime-config materialization time.

## Runtime Workspace Contract

`RuntimeDirectories` remains the authoritative workspace layout:

- `payloads/staging/` for copied bundled archives
- `runtime/rootfs/` for the extracted Debian rootfs bundle
- `runtime/files/` for the extracted OpenClaw runtime archive
- `runtime/config/` for generated runtime-facing config and launch metadata
- `runtime/logs/` for stdout/stderr capture and recent boot logs
- `runtime/tmp/` for temp launch files
- `state/` for persisted install and launch state

The branch will extend this contract with:

- a persisted runtime-launch state file,
- a persisted host-control state file that records whether the host is expected to be running,
- a generated runtime config file with a stable filename,
- one or more log files with deterministic names for the current and recent launches.

## Real Runtime Host Design

### Keep `RuntimeManager` Stable Unless State Shape Forces Change

The current `RuntimeManager` surface is intentionally small:

- `status: StateFlow<RuntimeStatus>`
- `diagnostics: StateFlow<DiagnosticsSummary>`
- `start()`
- `stop()`

The branch should preserve those operations and prefer enriching `RuntimeStatus` / `DiagnosticsSummary` over adding brand-new public runtime APIs. If an extra runtime-facing operation is required for provider export or repair, add an internal collaborator first and only widen `RuntimeManager` if the public seam becomes too distorted.

### `RealRuntimeManager` Responsibilities

`RealRuntimeManager` becomes the orchestration layer that owns:

- bundled payload lookup and validation,
- install-state read/repair decisions,
- install-before-start behavior,
- runtime-config existence and freshness checks,
- launch and stop orchestration,
- status/diagnostics state derivation,
- restoration from persisted install and launch state on recreation.

It should stay thin by delegating to focused collaborators.

### New Runtime Collaborators

The minimum collaborator set for Tasks 5-8 is:

- `RuntimeBundledPayloadSource`
  - Android-only payload stream reader used by `BootstrapInstaller`.
- `RuntimeLaunchStateStore`
  - Persists whether the runtime is stopped, starting, running, stopping, or failed, together with pid-like/process-token metadata and recent failure text.
- `HostControlStateStore`
  - Persists `desiredRunning`, last blocking state, and last recoverable failure so service recreation can decide whether to re-enter recovery or stay stopped.
- `RuntimeProviderConfigWriter`
  - Builds the runtime-facing config representation from `ProviderRuntimeExport` plus `SecretStore`, writes `openclaw.json5` into `runtime/config/`, and reports config-materialization-specific failures.
- `RuntimeProcessLauncher`
  - Creates the real process command, working directory, environment, stdout/stderr destinations, and launches the runtime process.
- `RuntimeHealthChecker`
  - Uses process state, launch metadata, and recent log evidence to classify `Starting`, `Running`, `Stopped`, `Degraded`, and `Error`.
- `RuntimeLogCollector`
  - Reads recent stdout/stderr output into `DiagnosticsSummary` details and failure headlines.

`BootstrapInstaller` and `RuntimeInstallStateStore` remain reusable as-is, with only surgical fixes if tests expose gaps.

### Launch Strategy

The real launch path should be the smallest credible path supported by the bundled assets:

1. Ensure the bundled manifest validates.
2. Ensure the runtime workspace is installed and layout-valid.
3. Ensure export metadata and secret availability are present so runtime config can be materialized for the current provider state.
4. Launch the bundled OpenClaw runtime from the extracted runtime archive using the Debian rootfs Node toolchain.
4. Launch the rootfs-global `openclaw` CLI using the Debian rootfs toolchain, while treating the extracted `openclaw-<version>-*/` tree as workspace/context rather than the direct executable.
5. Capture stdout/stderr into files under `runtime/logs/`.
6. Move status from `Starting` to `Running` only after real health evidence exists.

To keep Phase 4 isolated, the runtime config should not depend on a real bridge transport. Bridge-related values may remain absent or stub-compatible as long as the runtime can boot without them.

### Expected Runtime Inputs

This branch standardizes the following launch assumptions:

- runtime executable: rootfs-global `openclaw` under `runtime/rootfs/installed-rootfs/debian/usr/bin/`
- runtime working directory: extracted OpenClaw package root under `runtime/files/openclaw-<version>-*/`
- runtime config path: fixed file under `runtime/config/`, passed explicitly through environment
- runtime state dir: explicit directory under `state/`
- log files: deterministic files under `runtime/logs/`, one current stdout file and one current stderr file at minimum
- process health:
  - process exists and has not exited,
  - recent logs do not indicate immediate boot failure,
  - optionally, additional filesystem evidence can promote `Starting` to `Running`

## Provider Export Design

### Separate UI Draft Model From Runtime Export Model

`ProviderConfigDraft` remains the Android editing model. The runtime-facing config must be a separate structure, owned by `runtime:impl`, so the branch does not leak secret-bearing runtime representation back into the UI/domain editing surface.

To keep domain modules decoupled from `runtime:impl`, the branch should introduce a narrow storage-facing seam such as `ProviderExportStore` in a low-level shared module, with the file-backed implementation living in `runtime:impl`.

### Export Timing

The consistent rule for Tasks 7-8 is:

- write non-secret export metadata on every successful save, and
- materialize `openclaw.json5` from export metadata plus the secret during runtime start.

This provides both early feedback for metadata validity and start-time correctness for the secret-bearing runtime config.

### Export Failure Semantics

The implementation must distinguish:

- Android-side draft storage success,
- secret-storage success,
- metadata export failure,
- runtime-config materialization failure,
- runtime launch failure after successful config materialization.

Those failure classes should map to different `HostErrorCategory` or at least different error messages and diagnostics headlines, so the UI and tests can tell them apart.

### Runtime Config Location

The generated config belongs under `runtime/config/` with the stable filename `openclaw.json5`. The runtime should read it through an explicit environment variable or command argument rather than relying on a default config search path.

## Domain And Service Adaptation

### `StartHostUseCase`

`StartHostUseCase` must stop treating provider completeness as purely an Android form concern. Its new startup contract is:

1. required permissions granted,
2. provider export metadata plus secret availability sufficient for launch,
3. bridge started,
4. runtime start requested,
5. bridge rolled back if runtime start fails.

The runtime layer remains the authority on export freshness and install-before-start behavior, but `StartHostUseCase` should still persist blocking states such as `PermissionRequired` or `ConfigurationInvalid` so restart/recovery logic has durable truth.

### `ObserveHostOverviewUseCase`

The overview should remain a small combine of runtime, bridge, permissions, and provider truth, but it must now reflect real runtime semantics:

- install required
- configuration invalid
- degraded
- error

It should prefer runtime-derived truth once provider export exists, instead of reporting readiness from Android-side completeness alone.

### `HostForegroundService`

The service should continue to be a lifecycle shell, not a second runtime manager. Recovery work belongs in domain/runtime plus runtime/impl, with the service only invoking the right start/stop path and staying sticky when the host is supposed to be active.

Service recreation should stop treating `null` intent as unconditional restart. Instead, it should consult the persisted host-control state and only attempt recovery when `desiredRunning` is still true.

## UI Surface Strategy

The branch keeps UI changes intentionally narrow:

- `Home` reflects real readiness and real failure states through `HostOverview`.
- `Runtime` reflects install/start/stop/diagnostics updates already exposed by `RuntimeManager`.
- `Provider` remains draft-driven, with additional feedback when export succeeds or fails.

No new UI architecture should be introduced unless a blocking limitation appears.

## Milestone Commit Boundaries

Make milestone commits in this order:

1. design and execution docs
2. remaining Task 1-4 fixes and tests
3. `RealRuntimeManager` and runtime lifecycle collaborators
4. domain/service/app wiring for real runtime state
5. provider export contract and runtime export writer
6. provider export wiring into save/start paths
7. final integration verification updates if needed

## Verification Strategy

At minimum, the branch must keep these slices green:

- `:runtime:impl:testDebugUnitTest`
- `:domain:provider:testDebugUnitTest`
- `:domain:runtime:testDebugUnitTest`
- `:service:host:testDebugUnitTest`
- `:app:testDebugUnitTest`
- `:app:assembleDebug`

Phase-end verification should also re-check the built APK for:

- `assets/bootstrap/debian-rootfs.tar.xz`
- `assets/bootstrap/openclaw-2026.3.13.tgz`
- `assets/bootstrap/manifest.json`

## Open Risks

- The Debian-rootfs launch path may require more Android-specific wrapping than a naive `ProcessBuilder` call.
- The payload appears to omit a complete proot launcher, so the branch may need to stop at a well-abstracted launcher seam if Android host execution cannot yet enter the rootfs directly.
- OpenClaw config shape may be richer than the current provider draft, so the first export contract must focus on the smallest bootable subset.
- Service recreation can drift from runtime disk truth unless launch state is persisted separately from install state.
- It is easy to accidentally couple Phase 3 provider export to Phase 4 bridge requirements; this branch must avoid that.

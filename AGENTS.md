# AGENTS.md

Repository instructions for AI coding agents working on Findroid.

## Dev environment

- `nix develop` provides the full toolchain (JDK 21, Android SDK, `just`, `ktfmt`) and
  installs the repo's pre-commit hooks (see `flake.nix`'s `git-hooks.nix` integration —
  trailing whitespace, EOF fixer, merge-conflict/large-file checks, `nixfmt`, `statix`).
  The generated `.pre-commit-config.yaml` is gitignored — it's regenerated from
  `flake.nix` on every shell entry, don't hand-edit it.
  - Deliberately **no** `ktfmt` pre-commit hook: nixpkgs only ships a recent standalone
    `ktfmt` (0.63+), but the project's Gradle plugin pins `ktfmt` 0.26.0 (see
    `gradle/libs.versions.toml`), and the two format some constructs differently — a
    hook running the wrong version could "fix" a file into a state that then fails
    CI's real `ktfmtCheck`. This actually happened once: it inserted spurious blank
    lines between every `include()` in `settings.gradle.kts`. Use `just lint` (runs
    the pinned Gradle plugin remotely) as the authoritative formatting check.
- Prefer the `justfile` recipes over raw `./gradlew`/`ssh`/`adb` invocations — run
  `just --list` for the full set. It wraps everything below (remote builds, fetching
  APKs, and Mi Pad 4 install/logcat/adb-enable) in composable recipes.

## Builds

- **Never run Gradle builds locally on this machine (`fnuc`)** — this is a multi-module
  Android project and local compiles are heavy. Always build on `rofl-13.brkn.lol` or
  `rofl-14.brkn.lol` instead. The `justfile` automates this:
  - `just sync [host]` — rsync the working tree to the remote build host (excludes
    `.git/`, `build/`, `.gradle/`).
  - `just gradle [host] <tasks...>` — sync, then run arbitrary Gradle tasks remotely via
    `nix develop --command ./gradlew <tasks>`.
  - `just build [flags]` — build the libre-flavor APK remotely. Flags: `--tv`/`--phone`
    (default `--phone`), `--debug`/`--release` (default `--debug`), `--host=<host>`.
    E.g. `just build --debug` (phone debug, the common case) or `just build --tv --release`.
  - `just lint` — remote `ktfmtCheck` (mirrors `.github/workflows/lint.yaml`).
  - `just test` — remote unit test suites for `:data` and `:core`.
  - `just fetch [flags]` — scp the built APK split back to `./dist/` locally. Same flags
    as `just build`, plus `--abi=<abi>` (default `arm64-v8a`).
  - `just build-fetch [flags]` — build + fetch in one step. Same flags as `just build`.
  - Flag parsing for `build`/`fetch`/`build-fetch`/`deploy` is shared via
    `.just-parse-flags.sh` (not a real just recipe — plain bash, invoked by those recipes
    since just has no native flag/option parser).
  - Manually, the equivalent is:
    1. `rsync -az --delete --exclude='.git/' --exclude='**/build/' --exclude='.gradle/' --exclude='**/.gradle/' ./ rofl-13.brkn.lol:~/devel/private/pschmitt/findroid-verify/`
    2. `ssh rofl-13.brkn.lol 'cd ~/devel/private/pschmitt/findroid-verify && nix develop --command ./gradlew <tasks>'`
    3. Re-run the rsync after every local edit before rebuilding remotely — there is no
       watch/sync daemon, it's a one-shot copy each time.
  - Plain Nix derivation builds (non-Gradle) already offload to rofl-13/rofl-14 via
    configured remote builders; only Gradle itself needs this manual redirect, since
    Gradle always executes wherever it's invoked regardless of Nix's remote-builder config.
- Gradle modules use product flavors (at least a "Libre" flavor), so bare task names like
  `:core:compileDebugKotlin` are ambiguous. Use the flavor-qualified task name (e.g.
  `compileLibreDebugKotlin`), or run `./gradlew tasks` in the target module first to confirm
  the exact name.
- Formatting a Kotlin file directly (not a full Gradle build) is fine to run locally:
  `just format` runs the standalone `ktfmt` CLI over all tracked `.kt`/`.kts` files.
  Treat it as an advisory quick pass only — it's a newer `ktfmt` version than CI's
  pinned one (see the pre-commit note above) — and confirm with `just lint` before
  relying on it.

## Physical test device

- A **Mi Pad 4** (`arm64-v8a`) is available for installing and checking debug builds.
  Reachable via SSH at `mi-pad-4.lan`, port `8022` (Termux, rooted). The `justfile` wraps
  the common operations, all built on real `adb` (not `scp`/`pm install`):
  - `just mipad-connect` — the core primitive. Finds the port `adbd` is actually
    listening on via `su -c 'ss -ltnp'` over SSH (adbd is usually already running — the
    device doesn't rely on a fixed port), `adb connect`s to it, and prints the resulting
    `host:port` on stdout (status goes to stderr) so other recipes can capture it with
    `target=$(just mipad-connect)`. Only if nothing is listening does it fall back to
    forcing `adbd` on via root (`setprop service.adb.tcp.port` + restart).
  - `just mipad-install <apk>` — connects, then `adb install -r`. Deliberately avoids
    `scp` into `/sdcard` + `pm install`: `system_server` can't read the FUSE-backed
    `/sdcard` back (SELinux denies it — `avc: denied { read } ... tcontext=u:object_r:fuse:s0`),
    and Termux's `sshd` has no `sftp-server` subsystem configured anyway (plain `scp`
    fails with "Connection closed" unless you pass `-O` for the legacy protocol). `adb
    install` sidesteps all of that.
  - `just deploy [flags]` — build the APK remotely, fetch it, and install it on the
    Mi Pad 4 in one step. Same flags as `just build`, e.g. `just deploy --debug`.
  - `just mipad-logcat [filter]` — tail `logcat` from the device, optionally grepped.
  - `just mipad-uninstall <pkg>` — `adb uninstall` a package (see the signature-mismatch
    gotcha below).
  - `just mipad-shell` — interactive SSH shell on the device.
  - Signature mismatch gotcha: if the device already has a build signed with a different
    key than the one you're installing, install fails with
    `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Fix is `just mipad-uninstall <applicationId>`
    then install fresh — this wipes local app data (Room DB, playback positions,
    download records). Confirm with the user before doing this if it's not their own
    throwaway data.

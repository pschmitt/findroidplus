# Reproducible builds

This documents what's currently in place to let a third party verify a released JollyFin APK
was actually built from the source at the commit it claims, and what's still missing. It's an
ongoing effort, not a finished guarantee — see "Known gaps" below.

**Empirically verified (2026-07-24, commit `7dce4764`)**: built `:app:phone:assembleLibreRelease`
from a clean checkout three times — twice on the same host, once on a second, differently
provisioned host — each with `--rerun-tasks` to force full recompilation (no shared build-cache
hits). All three produced **byte-identical output for every one of the 988 files inside the
APK** (dex, `resources.arsc`, manifest, every `META-INF` entry), in identical zip order, with the
same fixed `1981-01-01 01:01` entry timestamps AGP uses for reproducible packaging. The only
difference between hosts was the APK signature itself, because each host's Gradle debug keystore
is locally auto-generated and therefore host-unique - signing with the same (CI/release) key would
have made the two cross-host APKs fully byte-identical, signature included. In short: the build
*process* is fully deterministic today; only the signing step introduces variance, and only
because these test builds intentionally didn't use the shared private key.

## What's already pinned

- **App version**: `versionCode`/`versionName` are static, checked into
  [`buildSrc/src/main/kotlin/Versions.kt`](buildSrc/src/main/kotlin/Versions.kt) — not derived
  from a timestamp or build counter.
- **Dependencies**: every dependency in
  [`gradle/libs.versions.toml`](gradle/libs.versions.toml) is pinned to an exact version (no
  `+`/`latest.release` ranges).
- **Toolchain**: Gradle, AGP, and Kotlin versions are all exact-pinned (see
  `gradle/wrapper/gradle-wrapper.properties` and `gradle/libs.versions.toml`). The Gradle wrapper
  also verifies the downloaded distribution against a `distributionSha256Sum`.
- **Nix dev shell**: [`flake.nix`](flake.nix) + `flake.lock` pin an exact JDK and Android SDK
  toolchain (locked nixpkgs revision), used by the `just` recipes for local/remote builds (see
  `AGENTS.md`).
- **CI JDK**: GitHub Actions workflows pin an exact Temurin patch version (`21.0.11`, the latest
  Temurin has published as of this writing - Temurin's release cadence lags nixpkgs' OpenJDK
  slightly, so this doesn't match the Nix dev shell's `21.0.12` exactly) rather than a floating
  major version, so the JDK used to build a given release doesn't silently drift between runs.
- **Embedded commit**: every release APK (phone and TV) embeds the exact commit it was built from
  as `BuildConfig.GIT_REVISION`, computed via `git describe --always --abbrev=12 --dirty`. You can
  check this in the app under **Settings → About**, or directly from the APK:

  ```sh
  unzip -p phone-libre-arm64-v8a-release.apk 'classes*.dex' | strings | grep -E '^v[0-9]+\.[0-9]+\.[0-9]+'
  ```

  CI ([`.github/workflows/release.yaml`](.github/workflows/release.yaml)) verifies this landed
  correctly in the built APK before publishing, the same check `just build --release` runs for
  local builds (see [`justfile`](justfile)).
- **Checksums**: every GitHub release - `latest` and each tagged `vX.Y.Z` - includes a
  `SHA256SUMS` file covering every published APK.

## Rebuilding a release yourself

```sh
git clone https://github.com/pschmitt/jollyfin
cd jollyfin
git checkout <commit-or-tag>
nix develop --command ./gradlew :app:phone:assembleLibreRelease
```

The resulting APK at `app/phone/build/outputs/apk/libre/release/` will:
- Match the published `versionCode`/`versionName` for that commit.
- Contain the same `GIT_REVISION` string (checkable as above), if you also export `GIT_REVISION`
  before building — CI does this automatically, but a local build without it embeds `"unknown"`.
- Be functionally and byte-for-byte identical in its **unsigned content** (dex, resources,
  manifest) to the published build, assuming the same JDK/AGP/Kotlin/Gradle versions — which the
  Nix flake pins for you.

It will **not** have the same signature bytes as the published APK, because the CI signing key is
private (necessarily — anyone who could reproduce the signature could impersonate a real release).
To compare your rebuild against a published release without needing the private key, strip both
APKs' signatures and diff the remaining content, e.g. with `apksigner` and a tool like
[`diffoscope`](https://diffoscope.org/) or [`apkdiff`](https://github.com/facebookarchive/apkdiff).

## Known gaps

- **CI doesn't build through the Nix flake.** GitHub Actions installs JDK/Gradle directly on the
  bare runner rather than via `nix develop`, so the toolchain that's actually pinned in this repo
  (`flake.nix`/`flake.lock`) isn't strictly the one producing shipped APKs — only the JDK *patch
  version* is pinned to match. Full toolchain parity (same JDK vendor/build, same Android SDK
  build-tools binary) between the Nix dev environment and CI is a future improvement, not yet
  guaranteed.
- **No Gradle dependency-verification metadata.** Dependency versions are pinned, but nothing
  checksums the actual resolved artifacts (`gradle/verification-metadata.xml` doesn't exist yet).

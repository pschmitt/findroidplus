# Findroid task runner.
#
# Gradle must never run on this machine directly - it's a heavy multi-module Android
# project, so every build/test/lint recipe here shells out to a remote host
# (rofl-13.brkn.lol or rofl-14.brkn.lol) over SSH instead. See AGENTS.md.

set shell := ["bash", "-euo", "pipefail", "-c"]

remote_host := env_var_or_default("FINDROID_REMOTE_HOST", "rofl-13.brkn.lol")
remote_path := env_var_or_default("FINDROID_REMOTE_PATH", "~/devel/private/pschmitt/findroid-verify")
local_dist := env_var_or_default("FINDROID_DIST_DIR", "./dist")

mipad_host := env_var_or_default("MIPAD_HOST", "mi-pad-4.lan")
mipad_ssh_port := env_var_or_default("MIPAD_SSH_PORT", "8022")
mipad_adb_port := env_var_or_default("MIPAD_ADB_PORT", "5555")
mipad_abi := env_var_or_default("MIPAD_ABI", "arm64-v8a")

# List all available recipes
default:
    @just --list

# --- Remote build (rofl-13 / rofl-14) -------------------------------------

# Sync the working tree to the remote build host (excludes .git/build/.gradle)
sync host=remote_host:
    rsync -az --delete \
        --exclude='.git/' --exclude='**/build/' \
        --exclude='.gradle/' --exclude='**/.gradle/' \
        ./ {{host}}:{{remote_path}}/

# Run one or more Gradle tasks on the remote host (syncs first)
gradle host=remote_host *tasks: (sync host)
    ssh {{host}} 'cd {{remote_path}} && nix develop --command ./gradlew {{tasks}}'

# Build an APK remotely. Flags: --tv/--phone (default phone), --debug/--release (default debug), --host=<host>
# Release builds are signed with the persistent CI keystore (fetched from the rbw entry
# "Findroid CI Signing Keystore" and staged on the build host only for the duration of the
# build). Without CI_KEYSTORE_*, Gradle silently signs with the host's throwaway
# ~/.android/debug.keystore and devices carrying CI-signed installs (GitHub releases /
# Obtainium) reject the APK with INSTALL_FAILED_UPDATE_INCOMPATIBLE.
build *flags:
    #!/usr/bin/env bash
    set -euo pipefail
    read -r variant flavor host abi < <("{{justfile_directory()}}/.just-parse-flags.sh" {{remote_host}} {{mipad_abi}} -- {{flags}})
    if [[ "$flavor" != "release" ]]
    then
      just gradle "$host" ":app:${variant}:assembleLibre${flavor^}"
      exit 0
    fi
    if ! rbw unlocked >/dev/null 2>&1
    then
      printf 'rbw is locked - run "rbw unlock" first (needed for the CI signing keystore)\n' >&2
      exit 2
    fi
    tmpdir=$(mktemp -d)
    trap 'rm -rf "$tmpdir"' EXIT
    git_revision=$(git describe --always --abbrev=12 --dirty)
    rbw attachment get "Findroid CI Signing Keystore" --attachment findroid-ci.jks --output "$tmpdir/findroid-ci.jks"
    rbw attachment get "Findroid CI Signing Keystore" --attachment findroid-ci-keystore.env --output "$tmpdir/findroid-ci-keystore.env"
    just sync "$host"
    ssh "$host" 'mkdir -p ~/.findroid-ci-tmp && chmod 700 ~/.findroid-ci-tmp'
    scp -q "$tmpdir/findroid-ci.jks" "$tmpdir/findroid-ci-keystore.env" "$host:.findroid-ci-tmp/"
    # The keystore is shredded on the host whether or not the build succeeds.
    ssh "$host" "
      artifact={{remote_path}}/app/${variant}/build/outputs/apk/libre/${flavor}/${variant}-libre-${abi}-${flavor}.apk
      previous_mtime=0
      [[ -f \"\$artifact\" ]] && previous_mtime=\$(stat -c %Y \"\$artifact\")
      set -a
      . ~/.findroid-ci-tmp/findroid-ci-keystore.env
      set +a
      export CI_KEYSTORE_PATH=\$HOME/.findroid-ci-tmp/findroid-ci.jks
      export GIT_REVISION='$git_revision'
      cd {{remote_path}} && nix develop --command ./gradlew ':app:${variant}:assembleLibre${flavor^}' --rerun-tasks 2>&1 | tee ~/findroid-release-build.log
      rc=\$?
      if [[ \$rc -eq 0 && (! -f \"\$artifact\" || \$(stat -c %Y \"\$artifact\") -le \$previous_mtime) ]]
      then
        echo 'release build did not refresh its APK artifact' >&2
        rc=1
      fi
      if [[ \$rc -eq 0 ]] && ! (cd {{remote_path}} && nix develop --command sh -c 'unzip -p "\$1" "classes*.dex" | strings | grep -Fxq "\$2"' sh "\$artifact" "\$GIT_REVISION")
      then
        echo "release APK does not contain expected revision: \$GIT_REVISION" >&2
        rc=1
      fi
      shred -u ~/.findroid-ci-tmp/* 2>/dev/null || true
      rmdir ~/.findroid-ci-tmp 2>/dev/null || true
      exit \$rc
    "

# Copy a built APK split back to ./dist locally. Same flags as `build`, plus --abi=<abi>
fetch *flags:
    #!/usr/bin/env bash
    set -euo pipefail
    read -r variant flavor host abi < <("{{justfile_directory()}}/.just-parse-flags.sh" {{remote_host}} {{mipad_abi}} -- {{flags}})
    mkdir -p {{local_dist}}
    scp "$host:{{remote_path}}/app/${variant}/build/outputs/apk/libre/${flavor}/${variant}-libre-${abi}-${flavor}.apk" {{local_dist}}/
    if [[ "$flavor" == "release" ]]
    then
      apk={{local_dist}}/${variant}-libre-${abi}-${flavor}.apk
      git_revision=$(git describe --always --abbrev=12 --dirty)
      if ! unzip -p "$apk" 'classes*.dex' | strings | grep -Fxq "$git_revision"
      then
        rm -f "$apk"
        echo "fetched release APK does not contain expected revision: $git_revision" >&2
        exit 1
      fi
    fi

# Build an APK remotely and copy it back to ./dist. Same flags as `build`.
build-fetch *flags:
    #!/usr/bin/env bash
    set -euo pipefail
    just build {{flags}}
    just fetch {{flags}}

# ktfmt check via Gradle, remotely (mirrors .github/workflows/lint.yaml)
lint host=remote_host: (gradle host "ktfmtCheck")

# Run the fast unit test suites remotely
test host=remote_host: (gradle host ":data:testLibreDebugUnitTest" ":core:testLibreDebugUnitTest")

# Remote `./gradlew clean`
clean host=remote_host: (gradle host "clean")

# --- Mi Pad 4 test device (rooted, Termux SSH on port 8022) --------------

# Run an arbitrary command on the Mi Pad 4 over SSH
mipad-ssh +cmd:
    ssh -p {{mipad_ssh_port}} {{mipad_host}} "{{cmd}}"

# Interactive shell on the Mi Pad 4
mipad-shell:
    ssh -p {{mipad_ssh_port}} {{mipad_host}}

# Find the port adbd is actually listening on (via `ss -ltnp` over root SSH),
# starting it as a fallback if it isn't running at all, then `adb connect` to
# it. Prints the resulting "host:port" adb target on stdout so other recipes
# can capture it - status/progress goes to stderr.
mipad-connect:
    #!/usr/bin/env bash
    set -euo pipefail
    port=$(ssh -p {{mipad_ssh_port}} {{mipad_host}} "su -c 'ss -ltnp'" 2>/dev/null \
        | awk '/adbd/ { n = split($4, a, ":"); print a[n]; exit }')
    if [ -z "$port" ]; then
        echo "adbd not listening - starting it via root shell" >&2
        ssh -p {{mipad_ssh_port}} {{mipad_host}} \
            "su -c 'setprop service.adb.tcp.port {{mipad_adb_port}} && stop adbd && start adbd'" >&2
        sleep 1
        port={{mipad_adb_port}}
    fi
    target="{{mipad_host}}:$port"
    adb connect "$target" >&2
    echo "$target"

# Install an APK on the Mi Pad 4 over adb (network, via mipad-connect).
# Simpler and more reliable than scp + `pm install`: adb push/install runs as
# adbd, which doesn't hit the SELinux/FUSE permission issues a plain scp into
# /sdcard runs into when system_server tries to read the file back.
mipad-install apk:
    #!/usr/bin/env bash
    set -euo pipefail
    target=$(just mipad-connect)
    adb -s "$target" install -r {{apk}}

# Uninstall a package from the Mi Pad 4 (e.g. after a signing-key mismatch -
# see AGENTS.md). WARNING: wipes that app's local data (Room DB, playback
# positions, downloads).
mipad-uninstall pkg:
    #!/usr/bin/env bash
    set -euo pipefail
    target=$(just mipad-connect)
    adb -s "$target" uninstall {{pkg}}

# Tail logcat from the Mi Pad 4, optionally filtered by a grep pattern
mipad-logcat filter="":
    #!/usr/bin/env bash
    set -euo pipefail
    target=$(just mipad-connect)
    if [ -n "{{filter}}" ]; then
        adb -s "$target" logcat | grep -i --line-buffered "{{filter}}"
    else
        adb -s "$target" logcat
    fi

# Build an APK remotely, fetch it, and install it on the Mi Pad 4. Same flags as `build`.
deploy *flags:
    #!/usr/bin/env bash
    set -euo pipefail
    read -r variant flavor host abi < <("{{justfile_directory()}}/.just-parse-flags.sh" {{remote_host}} {{mipad_abi}} -- {{flags}})
    just build-fetch {{flags}}
    just mipad-install "{{local_dist}}/${variant}-libre-${abi}-${flavor}.apk"

# --- Formatting / hooks ----------------------------------------------------

# Format Kotlin sources locally with ktfmt (lightweight - not a Gradle build,
# safe to run on this machine). CAUTION: this is nixpkgs' standalone ktfmt,
# which is a newer version than the one CI actually uses (see
# gradle/libs.versions.toml) - the two format some constructs differently.
# Treat this as an advisory quick pass, not a substitute for `just lint`.
format:
    ktfmt --kotlinlang-style $(git ls-files '*.kt' '*.kts')

# Nix formatting/lint for this repo's flake.nix (per global AI context rules)
nix-fmt:
    nixfmt flake.nix

nix-lint:
    nix develop --command statix check

{
  description = "JollyFin Android development environment";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    android-nixpkgs = {
      url = "github:tadfisher/android-nixpkgs";
      inputs.nixpkgs.follows = "nixpkgs";
    };
    git-hooks = {
      url = "github:cachix/git-hooks.nix";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs =
    {
      self,
      nixpkgs,
      android-nixpkgs,
      git-hooks,
    }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs {
        inherit system;
        config.allowUnfree = true;
      };

      android-composition = android-nixpkgs.sdk.${system} (
        sdkPkgs: with sdkPkgs; [
          cmdline-tools-latest
          build-tools-36-1-0
          platform-tools
          platforms-android-36
        ]
      );

      pre-commit-check = git-hooks.lib.${system}.run {
        src = ./.;
        hooks = {
          trim-trailing-whitespace.enable = true;
          end-of-file-fixer.enable = true;
          check-merge-conflicts.enable = true;
          check-added-large-files.enable = true;
          check-yaml.enable = true;
          nixfmt.enable = true;
          statix.enable = true;

          # No ktfmt pre-commit hook: nixpkgs only ships a recent standalone ktfmt
          # (0.63+), but the project's Gradle plugin pins ktfmt 0.26.0 (see
          # gradle/libs.versions.toml), and the two format some constructs
          # differently (e.g. blank-line handling between top-level statements in
          # .kts files). A hook running the wrong version could "fix" a file into
          # a state that then fails CI's real `./gradlew ktfmtCheck`. Use
          # `just lint` (remote, runs the pinned Gradle plugin) as the
          # authoritative check instead - `just format` is still available for
          # a quick local pass, but treat its output as advisory, not final.
        };
      };

    in
    {
      checks.${system}.pre-commit-check = pre-commit-check;

      devShells.${system} = {
        default = pkgs.mkShell {
          buildInputs = with pkgs; [
            jdk21
            android-composition
            just
            ktfmt
          ];

          shellHook = pre-commit-check.shellHook + ''
            echo "🤖 JollyFin development environment"

            # Set JAVA_HOME for Gradle
            export JAVA_HOME=${pkgs.jdk21}/lib/openjdk

            # Set Android SDK path
            export ANDROID_SDK_ROOT=${android-composition}/share/android-sdk
            export ANDROID_HOME=$ANDROID_SDK_ROOT

            # Add Android tools to PATH
            export PATH=$PATH:$ANDROID_SDK_ROOT/platform-tools
            export PATH=$PATH:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin
            export PATH=$PATH:$ANDROID_SDK_ROOT/build-tools/36.1.0

            # Gradle configuration
            export GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.project.android.aapt2FromMavenOverride=$ANDROID_SDK_ROOT/build-tools/36.1.0/aapt2"

            echo "Java version: $(java -version 2>&1 | head -n1)"

            # Remove sdk.dir from local.properties if present, otherwise aidl
            # will fail to run
            if [ -f local.properties ]; then
              sed -i -E '/^sdk\.dir=/d' local.properties
            fi

            echo "✅ Environment ready!"
            echo "• JAVA_HOME: $JAVA_HOME"
            echo "• ANDROID_SDK_ROOT: $ANDROID_SDK_ROOT"
            echo "• Available commands: ./gradlew, adb, aapt2, just, ktfmt"
            echo ""
            if [ "$(hostname)" != "rofl-13" ] && [ "$(hostname)" != "rofl-14" ]; then
              echo "⚠️  Don't run ./gradlew directly on this machine - see AGENTS.md."
              echo "   Use the 'just' recipes below, which build on rofl-13/rofl-14 instead:"
              echo ""
              echo "🚀 Quick start:"
              echo "  just build-phone-debug              # Build debug APK (phone) on rofl-13"
              echo "  just build-and-fetch-phone-debug    # ...and copy it back to ./dist"
              echo "  just deploy-phone-debug             # ...and install it on the Mi Pad 4"
              echo "  just mipad-logcat                   # Tail logs from the Mi Pad 4"
              echo "  just --list                         # See all available recipes"
            else
              echo "🚀 Quick start (on a remote build host):"
              echo "  ./gradlew :app:phone:assembleLibreDebug   # Build debug APK (phone)"
              echo "  ./gradlew :app:phone:installLibreDebug    # Install to connected device"
              echo "  ./gradlew :data:testLibreDebugUnitTest :core:testLibreDebugUnitTest"
            fi
          '';
        };
      };
    };
}

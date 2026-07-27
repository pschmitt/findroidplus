# TODO

## FINDROID-7: Dependency currency (Renovate/Dependabot)

- [ ] Review upstream Findroid's dependency updates since this fork diverged and
      selectively pull in the ones that still make sense (don't blindly merge -
      this fork has diverged substantially from upstream in places)
- [x] Enable Renovate or Dependabot on this repo so dependency versions stay
      current going forward without manual tracking. First tried Dependabot
      (2026-07-18) since it needs no GitHub App install, but it produced zero
      PRs in 6 days despite `.github/dependabot.yml` being live and a scheduled
      Monday run passing — switched to Renovate instead (`renovate.json`
      restored to its pre-2026-07-18 content: `config:recommended` +
      `schedule:weekly` + `:semanticCommits`, kotlin/ksp grouped, `dependencies`
      label; validated with `renovate-config-validator`). Removed
      `.github/dependabot.yml` to avoid duplicate/conflicting automation.
      **Manual follow-up required**: install the Renovate GitHub App
      (https://github.com/apps/renovate) on `pschmitt/findroidplus` specifically
      — this is the one step that can't be done from a commit, and is exactly
      why the old inherited `renovate.json` was never actually active despite
      looking configured. Done (2026-07-24, set to "All repositories"). Even
      with the app installed, Renovate still produced nothing at first - this
      repo is a real GitHub fork (`fork: true`, parent
      `jarnedemeulemeester/findroid`), and Renovate disables itself on forks by
      default to avoid spamming them with irrelevant PRs. Added
      `"forkProcessing": "enabled"` to `renovate.json` to override that.

Status: in progress (2026-07-18) - automation enabled; the manual "review and
selectively pull in upstream dependency updates" item is still open and requires
human judgment.

## FINDROID-43: QR-code device provisioning

Scan-to-configure a new Findroid+ install from an already-configured instance,
instead of retyping server URL/credentials and Sonarr/Radarr/Seerr config by
hand on every new device.

Core export/import flow (biometric-gated encrypted QR export on phone, scan
+ apply + restart on import, versioned payload, editable Jellyfin/Sonarr/
Radarr/Seerr overrides, custom `findroidplus://` scheme + deep link, JVM unit
tests) is implemented and merged - see git log for FINDROID-43 commits.

- [ ] Not done: TV-side export (phone-only in v1, see FINDROID-43's own
      scope note above) and interactive on-device UX testing of the full
      flow (biometric prompt → generate → scan → apply) - verified so far
      via `just lint`/`just test`/a full `assembleLibreDebug` compile and a
      real CI-signed release install on both test devices (Mi Pad 4, Pixel
      5 "px5"), but nobody has actually tapped through the feature yet.

Status: mostly done (2026-07-27). Core export/import flow implemented,
compiles, lints, and unit tests pass; release APK built with the real CI
signing key and installed on both physical test devices. Still needs an
actual hands-on run-through of the QR scan/generate UX before calling this
fully done.

## FINDROID-44: Remote configuration of a running Findroid+ instance

Manage a Findroid+ instance running on another device (e.g. push a
Sonarr/Radarr auto-download rule, or a one-off episode download, to the Mi
Pad 4's instance) remotely, without touching that device directly.

- [x] Architecture chosen: no dedicated relay/server. Reuses Jellyfin's own
      per-user `DisplayPreferences` custom-data API (`customPrefs: Map
      <String,String>`) as the transport, scoped to `(displayPreferencesId
      ="findroidplus-remoteconfig", userId, client)` - every instance
      already talks to this same Jellyfin account continuously, so this
      needs zero new infrastructure and works for any fork user. Auth is
      implicit (same Jellyfin session that already gates everything else),
      not a separate mechanism.
- [x] Wire format: `RemoteConfigCommand` (`data/.../models/
      AutoDownloadRemoteCommand.kt`) is a `@Serializable sealed interface`
      with three variants sharing one JSON-serialized queue under
      `customPrefs["pending"]` - `ReconcileRules` (persists an ongoing
      auto-download rule, replaying `AutoDownloadRuleRepository
      .reconcileRules`'s own parameters verbatim rather than modeling
      add/remove separately), `EvaluateNow` (one-time "download whatever
      currently matches this scope, right now," no rule persisted - mirrors
      a local bulk download made without "also download new episodes"), and
      `DownloadItem` (a single already-known item + media source,
      immediate - the "this episode" case). A device heartbeat registry
      (`RemoteDeviceInfo`) lives alongside it under `customPrefs["devices"]`
      so a controller can list "which of my other devices are out there."
- [x] `RemoteConfigRepository` (interface in `data`) /
      `RemoteConfigRepositoryImpl` (impl in **`core`**, not `data` - applying
      `EvaluateNow`/`DownloadItem` needs `AutoDownloadRuleEvaluator`/
      `Downloader`, both `core`-only, and `data` has no dependency on `core`)
      implement enqueue (`pushRuleUpdate`/`pushDownloadWithScope`/
      `pushItemDownload`) and periodic apply (`syncNow`, via
      `RemoteConfigWorker`/`RemoteConfigScheduler`, 15-minute WorkManager
      floor, unconditional). The actual sync decision logic (which commands
      to apply/expire/dead-letter, which devices to prune) is a pure
      top-level function (`planRemoteConfigSync`, `data/.../repository/
      RemoteConfigSyncPlan.kt`) rather than a method on the impl, agnostic
      to which command subtype it's handling, so it's unit-testable without
      mocks - this `data` module has no mocking framework, and the existing
      convention here (`QueueStatusMatchingTest` et al.) is to extract pure
      branching logic into plain functions instead. `RemoteConfigSyncPlanTest`
      covers device-scoping, TTL expiry/dead-lettering, mixed command
      types, and the "device known-stale vs simply never seen yet"
      distinction (a real bug the tests caught - the first implementation
      dead-lettered commands for any device absent from the registry, not
      just ones confirmed stale via heartbeat TTL, which would have
      silently dropped rules pushed to a device before its very first
      sync).
- [x] Controller UX, two entry points, both sharing one `RemoteDevicePicker`
      component (`app/phone/.../film/components/RemoteDevicePicker.kt`,
      modeled 1:1 on `JellyfinServerUserPicker` from the QR export screen):
  - The dedicated auto-download rule editor
    (`AutoDownloadRulesScreen.kt`'s `EditRuleDialog`) - default "This
    device" applies to Room as before, picking another device calls
    `pushRuleUpdate` instead and shows a "Rule sent to X" toast
    (channel-based one-shot event, same pattern as `SearchEvent`/
    `DeleteItemEvent` elsewhere in the app).
  - The regular one-off Download popup (`DownloadScopeDialog.kt`, opened
    from `ItemButtonsBar`'s Download button on Show/Season/Episode
    screens) - the bulk/season scope branches to `pushDownloadWithScope`
    (mirroring each screen's local `downloadWithScope` exactly: an
    `EvaluateNow` command when seasons are picked, a `ReconcileRules`
    command too when "also download new episodes" is on - independent of
    each other, matching local semantics), and the Episode screen's
    "this episode" immediate case (new `DownloaderAction.PushDownload`,
    handled in `DownloaderViewModel` since that's what owns
    `Downloader.downloadItem` locally) branches to `pushItemDownload`.
- [x] Pull-to-refresh added to the auto-download rules screen
    (`PullToRefreshBox`, same Material3 indicator as Downloads/Library/
    Home) - drives an immediate `RemoteConfigRepository.syncNow()` instead
    of waiting out `RemoteConfigWorker`'s 15-minute WorkManager floor.
    Added after discovering (via `adb shell cmd jobscheduler run -f`
    testing) that force-running the WorkManager job doesn't actually
    execute `doWork()` if WorkManager's own scheduler considers it "before
    schedule" - it silently re-defers instead, which is why an early manual
    test looked like the push "didn't work." Pull-to-refresh sidesteps
    WorkManager's timing entirely by calling `syncNow()` directly.
- [x] Verified via remote `:app:phone:compileLibreDebugKotlin`, `ktfmtCheck`,
      and `:data:testDebugUnitTest`/`:core:testLibreDebugUnitTest` on
      rofl-13 - all green (2026-07-28, after fixing two real cross-module
      issues the first build caught: `core` was missing the
      kotlinx.serialization plugin/dependency, and `planRemoteConfigSync`/
      `RemoteConfigSyncPlan` were `internal` in `data`, invisible from the
      impl once it moved to `core`).
- [x] Partial on-device verification (2026-07-28, CI-signed release install
      on Mi Pad 4 and px5): confirmed live against the real Jellyfin
      server - app launches cleanly (no Hilt/DI crash from the new
      `RemoteConfigRepository`/`Downloader` injections), the Download
      dialog's device picker correctly lists a real other device ("Pixel
      5") fetched from the shared `DisplayPreferences` registry, proving
      the transport/heartbeat round-trip genuinely works end to end. Did
      **not** complete an actual push+download, since px5 was in active
      use at the time and confirming would have started a real multi-GB
      background download on it without more explicit go-ahead - stopped
      short of that deliberately rather than risk it.
- [ ] Not done: a completed push→receive round trip (queue a command from
      one device, confirm the target actually downloads/applies it) -
      blocked on both test devices being free/idle at the same time.
- [ ] Not done: TV-side support - this only touches the phone module.

Status: implementation done (2026-07-28), passing remote build/lint/unit
tests, and spot-verified live against the real server on both physical test
devices. Still needs one fully hands-off push→receive run and has no
TV-side counterpart yet.

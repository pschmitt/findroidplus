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
- [x] Completed push→receive round trip (2026-07-28): pushed a single-episode
      download from the Mi Pad 4 to px5; px5's own natural (unforced)
      `RemoteConfigWorker` cycle picked it up a few minutes later (confirmed
      via logcat - a real "Starting work"/"Worker result SUCCESS", not the
      earlier forced-run deferral) and the episode landed as a completed
      file in its downloads folder, right size and timing for the pushed
      item. First forced-run attempts on both devices hit the same
      before-schedule WorkManager deferral pull-to-refresh was built to
      work around - the natural periodic cycle is what actually delivered
      it here.
- [x] Removal/management gap closed: previously, once a rule or download was
      pushed, the origin device had no visibility or control over it - it'd
      have to be undone by hand on the target. Fixed with:
  - Each device now also publishes a summary of its own currently-active
    auto-download rules (`RemoteActiveRuleSummary`) alongside its heartbeat,
    refreshed every `syncNow()` - the wire format
    (`data/.../models/AutoDownloadRemoteCommand.kt`) is now a
    `@Serializable sealed interface RemoteConfigCommand` (`ReconcileRules`/
    `EvaluateNow`/`DownloadItem`) carrying an `originDeviceId` (so a
    controller can find its own still-pending pushes) and a `displayName`
    resolved once at push time (avoids re-querying Jellyfin just to render
    a management list, and survives the item being renamed/deleted
    server-side afterwards).
  - New `RemoteConfigRepository` methods: `pushRemoveRule` (just
    `pushRuleUpdate` with an empty scope - `reconcileRules` already treats
    that as "clear everything for this series," so no new apply-side logic
    was needed), `listPendingCommandsFromThisDevice`/`cancelPendingCommand`
    (retract a push before its target has even applied it).
  - New dedicated screen: **Settings → Downloads → Remote devices**
    (`app/phone/.../presentation/settings/remotedevices/`) - lists other
    devices with their active rules (each removable, with a confirm
    dialog) and this device's own still-pending pushes (each cancelable,
    no confirm needed since it's non-destructive). Spot-verified on Mi Pad
    4: correctly lists "Pixel 5," relative "last seen" time, and an
    accurate empty "No active rules" state; pull-to-refresh works.
- [x] Per-device opt-out: a "Allow remote management" toggle at the top of
      the Remote devices screen (`AppPreferences.remoteManagementEnabled`,
      default on). Turning it off calls
      `RemoteConfigRepository.setRemoteManagementEnabled(false)`, which
      removes this device's own entry from the shared registry immediately
      (not just once its heartbeat goes stale) and drops any commands still
      queued *for* it - doesn't touch commands this device has queued *for
      others*, since opting out is about not being managed, not about
      withdrawing pushes already sent elsewhere. `syncNow()` no-ops
      entirely while disabled.
- [x] Show posters on the Remote devices screen: each active-rule row now
      resolves and renders the real `FindroidShow` poster
      (`RemoteDevicesViewModel.resolveShowPosters`, concurrent per-show
      `jellyfinRepository.getShow` calls, best-effort) - a
      `RemoteActiveRuleSummary` only carries an id + name on the wire, not
      enough to render a poster, so the viewing device resolves it itself
      via the same shared Jellyfin session.
- [ ] Not done: TV-side support - this only touches the phone module.

Status: implementation done (2026-07-28), including cross-device rule/
download management (remove/cancel), a dedicated Remote devices screen with
poster art, and a per-device opt-out. Passing remote build/lint/unit tests
and spot-verified live against the real server on both physical test
devices, including one full unforced push→receive round trip. No TV-side
counterpart yet.

## FINDROID-45: `findroid-cli` - Termux command-line download management

A shell script (bash + curl/jq + socat) for Termux, with three distinct
command groups:

- **Remote** (`devices`, `rule push`/`remove`, `download push`, `pending
  list`/`cancel`): reuses the exact same Jellyfin `DisplayPreferences`
  shared-bucket transport FINDROID-44 built - the CLI participates in the
  same cross-device mesh as any real Findroid+ install, just without a Room
  DB/Downloader of its own to apply commands sent *to* it.
- **Local download** (`download get`/`list`/`rm`): a lightweight
  download-it-yourself path - resolves an item via the Jellyfin API,
  `curl`s the file straight to local storage. Does not touch the real
  Android app's own downloads at all.
- **Local control** (`local pair`, `local settings get`/`set`, `local
  download trigger`, `local debug`): actually configures the *real running
  Findroid+ app* on the same device - added after the user corrected the
  original scope ("my goal is to configure findroidplus itself, and not
  replace it"), since the two groups above only ever act as an independent
  peer, never touching the app's real settings/Downloader/credentials.

Local control transport: `core/.../localcontrol/LocalControlServer.kt` runs
an `android.net.LocalServerSocket` (Linux abstract namespace - reachable
from any process on the device, Termux included, bypassing Android's
per-app filesystem sandboxing) named `findroidplus_control`. Every accepted
connection's peer uid is read via `LocalSocket.peerCredentials`
(kernel-verified, unspoofable) - this is the real authorization boundary,
chosen over a plain loopback-TCP+token design specifically because it lets
the app show the user the *actual* connecting package name, not a
self-reported label. Auth is a pairing handshake (`local pair`): the CLI
sends `{"type":"pair_request","clientId":...}` over the still-open
connection, the app shows an Approve/Deny notification
(`PairingNotifier`/`PairingActionReceiver`) naming the real caller, and on
approval issues a random 256-bit token (only `SHA-256(token)` is persisted,
via `LocalControlAuth`/`SecureCredentialStore`) that's re-validated against
the *same peer uid* on every later call, not just the token string. Off by
default (`AppPreferences.localControlEnabled`), toggled + paired-client
list/revoke in a new Settings > Local CLI access screen
(phone-only, `app/phone/.../presentation/settings/localaccess/`).
Endpoints (`LocalControlRouter`): `GET`/`PATCH /settings/downloads` (via
`DownloadSettingsBridge`, the 10 real download `AppPreferences`),
`POST /downloads/trigger` (resolves the item, calls the app's own
`Downloader.downloadItem` exactly as `RemoteConfigRepositoryImpl` does for
a remote push), `POST /debug/proxy` (forwards to Jellyfin/Sonarr/Radarr/
Seerr using the app's already-stored credentials, reusing
`PvrHttpClient`/`PvrConfiguration`), `GET`/`DELETE /pair/clients`.

- [x] Remote + local-download groups implemented, shellcheck-clean,
      `bash -n` syntax-checked, JSON wire shape hand-verified against
      `AutoDownloadRemoteCommand.kt`'s kotlinx.serialization output.
- [x] Local control implemented end-to-end: `LocalControlServer`/
      `LocalControlAuth`/`LocalControlRouter`/`DownloadSettingsBridge`/
      `PairingNotifier`/`PairingActionReceiver` (core), the `localaccess`
      Settings screen (phone), and the CLI's `local` command group -
      shellcheck-clean, `bash -n` syntax-checked, arg-parsing/JSON-building
      paths smoke-tested against fake response fixtures.
- [ ] Not done: real end-to-end test against an actual Jellyfin server for
      the remote/local-download groups (no live server credentials were
      available in this environment).
- [ ] Not done: real on-device test of the pairing handshake + local
      control endpoints (needs the actual Findroid+ app running with
      "Local CLI access" enabled - a Linux abstract-namespace socket can't
      be meaningfully faked in this dev sandbox, only code-reviewed).

Status: remote/local-download groups implemented and smoke-tested
(2026-07-28) but never run against a live server. Local control API
(pairing flow) designed and implemented (2026-07-28) per the user's
explicit pivot away from "CLI as independent peer" toward "CLI configures
the real app" - needs a real on-device pairing test next.

## FINDROID-46: Onboarding screen redesign

- [ ] Redesign the onboarding screen layout
  - [ ] Make the primary button(s) vertical and bigger
  - [ ] Move the "Learn more about Jellyfin" button to the top-left corner

## FINDROID-47: Automatic backups don't actually run

- [ ] Investigate why scheduled auto-backups never fire - reported
      (2026-07-28) that no automatic backup has ever run despite
      `AppPreferences.autoBackupEnabled`/`autoBackupIntervalMinutes` being
      configured; `AutoBackupScheduler`/the worker behind it needs a real
      on-device check (is the periodic work actually enqueued? does it run
      and no-op, or never fire at all?).
  - [ ] Backup filenames should include the device name.
  - [ ] Rename "findroid" to "findroidplus" in backup filenames.

## FINDROID-48: Re-group the main Settings screen

- [ ] The main Settings screen currently greets the user with a long, flat
      wall of top-level categories - re-organize into fewer, more sensibly
      grouped sections rather than a 1:1 header per existing group (an
      earlier pass just added section labels to the existing groups
      as-is; this is the follow-up restructuring). Concrete examples from
      the user (2026-07-28):
  - [ ] "Cache" settings probably belong under "Network".
  - [ ] "Language" might be better homed under "Player".
  - [ ] "Offline mode" can probably go under "Downloads".
  - [ ] General principle: fewer top-level entries, each one a coherent
        theme, not a 1:1 mapping of every existing category to its own row.

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

A shell script (bash + curl/jq) for Termux, with three distinct command
groups:

- **Remote** (`devices`, `rule push`/`remove`, `download push`, `pending
  list`/`cancel`): reuses the exact same Jellyfin `DisplayPreferences`
  shared-bucket transport FINDROID-44 built - the CLI participates in the
  same cross-device mesh as any real Findroid+ install, just without a Room
  DB/Downloader of its own to apply commands sent *to* it.
- **Local download** (`download get`/`list`/`rm`): a lightweight
  download-it-yourself path - resolves an item via the Jellyfin API,
  `curl`s the file straight to local storage. Does not touch the real
  Android app's own downloads at all.
- **Local control** (`local token set`, `local settings get`/`set`, `local
  download trigger`, `local debug`): actually configures the *real running
  Findroid+ app* on the same device - added after the user corrected the
  original scope ("my goal is to configure findroidplus itself, and not
  replace it"), since the two groups above only ever act as an independent
  peer, never touching the app's real settings/Downloader/credentials.

Local control transport went through two designs, the first of which
turned out to be fundamentally broken on real devices:

1. **First attempt**: `android.net.LocalServerSocket` (a Linux
   abstract-namespace unix socket), with `LocalSocket.peerCredentials`
   giving a kernel-verified caller uid and a pairing handshake (notification
   Approve/Deny, then a token) for auth. On-device testing (2026-07-28)
   found this doesn't work at all: connecting from a different app
   (Termux) gets `EACCES` under SELinux enforcing (the normal state on
   every real device) and only succeeds under permissive mode - confirmed
   by directly toggling `setenforce` on a rooted test device. SELinux's
   default policy keeps arbitrary `untrusted_app` domains isolated from
   each other for raw local-socket IPC; no app-level fix can work around
   that boundary.
2. **Current design**: a `ContentProvider`
   (`core/.../localcontrol/LocalControlProvider.kt`, authority
   `${applicationId}.localcontrol`), whose `call()` method is Binder-backed
   - the IPC mechanism Android's own SELinux policy is written to permit
   between apps - and reachable from a plain shell via the OS's own
   `content call` command (no compiled helper needed in Termux). Auth is a
   single bearer token (`LocalControlAuth.getOrCreateToken()`/
   `regenerateToken()`), shown in Settings > Local CLI access and
   regeneratable at will, rather than a per-client pairing handshake -
   simpler, and a `call()` invoked via a shell command doesn't carry
   meaningful "this is Termux" caller identity the way an app-to-app Binder
   call would, so per-client tracking wasn't buying anything real. Request/
   response bodies travel as base64-encoded JSON extras (`token`/`method`/
   `path`/`body` in, `status`/`body` out) since raw JSON can't safely
   round-trip through `Bundle`'s `toString()` output or shell-argument
   passing. Off by default (`AppPreferences.localControlEnabled`).

Endpoints (`LocalControlRouter`, unchanged across both transport designs):
`GET`/`PATCH /settings/downloads` (via `DownloadSettingsBridge`, the 10 real
download `AppPreferences`), `POST /downloads/trigger` (resolves the item,
calls the app's own `Downloader.downloadItem` exactly as
`RemoteConfigRepositoryImpl` does for a remote push), `POST /debug/proxy`
(forwards to Jellyfin/Sonarr/Radarr/Seerr using the app's already-stored
credentials, reusing `PvrHttpClient`/`PvrConfiguration`).

- [x] Remote + local-download groups implemented, shellcheck-clean,
      `bash -n` syntax-checked, JSON wire shape hand-verified against
      `AutoDownloadRemoteCommand.kt`'s kotlinx.serialization output.
- [x] Local control implemented **and verified end-to-end on real
      hardware** (Mi Pad 4, 2026-07-28): enabled the toggle, read the real
      token off the Settings screen (via `uiautomator dump` - the token's
      own base64 charset made a couple of characters genuinely ambiguous
      to read from a screenshot, e.g. `O` vs `0`), ran `findroid-cli local
      settings get` and `local settings set maxParallelDownloads=N` from
      actual Termux (not adb shell) and got real data back both ways,
      reverted the test value afterward.
  - [x] Found and fixed a second real-device-only blocker beyond the
        SELinux one below: `content call`'s external-access path needs
        `android.permission.ACCESS_CONTENT_PROVIDERS_EXTERNALLY`, a
        signature-level permission `pm grant` refuses to hand out even as
        root ("not a changeable permission type") - it's implicitly held
        by the special `shell` uid (why plain `adb shell content call`
        testing worked earlier) but never by a regular app's own uid
        (Termux's, when run as itself). `su -c 'content call ...'` runs it
        as root the same way `adb shell` does, which does work - so
        **a rooted device is required for `local` commands specifically**
        (findroid-cli now routes through `su -c` automatically; everything
        else in the script is unaffected).
- [ ] Not done: real end-to-end test against an actual Jellyfin server for
      the remote/local-download groups (no live server credentials were
      available in this environment).

Status: remote/local-download groups implemented and smoke-tested
(2026-07-28) but never run against a live server. Local control API
redesigned (2026-07-28) from a socket+pairing scheme (found to be blocked
by SELinux on real devices) to a `ContentProvider`+single-token scheme, per
the user's own suggestion ("something more android-native... AIDL? Binder
IPC?") - implemented and verified end-to-end on real hardware the same day,
including finding and fixing the root-requirement blocker above.

## FINDROID-46: Onboarding screen redesign

- [x] Redesign the onboarding screen layout
  - [x] Make the primary button(s) vertical and bigger
  - [x] Move the "Learn more about Jellyfin" button to the top-left corner

Status: done (`f2933311`, 2026-07-28) - `WelcomeScreen.kt`: "Continue" is now
a taller full-width button with larger type, "Learn more" moved to a
corner-pinned text link out of the main action stack.

## FINDROID-47: Automatic backups don't actually run

- [x] Investigate why scheduled auto-backups never fire - root cause found:
      enabling the toggle before picking a destination folder silently
      cancelled/never enqueued the periodic work, with no error surfaced
      (`autoBackupLastError` was only ever written from inside the worker's
      own failure paths, which never got a chance to run).
  - [x] Backup filenames should include the device name.
  - [x] Rename "findroid" to "findroidplus" in backup filenames.

Status: done (`05978a68`, 2026-07-28) - `AutoBackupScheduler` now records a
specific error when bailing out enabled-but-no-folder (surfaced via the
existing Backup & Restore error banner) and clears it once a folder is
picked; filename format extracted into shared `BackupFileNaming` (device
model + `findroidplus` prefix) used by both the scheduled and manual backup
paths. Verified via remote compile/ktfmtCheck/unit tests on rofl-13.

## FINDROID-48: Re-group the main Settings screen

- [x] The main Settings screen currently greets the user with a long, flat
      wall of top-level categories - re-organize into fewer, more sensibly
      grouped sections rather than a 1:1 header per existing group (an
      earlier pass just added section labels to the existing groups
      as-is; this is the follow-up restructuring). Concrete examples from
      the user (2026-07-28):
  - [x] "Cache" settings probably belong under "Network".
  - [x] "Language" might be better homed under "Player".
  - [x] "Offline mode" can probably go under "Downloads".
  - [x] General principle: fewer top-level entries, each one a coherent
        theme, not a 1:1 mapping of every existing category to its own row.

Status: done (`33ba5d1d`, 2026-07-28) - Settings root now shows 7 coherent
groups instead of 10; every individual preference row preserved, only its
top-level home changed. Verified app:phone/app:tv compile + ktfmtCheck on
rofl-13.

## FINDROID-49: Simplify findroid-cli to local-only, drop root requirement, fix gaps

Reported (2026-07-28) after FINDROID-45's local control feature shipped:
- The CLI should ONLY talk to the local app - drop the "remote" command
  group (`devices`/`rule push`/`rule remove`/`pending list`/`pending
  cancel`, the cross-device Jellyfin `DisplayPreferences`-mesh peer
  behavior from FINDROID-44) and the plain "local download" group
  (`download get`/`list`/`rm`, direct-curl-to-storage, bypassing the real
  app entirely) altogether. `local` stops being a prefixed subcommand
  group and becomes the CLI's only mode - e.g. `findroid-cli settings get`
  instead of `findroid-cli local settings get`.
- Why does it require root? Root is a genuine, real limitation of the
  `ContentProvider`+`content call` transport specifically (see FINDROID-45:
  `content call`'s external-access path needs
  `ACCESS_CONTENT_PROVIDERS_EXTERNALLY`, a signature-level permission only
  the `shell`/root uid holds - confirmed `pm grant` refuses it even as
  root). The real fix is switching transport again, to a **loopback TCP
  socket** (127.0.0.1) instead of a ContentProvider: unlike the
  abstract-namespace Unix socket tried before THAT (blocked by SELinux
  domain separation), plain TCP loopback between apps is ordinary,
  unrestricted socket I/O gated only by the `INTERNET` permission the app
  already has - no SELinux wall, no signature permission, no root, and
  plain `curl`/`bash`'s own `/dev/tcp` works directly from Termux. The
  existing token-based auth (`LocalControlAuth`) and endpoint dispatch
  (`LocalControlRouter`) don't care about transport and can be reused
  unchanged - only the "how a request arrives" layer changes again.
- "Why can't I list downloads?" - there simply isn't a
  `GET /downloads` (list) endpoint yet, only
  `GET`/`PATCH /settings/downloads` (settings), `POST /downloads/trigger`
  (start one), and `POST /debug/proxy`. Needs a real "list current/
  in-progress downloads" endpoint (reuse
  `JellyfinRepository.getDownloads()` or equivalent) and a matching CLI
  command.
- "pls make sure *all* the cli commands work!" - every remaining command
  (after the simplification above) needs an actual on-device pass, the
  same way `settings get`/`settings set` were verified for FINDROID-45,
  not just a code read-through.

- [x] Switch local control transport from `ContentProvider` to a loopback
      TCP server (reuse `LocalControlAuth`/`LocalControlRouter` as-is).
      Implemented as `LocalControlServer` (NanoHTTPD, 127.0.0.1:48411,
      Bearer-token auth, honest enable-toggle that reports a real bind
      failure instead of silently claiming success).
- [x] Add a `GET /downloads` (list) endpoint + CLI `download list` command.
- [x] Strip `cli/findroid-cli` down to local-only: remove the remote
      command group and the local-download command group entirely, drop
      the `local` prefix so its subcommands are top-level.
- [x] Verify every remaining command end-to-end on a real device, no root
      required. Done on Mi Pad 4 via real Termux (not just `adb shell`):
      `token set`, `settings get`, `settings set`, `download list` (new),
      `download trigger` (error path - underlying logic unchanged from
      FINDROID-45's already-verified pass), `debug jellyfin`. Caught and
      fixed a real bug along the way: NanoHTTPD's `parseBody()` only
      special-cases `POST`/`PUT`, so `PATCH /settings/downloads` silently
      dropped its body - fixed by reading the raw body directly via
      `Content-Length` instead. px5 (Pixel 5) still needs this pass -
      its wireless-debugging connection was down (device locked/asleep)
      at verification time and wasn't force-reconnected, per the standing
      rule against bypassing a lock screen.

Status: **done** (2026-07-28) on Mi Pad 4; px5 re-enabled after this entry
was written and got the release build + full CLI pass separately (see git
log). CLI also gained `--json` and a pretty-TSV-by-default table renderer
for every data command in a same-day follow-up.

## FINDROID-50: browse Jellyfin/Sonarr/Radarr/Seerr + trigger downloads by name

Requested (2026-07-28): grow `findroid-cli` toward covering most of what the
app itself can do/configure - codified as a standing rule in `AGENTS.md`'s
new "CLI parity" section (new app functionality with a CLI-shaped equivalent
gets a matching local-control endpoint + CLI subcommand in the same change).
First concrete step, per the user: a way to browse the Jellyfin/Sonarr/
Radarr/Seerr libraries, plus triggering a download by name/season instead of
needing an item UUID up front (`findroid-cli download "Rick and Morty"
"Season 3"`).

- [x] `LocalControlRouter`: `GET /jellyfin/libraries`, `GET /jellyfin/items`
      (parentId/search/pagination), `GET /jellyfin/search` - all via the
      already-typed `JellyfinRepository` methods, no raw HTTP needed.
- [x] `LocalControlRouter`: `GET /sonarr/series`, `GET /radarr/movies`,
      `GET /seerr/requests`, `GET /seerr/discover/{path}`,
      `GET /seerr/search` - via the already-typed `SonarrApi`/`RadarrApi`/
      `SeerrApi` clients (same ad hoc construction pattern
      `resolveProxyClient` already uses for the debug proxy).
- [x] `POST /downloads/trigger-by-name`: resolve a movie/show by name
      (exact case-insensitive match, else single-candidate, else an
      ambiguous-match error listing candidates), then for a show resolve
      season/episode by number or name and trigger every matching episode's
      download. Deliberate guard rail: a bare show name with no season and
      no explicit `all` flag is rejected rather than silently downloading
      an entire series.
- [x] CLI: `library list`/`library browse`, `search`, `sonarr list`,
      `radarr list`, `seerr requests`/`discover`/`search`, and reworking
      `download`'s dispatch so anything past `list`/`trigger` is treated as
      a by-name trigger. Also added `--json`/pretty-TSV-table output to
      every data command (a same-day follow-up ask, see FINDROID-49).
- [x] On-device verification on Mi Pad 4 (real Termux, no root): every new
      command confirmed against the real library/Sonarr/Radarr/Seerr data,
      including the by-name guard rail (bare show name rejected), the
      exact-match-priority resolution ("Star Trek" doesn't get flagged
      ambiguous despite several "Star Trek: ..." shows existing), a real
      ambiguous-match error (two shows both literally named "Extras"), and
      a real season+episode resolution + trigger attempt.

Status: **done** (2026-07-28) on Mi Pad 4; deployed to px5 too. Both devices
got the release build from the same batch as this entry.

**Follow-up (2026-07-28, same day)** after real usage turned up gaps:
- [x] `search`/`library browse` were unintentionally movie/show-only (via
      `getSearchItems`) - individual episodes never matched. Repointed
      `search` at the unrestricted `/jellyfin/items` endpoint (already used
      by `library browse`) and added a `--type TYPE[,TYPE...]` filter to
      both, so e.g. `search --type episode "Salute Your Morts"` finds an
      episode whose title isn't also a show/movie name. Removed the now-
      redundant `/jellyfin/search` endpoint (`getSearchItems` was a strict
      subset of what `/jellyfin/items` already does).
- [x] `download NAME` couldn't resolve a bare episode title at all (its
      candidate search was movie/show-only) - it now also matches episodes
      directly, e.g. `download "Salute Your Morts"`. Ambiguous-match errors
      for episodes/seasons now include the series name (`"Pilot" (Severance
      S1E1)` vs. just `"Pilot"`) since a bare title alone doesn't
      disambiguate across shows.
- [x] `download ITEM_ID` (an id copied from `library browse`/`search`
      output, no `trigger` keyword) now auto-detects a UUID-shaped first
      argument and forwards to the id-based trigger, instead of searching
      for the literal UUID string as a title and failing.
- [x] `download -- NAME` added: forces by-name interpretation even when
      NAME collides with a reserved subcommand word (`list`/`trigger`/
      `cancel`/`remove`).
- [x] `download list` only ever showed movies - `FindroidShow.sources` is
      always empty (a show has no media source, only its episodes do), so
      every TV download was silently invisible. Now expands each downloaded
      show's seasons/episodes (offline DB reads, same pattern
      `DownloadsViewModel.refreshDownloads()` already uses) into the flat
      per-source list.
- [x] Added real in-progress-download visibility: each source's `status`
      ("downloading"/"completed") plus, while downloading, a live progress
      snapshot (percent/bytes/speed/eta) via the existing
      `Downloader.getProgressFlow()`. `download list --active`/`--completed`
      filters either way.
- [x] Added `download cancel DOWNLOAD_ID` (`Downloader.cancelDownload`) and
      `download remove ITEM_ID...` (`Downloader.deleteItems`) - both already
      existed on `Downloader` for the app's own Downloads screen, just
      weren't exposed to the local-control API yet. `cancel` verified live
      on Mi Pad 4 (triggered a real not-yet-downloaded episode, confirmed it
      in `download list --active` with real percent/size, cancelled it,
      confirmed it vanished from both `--active` and the completed list -
      no orphaned DB rows). `remove`'s success path is unverified live -
      the test device's Jellyfin server (`tv.brkn.lol`) became DNS-
      unreachable mid-session (unrelated to this change; confirmed via a
      plain `debug jellyfin` connectivity check), and its error paths
      (missing args, both single/multi ITEM_ID forms) were checked instead.
      Reuses `Downloader.deleteItems` verbatim (same method the app's own
      Downloads screen delete action already calls), so this is a real but
      low-severity gap - re-verify the success path once that device's
      Jellyfin connectivity is back.
- [x] Fixed a real correctness bug found while touching this: every
      `downloadId` in a JSON response was a raw 64-bit `Long` number -
      `jq`/JS-style JSON parsers only preserve ~53 bits of integer
      precision, so an extreme id could silently corrupt on the wire and
      break a later cancel/list-by-id call. Encoded as a string everywhere
      instead (`triggerDownload`, `triggerDownloadByName`, `download list`) -
      confirmed on-device with a real negative-valued 64-bit id (`downloadId`
      is `UUID.randomUUID().mostSignificantBits`, which is often negative
      as a signed `Long`) round-tripping through `download list`/`cancel`
      exactly, with no precision loss.
- [x] Follow-up fix: `check_response`'s blanket error text didn't reach
      into `download NAME`'s per-episode `triggered[].error` field, so a
      partial-batch failure (e.g. an episode with no media source) printed
      an unhelpful "409 unknown error". Now renders every row's own
      result/error whenever the response carries a `triggered` array,
      regardless of overall HTTP status. Confirmed on-device (a real
      episode with no media source now shows "No media source" in the
      table instead of "unknown error").

## FINDROID-51: Serve findroid-cli itself from the "Local CLI access" page

Requested (2026-07-28): let a user grab `findroid-cli` directly from the
device it's meant to control, the way Shizuku's `rish` shell client is
downloadable/installable straight from the Shizuku app - instead of the
current requirement to separately clone/copy the script from the
`findroidplus` repo onto the device before it's usable.

- [x] Design how the script gets served: a new unauthenticated `GET /cli` on
      `LocalControlServer`, routed before the bearer-token check (the one
      deliberate exception - it's a public script, not user data), returning
      the bundled `findroid-cli` asset verbatim with `Content-Type: text/plain;
      charset=utf-8` and `Content-Disposition: attachment;
      filename="findroid-cli"`.
- [x] "Local CLI access" settings screen: added a "Get findroid-cli" section
      (mirroring the existing token copy-to-clipboard UX) showing `curl
      http://127.0.0.1:48411/cli -o findroid-cli && chmod +x findroid-cli`
      with its own Copy button. QR code / share-intent considered out of
      scope for v1 - a straight curl one-liner already covers the Termux
      workflow the ticket asked for.
- [x] Keep the bundled script in sync with `cli/findroid-cli` at build time:
      `core/build.gradle.kts` registers a `CopyFindroidCliAsset` task and
      wires it in as a generated asset directory via AGP's variant API
      (`variant.sources.assets.addGeneratedSourceDirectory`) rather than
      writing straight into `src/main/assets` - the naive
      dependsOn-on-merge-tasks approach passed compile/ktfmtCheck/unit tests
      but failed a full `assembleLibreRelease` with "Property has implicit
      dependency" (lint-vital's model-generation task also reads
      `src/main/assets` without an explicit dependency edge, so its ordering
      vs. the copy was undefined). The variant-API registration fixes that by
      letting AGP wire every consumer (merge, lint-vital, packaging) itself.

Verified: `just gradle rofl-13.brkn.lol ":app:phone:compileLibreDebugKotlin"
":core:compileLibreDebugKotlin" "ktfmtCheck" ":core:testLibreDebugUnitTest"
":data:testDebugUnitTest"` -> BUILD SUCCESSFUL. A full CI-signed
`just build-fetch --release --phone` (forced with `--rerun-tasks`) also
succeeded end to end (`BUILD SUCCESSFUL in 3m 14s, 330 actionable tasks`);
extracting `assets/findroid-cli` from the resulting APK and diffing it
against `cli/findroid-cli` confirmed byte-for-byte (26021 bytes) parity.
Installed on the Mi Pad 4 (`dev.pschmitt.findroidplus`) and confirmed live:
`curl http://127.0.0.1:48411/cli` from Termux returns `200` with the correct
headers and exact script body, with no `Authorization` header sent - and a
sanity check that `GET /downloads` (an authenticated route) still returns
`401` without a token, confirming the `/cli` exception didn't leak auth
bypass onto any other route.

Status: done (2026-07-28) - implemented, remote-build-verified, and confirmed
end-to-end on real hardware (Mi Pad 4).

## FINDROID-52: findroid-cli command aliases

Requested (2026-07-28): add short aliases for the more common/verbose
`findroid-cli` subcommands so frequent usage doesn't require typing the full
word every time - e.g. `dl` for `download`, `rm`/`del` alongside `remove` for
`download remove`. Survey the current command list (`cli/findroid-cli`:
`token`, `settings`, `library`, `search`, `sonarr`, `radarr`, `seerr`,
`download` [`list`/`trigger`/`cancel`/`remove`/by-name], `debug`) for other
good alias candidates (e.g. `ls` for `list`, `lib` for `library`) while
implementing this, not just the two examples given.

- [x] Design and implement alias dispatch in `cli/findroid-cli` (top-level
      command aliases and, where it applies, subcommand aliases like
      `download rm`/`download del` alongside the existing `download remove`).
      Keep the canonical long-form names as the ones shown in `--help`/usage
      text; aliases are just shortcuts, not replacements.
- [x] Update the script's usage/help text to mention the aliases.
- [x] shellcheck-clean, `bash -n` syntax-checked, and re-verify the aliased
      commands behave identically to their canonical forms (ideally on a real
      device the way prior findroid-cli work was verified, per FINDROID-45/49/50).

Status: done (2026-07-28) - **static-verified only** (`shellcheck`/`bash -n`
clean, plus a stubbed-dispatch trace confirming every alias reaches the exact
same `cmd_*` function with the exact same args as its canonical form), not
re-verified on a real device like FINDROID-45/49/50 were. Aliases added:
top-level `lib` (library), `dl` (download), `cfg` (settings); subcommand
`ls` (list, everywhere it appears: `library`/`sonarr`/`radarr`/`download`),
`br` (library browse), `trig` (download trigger), `c` (download cancel),
`rm`/`del` (download remove), `req` (seerr requests), `disc` (seerr
discover). Deliberately skipped: `token`/`debug` (already terse),
`search`/`sonarr`/`radarr`/`seerr` top-level (already short, and a
single-letter alias would collide across `settings`/`search`/`sonarr`/
`seerr` all starting with `s`), `seerr search`/`settings get`/`settings set`
(already short). The by-name reserved-word list (needing `download --
NAME`) now also covers the new aliases (`ls`/`trig`/`c`/`rm`/`del`), noted
in the usage text.

## FINDROID-53: findroid-cli version subcommand + auto-download rule management

Requested (2026-07-28): two additions to `findroid-cli`/the local control API -
a `version` subcommand reporting both the CLI's own version and the running
app's build info, and a new `autodownload` command group to manage this
device's own local auto-download rules (add/list/remove) without opening the
app UI. Distinct from FINDROID-44's cross-device rule-push mechanism
(`RemoteConfigCommand`/`pushRuleUpdate`) - this is purely local rule
management, evaluated by this device's own WorkManager.

- [x] Added `CLI_VERSION="1.0.0"` to `cli/findroid-cli` (first time the script
      tracks its own version), with a comment to bump it on meaningful future
      changes.
- [x] New authenticated `GET /info` on `LocalControlRouter` returning
      `{"versionName", "versionCode", "gitRevision"}`. Since `core` can't
      reference `app/phone`'s own generated `BuildConfig` directly (and
      `app/tv` has a separate one it doesn't use for local control), added a
      small `AppVersionInfo` interface in `core` and bound it from
      `app/phone`'s `AppModule` (`@Provides` reading
      `dev.jdtech.jellyfin.BuildConfig.VERSION_NAME/VERSION_CODE/GIT_REVISION`),
      injected into `LocalControlRouter` alongside its other dependencies.
- [x] `findroid-cli version`: always prints the CLI's own version (works even
      with no token configured/app unreachable); additionally calls `GET
      /info` and prints the app's versionName/versionCode/gitRevision via the
      same `--json`/table conventions as `settings get` when reachable - a
      failed app request is non-fatal (still exits 0).
- [x] New auto-download rule management endpoints on `LocalControlRouter`,
      scoped to the current server+user (`AppPreferences.currentServer` /
      `JellyfinRepository.getUserId()`), backed by the existing
      `AutoDownloadRuleRepository` (`reconcileRules`/`deleteRule`/
      `deleteRulesForShow`/`getRules`/`getRulesForSeries`) - no new
      persistence, no touching FINDROID-44's push path:
      - `GET /autodownload/rules` - every rule for this device, with each
        `seriesId`/`seasonId` resolved to a show name/season number so the
        CLI doesn't need a second lookup.
      - `POST /autodownload/rules` - resolves a show by `seriesId` or `query`
        (case-insensitive exact match / sole search result / ambiguous-match
        error, scoped to `BaseItemKind.SERIES` only - reusing
        `triggerDownloadByName`'s resolution template), a season scope
        (`season`/`seasons` by number or name via the existing
        `matchByNumberOrName` helper, `"all": true` for every existing
        season, or neither for a future-seasons-only rule), then calls
        `reconcileRules(...)`.
      - `POST /autodownload/rules/remove` - by `id` (a single rule row) or by
        show (`seriesId`/`query`, clearing every rule for that series at
        once via `deleteRulesForShow` - mirrors
        `AutoDownloadRulesScreen.kt`'s own delete action).
      - Every mutation calls `RemoteConfigRepository.syncNow()` afterwards
        (mirroring `AutoDownloadRulesViewModel.republishActiveRulesSummary()`
        from the same-day `6335e38c` fix), non-fatal on failure, so the
        change is republished to the shared device registry immediately
        instead of waiting on the next periodic WorkManager sync.
- [x] `findroid-cli autodownload` command group (alias: `auto`):
      - `list`/`ls` - table of `ID/SHOW/SCOPE/ENABLED/ONLY_NEW/ONLY_UNWATCHED`.
      - `add`/`a` `NAME_OR_ID [--season S[,S...]] [--all-seasons]
        [--future-seasons] [--only-new] [--only-unwatched]` - resolves
        `NAME_OR_ID` exactly like `download NAME` (a UUID-shaped argument
        routes straight to `seriesId`).
      - `remove`/`rm`/`del` `NAME_OR_ID` - clears every rule for a show.
      - `remove-id RULE_ID` - clears a single rule row.
      - Updated `usage()` with the new `version` and `autodownload` entries
        and their aliases.

Verified remotely: `just gradle rofl-13.brkn.lol
":core:compileLibreDebugKotlin" ":app:phone:compileLibreDebugKotlin"
"ktfmtCheck" ":core:testLibreDebugUnitTest" ":data:testDebugUnitTest"` ->
BUILD SUCCESSFUL in 1m 25s, 117 actionable tasks executed, no warnings in the
touched files. `shellcheck cli/findroid-cli` and `bash -n cli/findroid-cli`
both clean. Locally stubbed `local_request` to trace `cmd_version`,
`cmd_autodownload_add` (season list, UUID+all-seasons, future-only-by-default,
season+future-seasons combined), `cmd_autodownload_remove`(-`_id`), and
`cmd_autodownload_list`'s table rendering - every request body/response
rendering matched the router's expected shape, and every alias/dispatch path
reached the correct function.

A full CI-signed `just deploy --release --phone` build installed successfully
on the Mi Pad 4 (`Performing Streamed Install` -> `Success`). On-device,
enabled/located the already-configured "Local CLI access" token via
`uiautomator`-driven navigation (Settings > Downloads > Local CLI access),
then ran the *real* served `GET /cli` script through the device's actual
Termux installation (its own `bash`/`curl`/`jq`, invoked via root since the
device is Magisk-rooted, rather than a host-side simulation):
- `findroid-cli version` -> printed `findroid-cli: 1.0.0` plus the real
  running app's `versionName 2.11.0` / `versionCode 47` / `gitRevision
  v2.11.0-2-g6335e38c51c0-dirty`.
- `findroid-cli autodownload list` (alias `auto ls`) correctly listed this
  device's 4 pre-existing real rules (Rick and Morty S9 + future seasons,
  House of the Dragon S3 + future seasons) with show names/season numbers
  resolved.
- `findroid-cli autodownload add "Mushoku Tensei: Jobless Reincarnation"
  --season 1 --only-new --only-unwatched` (resolved by name via a real
  Jellyfin search) created exactly the requested season-1 rule; a second add
  with no season flags correctly fell back to a future-seasons-only rule,
  and confirmed the *existing* `AutoDownloadRuleRepository.reconcileRules`
  invariant holds through this new path too - the future-seasons row came
  back `onlyNewEpisodes: true` even though `--only-new` wasn't passed for
  that call, since a future rule is always only-new by definition.
- `logcat` confirmed each add/remove triggered a real `GET`+`POST
  .../DisplayPreferences/findroidplus-remoteconfig` round-trip against the
  live Jellyfin server (`tv.brkn.lol`) immediately after the mutation -
  `syncNow()` republishing verified end-to-end, not just called.
- `findroid-cli autodownload remove "Mushoku Tensei: ..."` and, separately,
  `autodownload remove-id RULE_ID` (targeting just the newly-added rule's own
  numeric id) both worked, leaving the device's original 4 rules untouched
  throughout.
- Also installed the same signed release build on a second connected device
  (`R6AIB700W850L7G`, ASUS_AI2302) - install succeeded, but "Local CLI
  access" had never been enabled on that device before and enabling it was
  out of scope for this pass, so no CLI verification was done there; not a
  blocker per the ticket's own guidance.

Status: done (2026-07-28) - implemented, remote-build-verified, and confirmed
end-to-end on real hardware (Mi Pad 4) using the actual served CLI script run
through the device's own Termux binaries, including a real Jellyfin-server
round-trip for the immediate rule-sync republish. Second device
(ASUS_AI2302) received the same release build but wasn't otherwise exercised.

## FINDROID-54: Merge auto-download rules and remote devices screens

Requested (2026-07-28): "lets merge the auto-download rules and remote
devices views. They are more or less the same in the end." Both screens
were fundamentally "a show + season scope + toggle/remove", just scoped to
different devices - `AutoDownloadRulesScreen` for this device's own rules,
`RemoteDevicesScreen` for other devices' rules and this device's pending
pushes.

- [x] Merged into one screen, one Settings entry. Both existing
      `@HiltViewModel`s (`AutoDownloadRulesViewModel`, `RemoteDevicesViewModel`)
      kept as-is and instantiated side by side via `hiltViewModel()` in the
      merged `AutoDownloadRulesScreen` composable - no ViewModel merge, no
      new cross-module DI.
- [x] One `Scaffold`/`TopAppBar`/`PullToRefreshBox`/`LazyColumn`, top to
      bottom: the "Allow remote management" toggle, a "This device" header
      + this device's own show rule rows (edit/delete dialogs unchanged),
      an other-devices' "Remote devices" header + their active-rule sections
      (only shown when at least one other device exists - no jarring "no
      devices" message next to this device's own rules), then a "Pending"
      section for this device's own not-yet-applied pushes if any. A single
      generic empty state only shows up when there's truly nothing anywhere
      (no local rules, no other devices, no pending pushes).
- [x] Pull-to-refresh drives both ViewModels' `refresh()` - each already
      just launches its own `viewModelScope` coroutine and returns
      immediately, so calling both back to back already runs their
      `syncNow()`+reload concurrently; `isRefreshing` is the OR of both.
      Both event/toast paths wired: `AutoDownloadRuleEvent.RuleSentToDevice`
      via `ObserveAsEvents`, and remote-devices' `RemoveActiveRule`/
      `CancelPendingCommand` toasts inline in the merged `onAction`.
- [x] Navigation: collapsed `RemoteDevicesRoute` into `AutoDownloadRulesRoute`
      in `NavigationRoot.kt`. Removed `SettingsEvent.NavigateToRemoteDevices`
      end to end (`SettingsViewModel`'s now-deleted `remote_devices_title`
      `PreferenceCategory` → `SettingsEvent.kt` → phone/TV `SettingsScreen.kt`/
      `SettingsSubScreen.kt` `when` branches → `NavigationRoot.kt`'s
      `navigateToRemoteDevices` callback), leaving one `auto_download_rules`
      `PreferenceCategory` whose summary now reads "...on this device and
      others". `RemoteDevicesScreen.kt` gutted down to just the reusable,
      now-non-private `RemoteManagementToggleRow`/`DeviceSection`/
      `PendingCommandRow` composables the merged screen imports
      (`RemoteDevicesViewModel`/`RemoteDevicesState`/`RemoteDevicesAction`
      untouched). Deleted now-dead strings (`remote_devices_summary`,
      `remote_devices_empty` in core; `remote_devices_title`/
      `remote_devices_summary` in the settings module, which had their own
      duplicate copies) after grepping every reference first; kept
      `remote_devices_title` (core) since it's reused as the merged screen's
      "Remote devices" section header.

- [x] On-device verification on Mi Pad 4: `just deploy --release --phone`
      (CI-signed), then navigated Settings → Downloads → "Auto-download
      rules" (one entry now, confirmed the old "Remote devices" entry is
      gone). Merged screen renders exactly as designed: "Allow remote
      management" toggle, "This device" header with both real local rules
      (House of the Dragon, Rick and Morty - edit dialog opens with correct
      state incl. the "Push to" device picker, delete-confirmation dialog
      opens with correct show name, both canceled cleanly without touching
      real data), a "Remote devices" header showing "Pixel 5"'s real active
      rule, and no pending-commands section (correctly hidden when empty).
      Confirmed end-to-end against real other-device data: tapped the trash
      icon on Pixel 5's "Rick and Morty" rule, got the real confirm dialog,
      confirmed removal - "Removal sent to Pixel 5" toast fired and a
      "Pending" section appeared with a cancelable row; tapped its cancel (X)
      - "Push canceled" toast fired and the pending row disappeared, rule
      preserved on Pixel 5 (verified by re-reading the screen - the real
      rule was left untouched, nothing destructive done to the user's
      account). Pull-to-refresh worked (re-synced, "last seen" ticked
      forward, no crash). Top app bar's settings-icon action still navigates
      to Downloads settings correctly. `adb logcat` showed no
      exceptions/crashes for the whole session.
- [ ] On-device verification on px5 (second connected device, registered in
      the app as "Pixel 5"): the same CI-signed release APK installed
      successfully (`adb install -r`, no signature mismatch), but the app
      now crashes on every launch with a pre-existing, **unrelated**
      `javax.crypto.AEADBadTagException` inside
      `SecureCredentialStoreModule.provideEncryptedSharedPreferences` during
      Hilt's `BaseApplication.onCreate` - i.e. before any code this ticket
      touched ever runs. Looks like an Android Keystore key on that specific
      device that no longer decrypts its existing `EncryptedSharedPreferences`
      (not caused by this change - nothing in this diff touches DI, crypto,
      or `core`'s Kotlin source, only `app/phone`, `app/tv`, `settings`, and
      `core`'s string resources). `AGENTS.md`'s documented fix for
      keystore/signature trouble is uninstall+reinstall, which wipes that
      device's Room DB, playback positions, and downloads - the same doc
      says to confirm with the user first since it's not throwaway data, so
      that wasn't done here. Left px5 as found (crashing, pre-existing
      state); this needs the user's go-ahead before anyone wipes its app
      data.

Status: implemented, remote-compile-verified (`compileLibreDebugKotlin`/
`compileDebugKotlin` for `app:phone`/`modes:film`/`settings`, `ktfmtCheck`),
and confirmed working end-to-end on Mi Pad 4, including a real cross-device
rule removal + cancel against "Pixel 5"'s actual data. px5 itself couldn't be
exercised - it hit a pre-existing, unrelated keystore crash blocking app
launch entirely, left as-is pending the user's OK to wipe its local data.

**Note found along the way (2026-07-28)**: px5 (registered in-app as "Pixel
5") crashes on every launch of this same release build with a pre-existing
`javax.crypto.AEADBadTagException` in
`SecureCredentialStoreModule.provideEncryptedSharedPreferences` during Hilt's
`BaseApplication.onCreate` - before any code touched by FINDROID-53/54 ever
runs. Looks like an Android Keystore key on that device that no longer
decrypts its existing `EncryptedSharedPreferences`. The documented fix
(uninstall+reinstall) wipes that device's Room DB/playback positions/
downloads, so it needs the user's go-ahead first - not done yet.

## FINDROID-55: Re-organize Settings root into fewer, more logical sections

Requested (2026-07-28), several tweaks in one sitting, all landing on the
same `topLevelPreferences` structure in `SettingsViewModel.kt`:

- [x] Move "Local CLI access" out of Downloads > auto-download into the Data
      section, alongside Backup and Provision device.
- [x] Downloads screen: reorder so "Auto-download" comes before
      "Auto-delete".
- [x] Rename "Connections" to "Accounts and credentials" and move the
      Account section to be the first entry on the Settings root.
- [x] Fold "Player" into "Interface" (renamed "Appearance") - one combined
      visual+playback section instead of two top-level entries. Fixed the
      "MPV options" sub-screen's breadcrumb, which had hardcoded
      `settings_category_player` as its parent index.
- [x] Fold "Network" (general request/connect/socket/PVR-search timeouts,
      plus "Cache") into Downloads, the same way Cache was already folded
      into Network in an earlier pass.
- [x] Move the Data section (Backup, Provision device, Local CLI access) to
      sit just above About.
- [x] Rename the Downloads screen's "New item notifications" section header
      to just "Notifications".
- [x] Add a "Timeouts" header to the (previously unnamed) request/connect/
      socket/PVR-search timeout group folded in from Network.

Final top-level order: Account, Appearance, Downloads, Data, About (was:
Interface, Player, Account, Data, Download, Network, About). Every
individual preference row preserved; only which top-level group it lives
under, its label, and the overall ordering changed.

Status: done (2026-07-28) - `:settings`/`:app:phone`/`:app:tv` all compile
and `ktfmtCheck` passes on rofl-13. Not separately verified on-device beyond
compile (a pure data/config reorganization, no new runtime logic).

## FINDROID-56: Automatic backup silently fails on cloud-backed folders

Reported (2026-07-28, Mi Pad 4): automatic backup failed with "Could not
create backup file - check the backup folder is still accessible" while a
manual backup with the same folder/params succeeded immediately after -
user suspected the destination (Google Drive) was the cause.

- [x] Confirmed: `AutoBackupScheduler`'s `WorkManager` job had no network
      constraint at all (only `setRequiresBatteryNotLow(true)`), unlike
      `RemoteConfigScheduler`/`QueueStatusScheduler` elsewhere in this
      codebase, which both require `NetworkType.CONNECTED`. Manual backup
      uses `ActivityResultContracts.CreateDocument` - an interactive
      foreground picker, always run with the user present and therefore
      virtually always with live connectivity. Automatic backup reuses the
      persisted folder grant and calls `DocumentFile.createFile()`
      non-interactively from a background job that can fire with no network
      at all - a cloud-backed provider (Drive) genuinely needs network to
      create/write a file, unlike a local folder, and silently returns
      `null` (not an exception) when it can't reach the backend.
- [x] Added `setRequiredNetworkType(NetworkType.CONNECTED)` to
      `AutoBackupScheduler`'s constraints, matching the existing pattern.

Status: done (2026-07-28) - root-caused and fixed; verified remote compile.
Not yet re-confirmed on Mi Pad 4 that a subsequent scheduled run actually
succeeds against the Drive-backed folder (would require waiting out a real
backup interval) - the fix addresses the confirmed root cause, but a live
before/after backup-success confirmation on that device hasn't been done.

## FINDROID-57: Manual-import dialog polish + remote device picker polish

Small UI requests (2026-07-28):

- [x] Manual-import "reject" flow (`ManualImportSheet.kt`): the footer
      button read "Delete & blacklist" even though the confirm dialog it
      opens lets you toggle removal-from-client and blocklisting
      independently. Renamed to "Remove" (matching the dialog's own confirm
      button) and gave the dialog the same icon treatment as
      `DeleteSelectedDownloadsDialog`/`RemovePvrQueueItemDialog` elsewhere on
      the Downloads screen (icon+text title, icon+text confirm/dismiss
      buttons) instead of plain text-only buttons.
- [x] `RemoteDevicePicker` (shared by the auto-download rule editor and the
      regular Download popup): moved to the very top of the rule editor's
      `EditRuleDialog` instead of below the season/scope toggles, and
      restyled as a tonal control (leading device icon, rounded
      `surfaceContainerHigh` surface) instead of a plain list row, plus
      per-row device icons and primary-color emphasis on the selected
      device in its own picker dialog.

Status: done (2026-07-28) - verified remote compile/`ktfmtCheck` for both;
not separately re-verified on-device beyond the general release builds
installed for other same-day work.

## FINDROID-58: Seerr icon + reworded header on search's Seerr section

Requested (2026-07-28): on the Home page's search, when a result isn't in
the Jellyfin library, add a Seerr icon to the "Not in your library" header
and reword it.

- [x] `SearchBar.kt`'s Seerr-results header now uses `SectionServiceIcons`
      (the same brand-icon-plus-title `Row` pattern `HomeDiscoverSection`
      already uses) instead of bare text.
- [x] Reworded `media_seerr_section` from "Not in your library — request via
      Seerr" to "Not in your library — yet!" now that the icon itself
      conveys "via Seerr". Shared string, so `LibraryScreen.kt`'s own Seerr
      section picks up the new wording too (icon not added there - out of
      scope, only the Home search header was requested).

Status: done (2026-07-28) - verified remote compile/`ktfmtCheck`. Not
separately verified on-device.

## FINDROID-59: Delete downloaded files when removing a remote device's rule

Requested (2026-07-28): "when we delete auto-download rules from remote
devices we should give the user the option to also delete the already
downloaded files, just like for local devices." Today, removing a rule for
*this* device (`AutoDownloadRulesViewModel.deleteShowRule`) offers a "also
delete downloaded episodes" checkbox (`ClearDownloadsDialog`); removing a
rule shown for an *other* device (`RemoteDevicesAction.RemoveActiveRule` ->
`RemoteConfigRepository.pushRemoveRule`) has no such option - it just queues
a rule-clear command with no way to also ask that device to delete its
already-downloaded files for that show.

- [x] `pushRemoveRule` is just `pushRuleUpdate` with an empty scope, applied
      on the target device via `RemoteConfigRepositoryImpl.applyReconcileRules`
      -> `AutoDownloadRuleRepository.reconcileRules(...)`. Added
      `alsoDeleteDownloads: Boolean = false` to the `ReconcileRules` wire
      command (defaulted so an old-format command from a not-yet-upgraded
      device still decodes fine), threaded through `pushRemoveRule`/
      `pushRuleUpdate`.
- [x] `applyReconcileRules` (on the *receiving* device), when that flag is
      set, resolves the show's downloaded episodes
      (`database.getEpisodesByShowId(seriesId)` + `toFindroidEpisode`,
      mirroring `deleteShowRule`'s exact local pattern) and calls the
      existing top-level `clearDownloads(items, database, downloader)`
      helper after the rule itself is cleared - gated on the command
      actually being a full clear (`seasonIds` empty and `alsoFutureSeasons`
      false), not merely carrying the flag, so a `ReconcileRules` that still
      leaves part of the show's scope active never deletes anything
      regardless of what the pushing device set. `RemoteConfigRepositoryImpl`
      already had `database`/`downloader` injected - no new DI wiring
      needed.
- [x] UI: `RemoteDevicesScreen.kt`'s `ActiveRuleRow` confirm dialog now
      reuses `ClearDownloadsDialog` (same component the local delete flow
      already uses) instead of a plain `AlertDialog`, so it gets the same
      "also delete downloaded episodes" checkbox for free.
      `RemoteDevicesAction.RemoveActiveRule`/`RemoteDevicesViewModel` thread
      the new `alsoDeleteDownloads` flag through to `pushRemoveRule`.
- [ ] Real cross-device on-device verification (like FINDROID-44's own
      original testing) - push a remove-with-delete from one device, confirm
      the other device's rule *and* its downloaded files for that show are
      both gone after its next sync - not done yet.

Status: implemented (2026-07-28) - remote compile
(`:data:compileDebugKotlin`/`:core:compileLibreDebugKotlin`/
`:app:phone:compileLibreDebugKotlin`), `ktfmtCheck`, and
`:data:testDebugUnitTest`/`:core:testLibreDebugUnitTest` all clean. Not yet
verified on a real device - no live cross-device push-with-delete round
trip confirmed yet.

## FINDROID-60: findroid-cli start/stop the app

Requested (2026-07-28): "findroid-cli should have a start/stop command to
start/stop the app."

- [x] `stop`: no OS-level way for an unprivileged Termux process to
      force-stop another app (`android.permission.FORCE_STOP_PACKAGES` is
      signature/privileged-only - the same wall FINDROID-45's original
      ContentProvider transport hit for
      `ACCESS_CONTENT_PROVIDERS_EXTERNALLY`, confirmed `pm grant` refuses it
      even as root). Sidestepped by asking the app to exit *itself* instead:
      new authenticated `POST /app/stop` on `LocalControlRouter` that starts
      a background thread which sleeps 300ms then calls
      `Runtime.getRuntime().exit(0)` (same call `Activity.restartProcess()`
      already uses elsewhere), returning its 200 immediately so NanoHTTPD
      has time to actually write the response before the process dies -
      killing synchronously in the handler would race that write and the
      client would just see a dropped connection. No root needed.
- [x] `start`: the app isn't running yet in this case, so there's nothing to
      ask over the local control API - this is the one command that doesn't
      go through `local_request` at all. Shells out straight to `am start`
      (default package `dev.pschmitt.findroidplus`, overridable via a new
      `FINDROID_PACKAGE_NAME` env var for a debug/staging install). Works
      without root as long as the calling shell is in the foreground at the
      moment the command runs - Android's background-activity-start
      restrictions only block launches from processes with no visible UI.
      **Found and fixed a real bug during on-device verification**: the
      launcher activity is *not* `<applicationId>.MainActivity` - this
      app's `applicationId` (`dev.pschmitt.findroidplus`) was rebranded
      independently of its actual Kotlin/manifest `namespace`, which is
      still `dev.jdtech.jellyfin` (unchanged across every build variant).
      `am start` needs `<applicationId>/dev.jdtech.jellyfin.MainActivity` -
      confirmed via `cmd package resolve-activity` against the real
      installed package after the original guess failed with "Activity
      class ... does not exist" both unprivileged and as root.
- [x] `CLI_VERSION` bumped to 1.1.0. Updated `usage()` with both new
      commands and the new `FINDROID_PACKAGE_NAME` env var.

Status: **done** (2026-07-28) - verified end-to-end on the Mi Pad 4 (real
device, real token, real install): `am start` (corrected component) brings
the app up; `POST /app/stop` takes it back down (confirmed `GET /info`
stops responding); `am start` again brings it back up (confirmed `GET
/info` responds with the real `versionName`/`versionCode`/`gitRevision`).
Also confirmed the same-day Settings reorg (FINDROID-55) and hide-token
toggle are both live and correct on this device along the way.

## FINDROID-61: Rename `dev.jdtech` package namespace to `dev.pschmitt`

Requested (2026-07-28), found while debugging FINDROID-60's `am start`: this
fork's `applicationId` was rebranded to `dev.pschmitt.findroidplus`
(FINDROID-1) but the actual Kotlin/manifest package namespace across the
whole codebase is still `dev.jdtech.jellyfin` (upstream Findroid's
original) - `app/phone/build.gradle.kts`'s `namespace` is still
`dev.jdtech.jellyfin`, unchanged by any `applicationIdSuffix`. This is
exactly what caused FINDROID-60's first `am start -n
<applicationId>/<applicationId>.MainActivity` guess to fail - the real
component is `<applicationId>/dev.jdtech.jellyfin.MainActivity`.

- [ ] Not started/not scoped in detail yet - `dev.jdtech` currently appears
      in **558 files** (`.kt`/`.xml`/`.kts`) across every module: every
      `package dev.jdtech...` declaration and matching import, every
      module's `namespace` in its `build.gradle.kts`, `AndroidManifest.xml`
      references, generated Hilt/R class references, and likely test
      resources/fixtures too. This is a large, invasive, whole-repo rename,
      not a quick find/replace - needs real planning before starting:
      - Confirm whether `applicationId` and `namespace` actually need to
        match (they don't strictly have to, and Android doesn't care - the
        motivation here is just "our own code should live under our own
        namespace, not upstream's", not a functional requirement).
      - Decide the plan for the rename itself (a scripted `sed`-based mass
        rename of `package`/`import` lines is probably viable given how
        mechanical Kotlin package renames usually are, but each module's
        `namespace` in Gradle, `AndroidManifest.xml` component names
        anywhere written as fully-qualified rather than relative, and any
        string/reflection-based package name usage - e.g. anything doing
        `packageManager.getLaunchIntentForPackage`-style lookups keyed off
        the OLD namespace, or serialized class names in stored preferences/
        backups - need explicit auditing, not just a blind rename).
      - Decide whether this is one giant PR or a staged/module-by-module
        migration, given the size.
      - Whatever tooling reads `am start -n dev.pschmitt.findroidplus/
        dev.jdtech.jellyfin.MainActivity` today (`findroid-cli start`,
        FINDROID-60) would need updating in lockstep if/when this lands.

Status: not started (2026-07-28) - logged as a known, deliberately
deferred, large-scope rename; needs a real plan before any code changes.

## FINDROID-62: findroid-cli self-update subcommand

Requested (2026-07-28): "I want a self-update subcmd for the findroid-cli!
it should well, update itself by fetching the 'new' bin via the local tcp
server on port 48411."

- [x] The app already serves the exact bundled `cli/findroid-cli` script,
      unauthenticated, at `GET /cli` (`LocalControlServer.CLI_PATH`, added
      for the bootstrap-download use case - "the same way Shizuku's `rish`
      client is downloadable straight from the Shizuku app"). No new
      app-side work needed - this is a CLI-only change.
- [x] New `update` command: `curl`s `${BASE_URL}/cli` directly (bypasses
      `local_request`, same as `start` - no token needed, no JSON), checks
      the response looks like a real script (shebang + a `CLI_VERSION=`
      line) before trusting it, compares that version against this
      process's own `CLI_VERSION`, and - if different (or `--force`) -
      writes it to a temp file next to the resolved self path
      (`readlink -f "$0"`, to follow a PATH symlink to the real file) and
      atomically `mv`s it over itself, preserving the executable bit.
      Skips with "already up to date" otherwise.
- [x] Bump `CLI_VERSION` to 1.2.0, document `update` in `usage()`.

Status: **done** (2026-07-28) - implemented and exercised against a local
stub HTTP server on 48411 standing in for the app (not a real device):
verified a real update (version bump + content replaced + executable bit
preserved), the "already up to date" skip, `--json` output, and both
failure guards (server unreachable, response that doesn't look like a
script). Not yet verified against the real running app on a device.

## FINDROID-63: findroid-cli --json should print just the response body

Requested (2026-07-28): "when invoking the cli with --json, let's just
return the body. status is just noise."

- [x] `print_response_json` (the shared helper behind every `--json` call
      site) now prints `.body` via `jq -c` instead of the whole
      `{status, body}` wrapper - the HTTP status is still used internally
      to set the exit code (2xx -> 0), same as before, just not printed
      anymore.
- [x] `cmd_debug`'s own separate `--json` branch (it doesn't go through
      `print_response_json` - a non-2xx there is proxied-service output,
      not a CLI-level failure) updated the same way: status now goes to
      stderr (`HTTP %s`, same as its non-JSON path already did) and stdout
      gets just `.body`.
- [x] Updated the `--json` help text in `usage()` to match ("Print the
      response's body as JSON instead of a table").

Status: **done** (2026-07-28).

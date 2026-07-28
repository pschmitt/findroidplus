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

- [ ] Design how the script gets served: likely a new unauthenticated (no
      bearer token - there's nothing to protect, it's a public script, not
      user data) `GET /cli` on the existing loopback `LocalControlServer`,
      returning `cli/findroid-cli`'s contents verbatim (bundled as an Android
      asset/resource at build time, not read from a live git checkout).
- [ ] "Local CLI access" settings screen: a visible URL/command a user can
      `curl` from Termux (e.g. `curl http://127.0.0.1:48411/cli -o
      findroid-cli && chmod +x findroid-cli`), or a QR code / share-intent
      shortcut, mirroring how Shizuku's own onboarding surfaces `rish`.
- [ ] Keep the bundled script in sync with `cli/findroid-cli` at build time
      (copy as a build step / Gradle task) rather than hand-duplicating it -
      two copies drifting apart would be worse than the current
      copy-it-yourself status quo.

Status: not started (2026-07-28) - added to the backlog, not implemented yet.

## FINDROID-52: findroid-cli command aliases

Requested (2026-07-28): add short aliases for the more common/verbose
`findroid-cli` subcommands so frequent usage doesn't require typing the full
word every time - e.g. `dl` for `download`, `rm`/`del` alongside `remove` for
`download remove`. Survey the current command list (`cli/findroid-cli`:
`token`, `settings`, `library`, `search`, `sonarr`, `radarr`, `seerr`,
`download` [`list`/`trigger`/`cancel`/`remove`/by-name], `debug`) for other
good alias candidates (e.g. `ls` for `list`, `lib` for `library`) while
implementing this, not just the two examples given.

- [ ] Design and implement alias dispatch in `cli/findroid-cli` (top-level
      command aliases and, where it applies, subcommand aliases like
      `download rm`/`download del` alongside the existing `download remove`).
      Keep the canonical long-form names as the ones shown in `--help`/usage
      text; aliases are just shortcuts, not replacements.
- [ ] Update the script's usage/help text to mention the aliases.
- [ ] shellcheck-clean, `bash -n` syntax-checked, and re-verify the aliased
      commands behave identically to their canonical forms (ideally on a real
      device the way prior findroid-cli work was verified, per FINDROID-45/49/50).

Status: not started (2026-07-28) - added to the backlog, not implemented yet.

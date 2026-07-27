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

## FINDROID-41: Overflow menu polish round 2 - right-alignment, favorite move, Show search

Fast-follow on FINDROID-38/40's `ItemOverflowMenu` work, all from live testing
feedback right after it shipped.

- [x] The overflow icon was sitting wherever it fell in `ItemButtonsBar`'s
      wrapping `FlowRow` (visually wedged between Trailer and Download tiles).
      Added a new `overflowContent` param, rendered outside the FlowRow
      entirely via an outer `Row(verticalAlignment = CenterVertically)` with
      the FlowRow on `Modifier.weight(1f)` - the overflow icon now always
      sits pinned to the row's right edge regardless of how many tiles wrap.
      `trailingContent` (the Show/Season "delete downloads" size tile) still
      wraps normally inside the FlowRow - only the overflow icon itself moved.
- [x] Favorite/unfavorite moved from `ItemMetaRow`'s inline toggle into the
      overflow menu (as a heart-icon `DropdownMenuItem`) on all four detail
      screens - watched/unwatched stays on the meta line, favorite didn't.
- [x] Search (auto)/(manual) `DropdownMenuItem`s now carry the Sonarr/Radarr
      brand icon (`Color.Unspecified` tint, same as the old `PvrSearchButton`
      tile) instead of no icon at all.
- [x] Show's overflow was missing a search entry entirely (FINDROID-40 only
      added Info+Delete there, reasoning Show had no pre-existing PVR search
      feature to move). Added "Search (auto)" - `ShowAction
      .SearchSeriesAutomatic` → `SonarrSearchRepository
      .searchSeriesByTmdbId` (`ShowViewModel` already had the repository
      injected for the delete cascade). No manual/interactive counterpart -
      Sonarr's release picker is per-episode, not per-series, so there's no
      clean single "pick a release for the whole series" flow to build.
  - [x] Along the way, found and fixed: the new Show aggregate Info dialog
        was showing "0 episodes" - `ShowState.episodeCount` was computed from
        `database.getEpisodesByShowId`, which only knows about episodes
        that were downloaded or individually visited, not the show's true
        total. Replaced with a real per-season `repository.getEpisodes(showId,
        season.id)` count sum (downloads-size itself correctly stays
        Room-based - that one's genuinely about local disk usage).

Status: **done** (2026-07-27). Verified via remote
`:app:phone:compileLibreDebugKotlin` and `ktfmtCheck` on rofl-13, plus
CI-signed installs on all three test devices.

## FINDROID-42: DropdownMenu mispositioned + Mark watched moved to overflow

- [x] `ItemOverflowMenu`'s `DropdownMenu` was landing in a visibly wrong spot
      relative to its ⋮ icon - our bug, not a platform quirk: Material3's
      `DropdownMenu` anchors to its nearest positioned parent, not whatever
      composable happens to precede it, and the icon + menu weren't wrapped
      in a shared `Box`. Added one.
- [x] Mark watched/unwatched moved from `ItemMetaRow`'s inline toggle into
      the overflow menu too (joining favorite, moved there in FINDROID-41) -
      `ItemMetaRow` no longer has any toggle machinery at all now (removed
      `played`/`favorite`/`onPlayedClick`/`onFavoriteClick` params and the
      `MetaToggle` composable entirely - dead code once nothing called them).

Status: **done** (2026-07-27). Verified via remote
`:app:phone:compileLibreDebugKotlin` and `ktfmtCheck` on rofl-13, plus
CI-signed installs on all three test devices.

## FINDROID-43: QR-code device provisioning

Scan-to-configure a new Findroid+ install from an already-configured instance,
instead of retyping server URL/credentials and Sonarr/Radarr/Seerr config by
hand on every new device.

- [x] Export side: Settings → "Provision device" (`QrExportScreen`, `data/.../qrsetup/QrConfigManager.buildEnvelope`)
      serializes the current instance's config into a JSON payload and
      renders it as a QR code (ZXing `core`, no camera/GMS dependency).
      Checkboxes (all on by default, hidden/disabled per-service when that
      service isn't configured) to selectively include Jellyfin, Sonarr,
      Radarr, and Seerr config in the payload.
  - [x] Jellyfin section: server address(es) + a chosen user's session
        (reuses `backup.BackupServer`/`Server`/`ServerAddress`/`User`
        scoped to one server/user). When more than one server/user
        combination is configured on the device, a picker
        (`JellyfinServerUserPicker`, radio-button `BaseDialog`, same pattern
        as `SettingsSelectDialog`) lets you choose which one to embed,
        defaulting to the currently-active one; hidden entirely when
        there's only one combination. Stretch goal (typing in credentials
        for a user *not already logged in* on this device at all) is still
        deferred, not implemented.
  - [x] Export screen gated behind a biometric/PIN prompt
        (`androidx.biometric` `BiometricPrompt`, `BIOMETRIC_WEAK or
        DEVICE_CREDENTIAL`) before rendering anything, plus
        `FLAG_SECURE` on the window while the code is displayed to block
        screenshots.
  - [x] Sonarr/Radarr/Seerr checkboxes collapsed under an "Advanced"
        expandable section (collapsed by default); Jellyfin stays top-level.
  - [x] The code regenerates automatically on every relevant change
        (checkbox, server/user picker, passphrase) - no "Generate" button.
        `QrExportViewModel` cancels any in-flight generate job before
        starting a new one so rapid toggling can't let a stale result
        clobber a newer one.
  - [x] Encryption is on by default, not opt-in: a random 12-character
        legible-alphabet passphrase (`QrExportViewModel.generatePassword`,
        `SecureRandom`, ~60 bits, excludes `0/O/1/I/L`) is generated on
        load. The passphrase field is read-only (nothing to type) with a
        show/hide eye toggle and a regenerate button - the idea is you read
        it off this screen and tell it to whoever's scanning, not type one
        in yourself.
- [x] Import side: "Scan QR code" entry point on the Welcome screen
      (`QrScanScreen`, CameraX `ImageAnalysis` + ZXing `MultiFormatReader`)
      that decodes the payload and applies it via
      `QrConfigManager.applyEnvelope` (`AppPreferences`, Room
      server/address/user rows, `SecureCredentialStore` secrets), shows a
      brief success confirmation, then does a full process restart
      (`Activity.restartProcess()`, same as the existing restore-from-backup
      flow) so the newly-current server/user take effect.
- [x] Payload is a `BackupCrypto`-wrapped (AES-256-GCM + PBKDF2, reused
      as-is from the backup feature), Base64-encoded blob with an optional
      user-supplied passphrase (blank = unencrypted) - same
      optional-password UX as the existing backup/restore feature.
- [x] Versioned from the start: `QrConfigEnvelope.version`
      (`data/.../qrsetup/QrConfigData.kt`), decode rejects payloads with a
      newer version than the client understands
      (`QrConfigCodec.UnsupportedVersionException`).
- [x] JVM unit tests for the pure encode/decode pipeline (crypto round-trip
      incl. wrong-password/corrupt-payload/unsupported-version cases, and a
      ZXing encode→decode round-trip) - `data/src/test/.../qrsetup/`.
- [x] Icons on the Welcome screen's buttons (Learn more/Continue/Scan QR
      code/Restore from backup), including a new hand-authored
      `ic_qr_code` drawable (no existing "qr code" glyph in this project's
      Feather-style icon set).
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

Rough idea, not yet designed: be able to manage a Findroid+ instance running
on another device (e.g. create Sonarr/Radarr auto-download rules on the Mi
Pad 4's instance) remotely, without touching that device directly.

- [ ] Figure out the actual architecture - this likely needs *some* server
      component the app can poll or receive pushes from, since Android apps
      aren't normally reachable inbound. Options to weigh: a lightweight
      companion service/relay, piggybacking on an existing always-on service
      the user already runs, or a pull-based model (app periodically checks
      for pending remote-config changes against a server) instead of a
      push/inbound one.
  - [ ] Whichever model is chosen, needs auth so only the owner can push
        config changes to their own instance(s).
- [ ] Scope which config should even be remotely manageable first (the
      motivating case is Sonarr/Radarr auto-download rules) rather than
      trying to make all settings remotely editable from day one.
- [ ] Revisit after FINDROID-43 lands - the QR export/import payload format
      and PVR-config data model built there likely overlaps heavily with
      whatever a remote-config channel would need to transmit.

Status: not started (2026-07-27) - design only, no implementation approach
chosen yet.

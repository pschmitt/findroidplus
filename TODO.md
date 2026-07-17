# TODO

## FINDROID-1: Rebrand fork to Findroid+

Rename the package and app to distinguish this fork from upstream Findroid.

- [x] App name: `Findroid+` (`app_name` in `core/src/{main,debug,staging}/res/values/strings.xml`)
- [x] Package / applicationId: `dev.pschmitt.findroidplus` (`app/phone`, `app/tv`;
      library module namespaces stayed `dev.jdtech.jellyfin.*` — internal only, not
      user-facing, not worth the churn of moving every source file)
- [x] Bump version to `2.0.0` (`buildSrc/src/main/kotlin/Versions.kt`, `APP_CODE` 32→33)
- [x] App icon: added a "+" badge (green circle, white plus) to
      `core/src/main/res/drawable/ic_launcher_foreground.xml` and
      `core/src/main/ic_launcher-playstore.png`. TV banner left as-is (lower priority,
      different composition).
- [x] Branding text updated: README (title, banner image regenerated, install
      instructions rewritten for Obtainium/GitHub Releases since this fork isn't on
      Google Play/Amazon/F-Droid/IzzyOnDroid under this package name), `welcome`/
      `welcome_text`/`privacy_policy_notice` strings, fastlane metadata
      (`title.txt`/`full_description.txt`), `release-latest.yaml` release-notes text.
      `sync-upstream.yaml` deliberately left untouched (must keep pointing at the real
      upstream repo).
  - Deferred: ~30 non-English translated `strings.xml` files still say "Findroid" in
    the `welcome`/`privacy_policy_notice` strings — cosmetic only, low priority for a
    single-maintainer fork, not blocking.
- [x] GitHub repo renamed `pschmitt/findroid` → `pschmitt/findroidplus` (`gh repo
      rename`), local `origin` remote updated, repo description updated to mention
      this is a vibe-coded fork with better download handling + Sonarr/Radarr
      integration. README badges/store links repointed or removed as appropriate
      (Play/Amazon/F-Droid/IzzyOnDroid links removed — not applicable to this fork;
      stat badges removed rather than repointed at the request of the user).
- [x] Verified: remote build (`just gradle rofl-13.brkn.lol ktfmtCheck
      :app:phone:assembleLibreDebug :app:tv:assembleLibreDebug`) succeeded; installed
      the phone debug build on the Mi Pad 4 and confirmed it launches
      (`dev.pschmitt.findroidplus.debug/dev.jdtech.jellyfin.MainActivity` resumed, no
      fatal errors in logcat).

Status: **done**.

## FINDROID-2: PVR/Seerr integration polish, round 2

Batch of UX todos around the Sonarr/Radarr/Seerr integrations and general navigation.

- [x] Rename the Jellyseerr integration to Seerr (rebrand); persisted pref/credential
      keys keep the legacy `jellyseerr` names so existing devices/backups keep working
- [x] Integrations settings: add the Sonarr/Radarr/Seerr brand logos (page was bland)
- [x] Integrations settings: "Get API key" link per service opening its web UI
      settings page (`{baseUrl}/settings/general` for Sonarr/Radarr, `{baseUrl}/settings`
      for Seerr)
- [x] Merge the Movies and TV Shows views into a single "Media" tab
      (All/Movies/Shows filter chips)
- [x] Native Seerr integration: no separate Discover tab; searching in Media also
      searches Seerr so missing content can be requested in place (recent requests show
      when opening search)
- [x] Devices without cellular connectivity (e.g. Mi Pad 4): disable the "Download
      using mobile data" setting, like "Download location" is disabled without
      external storage
- [x] Navigation: the settings cog only appears on some views - show it consistently
      on all top-level views
- [x] Pause/delete downloads in Sonarr/Radarr/Seerr (queue item actions, request
      cancellation). Note: the Sonarr/Radarr v3 APIs expose no per-item *pause* -
      pausing lives in the download client - so this is remove (with
      remove-from-client/blocklist flags) plus Seerr request cancellation.
- [x] Download dialog: behaves awkwardly when a show already has an auto-download rule
      and the download button is hit on a single episode - make it ergonomically sound
      ("This episode" is now always the preselected default; an existing rule is noted
      and stays untouched unless the bulk selection is edited)
- [x] Calendar: include the exact air time of episodes, in local time
- [x] Auto-backup filenames: use human-friendly timestamps
      (e.g. `xxx-2026-07-17T08:58:03+02:00`)

Status: **done** (2026-07-17). Verified via remote builds on rofl-13 (unit tests +
phone/TV compile) and a CI-signed install on the Mi Pad 4.

## FINDROID-3: UI overhaul round

- [x] Episode/movie detail pages: simpler, organized, ergonomic (dot-separated meta
      line, compact icon action row instead of labeled button bar, PVR search moved
      into the action row)
- [x] Integrations settings: Radarr logo looked odd (white disc) - use the official
      brand look (yellow arrow on the near-black disc); enable toggle on the same
      line as `<LOGO> Name <TOGGLE>`
- [x] Home: Seerr-powered show/movie discovery rows (trending/popular; cards open the
      detail view below, toggleable via Settings > Interface > Home)
- [x] Settings: merge "Servers", "Integrations" and "Users" into one section
      ("Connections" group)
- [x] Seerr items not yet in the library get a tap action opening a dedicated detail
      view (poster/backdrop, overview, status, request action) - reachable from the
      Media search results, recent requests, and Home discovery rows
- [x] "Unrequest" support: cancel a movie/show's open Seerr requests from the detail
      view (Seerr requests are per movie/season, so this lands at that granularity)

Status: **done** (2026-07-17). Verified via remote builds on rofl-13/rofl-14 (unit tests +
phone/TV compile); release APK installed on both test devices. Note: release builds only get
the persistent CI signature when the CI_KEYSTORE_* env vars are exported on the build host -
a plain `just build-fetch --release` signs with the host's throwaway debug keystore.

## FINDROID-4: navigation & connections round

- [x] Media: separate "Requested" tab/filter listing the Seerr requests
- [x] Calendar: entries that aren't in the library yet (no click action today) open the
      Seerr media detail view
- [x] Settings: one combined "Connections" view (Servers + Users + Integrations) instead
      of three separate screens; keyboard must not cover focused inputs (currently does
      for the Seerr fields)
- [x] Title icons for the Media, Downloads, Calendar and Settings views

Status: **done** (2026-07-17). Verified with remote `just lint` and Libre debug Kotlin
compilation for the phone and TV apps on rofl-13.

## FINDROID-5: Detail-page ergonomics, branding, settings & resilience round

- [x] Episode/movie detail pages: rework the action row so all buttons (incl. the PVR
      search button) look consistent, with labels (vertical space is available); the
      info button floats to the right of the title; mark watched/unwatched and favorite
      move up onto the meta line (runtime etc.); extra whitespace above the synopsis;
      restyle the context text (show name / season / episode number) and put the episode
      name above it — ergonomic, thought-about
- [x] Tablets only: drop the "Home" button next to "Back" (Home is already a menu entry)
- [x] Branding: app icon and about-page icons/banners must clearly read Findroid+, not
      upstream "Findroid"
- [x] Media view: icons for the All / Movies / Shows / Requested filter items (Seerr
      logo for Requested)
- [x] Connections settings: fold the dedicated Jellyfin "Servers" and "Users" screens
      into the Connections view itself, configured inline just like Sonarr/Radarr/Seerr
- [x] Sonarr/Radarr/Seerr configs: optional "advanced" settings — extra HTTP request
      header(s), HTTP basic auth, etc.
- [x] Settings: every entry gets an icon (Theme, Gestures, …)
- [x] Default image/media cache size: 50 MB
- [x] Downloads view: friendly empty state when nothing is downloaded yet — short hint
      text plus a link to Home ("To download media, open an episode/movie and press the
      download button")
- [x] Graceful degradation everywhere: Sonarr/Radarr/Seerr down → clear non-blocking
      errors; Jellyfin down → offer enabling Offline mode; no crashes or silent spinners

Status: **done** (2026-07-17). Verified with remote `ktfmtCheck` and Libre release
builds for phone and TV on rofl-14; the signed phone APK was installed on the ASUS
phone and Mi Pad 4.

## FINDROID-6: Downloads storage summary & PVR request granularity

- [x] Downloads screen: storage summary card (on-device usage + a single PVR
      "server storage" bar - Sonarr/Radarr assumed to share a disk rather than
      showing both; free/total resolved via `/rootfolder` matched against
      `/diskspace` by path, not just the largest visible mount) with color-coded
      usage bars (IEC/binary units - GiB/TiB - to match Sonarr/Radarr's own UI)
- [x] Download widgets (item detail pages + PVR queue rows): show the download
      size on the same line as the speed/ETA text
- [ ] Seerr: allow requesting/searching a specific season of a show that isn't in
      the Jellyfin library yet, not just the whole series - most requests are for
      "just season 1", not the entire show
- [ ] Downloads screen: the local download list takes a noticeable moment to
      appear on open - investigate why (should be instant, it's just a local DB
      read)

Status: in progress (2026-07-18) - storage summary and download-size shipped;
season-level Seerr requests and the downloads-list load delay still open.

## FINDROID-7: Dependency currency (Renovate/Dependabot)

- [ ] Review upstream Findroid's dependency updates since this fork diverged and
      selectively pull in the ones that still make sense (don't blindly merge -
      this fork has diverged substantially from upstream in places)
- [x] Enable Renovate or Dependabot on this repo so dependency versions stay
      current going forward without manual tracking. Went with Dependabot
      (`.github/dependabot.yml`, `gradle` + `github-actions` ecosystems, weekly,
      kotlin/ksp grouped) — zero extra setup, activates as soon as this is merged
      to `main`. Removed the stale `renovate.json` (inherited from upstream via
      `sync-upstream` merges; the Renovate GitHub App was never actually installed
      on this fork, confirmed via `gh pr list` finding no Renovate PRs against
      `pschmitt/findroidplus`) to avoid confusion/duplicate automation later.
      **Manual follow-up**: none needed for Dependabot itself. Optionally, enable
      "Dependabot alerts" under Settings > Code security (currently disabled on
      this fork per the GitHub API) if security-vulnerability alerts are wanted
      too — that's a separate toggle from version updates.

Status: in progress (2026-07-18) - automation enabled; the manual "review and
selectively pull in upstream dependency updates" item is still open and requires
human judgment.

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
- [ ] Home: Seerr-powered show/movie discovery rows (trending/popular, requestable)
- [x] Settings: merge "Servers", "Integrations" and "Users" into one section
      ("Connections" group)
- [ ] Seerr items not yet in the library get a tap action opening a dedicated detail
      view (poster/backdrop, overview, status, request action)
- [ ] "Unrequest" support: cancel a movie/show's open Seerr requests from the detail
      view (Seerr requests are per movie/season, so this lands at that granularity)

Status: **in progress**.

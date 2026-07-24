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
- [x] Fix the on-device storage bar's flakiness: `DownloadsViewModel` read
      `_state.value` (the receiver of `.copy(...)`) *before* an in-flight
      suspending call resolved, so whichever of the two independent
      diskSpace/deviceStorage fetches finished second could silently overwrite
      the other's update with a stale snapshot - a genuine, timing-sensitive
      lost-update race. Converted every state mutation in the ViewModel to the
      atomic `_state.update { it.copy(...) }` form.
- [x] Seerr: allow requesting/searching a specific season of a show that isn't in
      the Jellyfin library yet, not just the whole series - most requests are for
      "just season 1", not the entire show. `SeerrApi.createRequest` now sends
      `seasons: [N]` instead of `seasons: "all"` when a season-scoped view fires
      the request.
- [x] Seerr: show-level view had no way to reach a specific season at all (only
      an indirect two-hop path via Calendar/PVR-queue episode entries). Added a
      season list (modeling Jellyseerr's `mediaInfo.seasons` array, with each
      row's own `SeerrStatusChip`) to the show-level Seerr view; tapping a
      season navigates into a season-scoped view via the existing
      `navigateToSeason` callback.
- [x] Request buttons across the app (show/season request, inline search-result
      request) now consistently show the Seerr icon, matching the
      cancel-request/PVR-search buttons.
- [x] Every file/transfer size in the app now uses consistent binary (IEC)
      units (GiB/TiB) via a shared `formatBinaryFileSize` helper, instead of
      mixing Android's decimal `Formatter` in most places with binary units on
      the Downloads storage bars.
- [x] Downloads screen: the local download list took a noticeable moment to
      appear on open - root-caused to an N+1 query pattern in
      `toFindroidMovie`/`toFindroidEpisode` (one `getUserDataOrCreateNew`/
      `getSources`/`getTrickplayInfo` round trip, plus one
      `getMediaStreamsBySourceId` per source, *per row*), plus `toFindroidMovie`
      redundantly calling `getSources` twice. Added batched
      `toFindroidMovies`/`toFindroidEpisodes` (`IN (...)`-based DAO queries) used
      by the Downloads screen instead.

Status: **done** (2026-07-18). All four items shipped and merged.

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

## FINDROID-8: PVR queue status detail

- [x] Sonarr/Radarr queue rows showing "Warning" with no further detail: root-caused
      via the live Sonarr API (`/api/v3/queue`) - a season-pack release can finish
      importing every file it contains and still get flagged
      `trackedDownloadState=importBlocked` because Sonarr expected more episodes in
      the release than it found. The detailed reason lives in the queue item's
      `statusMessages` array (bare top-level reasons plus a noisy per-file import
      breakdown), which `SonarrQueueItem`/`RadarrQueueItem` weren't modeling at all -
      only a flat `errorMessage` (frequently `null` for warnings). Added
      `PvrStatusMessage`/`statusMessages` to both DTOs and a fallback in
      `QueueStatusMatching.kt` that surfaces the bare top-level reason (or the first
      per-file detail if none exists) when `errorMessage` is absent, so queue rows
      now show Sonarr/Radarr's real explanation instead of a generic label.

Status: **done** (2026-07-18).

## FINDROID-9: PVR queue bulk actions + manual import review

- [x] Downloads screen: no way to clear every pending Sonarr/Radarr download at
      once - long-pressing the "Pending downloads" section header (or a single
      row) now enters a selection mode mirroring the existing local-downloads
      one (checkboxes, top-bar count/clear/bulk-remove), reusing the
      removeFromClient/blocklist confirmation. `QueueStatusRepository` gained a
      `removeQueueItems` batch method (concurrent per-item Sonarr/Radarr calls,
      one shared snapshot refresh at the end - the v3 API has no bulk
      queue-delete endpoint).
- [x] Added a "manage imports" review UI for downloads Sonarr/Radarr couldn't
      fully auto-import (`trackedDownloadState=importBlocked`, etc.) - lists the
      individual files via `GET /api/v3/manualimport?downloadId=X` (service's
      own guessed episode/quality mapping + rejection reasons per file, grounded
      in a live Sonarr response) with a per-file checkbox, and imports the
      selected ones via `POST /api/v3/command` (`ManualImport`). Reachable from
      a new icon on `WARNING`/`FAILED` queue rows. Files Sonarr couldn't map to
      any episode at all are shown (for visibility) but not selectable - fixing
      those still requires Sonarr/Radarr's own web UI, since assigning a
      different episode/movie to a file isn't implemented yet (a real, but
      separate, follow-up from surfacing the review UI itself).
- [x] The "manage imports" sheet only offered an import action - no way to
      reject a release outright (delete + blacklist) without backing out to
      the queue row's trash icon. Found via a real case: Sonarr flagged a
      "Silo" release with `Caution: Found potentially dangerous file with
      extension: .scr` (a disguised Windows executable, not a real video
      file) - exactly the situation where the right move is reject-and-
      blacklist, not import. Added a "Delete & blacklist" action to the sheet
      (same removeFromClient/blocklist confirmation as the row-level one).

Status: **done** (2026-07-18).

## FINDROID-10: Trailer button for not-yet-in-library Seerr media

- [x] Movies/shows the user has only requested via Seerr (not in the Jellyfin
      library yet) had no trailer option, unlike already-imported items - added
      a "Trailer" button to the Seerr media detail view. Jellyseerr's
      `/api/v1/movie|tv/{tmdbId}` detail responses already embed a
      `relatedVideos` array (confirmed live against the real instance) with
      TMDB's videos (trailers/teasers/clips/...), each with a ready-to-open
      YouTube URL - added `SeerrRelatedVideo`/`relatedVideos` to the DTOs and
      picks the first "Trailer"-typed YouTube entry (falling back to any
      YouTube video if TMDB didn't tag one as a trailer). Opens the same way
      library-item trailers already do (`LocalUriHandler.openUri`, i.e. the
      YouTube app or a browser) - no new playback mechanism needed.
- [x] Season/episode views showed a series-wide trailer rather than something
      scoped to that season - confirmed live that this isn't a wiring bug:
      Jellyseerr's season and episode detail endpoints carry no video data at
      all, so `relatedVideos` on the show-level endpoint is the only source,
      and TMDB doesn't tag those videos by season. Added a best-effort
      improvement: when a season is in view, prefer a video whose free-text
      `name` mentions that season number (e.g. "Season 3 Official Trailer")
      before falling back to the show-wide pick - helps for shows with
      clearly-named season trailers, a no-op otherwise.

Status: **done** (2026-07-18).

## FINDROID-11: On-device storage bar shows real device usage

- [x] The "On this device" storage bar showed this app's own downloads as the
      "used" portion against total device capacity, silently implying nothing
      else was using space. Now uses the device's actual used space
      (`total - available` from `StatFs`, already tracked by
      `DeviceStorageStats`) as the bar's used portion, with this app's own
      downloads carved out as a distinct highlighted sub-segment within it
      (plus a small color-coded caption below, e.g. "1.2 GiB used by
      downloads") - the server storage row is unchanged, since there's no
      equivalent "ours vs. other" split available for it.
- [x] Downloads screen: the storage summary card was visibly narrower than the
      show-title cards right above/below it in the same list - it had an
      outer `Card` margin *in addition to* its inner content padding, while
      every other card in that list (`SectionHeader`, `ShowGroupHeader`) is
      edge-to-edge with a single padding layer. Removed the redundant outer
      margin so widths match.

Status: **done** (2026-07-18).

## FINDROID-12: Bulk-download truncated by leaving the screen mid-enqueue

- [x] "Download > Entire show" for a real show ("Alien: Earth", 8 episodes)
      only queued 4-5 episodes instead of all 8 - reproduced live and traced
      to `ShowViewModel`/`SeasonViewModel`/`EpisodeViewModel`'s
      `downloadWithScope` running its per-episode enqueue loop in
      `viewModelScope`, which is cancelled the instant the user navigates away
      from that screen (e.g. tapping another tab to check download progress -
      exactly what triggered it in testing) - silently truncating whatever
      part of the batch hadn't been enqueued yet, with no error or indication
      to the user. `DownloaderImpl.kt` already carried a TODO flagging this
      exact class of bug. Added a process-lifetime `@ApplicationScope`
      `CoroutineScope` (Hilt-provided, `core/.../di/ApplicationScopeModule.kt`,
      same pattern already used for `QueueStatusRepositoryImpl`'s poll loop)
      and moved all three `downloadWithScope` enqueue loops onto it instead of
      `viewModelScope`. Verified live: re-ran the same "download entire show,
      then immediately switch tabs" sequence and all 8 episodes queued
      correctly this time.

Status: **done** (2026-07-18).

## FINDROID-13: Download-scope dialog cleanup, search dedup, dialog polish

- [x] The download-scope dialog had two separate, confusing toggles -
      "Auto-download future seasons" (new seasons only, never backfills
      episodes in a season already picked) and "Automatically download new
      episodes" (new episodes in seasons already picked, but not new seasons).
      A show still airing its current season needed both toggled to actually
      stay up to date, with no obvious reason why. Merged into a single
      "Automatically download new episodes" toggle that always covers both -
      new episodes in whatever's selected, and brand new seasons once they
      exist - since "keep this show current" is one intent, not two. The more
      advanced per-rule editor (`AutoDownloadRulesScreen`) still exposes the
      two independently for power users; only the everyday download dialog
      (phone + tv) was simplified.
- [x] Media/Home search results: a Seerr result already in the Jellyfin
      library duplicated the library result shown right above it. Added
      tmdbId-based dedup (`FindroidItem.tmdbIdOrNull()`) that hides any Seerr
      result whose tmdbId matches a Jellyfin result already in the list, in
      both search surfaces (`LibraryScreen`'s inline Media search and the
      shared `SearchViewModel`/`FilmSearchBar` used by Home).
- [x] Downloads screen: no visual gap between the storage summary card and
      whatever's below it (the local downloads list, or the PVR queue) - they
      sat flush against each other. Added a bottom margin to the storage card.
- [x] "Delete download" (and every other trash-icon confirmation dialog -
      clear-all, delete-selected, delete-show) rendered the trash icon
      centered *above* the title instead of inline with it - that's just how
      Material3's `AlertDialog` `icon` slot always lays out, regardless of
      what's passed to it. Fixed by building the title as an icon+text `Row`
      instead of using the dedicated `icon` parameter.

Status: **done** (2026-07-18).

## FINDROID-14: PVR unavailable banner was too trigger-happy

- [x] The Downloads screen's "Sonarr/Radarr unavailable" banner appeared on a
      single failed poll - no grace period, and the failed poll also silently
      wiped whatever queue entries were already showing for that service.
      Added: (1) a one-time retry with a short delay inside the same fetch
      attempt, for a quick transient blip; (2) tolerance for a few consecutive
      failed polls (reusing the last known-good queue meanwhile) before
      actually surfacing the error - with the Downloads screen's 10s poll
      cadence that's ~20-30s of grace beyond the retry. Also bumped Sonarr/
      Radarr's HTTP read timeout (30s -> 45s) since a busy-but-alive instance
      (mid disk-scan/import) can legitimately take a while to answer.

Status: **done** (2026-07-19).

## FINDROID-15: Downloads storage summary ignores external storage

- [x] The Downloads screen's "On this device" storage bar only ever showed
      storage index 0 (internal), even on a device where the configured
      download location is external/removable storage (e.g. an SD card on
      the Mi Pad 4) - meaning downloads were shown as if they were sitting on
      internal storage even when they physically weren't. `Downloader` now
      exposes stats for every mounted app-storage volume
      (`getAllStorageStats()`, replacing the single-index `getStorageStats()`),
      each carrying its root path and whether it's removable. The Downloads
      screen renders one usage bar per volume (labeled "Internal"/"External"
      once there's more than one) and attributes each local download to the
      *correct* volume by matching its file path's prefix against each
      volume's root, instead of assuming everything is on index 0.
- [x] The multi-volume display above surfaced a *real* download-location bug:
      new downloads kept landing on internal storage even with "External"
      configured as the download location. Root cause: `AutoDownloadRuleEvaluator`
      (used by every season/show bulk download and by `AutoDownloadWorker`'s
      background auto-downloads - i.e. the path `DownloadScopeDialog` actually
      goes through) hardcoded `storageIndex = 0` for every episode, completely
      ignoring the configured preference. Only the single-item quick-download
      path (`ItemButtonsBar.startDownload()`) resolved it correctly. Added
      `Downloader.resolvePreferredStorageIndex()` (wraps the existing
      `resolveDownloadStorageIndex()` helper, falling back to 0 only for "ask"
      or an unmounted preferred volume) and use it in the evaluator instead of
      the hardcoded 0.
- [x] Reverted the Material3 "stop indicator" dot removed from progress bars
      earlier this fork's history - re-added across Downloads/Settings/the
      downloader card.
- [x] Renamed the Downloads storage label "On this device" to "This Device".

Status: **done** (2026-07-19).

## FINDROID-16: Migrate selected downloads between storage volumes

- [x] The External storage row reused the same phone icon as Internal - gave
      it a distinct literal SD-card icon (`ic_sd_card`, converted from
      Lucide Lab's `card-sd`) so the two are visually distinguishable at a
      glance, not just by label text.
- [x] Added a way to move specific selected downloads (not everything on a
      volume) to a different storage location: long-press to select
      movies/episodes on the Downloads screen (existing multi-select), then a
      new "migrate" icon (`ic_arrow_right_left` - the original
      `ic_arrow_down_up` doubled as the library sort icon elsewhere and read
      as a filter control here) in the top bar (before the trash icon) opens
      a confirmation dialog and moves just the selection. Only shown when
      more than one storage volume actually exists
      (`state.deviceStorages.size > 1`) - meaningless on a device with no
      external/removable storage. `Downloader.migrateItems()`/`moveItems()`
      mirror the existing `deleteItems()`/`deleteItem()` pair (a new
      `MigrateDownloadsWorker`, backed by WorkManager so it survives the app
      being backgrounded, same as bulk-delete) - the selection-scoped
      counterpart to the existing whole-volume `moveDownloads()` used when
      the download-location preference changes in Settings. Progress shown
      via a bottom card mirroring the existing delete-progress one.
- [x] Reworked the migrate dialog from a generic `StorageSelectionDialog`
      picker into a dedicated `MigrateDownloadsDialog` confirmation: since
      the selection's current volume is never offered as its own
      destination, and there are normally only two volumes total, "pick a
      destination" always collapsed to one meaningful choice anyway - so the
      dialog now states the move outright ("Move N item(s) (X) from Internal
      to External?") instead of asking. Shows the destination's
      `StorageUsageBar` widget (same one used on the Downloads screen) with
      *projected* post-move usage, highlighting exactly the incoming bytes -
      a preview of the move's impact, not just today's usage.

Status: **done** (2026-07-19).

## FINDROID-17: Optimistic bulk-deletion UI on the Downloads screen

- [x] Bulk/single/group deletion (`DownloadsViewModel.deleteItems()`) used to
      wait for `Downloader.deleteItems()` (which just enqueues
      `DeleteDownloadsWorker`) and then immediately call `refreshDownloads()`
      - re-querying the DB before the background worker had actually deleted
      anything, then relying on the 3s periodic poll to eventually catch up
      once it had. That's what made bulk deletion feel "choppy": rows sat in
      the list until the next poll, then several vanished at once. Fixed by
      removing selected items from `movies`/`showGroups` in local state
      instantly and letting `DeleteDownloadsWorker` run in the background
      unobserved; `clearAllDownloads()` got the same treatment (clears the
      list instantly instead of waiting on `refreshDownloads()`).
- [x] To still reconcile with the DB in case of a partial failure (an item
      that didn't actually delete shouldn't stay gone from the list forever),
      `getDeleteProgressFlow()`'s collector now calls `refreshDownloads()`
      once a batch transitions from running to finished - the same
      finish-triggered-refresh pattern already used for migrate's
      `moveProgress`.

Status: **done** (2026-07-19).

## FINDROID-18: End-of-track "stop indicator" dots on every progress/usage bar

- [x] The custom-drawn `StorageUsageBar` (used for the Internal/External/PVR
      rows on the Downloads screen) never had a Material3 stop-indicator dot
      to begin with - it's hand-rolled with `Row`/`Box`, not a real
      `LinearProgressIndicator`, so the earlier stop-indicator revert
      (removing `drawStopIndicator = {}` from real `LinearProgressIndicator`
      call sites) didn't touch it at all. Added a hand-drawn 4.dp circle at
      the track's trailing edge to match.
- [x] Four *real* `LinearProgressIndicator`s (delete-progress, move-progress,
      per-item download-progress, PVR queue status) were forcing
      `.height(3.dp)` - one dp short of Material3 1.4's own stop-indicator
      size (`LinearProgressIndicatorTokens.StopSize = 4.dp`), which clipped
      the dot into invisibility even though the code was otherwise using the
      default (undocumented from the outside - confirmed by reading
      Material3's actual token values). Bumped all four to `.height(4.dp)`.

Status: **done** (2026-07-19).

## FINDROID-19: Per-item storage icon + reactive migrate destination picker

- [x] Downloads screen episode/movie rows and show-group headers now show a
      small Internal/External icon next to their file size
      (`storageIconFor()`, gated on `deviceStorages.size > 1` same as
      everywhere else) - resolved from the downloaded file's actual on-disk
      path against each known volume's root, same attribution logic already
      used for the storage summary card. A show group only gets an icon when
      every episode agrees on the same volume; a split group (e.g. partially
      migrated) shows none rather than a misleading one.
- [x] `MigrateDownloadsDialog` no longer assumes a single "obvious" other
      destination: when the current selection is already split across more
      than one volume, either direction is a legitimate choice (e.g.
      consolidate onto internal vs. onto external), so it now shows a
      destination picker (radio rows) in that case only - a homogeneous
      selection stays a plain confirmation. Picking a destination is fully
      reactive: only sources not already on the chosen target actually move,
      so the item count/size/projected-usage preview recompute live as the
      user switches targets, and the confirm button disables itself
      ("Everything selected is already on X") if the chosen target would
      move nothing.

Status: **done** (2026-07-19).

## FINDROID-20: Per-item "currently moving/deleting" indicators

- [x] Downloads screen: items included in an in-flight migrate batch
      (`DownloadsState.migratingIds`, set the moment `migrateSelected()` is
      called - not after the worker finishes enqueuing, so there's no gap
      before the indicator appears) now show a "Moving…" status + an
      indeterminate progress bar in place of their file size, and a spinner
      instead of the play button. `MigrateDownloadsWorker` only reports an
      aggregate done/total for the whole batch, not which item it's on right
      now, so every id in the batch is marked "moving" for the batch's whole
      duration rather than pinpointing the exact one being copied. Tapping a
      migrating row, and swiping to delete it (or its show group), are both
      disabled while it's mid-move.
- [x] Considered doing the same (row stays, shows "Deleting…", play button
      hidden) for the Downloads screen's own delete flow, but that list
      already removes rows instantly on delete (FINDROID-17) - there's no
      row left to put an indicator on, and reverting that would undo the
      "less choppy" fix. Scoped instead to the movie/episode *detail page*'s
      own single-item delete button (`ItemButtonsBar`/`PlayOverlayButton`),
      which isn't a list and doesn't disappear: added
      `DownloaderState.isDeleting`, set by `DownloaderViewModel.deleteDownload()`
      for the duration of the (non-worker, inline suspend) `Downloader.deleteItem()`
      call. While true, `PlayOverlayButton` disables itself and shows a
      spinner instead of the play icon (its `isDownloaded()`-based
      enablement can't be trusted mid-delete: the bytes may already be gone
      from disk before the DB catches up), and `ItemButtonsBar`'s delete
      tile itself disappears to prevent a second tap queuing another delete.
      Movie/Episode screens only - Show/Season's delete affordance is a
      different, already-bulk flow with no single "this file" semantics.

Status: **done** (2026-07-19).

## FINDROID-21: Detect and recover downloads whose local file vanished

- [x] Reported bug: reformatting an SD card used as a download location wipes
      the files on disk, but the DB still has LOCAL source rows pointing at
      them, so the Downloads screen kept listing those episodes/movies as
      downloaded - showing "0 B" for their size (`FindroidSource.size` is a
      live `File(path).length()` read, which silently returns 0 for a
      vanished file instead of throwing) and still offering Play, which would
      fail against a file that's gone.
- [x] Added `FindroidItem.isDownloadBroken()` (data/.../FindroidItem.kt): a
      completed (non-`.download`) LOCAL source is never legitimately 0
      bytes, so `size <= 0` is an unambiguous "file's actually missing"
      signal - cheap, no extra I/O beyond the stat already happening.
- [x] Downloads screen rows for a broken item show an error-tinted "File
      missing - tap to re-download" status instead of the (misleading) "0 B"
      size, and swap the play button for two icons: re-download and delete
      (reusing the existing swipe-to-delete path). Tapping the row body is a
      no-op rather than guessing "play" or "re-download" - same treatment as
      the migrating-row guard added earlier this session.
- [x] `DownloadsViewModel.redownloadItem()`/`redownloadAllBroken()` just call
      `Downloader.downloadItem()` again for the broken source(s) -
      `insertSource` is `OnConflictStrategy.REPLACE` on the same id, so it
      overwrites the stale row in place with no explicit delete-first step
      (confirmed via `AutoDownloadRuleEvaluator`'s identical re-trigger
      pattern). Progress tracking falls out for free: the fresh `.download`
      path makes `reconcileDownloadProgress()`'s existing `isDownloading()`
      check pick it up on the next refresh, same as any other new download.
- [x] Added a dismissless `BrokenDownloadsBanner` card (shown above the
      movies/shows list whenever `brokenCount > 0`) with a single "Re-download
      all" button, so a whole reformatted volume's worth of broken items
      doesn't need re-downloading one row at a time.

Status: **done** (2026-07-19).

## FINDROID-22: Storage indicator on the movie/episode detail page

- [x] Reused the Downloads screen's "icon + size" row caption on the
      movie/episode detail page itself: a new shared `LocalStorageIndicator`
      composable (`app/phone/.../components/LocalStorageIndicator.kt`), shown
      right below `ItemButtonsBar` whenever the item has a completed local
      download. Resolves Internal/External via a new
      `isPathOnRemovableStorage(context, path)` helper in
      `core/.../utils/DownloadStorage.kt` - the inverse of the existing
      `resolveDownloadStorageIndex()` (path -> volume type, instead of
      preference -> volume index).
- [x] Also broken-aware for free: passes `item.isDownloadBroken()` through,
      so a vanished local file shows the same error-tinted "File missing"
      caption here as it does in the Downloads list, rather than a
      confusing "0 B" on the one screen that didn't get that treatment.

Status: **done** (2026-07-19).

## FINDROID-23: Downloads screen polish - section order and Sonarr badge

- [x] Moved the "Pending downloads" (PVR queue) section up, right below the
      storage usage summary card - it used to render last, after movies and
      all show groups, which buried it at the bottom of a long list.
- [x] Fixed the Sonarr/Radarr badge on each `PvrQueueRow` poster thumbnail
      rendering as a plain solid-color circle instead of the actual logo:
      `Icon()` applies its `tint` (defaulting to `LocalContentColor.current`)
      across the whole vector, flattening a multi-color brand mark into a
      silhouette of its dominant shape (the outer disc). Fixed with
      `tint = Color.Unspecified`, the same fix already applied to the
      identical badge in `SeerrMediaScreen.kt`'s `PvrSearchButtonLabel`.

Status: **done** (2026-07-20).

## FINDROID-25: Reorder Home screen sections

- [x] Every independently-rendered Home row - Suggestions, Continue Watching,
      Next Up, each library's "Latest <library>" shelf, each Seerr Discover
      row (Trending/Popular Movies/Popular Shows), and Pending downloads -
      is now individually reorderable, not just movable as a fixed group.
- [x] Added `HomeSectionKeys`/`resolveHomeSectionOrder()`
      (`core/.../utils/HomeSectionOrder.kt`): each row gets a stable string
      key (fixed for the singleton rows, `view:<libraryId>` /
      `discover:<titleRes>` for the dynamic ones), and the merge function
      keeps a persisted order's relative positions while appending any
      brand-new key (a library added since, a section just enabled) at the
      end instead of dropping it or jumping it to the front.
- [x] `HomeState.sectionOrder` is the fully-resolved render order;
      `HomeScreen`'s `LazyColumn` now iterates it and dispatches to the
      matching composable per key, replacing the old fixed sequence of
      `if/let` blocks.
- [x] New "Customize home screen" settings screen
      (`app/phone/.../settings/homelayout/`, backed by a
      `HomeLayoutSettingsViewModel` in `modes/film` - it needs
      `JellyfinRepository`/`PvrConfiguration`, which the `settings` module
      itself can't depend on) lists every currently-enabled row with
      up/down move buttons; each move persists immediately to a new
      `homeSectionOrder` preference, no separate Save step.
- [x] Reordering takes effect back on Home without a full reload: a
      `LifecycleResumeEffect` calls a new, network-free
      `HomeViewModel.refreshSectionOrder()` on resume, which just re-reads
      the preference and re-merges against whatever's already loaded.
- [x] TV shares `HomeState`/`SettingsEvent` with phone but doesn't get this
      screen in this pass - the new `PreferenceCategory` entry is
      phone-only (`supportedDeviceTypes = listOf(DeviceType.PHONE)`), and
      TV's two `SettingsScreen`/`SettingsSubScreen` event handlers just
      no-op the new `NavigateToHomeLayout` event, same treatment already
      given to phone-only Integrations settings.
- [x] Follow-up: added long-press-drag reordering directly on Home itself
      (not just the settings screen), using `sh.calvin.reorderable:reorderable`
      (added to `app/phone` only). Each section gets a small floating grip
      handle (new `ic_grip_vertical` icon, converted from Lucide) in the
      left gutter rather than making the whole row a drag target - the
      Suggestions row is itself a swipeable `HorizontalPager`, so a
      long-press-anywhere modifier on the whole item would fight that
      nested gesture; a dedicated handle sidesteps it and matches how the
      library's own demos are built (handle icon, not whole-row-draggable).
      `HomeAction.OnReorderSections(fromIndex, toIndex)` mutates
      `HomeState.sectionOrder` and persists to the same `homeSectionOrder`
      preference the settings screen writes, so either surface stays in
      sync with the other. The settings screen's up/down buttons stay as
      the accessible alternative - drag gestures don't work well with
      TalkBack.
- [x] Follow-up based on feedback: removed the persistent grip handle -
      long-pressing a section's own title now starts the drag instead, via
      a new `titleModifier` param threaded into `HomeCarousel`/`HomeSection`/
      `HomeView`/`HomeDiscoverSection`/`HomeDownloadProgress`, applied to
      just the title `Text` (not the whole row, so e.g. `HomeView`'s "view
      all" arrow button stays independently clickable). Suggestions never
      had a title before this - added one ("Suggestions") since long-press
      needs somewhere non-scrolling to land, and it doubles as fixing the
      "which section is this?" ambiguity when a row has no content.
- [x] Fixed "Pending downloads" being completely invisible (no title, no
      content, just an unlabeled drag target) whenever the PVR queue is
      empty - `HomeDownloadProgress` now always renders its title, showing
      a muted "No pending downloads" line in place of the queue cards when
      there's nothing active, instead of the call site skipping the whole
      composable.
- [x] Sections can now be individually hidden and restored from "Customize
      home screen", not just reordered: a new `homeHiddenSections`
      preference (same comma-joined-keys format as `homeSectionOrder`)
      holds the hidden set. Hiding a row just adds its key there - its
      position in the order list is left untouched, so restoring drops it
      right back where it was. `HomeViewModel.recomputeSectionOrder()`
      filters hidden keys out before resolving Home's render order, so a
      hidden section never appears there; the settings screen lists hidden
      rows in their own "Hidden sections" group at the bottom with a
      restore (+) button.
- [x] Follow-up: each row in "Customize home screen" now shows the
  Sonarr/Radarr/Seerr brand icon(s) its content actually depends on -
  Sonarr and/or Radarr (whichever is enabled) for "Pending downloads",
  Seerr for the three Discover rows - reusing the `tint = Color.Unspecified`
  fix from earlier in this file's history so the full-color logos don't
  get flattened by `Icon()`'s default tint. Same icons were added to the
  actual Home screen section titles too (new shared `SectionServiceIcons`
  composable), not just the settings list.
- [x] Follow-up: added a "Reset layout" action (top bar icon + confirm
  dialog) to "Customize home screen" that clears both `homeSectionOrder`
  and `homeHiddenSections`, restoring the default order and unhiding
  everything in one tap.
- [x] Follow-up: entering drag mode on Home now has a subtle "picked up"
  animation - the dragged section scales up slightly, gains a soft shadow,
  and picks up a faint surface tint (all `animate*AsState`-driven, so they
  ease in/out rather than snapping), instead of just silently reordering
  with no feedback that a drag started.

Status: **done** (2026-07-20).

## FINDROID-24: "NEW" badge for recently-added library items

- [x] The home page's "Latest <library>" sections show individual episodes
      as well as movies/shows, with no way to tell a just-aired episode from
      one added months ago. Added a `NewBadge` (`ItemCard`'s existing badge
      row - alongside `DownloadedBadge`/`PlayedBadge`/`ItemCountBadge`) shown
      whenever `FindroidItem.isRecentlyAdded()` (dateCreated within the last
      7 days) is true.
- [x] Added `dateCreated: DateTime?` to the `FindroidItem` interface (and
      every implementer), wired from `BaseItemDto.dateCreated` in the
      online mapping functions for `FindroidMovie`/`FindroidEpisode`/
      `FindroidShow` - null for offline/DB-rebuilt items (not persisted in
      Room, and not needed there since downloaded items don't show in the
      "Latest" carousels anyway).

Status: **done** (2026-07-20).

## FINDROID-26: Manual import failure - untruncated, copyable error details

- [x] Reported bug: manually importing a finished-but-blocked Sonarr
      download failed with a Sonarr-side "'Name' must not be empty"
      validation error (`NotNullValidator`) for an episode Sonarr itself had
      already flagged with the rejection "Episode has a TBA title and
      recently aired" - i.e. Sonarr's own episode metadata wasn't fully
      synced yet for a just-aired episode, and its manual-import command
      validation trips on the missing title server-side. This is an
      upstream Sonarr data-completeness issue (our request never sends an
      episode title at all - Sonarr resolves it server-side from the
      episode id), not something fixable from the request payload; the app
      already surfaces Sonarr's own rejection reason before the user
      proceeds.
- [x] Fixed a real bug found while investigating: `SonarrApi`/`RadarrApi`/
      `SeerrApi`'s `execute()` truncated every non-2xx response body to 200
      characters (`body.take(200)`) before it even reached the error
      message shown in the UI - independent of any UI-level clipping, the
      full validation JSON (multiple error objects, `formattedMessage`,
      etc.) was already gone by the time it got wrapped in a
      `PvrApiException`. Bumped to 4000 characters, comfortably fitting any
      realistic API error body while still bounding pathological ones (an
      HTML error page).
- [x] Added `MessageDetailsDialog` (`app/phone/.../components/ErrorDialog.kt`,
      alongside the existing `ErrorDialog` for `Throwable`s) - a scrollable,
      selectable full-text view with Copy (clipboard) and Share (Android
      share sheet) buttons, for messages that don't fit the inline error
      text they're normally shown in. Wired into `ManualImportSheet`'s
      inline import-error text and `PvrErrorBanner` (PVR fetch-failure
      banner on the Downloads screen and Calendar) - both are now
      clickable, 2-line-clamped previews that open the full text on tap
      instead of only ever showing a silently-clipped snippet.

Status: **done** (2026-07-20).

## FINDROID-27: Home layout polish round 2

- [x] Service-icon rows (Sonarr/Radarr/Seerr, added in FINDROID-26) now show
      the icon *before* the title text everywhere, not after - "Customize
      home screen" rows, the Home section titles themselves, and the
      "Pending downloads" header on the Downloads screen (new
      `SectionHeader.leadingIcons` param there, derived from whichever
      services actually have a queue group or error right now rather than a
      static "is it configured" check).
- [x] Sections backed directly by Jellyfin (Suggestions, Continue Watching,
      Next Up, every "Latest <library>") now get the Jellyfin logo as their
      own service icon, for the same "which of your servers/integrations
      is this section talking to" affordance the PVR/Seerr icons already
      gave the other rows.
- [x] Found and fixed why the "NEW" badge (FINDROID-24) never actually
      appeared: `JellyfinRepositoryImpl.getLatestMedia()` only requested
      the `ProviderIds` field from the server - `DateCreated` isn't in
      Jellyfin's default field set, so every item's `dateCreated` came back
      null and `isRecentlyAdded()` was always false. Added
      `ItemFields.DATE_CREATED` to the request. Also gated the badge on
      `!item.played`, so a recently-added item you've already watched
      doesn't get flagged "NEW".
- [x] Changed the *default* Home section order (only takes effect for a
      layout that's never been touched - an existing custom order or hidden
      set is left alone) to: Pending downloads, Latest Shows, Next Up,
      Continue Watching, Latest Movies, Suggestions, Trending, Popular
      Shows, Popular Movies. Views are now split by `CollectionType`
      (TV/movie) in `HomeViewModel.recomputeSectionOrder()` and
      `HomeLayoutSettingsViewModel.load()` so each "Latest ..." lands next
      to its own slot instead of all views being grouped together.
- [x] Bumped to version 2.1.0 (versionCode 34).
- [x] Follow-up: refreshing Home showed two loading indicators at once -
      `HomeHeader`'s own spinner and Material3 `PullToRefreshBox`'s default
      pull indicator, both driven by the same `state.isLoading`. Suppressed
      the pull-to-refresh one (`indicator = {}`) and kept the header's,
      without losing the pull gesture itself.

Status: **done** (2026-07-20).

## FINDROID-28: Battery saver downloads, calendar caching/TZ bugs, settings grouping

- [x] Pause all active downloads when Android's battery saver turns on, resume
      them when it turns off. Implemented: `Downloader.pauseAllForBatterySaver()`/
      `resumeBatterySaverPausedDownloads()` (`core/.../utils/DownloaderImpl.kt`) -
      cancels/re-enqueues WorkManager unique work per source, tracked via a new
      `sources.pausedByBatterySaver` column (Room DB bumped to v14) so only
      downloads *this* feature paused get auto-resumed, not ones the user paused
      manually. New `BatterySaverReceiver` (manifest-registered,
      `android.os.action.POWER_SAVE_MODE_CHANGED`) plus a `BaseApplication`
      startup check (the broadcast only fires on state *change*, so battery
      saver already being on when the app launches needs an explicit check).
      Gated behind a new "Pause on battery saver" toggle in Settings > Downloads
      (`pauseDownloadsOnBatterySaver`, default on). Phone only (TV has no
      downloads feature/WorkManager `Configuration.Provider` at all).
      Verified: `BatterySaverReceiver` confirmed registered for
      `android.os.action.POWER_SAVE_MODE_CHANGED` via `dumpsys package`, and
      toggling battery saver on/off on-device (`dumpsys battery unplug` +
      `settings put global low_power 1/0`) caused no crash. CI-signed release
      build installed on both test devices (ASUS phone + Mi Pad 4), app
      launches cleanly.
- [x] Calendar view: re-fetches from scratch every time the screen opens -
      cache the result so reopening the tab doesn't always re-hit the
      Sonarr/Radarr/Jellyfin APIs. Implemented: new `CalendarCache`
      (`modes/film/.../calendar/CalendarCache.kt`), a `@Singleton` holding the
      last `CalendarResult` - same "outlive the screen-scoped ViewModel"
      pattern `LibraryItemsCache` already uses for the Library tabs, just
      holding a plain snapshot instead of a cached Flow since there's no
      paging involved. `CalendarViewModel.init` now paints the cached result
      immediately (no spinner) before kicking off a background refresh;
      `load()` only shows the loading spinner when there's nothing on screen
      yet (first load this process, or state is still empty), so the tab
      reopens instantly and refreshes silently behind it. In-memory only, not
      persisted - a fresh process still fetches once, same as before.
- [x] Calendar/episode airtime bugs reported by the user (currently airing
      season): (1) a TBA episode only shows the date, not the exact airtime;
      (2) opening a not-yet-released episode shows an airtime one day earlier
      than the Calendar list did. Root-caused both:
      (1) was a real rendering gap, not a data gap - Sonarr's calendar entries
      already carry a full `airDateUtc` instant for currently-airing-season
      episodes (same field the Calendar tab already renders via
      `CalendarEntry.airTime`), but the Season screen's placeholder row for
      episodes Sonarr knows about that aren't in Jellyfin yet
      (`UpcomingEpisode`, rendered by `UpcomingEpisodeCard`) never carried an
      `airTime` field at all, only a date. Added `UpcomingEpisode.airTime`
      (parsed the same way as `CalendarEntry.airTime`, via
      `SeasonEpisodesMatching.kt`'s existing `parseLocalTime`) and render it
      alongside the date on both phone and TV.
      (2) was a genuine timezone bug, but not in the Sonarr/Radarr calendar
      code path (`CalendarMatching.kt`'s `parseFlexibleDate`/`parseLocalTime`
      were already correct - both convert the UTC instant to the system
      default zone before extracting the date/time). It was in
      `DateTime.format()` (`core/.../utils/CoreExtensions.kt`), the formatter
      used by the Jellyfin-native episode/movie detail screens for
      `premiereDate`: Jellyfin's metadata providers only ever supply a
      calendar date for premiere dates (no real time-of-day), which the
      server always emits as midnight UTC. `DateTime.format()` was converting
      that midnight-UTC value to an `Instant`/`Date` and re-rendering it in
      the device's default (local) time zone - for any zone behind UTC, that
      rolls the *displayed* date back a full day (e.g. "Jul 25" shows as
      "Jul 24"), exactly the "-1 day" the user saw. Fixed by formatting the
      date components directly with no zone conversion at all. Also wired the
      already-fetched-but-unused `CalendarEntry.airTime` into the ShowScreen
      "next episode airs" banner, and extracted a shared `formatCalendarTime`
      (`core/.../utils/DateUtils.kt`) so the Calendar tab, Season screen, and
      ShowScreen banner all format air times the same way. Note: separately
      noticed `ShowViewModel.getNextAiring` filters calendar entries by
      `it.itemId == showId`, but `CalendarEntry.itemId` is a *season* id for
      Sonarr entries (see `matchSonarrCalendar`), not the show id - so that
      banner likely never actually resolves an entry today. Left unfixed,
      out of scope for this pass (would need `CalendarEntry` to carry a
      separate show/series id).
- [x] Settings > Downloads: the preference list is a long flat list - group
      related settings (e.g. the auto-delete-watched toggle + its hours
      input) under (sub)headings for readability. `PreferenceGroup` already
      supported an optional `nameStringResource` subheading (used by the
      Player screen's Gestures/Seeking/Trickplay groups) - no new UI
      primitive needed, just restructured the single flat Downloads
      `nestedPreferenceGroups` list into four `PreferenceGroup`s in
      `SettingsViewModel.kt`: Network (mobile data, roaming), Storage
      (location, parallel downloads, pause-on-battery-saver), Auto-delete
      (watched toggle + hours input), Auto-download (check interval +
      Auto-download rules category).
- [x] Individual settings sub-screens (Downloads, Player, etc.) don't show an
      icon in their header, unlike their entry row on the main Settings list -
      add the same icon to each sub-screen's header for consistency. Root
      cause: the shared phone `SettingsScreenLayout` (`app/phone/.../
      settings/SettingsScreen.kt`) only ever passed an `iconRes` to
      `TopBarTitle` for the root Settings screen, hardcoded to
      `ic_settings`. Threaded the icon of the deepest-matched
      `PreferenceCategory` through as a new `SettingsState.
      titleIconDrawableId`, set in `SettingsViewModel.loadPreferences()`
      during its existing drill-down loop, and consumed by
      `SettingsScreenLayout` - fixes every sub-screen driven by the generic
      preference tree (Downloads, Player, mpv options, Interface, Network,
      Cache, etc.) in one place. The four bespoke phone screens that bypass
      that tree entirely (Backup, Integrations/Connections, Customize home
      screen, About) each got a direct `TopBarTitle` + hardcoded icon
      (reusing the same drawable as their main-list row) since they don't
      go through `SettingsViewModel` state. Also covered TV: its separate
      `SettingsSubScreen.kt` scaffold had no icon anywhere (not even the
      root screen), so added an `Icon` next to the title there too, sourced
      from the same new `titleIconDrawableId` state field.
- [x] User feedback: "the UI for adding Jellyfin servers/users is a bit
      weird." Settings > Connections' inline Jellyfin section
      (`app/phone/.../settings/integrations/IntegrationsSettingsScreen.kt`,
      `JellyfinConnectionSection`) had drifted from the onboarding flow it was
      folded from in FINDROID-5 (`app/phone/.../presentation/setup/{addserver,
      login,servers,users}`, backed by `:setup`): plain `Button`s instead of
      the `ServerItem`/`UserItem`/`LoadingButton` card components used
      everywhere else in setup; the "add server" text field stayed
      permanently open even with a server already configured; the
      username/password login form stayed visible even after already being
      signed in; no server discovery (mDNS), no Quick Connect, and no
      public/guest user listing, despite the standalone onboarding
      Login/AddServer screens supporting all three; and a single shared error
      message with no indication whether it came from "add server" or
      "login". Reworked the section to close those gaps: server/user rows are
      now card-styled like `ServerItem`/`UserItem` with a selected checkmark
      and trailing delete icon; "add server" collapses behind an "Add another
      server" toggle once a server exists and auto-collapses again after a
      successful add; the login form only renders when there's no active user
      or the user explicitly taps "Add another user" (otherwise a compact
      "Signed in as X" row shows instead); added Quick Connect, public/guest
      user listing (tap to prefill username), and mDNS discovery, matching
      onboarding; split the one `jellyfinError` field into
      `addServerError`/`loginError` so failures render inline via
      `supportingText` next to the field that caused them, same as
      `AddServerScreen`/`LoginScreen` already do. Onboarding itself
      (`:setup` module, phone + TV screens) was left untouched - already
      consistent with the rest of the app's polish and not the source of the
      complaint. TV has no PVR Integrations/Connections screen at all, so this
      is phone-only. Verified: remote `:app:phone:compileLibreDebugKotlin`,
      `:app:tv:compileLibreDebugKotlin` and `ktfmtCheck` on rofl-13.

Status: **done** (2026-07-21). All five items shipped: battery saver
downloads, calendar caching/airtime fixes, settings grouping/header icons,
and the Connections add-server/user redesign. CI-signed release build
verified and installed on both test devices.

## FINDROID-29: Post-deploy bug reports (airtime mismatch, backup scroll, restore paste)

- [x] User caught a remaining airtime bug after FINDROID-28 shipped: the
      Season screen's upcoming-episode row correctly showed "Airs Jul 27 at
      3:00 AM" (Sonarr `airDateUtc`, localized), but tapping into it opened
      the Seerr/TMDB detail screen (`SeerrMediaScreen`) showing
      "2026-07-26" - a different date for the same episode. That screen
      independently re-fetches episode metadata from Jellyseerr/TMDB and
      renders TMDB's plain, unlocalized `air_date` via a naive substring,
      unrelated to the Sonarr-derived value the list just showed. Fixed by
      threading the already-localized `airDate`/`airTime` through
      `SeasonAction.NavigateToSeerr` -> `SeerrMediaRoute` ->
      `SeerrMediaViewModel`, so the detail screen shows the same value.
- [x] Settings > Backup & Restore: the Restore button rendered as a
      barely-visible hairline at the bottom of the screen. Root cause: the
      content `Column` had no `verticalScroll` modifier at all - once the
      auto-backup section grew tall enough, everything below (including
      Restore) got pushed past the viewport with no way to scroll to it.
      Added `.verticalScroll(rememberScrollState())`.
- [x] Restore backup password prompt: added a paste-from-clipboard button,
      mirroring the existing pattern on the Integrations API key field
      (same `ic_clipboard_paste` icon, `ClipboardManager.getText()`).

Status: **done** (2026-07-21). Verified via remote compile + `ktfmtCheck`
on rofl-13; CI-signed release build installed on all three connected
devices (ASUS phone, Pixel 5, Mi Pad 4).

## FINDROID-30: Scheduled auto-backup failing silently forever

- [x] User reported Settings > Backup & Restore showing "Backup interval:
      1 day" but "Last backup" stuck 5 days stale, while the manual "Back
      up now" button worked fine. Root cause: `AutoBackupWorker.doWork()`
      had several silent failure points - a missing `autoBackupFolderUri`,
      a `DocumentFile.fromTreeUri()` that returned null (e.g. a stale SAF
      tree grant after the parent document was moved/deleted/revoked), and
      a null `folder.createFile()` - each just did `return Result.failure()`
      with zero logging. A generic `catch` block did log via `Timber.e` and
      returned `Result.retry()`, but that's invisible in a release build
      nobody checks logcat on. Since this is a periodic unique work item,
      `Result.failure()`/`Result.retry()` never cancels the schedule -
      WorkManager just keeps retrying on the same cadence and failing the
      same way indefinitely, with nothing ever surfaced to the user.
- [x] Added `Timber.w`/`Timber.e` logging at every failure branch in
      `AutoBackupWorker.doWork()`, including the folder URI where relevant,
      so a diagnosis is at least possible via logcat/bugreport.
- [x] Added `AppPreferences.autoBackupLastError` (`Preference<String?>`,
      key `pref_backup_last_error`) that the worker sets to a short
      human-readable reason on every failure path and clears (`null`) on
      success, mirroring how `lastBackupTimestamp` is already
      read/written from this same `CoroutineWorker`. Persisting it means
      it survives process death and is available the next time the user
      opens Settings, not just while the worker happens to be running.
- [x] Surfaced it in `BackupSettingsScreen`: when `lastBackupError` is
      set, an error-colored banner (same `errorContainer`
      background/icon-+-text/tap-to-expand pattern as `PvrErrorBanner`)
      shows next to "Last backup", reusing the existing
      `MessageDetailsDialog` for the full copyable/shareable text. New
      string `backup_auto_error` ("Automatic backup failed: %1$s"). The
      manual "Back up now" button's existing snackbar-based error flow
      (`BackupSettingsEvent.BackupNowError`) was left untouched - this is
      specifically for the background/scheduled path, which previously had
      no feedback mechanism at all.
- [x] TV has no Backup & Restore screen, so no TV-side changes were needed.

Status: **done** (2026-07-21). Verified via remote
`:app:phone:compileLibreDebugKotlin` and `ktfmtCheck` on rofl-13.

## FINDROID-31: Show missing seasons on the Show screen (request seasons not in Jellyfin yet)

The Season screen already surfaced Sonarr-known episodes missing from a
season (`UpcomingEpisode`/`UpcomingEpisodeCard`), letting a user open the
Seerr detail view and request them. Nothing equivalent existed one level
up: a show with e.g. seasons 1-3 imported but season 4 known to Sonarr and
not yet downloaded showed only 1-3 on the Show screen, with no way to
discover or request season 4 short of already knowing to search for the
show in Seerr directly.

- [x] Added `UpcomingSeason` (`data/.../models/UpcomingSeason.kt`) and
      `matchMissingSeasons` (`data/.../repository/SeasonEpisodesMatching.kt`)
      - the show-level sibling of `UpcomingEpisode`/`matchUpcomingEpisodes`.
      Sonarr's v3 API has no "season" resource of its own, so a missing
      season is inferred by grouping `GET /api/v3/episode` (already fetched
      per-series for the Season screen's feature) by `seasonNumber` and
      diffing against the season numbers already present as real
      `FindroidSeason`s. Season 0 ("Specials") is excluded - present on
      nearly every series and usually unmonitored, it would be noise on
      every show rather than a useful placeholder.
- [x] `SeasonEpisodesRepository` gained `getMissingSeasons()` alongside the
      existing `getUpcomingEpisodes()`; both now share a
      `fetchSeriesEpisodes()` helper in the impl (same tvdbId->seriesId
      resolution, same Sonarr client) instead of duplicating that lookup.
- [x] `ShowViewModel.loadShow()` fires `loadMissingSeasons()` *after* the
      main state emit (real show/season data hits the screen immediately;
      the Sonarr round trip fills in placeholders afterwards), same
      sequencing `SeasonViewModel.loadUpcomingEpisodes()` already uses.
      Gated on `appPreferences.sonarrEnabled` + a resolved show `tvdbId`,
      same as the Season screen.
- [x] Phone: new `UpcomingSeasonCard` (mirrors `UpcomingEpisodeCard` -
      dimmed, calendar-icon placeholder, "Not yet available" badge, no
      poster) appended after the real `ItemCard` entries in the Show
      screen's seasons `LazyRow`. Tapping one opens `SeerrMediaRoute` scoped
      to that `seasonNumber`, via a new `ShowAction.NavigateToSeerr` wired
      through `NavigationRoot`, mirroring `SeasonAction.NavigateToSeerr`.
      Needs the show's `tmdbId` (`ShowState.seriesTmdbId`), same source as
      `SeasonState.seriesTmdbId`.
- [x] TV: same placeholder card added to the seasons row, but
      non-interactive (`enabled = false`), matching the existing TV
      `UpcomingEpisodeCard` - TV has no `SeerrMediaRoute` wired up at all
      yet, so there's nothing a tap could do there either.
- [x] Added `matchMissingSeasons` unit tests to
      `SeasonEpisodesMatchingTest.kt` (missing-season filtering, per-season
      episode counts, monitored-if-any-episode-monitored, season-0
      exclusion, sort order, all-known-returns-empty).

Status: **done** (2026-07-21). Verified via remote
`:app:phone:compileLibreDebugKotlin`/`:app:tv:compileLibreDebugKotlin`,
`ktfmtCheck`, and `:data:testDebugUnitTest`/`:core:testLibreDebugUnitTest`
on rofl-13. Not installed/deployed to a device for this change.

## FINDROID-32: "Marked for deletion" indicator + exclude-from-auto-delete pin

User request: since the app can auto-delete watched downloads, show which
items are about to be deleted (Downloads list + episode/movie detail pages)
and offer a way to protect specific ones.

- [x] Root problem found while designing this: `AutoDeleteWatchedWorker`
      computed eligibility by calling `JellyfinRepository.getEpisode()` (a
      live network round trip) for every downloaded episode, and the local
      DB never tracked `lastPlayedDate` at all - so there was no way for a
      UI badge to compute "is this marked for deletion" without duplicating
      that network call on every screen render. Added
      `FindroidUserDataDto.lastPlayedDate` (Room, DB bumped to v15), kept in
      sync locally by every `setPlayed` call site in
      `JellyfinRepositoryImpl`/`JellyfinRepositoryOfflineImpl` (set to
      `DateTime.now()` when marking played, cleared to `null` when
      unplaying). `AutoDeleteWatchedWorker` now computes eligibility
      entirely from local DB state via the new
      `FindroidEpisode.isMarkedForAutoDeletion(hours)` extension - no more
      network round trip, and the worker and the UI badge are now
      guaranteed to agree since they share the exact same check.
- [x] Added `FindroidSourceDto.excludeFromAutoDelete` (same v15 migration,
      `@ColumnInfo(defaultValue = "0")`, mirrors the recent
      `pausedByBatterySaver` column) - a per-download "keep" pin.
      `isMarkedForAutoDeletion()` returns false for an excluded source
      regardless of watched state, so the worker respects it directly.
      Scoped to episodes only (movies are never auto-deleted by this
      worker, so a pin for them would protect against nothing).
- [x] Downloads screen: episode rows show a tertiary-colored "Marked for
      deletion" caption (trash icon) next to the file size when eligible,
      and a lock/unlock icon button toggles the exclude pin - both only
      rendered while the auto-delete-watched preference is actually on.
- [x] Episode detail page: `LocalStorageIndicator` (below `ItemButtonsBar`)
      gained the same "Marked for deletion" caption; `ItemButtonsBar`
      gained a new "Keep" action tile (lock/unlock icon, `checked` state)
      next to the existing Delete tile, wired the same way.
- [x] Follow-up from a screenshot: moved the local-file-size indicator on
      the episode detail page from below the action-button row (where it
      visually crowded the "Delete" label) to float on the right of the
      show name / season-episode text block instead, vertically centered
      across both lines.

Status: **done** (2026-07-21). Verified via remote
`:app:phone:compileLibreDebugKotlin`, `:data:testDebugUnitTest`, and
`ktfmtCheck` (forced rerun) on rofl-13.

## FINDROID-33: Missing-season ordering + real posters

Two follow-ups on FINDROID-31's missing-season placeholder cards:

- [x] They were appended after every real season regardless of number
      (two separate `items()` blocks in the seasons `LazyRow`), so e.g.
      season 4 missing on a show with seasons 1-3 and 5 showed as
      "1, 2, 3, 5, 4" instead of "1, 2, 3, 4, 5". Fixed by merging real
      `FindroidSeason`s and `UpcomingSeason` placeholders into one list
      (new private `SeasonRowItem` sealed type, phone + TV) sorted by
      season number before rendering a single `items()` block.
- [x] They showed a bare calendar icon instead of a poster. Sonarr's v3
      API has no per-season poster art, but TMDB does (via Seerr's
      `GET /tv/{id}/season/{n}`, the same endpoint the Seerr season
      detail view already uses for `SeerrSeasonDetail.posterUrl`) - added
      `SeerrRepository.getSeasonPosterUrls(tmdbId, seasonNumbers)`
      (parallel fan-out, one call per season, a single bad lookup maps to
      `null` instead of failing the batch) and a new
      `UpcomingSeason.posterUrl` field. `ShowViewModel.loadMissingSeasons()`
      fetches posters in a second round trip *after* the placeholder cards
      are already on screen (poster art is a nice-to-have, not worth
      delaying the cards for), gated on Seerr being configured.
      `UpcomingSeasonCard` (phone + TV) now renders the real poster via
      Coil `AsyncImage` when present, falling back to the calendar icon
      otherwise (loading, no Seerr, or TMDB has none for that season).

Status: **done** (2026-07-21). Verified via remote
`:app:phone:compileLibreDebugKotlin`/`:app:tv:compileLibreDebugKotlin`,
`:data:testDebugUnitTest`, and `ktfmtCheck` (forced rerun) on rofl-13.

## FINDROID-34: PVR search button brand icon + clearer "Delete download" label

- [x] `PvrSearchButton` (Episode/Movie detail pages, Season screen episode
      rows, Calendar upcoming-episode rows) used a generic magnifier icon
      regardless of which service it searches. Added a required `service:
      PvrSource` param and switched to the actual Sonarr/Radarr brand icon
      (`ic_sonarr`/`ic_radarr`, full color via `tint = Color.Unspecified` -
      same fix already applied elsewhere in the app so brand logos don't
      get flattened to a silhouette by `Icon()`'s default tint). Needed a
      new `iconTint` param on `ItemActionButton` itself to override the
      icon's tint independently of the tile's `contentColor` (which
      controls the tonal container/label, not the icon glyph). All 4 call
      sites now pass `PvrSource.SONARR` (episode-related) or
      `PvrSource.RADARR` (movie).
- [x] User feedback: the detail-page "Delete" button was ambiguous - not
      clear whether it removes the item from Jellyfin or just the local
      download. Relabeled to the existing `delete_download` string
      ("Delete download"), already used elsewhere in the app (Downloads
      screen swipe-to-delete) for the exact same action.

Status: **done** (2026-07-21). Verified via remote
`:app:phone:compileLibreDebugKotlin` and `ktfmtCheck` (forced rerun) on
rofl-13.

## FINDROID-35: Notify on new library items, with a gated "Download" action

Periodic background check that detects items newly added anywhere in the
Jellyfin library (movies and episodes, not just PVR-tracked ones) and posts
a local notification, with an inline "Download" action when nothing is
already going to download the item automatically.

- [x] New `NewItemNotificationWorker` (`core/.../work/`) diffs the current
      library against the previous check. Fetches
      `JellyfinRepository.getItems(includeTypes = [MOVIE, EPISODE],
      recursive = true, sortBy = DATE_ADDED, sortOrder = DESCENDING, limit =
      100)` - server-side sort by `DateCreated` gives "most recently added"
      without needing `ItemFields.DATE_CREATED` on the response (that field
      only affects whether the property comes back populated on the model,
      not the sort order itself). New items are whatever wasn't in the
      previous cycle's fetched-id set.
- [x] State (last-check timestamp, and the fetched-id set from the last
      cycle) is kept in `AppPreferences`
      (`newItemNotifications{Enabled,CheckIntervalMinutes,LastCheckMillis,SeenItemIds}`),
      not a new Room table/column - deliberate, per instructions, to avoid
      a schema-version collision with FINDROID-36 (pending pre-order
      downloads) being built concurrently in another worktree. The seen-id
      set is self-bounding: each cycle just replaces it with that cycle's
      (capped) fetch, so it never grows unboundedly, at the cost of an item
      being able to scroll out of the window unobserved if more than 100
      items land between two checks - judged an acceptable tradeoff.
      First-ever check (no baseline yet) only records state and does not
      notify, so turning the feature on doesn't fire a notification for the
      entire pre-existing library.
- [x] Root problem while wiring the "Download" action's eligibility gate:
      `AutoDownloadRuleEvaluator.evaluate()` only knew how to enumerate and
      queue a whole season fetched fresh from the server - there was no
      standalone way to ask "does an enabled rule already cover this one
      already-known episode". Extracted a new top-level `coversEpisode(rule,
      episode)` predicate (same enabled/scope/onlyUnwatched/onlyNewEpisodes
      filters `evaluate()` applies per-episode) and had `evaluate()` call it
      too, so the two can't drift apart. Movies aren't covered by the rule
      system at all (rules are series/season-scoped), so every downloadable
      new movie without an existing source is eligible unconditionally.
- [x] Notification presentation is grouped/batched, not one-per-item: 1 new
      item posts a single notification; 2-6 post a real Android notification
      group (one `setGroupSummary` + one child per item, each independently
      actionable/tappable); more than 6 collapse into one inbox-style
      summary notification (tap to open the app, no per-item actions -
      ambiguous which item a shared button would target once there's too
      many to show as children). New `new_items` notification channel, kept
      separate from the existing `downloads`/`pvr_downloads` channels so
      users can mute it independently. `NewItemNotifier` builds all three
      shapes; `NewItemDownloadActionReceiver` (mirrors `DownloadActionReceiver`)
      handles the action tap, re-fetching the item server-side (Intent
      extras only carry an id + movie/episode flag) before enqueuing via
      `Downloader.downloadItem()`.
- [x] Tapping a notification (or its child) opens the specific item, not
      just the app. `DeepLinkViewModel` gained `resolveItem(itemId,
      isMovie)` - a direct id-based lookup (`getMovie`/`getEpisode`),
      simpler than its existing fuzzy `jellyfin://` name-matching resolver
      since the notification already knows the exact item. `MainActivity`
      gained a `FindroidMovie` branch in its deep-link `LaunchedEffect`
      (only `Show`/`Season`/`Episode` were handled before - movies had no
      notification-driven entry point until now) and reads new
      `EXTRA_OPEN_ITEM_ID`/`EXTRA_OPEN_ITEM_IS_MOVIE` intent extras,
      mirroring the existing `EXTRA_OPEN_DOWNLOADS` pattern.
- [x] Settings: new "New item notifications" group under the existing
      Downloads settings category (switch + check-interval, same
      presets/range picker as the auto-download interval). Off by default -
      unlike auto-download rules (an explicit per-show opt-in), this checks
      the whole library unconditionally, so it shouldn't start notifying
      anyone who hasn't deliberately turned it on. Scheduling in
      `BaseApplication.scheduleNewItemNotifications()` follows
      `scheduleAutoDeleteWatched()`'s pattern exactly (cancels the periodic
      work outright while the toggle is off, rather than leaving a
      worker that wakes the process just to no-op). Phone only, matching
      the existing precedent that TV's `BaseApplication` doesn't schedule
      any background workers at all (no downloads/auto-download/sync on
      TV).
- [x] New `ic_bell` drawable (core + settings modules, same Lucide-style
      vector convention as the rest of the icon set - no matching icon
      already existed).

Status: **done** (2026-07-22). Verified via remote
`:app:phone:compileLibreDebugKotlin`, `:app:tv:compileLibreDebugKotlin`,
`ktfmtCheck` (forced rerun), and `:data:testDebugUnitTest`/
`:core:testLibreDebugUnitTest` on rofl-13. Not installed/deployed to a
device for this change.

## FINDROID-36: "Pre-order" downloads for not-yet-available seasons/episodes

User request: the Show/Season screens already render dimmed
`UpcomingSeasonCard`/`UpcomingEpisodeCard` placeholders for Sonarr-known
seasons/episodes that aren't in the Jellyfin library yet - let the user
queue a download request for one of those so it starts automatically once
Sonarr grabs it, Jellyfin scans it in, and it actually appears, instead of
requiring a manual re-visit to notice and tap "download".

- [x] New Room entity `PendingDownloadRequestDto` (table
      `pending_download_requests`, DB bumped to v16 with an `AutoMigration`)
      keyed by Jellyfin `seriesId` + Sonarr-style `seasonNumber`/nullable
      `episodeNumber` (null = whole-season request) rather than a Jellyfin
      item id, since there isn't one until the target actually appears.
      `sonarrEpisodeId` stashed for convenience, not currently used for
      matching. DAO's `getPendingDownloadRequest` needed an `OR (x IS NULL
      AND :x IS NULL)` clause since `episodeNumber = :episodeNumber` never
      matches when both sides are NULL in SQL.
- [x] `PendingDownloadRequestRepository`/`Impl` (mirrors
      `AutoDownloadRuleRepository`'s shape) - `queue`/`cancel`/`isQueued`
      per season/episode, `getQueuedForSeries` for the Show/Season screens,
      `getAll` for the worker.
- [x] `PendingDownloadFulfiller` (mirrors `AutoDownloadRuleEvaluator`):
      resolves one request against the live Jellyfin library by season/
      episode number, dedups against an existing `sources` row exactly like
      the auto-download evaluator, and enqueues via `Downloader
      .downloadItem`. A whole-season request only counts as fulfilled once
      the season actually has episodes to download (a bare season shell
      with none yet is left pending rather than silently dropped). One-off
      semantics, not a persistent "keep following new episodes" rule -
      that's what `AutoDownloadRuleRepository` is already for.
- [x] `PendingDownloadWorker` (periodic `CoroutineWorker`, same
      current-server-only scoping rationale as `AutoDownloadWorker` since
      `Downloader`/`JellyfinRepository` are server-scoped singletons),
      registered in `app/phone/.../BaseApplication.kt` alongside the
      existing auto-download scheduling (periodic + one-shot startup run).
      Reuses the existing `autoDownloadCheckIntervalMinutes` preference
      rather than adding a new one. Phone-only, like every other
      WorkManager job - the TV app schedules none.
- [x] `PendingDownloadFulfilledNotifier`, same lazy-channel/tap-opens-app
      pattern as `PvrDownloadFinishedNotifier`, separate channel so users
      can mute pre-order fulfillment notifications independently of PVR
      queue-finished ones.
- [x] UI: `UpcomingEpisodeCard`/`UpcomingSeasonCard` (phone) gained a
      download/check icon-button toggle (queue when unqueued, cancel when
      already queued, primary-tinted when queued) - inline next to the PVR
      search button on the episode row, overlaid on the poster corner for
      the season card. Same on the TV equivalents; those cards' outer
      `Surface` stays `enabled = false` (no Seerr screen to open there) but
      the new icon button is independently focusable, since it's now the
      one actionable thing the row can do. `ShowState`/`SeasonState` gained
      `queuedSeasonNumbers`/`queuedEpisodeNumbers`, refreshed after every
      toggle from `PendingDownloadRequestRepository.getQueuedForSeries`.
- [x] Room schema JSON for v16 hit the known `just sync` rsync-wipes-the-
      schema-dir-before-KSP-regenerates-it gotcha - forced via
      `./gradlew :data:kspDebugKotlin --rerun-tasks` directly over SSH on
      rofl-13, then scp'd back into `data/schemas/...ServerDatabase/16.json`
      and committed.

Status: **done** (2026-07-22). Verified via remote
`:app:phone:compileLibreDebugKotlin`/`:app:tv:compileLibreDebugKotlin`,
`ktfmtCheck` (forced rerun), and `:data:testDebugUnitTest`/
`:core:testLibreDebugUnitTest` on rofl-13. No existing Room migration unit
test class was found to re-run.

## FINDROID-37: Collapsible seasons + expected download size + low-space warning in the download-scope dialog

`DownloadScopeDialog` (phone + TV, separate implementations) rendered a
flat, always-expanded toggle row per season and gave no indication of how
much a bulk selection would actually download or whether the device had
room for it.

- [x] Season rows now live behind a collapsible "Seasons (N)" header
      (chevron up/down, existing `ic_chevron_down`/`ic_chevron_up`
      drawables), collapsed by default so a 12-season show doesn't dominate
      the dialog on open. Collapsed rows aren't composed at all - phone
      wraps `seasons.forEach` in an `if (seasonsExpanded)`, TV skips the
      `items(seasons)` block entirely in the `LazyColumn` scope. The "whole
      show" toggle stays outside the section, always visible.
- [x] Added `ShowViewModel.getUndownloadedEpisodeSize`/
      `SeasonViewModel.getUndownloadedEpisodeSize(seasonId)`: fetches a
      season's episodes via `JellyfinRepository.getEpisodes(fields =
      listOf(ItemFields.MEDIA_SOURCES))` (same call shape as
      `AutoDownloadRuleEvaluator`/`PendingDownloadFulfiller`, read-only,
      neither of those files touched), skips any episode where
      `ServerDatabaseDao.getSources(episodeId)` already returns a row (already
      downloaded/queued), and sums the remaining episodes' primary source
      `size` - "how much *more* space this selection needs", not its full
      size on disk.
      Threaded through exactly like the existing `getSeasons` lambda:
      `ItemButtonsBar` gained a `getSeasonSize` param (phone only, since
      TV's `DownloadScopeDialog` is invoked directly from `ShowScreen`/
      `SeasonScreen` with no `ItemButtonsBar` equivalent), wired from both
      platforms' Show/Season screens to the new ViewModel method.
- [x] `DownloadScopeDialog` caches fetched sizes in a
      `mutableStateMapOf<UUID, Long>` keyed by season id - toggling a season
      off and back on doesn't re-fetch. A `LaunchedEffect(selectedSeasonIds)`
      fetches any newly-selected, not-yet-cached seasons in parallel
      (`async`/`awaitAll` inside `coroutineScope`) and shows a small spinner
      next to the total while any selected season's size is still pending.
      Total is `formatBinaryFileSize` of the cached sizes' sum, shown only
      in bulk-selection mode (phone's single-episode `thisEpisodeOnly` mode
      skips the estimate - it's a single already-known-size download).
- [x] Low-space warning: available bytes resolved once when the dialog
      opens by reusing `ItemButtonsBar`'s existing
      `resolveDownloadStorageIndex` + `StatFs.availableBytes` pattern. If
      the configured download location is unresolved (index -1, i.e. "ask"
      or unset - true today for Show/Season screens on both platforms,
      which never wired `downloadLocationPreference` through), falls back
      to the *maximum* available bytes across all mounted volumes rather
      than the first one, so a full primary volume doesn't produce a false
      warning when the user will get to pick a different volume in a later
      step. Shown as inline error-tinted text with `ic_alert_circle` once
      the size estimate has finished loading and exceeds available space -
      warning only, never blocks the download button.
- [x] New strings: `download_scope_seasons_header`,
      `download_scope_estimated_size`, `download_scope_low_space_warning`
      (English base only, per repo convention for machine-translated
      locales).

Status: **done** (2026-07-22). Verified via remote
`:app:phone:compileLibreDebugKotlin`/`:app:tv:compileLibreDebugKotlin`,
`:modes:film:compileDebugKotlin`, `:core:compileLibreDebugKotlin`, and
`ktfmtCheck` on rofl-13. No Room schema changes in this feature.

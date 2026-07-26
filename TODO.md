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

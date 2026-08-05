# Disposable Jellyfin fixture for Play Store screenshots (FINDROID-71)

A throwaway, pre-configured Jellyfin server used only to capture Play Store screenshots via
`.github/workflows/screenshots.yaml` (or locally via `just jellyfin-fixture-up`). Modeled on the
sibling `netbox-and-chill` (Nyetbox) project's own screenshot-automation fixture - see that
repo's `docs/screenshots.md` for the parallel NetBox-side writeup.

## Why a pre-baked config instead of scripting the setup wizard fresh every run

Unlike `netbox-docker`, Jellyfin has no `SUPERUSER_*`-env-var style auto-provisioning, and its
setup wizard + first library scan (which normally fetches real metadata/artwork from TMDB) is
slow and non-deterministic if run live on every CI job. Instead, `config-fixture/` is a **fully
baked** Jellyfin `/config` directory - admin user already created, remote access disabled, and a
Movies library already scanned with real metadata and artwork - committed to the repo. CI (and
`just jellyfin-fixture-up`) just seeds a fresh Docker volume from it and starts Jellyfin already
fully configured; no wizard, no scan, no TMDB round-trip at run time.

Login for the fixture: username `admin`, password `adminpass123`.

## Why the media files aren't committed

`media/` is gitignored. Video, even a short clip, doesn't compress well in git history, so the
actual bytes are fetched on demand instead: `just jellyfin-fixture-media` (`fetch-media.sh`)
downloads two small, already-short **official** trailer encodes - deliberately *not* full films
trimmed/re-encoded locally, since that's heavy CPU work with no business running on a developer
workstation just to produce a few MB of fixture data:

- **Big Buck Bunny** (2008, Blender Foundation, CC BY 3.0) - official iPhone-encoded trailer
  (~3.9MB).
- **Sintel** (2010, Blender Foundation, CC BY 3.0) - official 480p trailer (~4.4MB).

Poster art is fetched from Wikimedia Commons (the same blender.org-sourced, CC BY 3.0 images used
on each film's Wikipedia article) - no image generation needed either.

**The fetched filenames/paths must exactly match what `config-fixture/` already has cataloged** -
Jellyfin's baked SQLite catalog keys items by path. If you need different/additional content,
regenerate the fixture (below) rather than just editing `fetch-media.sh` in place.

## Regenerating the fixture

1. `rm -rf ci/jellyfin/config-fixture ci/jellyfin/media`
2. `./ci/jellyfin/fetch-media.sh`
3. Run Jellyfin against `ci/jellyfin/media` with a fresh, empty `/config` (see the Startup REST
   API sequence baked into this history if scripting it: `/Startup/User` ->
   `/Startup/Configuration` -> `/Startup/RemoteAccess` -> `/Startup/Complete`, then
   authenticate and `POST /Library/VirtualFolders` pointed at `/media/movies` with
   `EnableInternetProviders: false` at the library level so a future *live* re-scan in CI never
   depends on TMDB - only the one-time bake here does, deliberately, to get real metadata/art).
4. For each item, `POST /Items/{id}/Refresh?metadataRefreshMode=FullRefresh&imageRefreshMode=FullRefresh&replaceAllImages=true&replaceAllMetadata=true`
   - this is what actually triggers a real TMDB lookup regardless of the library's own
     `EnableInternetProviders` setting (confirmed empirically: that flag only gates *automatic*
     scans, not an explicit admin-triggered full refresh).
5. Verify via `GET /Items?Recursive=true&IncludeItemTypes=Movie&Fields=Overview` that both items
   have real overviews/images, stop the container, and copy `/config` back over
   `ci/jellyfin/config-fixture/` (drop `log/` and `data/SQLiteBackups/` - regenerated, not
   needed).

## Gotcha hit while baking this

A locally-extracted poster frame that's letterboxed/widescreen (e.g. straight off a 2.39:1 film
frame) silently gets ignored by Jellyfin's local-image heuristics - it reads as a "backdrop", not
a "poster". Not relevant now that posters come from Wikimedia Commons (already proper portrait
aspect ratio), but worth knowing if this fixture ever needs frame-grabbed art again.

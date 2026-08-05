#!/usr/bin/env bash
# Fetches the small, already-short, official Creative-Commons trailer clips the Jellyfin
# screenshot fixture expects, into ci/jellyfin/media/ (gitignored - see FINDROID-71). Deliberately
# NOT vendored in the repo: video, even a short clip, doesn't compress well in git history, and
# these sources are stable enough to re-fetch on demand. Run via `just jellyfin-fixture-media`.
#
# Plain downloads only, no local transcoding - both sources already publish small pre-made
# trailer encodes, so there's no need to fetch a full-length film and trim/re-encode it (heavy
# CPU work that has no business running on a developer workstation just to produce a few MB of
# fixture data).
#
# Output paths/filenames must exactly match what ci/jellyfin/config-fixture/ already has
# cataloged (Jellyfin's library scan matched by folder+file path when the fixture was baked) -
# don't rename anything here without re-baking the fixture to match.
set -euo pipefail

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
media_dir="$script_dir/media/movies"

fetch() {
  local name=$1 video_url=$2 video_ext=$3 poster_url=$4
  local dir="$media_dir/$name"
  local out="$dir/$name.$video_ext"

  if [[ -f "$out" ]]; then
    echo "Already present: $out" >&2
    return 0
  fi

  mkdir -p "$dir"
  echo "Fetching $name trailer..." >&2
  curl -sL --fail --max-time 120 -o "$out" "$video_url"
  echo "Fetching $name poster..." >&2
  curl -sL --fail --max-time 60 -o "$dir/poster.jpg" "$poster_url"
}

# Big Buck Bunny (2008) - Blender Foundation, CC BY 3.0. Official small iPhone-encoded trailer
# (~3.9MB, already short). Poster: Wikimedia Commons (blender.org-sourced, CC BY 3.0).
fetch "Big Buck Bunny (2008)" \
  "https://download.blender.org/peach/trailer/trailer_iphone.m4v" "m4v" \
  "https://upload.wikimedia.org/wikipedia/commons/c/c5/Big_buck_bunny_poster_big.jpg"

# Sintel (2010) - Blender Foundation, CC BY 3.0. Official small trailer (~4.4MB, already short).
# Poster: Wikimedia Commons (blender.org-sourced, CC BY 3.0).
fetch "Sintel (2010)" \
  "https://download.blender.org/durian/trailer/sintel_trailer-480p.mp4" "mp4" \
  "https://upload.wikimedia.org/wikipedia/commons/8/8f/Sintel_poster.jpg"

echo "Done: $media_dir" >&2

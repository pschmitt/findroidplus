![Findroid banner](images/findroid-banner.png)

# Findroid+

Findroid+ is [pschmitt](https://github.com/pschmitt)'s fork of
[Findroid](https://github.com/jarnedemeulemeester/findroid), a third-party native Android/Android
TV client for Jellyfin — with added features such as Sonarr/Radarr integration.

**This project is in its early stages so expect bugs.**

## Installation

Findroid+ isn't published on Google Play, Amazon Appstore, F-Droid, or IzzyOnDroid — those
listings are for the upstream Findroid project, under a different package name, and installing
from them will **not** give you this fork.

Instead, install and auto-update Findroid+ via [Obtainium](https://obtainium.imranr.dev/)
pointed at this repository, or grab an APK directly from the
[Releases page](https://github.com/pschmitt/findroidplus/releases).

[<img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" alt="Get it on Obtainium" height="60">][obtainium-link]

The badge tracks the `phone`/`release` APK (applicationId `dev.pschmitt.findroidplus`, no
`.debug` suffix, arch auto-selected) from the ["latest"](https://github.com/pschmitt/findroidplus/releases/tag/latest)
pre-release build. Want the TV build or a debuggable variant instead? Add the app normally in
Obtainium via this repo's URL and adjust the APK filter regex/prerelease settings, or just grab
the specific APK from the [Releases page](https://github.com/pschmitt/findroidplus/releases).

[obtainium-link]: https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22dev.pschmitt.findroidplus%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fpschmitt%2Ffindroidplus%22%2C%22author%22%3A%22pschmitt%22%2C%22name%22%3A%22Findroid%2B%22%2C%22preferredApkIndex%22%3A0%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Atrue%2C%5C%22fallbackToOlderReleases%5C%22%3Atrue%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22phone-.*-release%5C%5C%5C%5C.apk%24%5C%22%2C%5C%22invertAPKFilter%5C%22%3Afalse%2C%5C%22autoApkFilterByArch%5C%22%3Atrue%2C%5C%22trackOnly%5C%22%3Afalse%7D%22%7D

## Features
- Native interface for phone and Android TV, browsing movies, series, seasons, and
  episodes (direct play only, no transcoding)
- Offline downloads for playback on the road
- Playback via ExoPlayer or mpv, with broad codec support (H.264/H.265/H.266, VP8/VP9/AV1,
  DTS/TrueHD/AC-3, styled SSA/ASS subtitles, and more) and optional software decoding fallback
- Picture-in-picture mode
- Media chapters with timeline markers and chapter navigation gestures
- Trickplay (Jellyfin 10.9+) and media segment skip/auto-skip (Jellyfin 10.10+)
- Sonarr/Radarr integration: upcoming-release calendar and download queue status

## License
This project is licensed under [GPLv3](LICENSE).

The logo is a combination of the Jellyfin logo and the Android robot.

The Android robot is reproduced or modified from work created and shared by Google and used according to terms described in the Creative Commons 3.0 Attribution License.

Android is a trademark of Google LLC.

Google Play and the Google Play logo are trademarks of Google LLC.

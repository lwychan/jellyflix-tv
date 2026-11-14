# Notflix

[![License: GPL v2](https://img.shields.io/badge/License-GPLv2-blue.svg)](LICENSE)

A personal Jellyfin client for **Android TV / Google TV**. It does one job: browse
and play the films and shows on one home Jellyfin server.

> Status: personal use. Not for distribution.

## What it does

- Profile picker and sign-in against the fixed home server (set in
  `SessionStore.DEFAULT_SERVER_URL`).
- Home with Continue watching, Next up, and recently added rows; Shows and Movies
  as 5-per-row grids; Search; title pages with episodes.
- Server branding: the login disclaimer and the splash image are read from the server.

### Player

- Netflix-style layout: back and title top left; Episodes, Subtitles and Audio top right; play,
  replay 10 s, scrub bar and time remaining along the bottom.
- Skip Intro, Skip Recap, Skip Credits and Next Episode, from the server's media segments.
  Titles without segments get no button.
- An episode list over the video, a season at a time, with each episode's synopsis.
- Chapter ticks on the scrub bar, from the file's chapters.
- The next episode starts when one ends; after the last one the player returns to the title page.

### Playback choice

- **Direct play** (the raw file) is used only when the server confirms the TV can play it as it is.
  Otherwise the server's HLS stream is used (a remux or a transcode).
- Settings → Playback:
  - *Prefer direct play*: on by default. Off sends everything through the server.
  - *Tunneled decoding*: off by default.
  - *Match display refresh rate*: on by default.
- **Stall fallback:** if playback sits buffering for 15 s with no movement, the player
  switches once to the server's HLS stream at the same point.

## Stack

| Concern          | Choice                                                   |
| ---------------- | -------------------------------------------------------- |
| Language         | Kotlin 2.0                                               |
| UI               | Jetpack Compose + `androidx.tv:tv-material`              |
| Navigation       | `androidx.navigation.compose`                            |
| DI               | Hilt                                                     |
| Playback         | AndroidX Media3 (ExoPlayer) 1.4                          |
| Jellyfin API     | `org.jellyfin.sdk:jellyfin-core` 1.6                     |
| Persistence      | DataStore (preferences)                                  |
| Images           | Coil                                                     |

## Build and install

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk ANDROID_HOME=$HOME/Android/Sdk
./gradlew :app:testDebugUnitTest :app:assembleDebug
adb connect <tv-ip>:5555
adb install -r app/build/outputs/apk/debug/notflix-app-0.1.0-debug.apk
```

Use `install -r` to update in place: it keeps the app's data as long as the signing key matches.

## Repository layout

```
app/                Android TV application
  src/main/java/com/notflix/tv/
    data/           Jellyfin SDK wrapper, session, media repo, settings, branding
    ui/             Compose screens + ViewModels
    playback/       ExoPlayer, stream choice, refresh-rate match, device profile
    di/             Hilt modules
  src/test/         Unit tests (stream choice)
docs/
  ARCHITECTURE.md   How the pieces fit together
```

## License

GPL-2.0 — same family as the upstream Jellyfin ecosystem.

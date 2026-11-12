# Jellyflix

[![Build](https://github.com/ZL154/jellyflix-tv/actions/workflows/build.yml/badge.svg)](https://github.com/ZL154/jellyflix-tv/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/ZL154/jellyflix-tv?include_prereleases&label=release)](https://github.com/ZL154/jellyflix-tv/releases/latest)
[![License: GPL v2](https://img.shields.io/badge/License-GPLv2-blue.svg)](LICENSE)

**THIS IS NOT COMPLETE AND IS A WORK IN PROGRESS**

A Jellyfin client for **Android TV / Google TV** focused on two things the
official clients leave on the table:

1. **Playback that feels native.** Tunneled MediaCodec decoding, display
   refresh-rate matching (goodbye 24 fps judder), real Atmos / DTS passthrough,
   and a device profile built from the codec list the TV actually reports —
   not a generic Android fallback that transcodes when it shouldn't.
2. **Real plugin support on the TV itself.** Third parties can ship APKs that
   add home rows, subtitle providers, scrobblers, and playback hooks. Plugins
   load in-process via `DexClassLoader` but are scoped by a declared
   permission list the user approves the first time a plugin is enabled.

> Status: **scaffold**. The project builds a working skeleton — server connect,
> login, home, library grid, ExoPlayer playback, plugin loader — and the core
> optimisations are wired up. Details screen, settings, Quick Connect, 4K HDR
> track preferences, and the in-app plugin store are the next milestones.

## Stack

| Concern          | Choice                                                   |
| ---------------- | -------------------------------------------------------- |
| Language         | Kotlin 2.0                                               |
| UI               | Jetpack Compose + `androidx.tv:tv-material` (1.0 stable) |
| Navigation       | `androidx.navigation.compose`                            |
| DI               | Hilt                                                     |
| Playback         | AndroidX Media3 (ExoPlayer) 1.4                          |
| Jellyfin API     | `org.jellyfin.sdk:jellyfin-core` 1.6                     |
| Persistence      | DataStore (preferences)                                  |
| Images           | Coil                                                     |
| Min / Target SDK | 23 / 34                                                  |

## Install on a TV

### Option A — sideload a prebuilt APK (easiest)

Every push to `main` produces a release APK in **[GitHub Actions → latest run →
Artifacts](https://github.com/ZL154/jellyflix-tv/actions/workflows/build.yml)**,
and every tagged `v*` release publishes APKs to **[Releases](https://github.com/ZL154/jellyflix-tv/releases)**.

1. Enable Developer options → ADB debugging on the TV (Settings → Device
   Preferences → About → tap Build ×7).
2. From a laptop on the same network:
   ```bash
   adb connect <tv-ip>:5555
   adb install jellyflix-app-*.apk
   adb install jellyflix-sample-plugin-*.apk   # optional, reference plugin
   ```
3. No ADB? Drop the APK on a USB stick or use an app like *Send files to TV*
   or *X-plore* on the TV, then open the APK to install.

### Option B — build from source

For iterating on code, install **Android Studio Panda 4 (2025.3.4)** or
newer — full setup in [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md).

```bash
# One-off: generate the Gradle wrapper jar (not committed).
gradle wrapper --gradle-version 8.9

./gradlew :app:installRelease         # installs over ADB
./gradlew :sample-plugin:installDebug # optional reference plugin
```

## Repository layout

```
app/                Main Android TV application
  src/main/java/com/jellyflix/tv/
    data/           Jellyfin SDK wrapper, session, media repo
    ui/             Compose screens + ViewModels (connect, login, home, library)
    playback/       ExoPlayer, refresh-rate match, device-profile builder
    plugin/         Plugin host + DexClassLoader-based manager
    di/             Hilt modules
plugin-api/         Stable interfaces third-party plugins compile against
sample-plugin/      Reference plugin: adds a "Trending" row to home
docs/
  ARCHITECTURE.md   How the pieces fit together
  PLUGINS.md        How to write a Jellyflix plugin
```

## Differentiators (what makes "optimized" real)

These are the things a Jellyfin client on a TV tends to get subtly wrong; the
scaffold wires each up in `playback/`:

- **Tunneled decoder path** (`PlaybackOptimizer.renderers`) — MediaCodec
  hands frames directly to the display pipeline, skipping a GPU compositor
  hop. Cuts 40–100 ms of latency and CPU load.
- **Refresh rate matching** (`PlaybackOptimizer.matchRefreshRate`) — on frame
  rate detection we reconfigure the window to the closest supported mode
  (23.976 → 23.976, 25 → 50, 59.94 → 59.94). No 3:2 pulldown judder.
- **Real device profile** (`StreamUrlResolver.buildDeviceProfile`) — the
  server-side direct-play decision is made from `MediaCodecList`, not from
  a one-size profile. HEVC 10-bit, AV1, EAC3 JOC, TrueHD Atmos all get
  declared when the decoder supports them.
- **Audio passthrough + offload** — `DefaultRenderersFactory` is built with
  offload + float output + passthrough enabled. Atmos and DTS:X bit-perfect
  when the panel/receiver exposes it.
- **MediaSession service** — the Now-Playing card in the launcher shows the
  current episode; Assistant remotes can pause/seek without re-opening the app.

## License

GPL-2.0 — same family as the upstream Jellyfin ecosystem.

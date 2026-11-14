# Development

The CI-and-download loop is fine for shipping. For iterating on features you
want a local toolchain so edit→run is ~15 seconds instead of 3 minutes.

## One-time setup

1. **Install Android Studio Panda 4** (2025.3.4) from
   https://developer.android.com/studio.
   The first-run wizard installs the Android 14 (API 34) SDK and build tools.
2. **Open this folder as a project.** Studio detects Gradle and prompts to
   generate the wrapper jar; accept. Sync will download dependencies (~500MB
   first time).
3. **Optional — create an Android TV emulator.**
   Tools → Device Manager → Create Device → *Television* category →
   **Android TV (1080p)** → choose the **API 34 / Google TV** system image.

## Running

- **On the emulator**: click the green ▶️ with the TV emulator selected.
- **On your real TV**:
  1. On the TV: Settings → Device Preferences → About → tap **Build** 7×.
  2. Developer options → enable **ADB debugging**.
  3. From your laptop (same network): `adb connect <tv-ip>:5555`.
  4. The TV shows up next to the emulator in Studio's device dropdown.

## Iteration tips

- **Instant Run / Apply Changes** (⌃⌘R / Ctrl+F10) redeploys code changes
  without a full APK reinstall — saves ~10s per cycle.
- **Logcat** (⌘6) filtered to tag `Notflix` shows our Timber output.
- **Layout Inspector** (Tools → Layout Inspector) is useful for debugging
  focus / D-pad navigation on TV.

## When you don't need Studio

If you're only tweaking YAML, markdown, Gradle versions, or doc strings,
just push. The CI build in `.github/workflows/build.yml` catches compile
errors in ~2 minutes and the failed log tells you exactly what to fix.

## Publishing what you built

See [RELEASING.md](RELEASING.md). Short version: bump version in
`app/build.gradle.kts`, tag `vX.Y.Z`, push the tag. The release workflow
does the rest.

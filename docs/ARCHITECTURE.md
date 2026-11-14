# Architecture

## Layers

```
┌──────────────────┐
│       app        │  Compose UI, ViewModels, Activities, Hilt graph
│                  │
│   playback/ ─────┼──► StreamUrlResolver → Jellyfin /PlaybackInfo
│   data/ ─────────┼──► JellyfinClient (SDK) + SessionStore / SettingsStore (DataStore)
└──────────────────┘
```

There are no plugins. The app talks only to the one home Jellyfin server.

## Data flow: playback

```
User clicks "Play" on a Movie/Episode detail
 ├─► PlayerActivity starts with itemId; reads SettingsStore once
 ├─► StreamUrlResolver.resolve(itemId, allowDirect = preferDirectPlay)
 │     ├─ buildDeviceProfile(): hardware video decoders + all audio decoders
 │     ├─ POST /Items/{id}/PlaybackInfo
 │     └─ StreamRoute.choose(): DIRECT_FILE only if the server says the raw file plays;
 │        otherwise SERVER_HLS (the server's remux or transcode URL)
 ├─► ExoPlayer.setMediaItem(stream.url)
 ├─► onVideoSizeChanged → PlaybackOptimizer.matchRefreshRate (if the setting is on)
 └─► stall watchdog: 15 s buffering with no movement → one switch to SERVER_HLS at the same point
```

`StreamRoute` is a pure function (see `StreamRouteTest`), so the choice between
the raw file and the server's stream can be tested without a device.

## Auth & session

`SessionStore` keeps the server URL, user id, access token, and device id in
a single `DataStore<Preferences>` file (`auth`). The file is explicitly
excluded from cloud backup via `backup_rules.xml` so a Google Backup restore
on a new TV does not leak the token.

`SessionViewModel` is the top-level state holder and drives the NavHost in
`MainActivity`. Its stages — `NeedsServer / NeedsLogin / Authenticated` —
map to the profile picker, login and home routes.

## Settings

`SettingsStore` holds three playback toggles, each read by the player:

| Setting                  | Default | Read by                                        |
| ------------------------ | ------- | ---------------------------------------------- |
| Prefer direct play       | on      | `StreamUrlResolver.resolve(allowDirect)`       |
| Tunneled decoding        | off     | `DefaultTrackSelector` in `PlayerActivity`     |
| Match display refresh    | on      | `PlaybackOptimizer.matchRefreshRate`           |

## Optimisation levers

| Lever                          | Where                                           |
| ------------------------------ | ----------------------------------------------- |
| Tunneled decoding (setting)    | `PlayerActivity.buildPlayer`                    |
| Refresh-rate matching          | `PlaybackOptimizer.matchRefreshRate`            |
| Device profile from MediaCodec | `StreamUrlResolver.buildDeviceProfile`          |
| Audio offload + float          | `DefaultRenderersFactory` flags in `renderers`  |
| Track selection defaults       | `PlaybackOptimizer.trackParameters`             |
| Stall fallback to server HLS   | `PlayerActivity.startStallWatchdog`             |
| MediaSession / Now Playing     | `PlaybackService`                               |
| Seek increments (10s / 30s)    | `ExoPlayer.Builder` in `PlayerActivity`         |

# MeloX Android

[中文](README.md)

[![Downloads](https://img.shields.io/github/downloads/lladlam/MeloX-Android/total?label=downloads&color=2ea44f)](https://github.com/lladlam/MeloX-Android/releases)
[![Release](https://img.shields.io/github/v/release/lladlam/MeloX-Android?display_name=release&label=release&color=ff2d55)](https://github.com/lladlam/MeloX-Android/releases/latest)
[![Last Commit](https://img.shields.io/github/last-commit/lladlam/MeloX-Android/main?label=last%20commit&color=007aff)](https://github.com/lladlam/MeloX-Android/commits/main)
[![QQ Group](https://img.shields.io/badge/QQ%20Group-MeloX--Android-12B7F5?logo=tencentqq&logoColor=white)](https://qm.qq.com/q/wbhFQxj7mo)
[![Telegram](https://img.shields.io/badge/Telegram-MeloX--Android-26A5E4?logo=telegram&logoColor=white)](https://t.me/+Eiy42NxIVNUwOWU1)
[![License](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

<p align="center">
  <img src="android/app/src/main/res/drawable-nodpi/ic_launcher_foreground.png" width="128" alt="MeloX Android icon" />
</p>

<p align="center">
  Native Android port of MeloX, built with Kotlin and Jetpack Compose
</p>

> [!IMPORTANT]
> **MeloX Android is still in development.** Core features for the current Android scope have been migrated and continue to follow the MeloX mainline. Third-party APIs, OEM protocols, and OS versions can still change compatibility.

> MeloX Android is an unofficial open-source project. It is not affiliated with, partnered with, or authorized by NetEase Cloud Music, Xiaomi, Apple, or their related companies.

## Current version: 0.6.1

`0.6.1` continues from `0.6.0` with playback-queue restore, lyric timing, HyperOS Super Island, LX source crypto, and a bottom-bar rework covering spring motion, glass, and the mini player.

- Download and full notes: [GitHub Releases](https://github.com/lladlam/MeloX-Android/releases/tag/0.6.1)
- Version history: [CHANGELOG.md](CHANGELOG.md)

> [!WARNING]
> Third-party music sources are imported by the user and depend on external services. Sources used for testing before release have failed because of dead endpoints, rate limits, DNS or certificate errors, or unplayable URLs. MeloX cannot guarantee that any third-party source works. If you hit a problem, open Settings → About, scroll to the bottom, export all MeloX logs, and send them to `lladlam114@gmail.com`. Logs may contain account state, song metadata, request URLs, or other private data. Review them before sending.

## About

MeloX Android is a native Android port of the design, interaction, and business logic in [lladlam/MeloX](https://github.com/lladlam/MeloX).

The goal is not a WebView wrapper. MeloX is reimplemented with native Android capabilities:

- **Kotlin + Jetpack Compose** for the interface;
- **AndroidX Media3 / ExoPlayer** for playback, background audio, and the system media session;
- iOS-only capabilities mapped to Android platform features;
- optional HyperOS Focus Notification / Super Island support, while remaining usable on stock Android;
- a MeloX / Apple Music style player, lyrics, library, and transitions;
- Apple Watch features are outside the Android scope.

Root is not required. Platform extras use standard Android or OEM APIs and degrade when unsupported.

## Implemented

### Account and NetEase Cloud Music

- NetEase web login;
- persistent `MUSIC_U` cookie session;
- authenticated search, song details, lyrics, playback URLs, and library requests;
- playback cache refresh after account entitlement changes.

### Search and library

- Songs, playlists, albums, artists, users, and podcasts;
- Liked songs;
- User playlists;
- Create public or private playlists, add songs, and remove songs from your own playlists;
- Recently played;
- Playlist, album, artist, song wiki, and comment details;
- Charts, home recommendations, daily recommendations, private FM, and heart mode;
- Podcast home, categories, subscriptions, and episode details;
- NetEase cloud drive read, search, upload, playback, and delete;
- Multi-select download, per-download quality, offline lyrics, auto cache, and storage repair;
- App-private offline copies, optional MediaStore export, and a local library by song, artist, album, and folder;
- Play directly from the library or search results.

### Player

- Mini player;
- Apple Music and Classic full-screen shells;
- Queue;
- Play next, manual queue, and queue ordering;
- Play, pause, previous, next, and seeking;
- Background playback;
- MediaSession and system media controls;
- Lock-screen metadata;
- Artwork and dynamic color background;
- AutoMix dual-player preload, analysis, transition planning, tempo matching, fades, and EQ envelope;
- 10-band equalizer from 31 Hz to 16 kHz, presets, system or player volume, and a sleep timer;
- Shared-element player transitions, expand and collapse, and landscape player.

### Quality

NetEase playback follows the MeloX quality model:

- Standard: `standard`
- Higher: `exhigh`
- Lossless: `lossless`
- Hi-Res: `hires`
- HD surround: `jyeffect`
- Immersive surround: `sky`
- Master: `jymaster`

The playable quality depends on the track, account entitlement, region, and the NetEase response. Unavailable targets fall back to an available resource, and the player shows the quality the server actually returned when possible.

### Lyrics

- Line-synced LRC;
- Official word-synced YRC;
- Current-line follow and scrolling;
- Word-level progress;
- Focus, scale, and color transitions;
- Ruby layout, translation, interlude countdown, tap-to-seek, and long-press share;
- Apple Music trailing scroll, bounce, blur, highlight, long-tone, and refresh-rate settings;
- EVA layouts and 18 TextPV templates;
- Skyline landscape lyrics;
- System media lyrics, a dedicated lyric notification, and a draggable floating lyric window.

### HyperOS

- Standard Android media notification and MediaSession;
- Optional HyperOS Focus Notification / Super Island bridge;
- OEM features live in a separate adapter and are not required on stock Android.

### Social, recognition, and settings

- Listen Together rooms, members, progress, and queue sync;
- Contacts, conversations, text messages, and in-app sharing;
- Microphone recording, NetEase audio fingerprinting, and continuous recognition;
- First-run guide, clipboard link detection, page ordering, and feature switches;
- GitHub version check, update prompt, license, and settings reset.

### Third-party music sources

- Third-party sources can be enabled separately in Music Services after reading their agreement. The switch is local and is not cloud-controlled.
- YouTube Music uses the dual-backend design from [lladlam/Square](https://github.com/lladlam/Square). The InnerTube module is vendored at `android/innertube/` from [Metrolist](https://github.com/mostafaalagamy/Metrolist) and keeps the `com.metrolist.innertube` package.
- Spotify follows the Square Spotify backend. Playback uses `librespot-java`; catalog and OAuth use the Spotify Web API. The user enters a Client ID in the app.
- Spotify, YouTube Music, NetEase, and the other sources share the provider-neutral download and offline path (`MeloXProviderDownloadStore`).
- vivo devices automatically try OriginOS Atomic Island. There is no separate switch. Unsupported devices keep the standard Android media notification. The adapter uses the documented local `notification.superx.*` extras and does not depend on EMAS push.
- LX Music compatible JavaScript sources can be imported and resolved in a restricted QuickJS runtime.
- A personal CHKSZ API key can be configured to resolve NetEase, QQ Music, and Kugou tracks first. Register at `api.chksz.com`. Registration currently requires a LinuxDo account.
- If third-party resolution fails, playback falls back to the platform's native player. Lyrics still use MeloX's own lyric path.

## Platform scope

iOS Live Activity and Dynamic Island map to the Android media notification, lyric notification, and optional HyperOS Focus Notification. iOS picture-in-picture lyrics map to the Android floating lyric window. Apple Watch, watchOS, and macOS targets are not part of the Android APK.

Liquid Glass uses Kyant0 `AndroidLiquidGlass` Backdrop. The result still depends on the Android version, GPU, and OEM compositor.

## Stack

| Use | Android implementation |
| --- | --- |
| UI | Kotlin + Jetpack Compose |
| Navigation / lifecycle | AndroidX Navigation / Lifecycle |
| Playback | AndroidX Media3 / ExoPlayer |
| System media controls | MediaSession / MediaSessionService |
| Networking | OkHttp |
| Images | Coil 3 |
| Async | Kotlin Coroutines |
| Glass / Backdrop | Kyant0 `AndroidLiquidGlass` / `backdrop` |
| Xiaomi extras | HyperOS Focus Notification adapter |

## iOS to Android

| MeloX / iOS | MeloX Android |
| --- | --- |
| SwiftUI | Jetpack Compose |
| NavigationStack | Navigation Compose |
| AVPlayer / AVFoundation | Media3 ExoPlayer |
| MPNowPlayingInfoCenter / Remote Commands | MediaSession |
| Live Activity / Dynamic Island | Standard media notification + optional HyperOS Focus Notification / Super Island |
| SwiftUI Mesh / Flowing Light | Compose Canvas / dynamic color background |
| Apple Liquid Glass | AndroidLiquidGlass Backdrop refraction, blur, and fallback |

## Requirements

- Android 8.0 (API 26) or newer;
- Android SDK 37;
- JDK 17;
- Gradle 9.5.0;
- Open the `android/` directory in a current Android Studio.

Some RuntimeShader and Backdrop effects need a newer Android version. Older versions use a fallback style.

## Build

1. Clone:

   ```bash
   git clone https://github.com/lladlam/MeloX-Android.git
   cd MeloX-Android
   ```

2. Open this directory in Android Studio:

   ```text
   MeloX-Android/android
   ```

3. Install Android SDK 37 and use JDK 17.

4. Or build a debug APK with Gradle:

   ```bash
   cd android
   ./gradlew :app:assembleDebug --no-daemon --max-workers=2 --stacktrace
   ```

5. Output:

   ```text
   android/app/build/outputs/apk/debug/app-debug.apk
   ```

GitHub Actions builds a debug APK when `main` changes. Development APKs published to users are signed with the project release key and attached to the matching GitHub Release.

## Release signature

Official APKs on GitHub Releases are signed with the MeloX release certificate. SHA-256 fingerprint:

```text
DF:CC:A9:86:5B:87:A4:02:D3:41:98:5A:48:EB:13:2B:D8:67:9D:FA:6D:9D:50:2F:36:5D:D1:62:10:A5:EB:E9
```

Verify with Android SDK Build Tools:

```bash
apksigner verify --verbose --print-certs MeloX-Android-0.5.3.apk
```

## Layout

```text
.
├── android/
│   ├── app/
│   │   └── src/main/
│   │       ├── kotlin/com/lladlam/melox/
│   │       │   ├── core/          # account, network, quality, lyrics, library
│   │       │   ├── playback/      # Media3 service and source resolution
│   │       │   ├── platform/      # HyperOS and other platform adapters
│   │       │   └── ui/            # Compose screens, player, library, glass
│   │       └── res/
│   ├── build.gradle.kts
│   ├── innertube/                  # vendored Square/Metrolist InnerTube module
│   └── settings.gradle.kts
├── .github/workflows/             # Android CI / Release
├── LICENSE
└── README.md
```

## Credits

MeloX Android comes from the MeloX Android migration and also uses or references the projects below. **Each project keeps its own license. The GPLv3 of MeloX Android does not replace those licenses.**

### Upstream

- [lladlam/MeloX](https://github.com/lladlam/MeloX) — upstream MeloX and the main source of implementation, interface, interaction, and NetEase logic. Mostly GPLv3.

### Direct Android dependencies

- [AndroidX / Jetpack Compose](https://github.com/androidx/androidx) — Compose UI, Activity, Lifecycle, and Navigation. Mostly Apache License 2.0.
- [AndroidX Media3](https://github.com/androidx/media) — ExoPlayer, MediaSession, and background playback. Apache License 2.0.
- [Coil](https://github.com/coil-kt/coil) — Compose image loading. Apache License 2.0.
- [OkHttp](https://github.com/square/okhttp) — HTTP client. Apache License 2.0.
- [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) — coroutines. Apache License 2.0.
- `librespot-java` — Spotify session, playback, and offline cache. Apache License 2.0.
- [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) — anonymous YouTube Music search and stream URLs. GPL-3.0.
- [Metrolist InnerTube](https://github.com/mostafaalagamy/Metrolist) — signed-in YouTube Music catalog, library, and playlists. GPL-3.0, vendored at `android/innertube/`.
- [Miuix](https://github.com/compose-miuix-ui/miuix) — current Backdrop / Blur experiment uses `miuix-blur`. Apache License 2.0.

### Liquid Glass

The Android app depends on the `backdrop` component from [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass). `MeloXBackdropComponents.kt` adapts the official `LiquidButton` and `LiquidBottomTabs` samples while keeping MeloX iOS sizes and layout.

The glass effect is still experimental.

### API and UI references

- [NEORUAA/MeiloX](https://github.com/NEORUAA/MeiloX) — Mei-based Apple Music style NetEase client used as a UI reference.
- [thlucas1/SpotifyWebApiPython](https://github.com/thlucas1/SpotifyWebApiPython) — Spotify Web API reference.
- [bromothymolb/bilibili-api-zoku](https://github.com/bromothymolb/bilibili-api-zoku) — Bilibili API reference.

### Upstream MeloX references

Upstream MeloX also credits these projects. The Android port inherits parts of their lyric and NetEase work through that implementation:

- [jayfunc/BetterLyrics](https://github.com/jayfunc/BetterLyrics) — word-synced lyric rendering, glow, and motion;
- [WXRIW/Lyricify-Lyrics-Helper](https://github.com/WXRIW/Lyricify-Lyrics-Helper) — NetEase YRC parsing;
- [qier222/YesPlayMusic](https://github.com/qier222/YesPlayMusic) — NetEase API and player reference.

If a later Android version migrates PV Tool, BeatNet, or other MeloX features, their separate licenses and attribution will be kept as required upstream.

## Disclaimer

This project is for learning, research, and open-source exchange.

- MeloX Android is not intended to bypass payment, copyright, region locks, or NetEase service limits;
- Users must follow local law, the NetEase Cloud Music terms, and music copyright;
- Third-party interfaces can change, and continued availability is not guaranteed;
- The project is provided without warranty under its license. Use is at your own risk.

## License

The MeloX Android code is released under the same **GNU General Public License version 3 (GPLv3)** as upstream MeloX. See [LICENSE](LICENSE).

Copying, modifying, or distributing this project requires compliance with GPLv3, including source availability, copyright notices, change notices, and same-license distribution.

## Links

- [LINUX DO](https://linux.do/)

Third-party code, libraries, assets, and models keep their own licenses. Contributors retain copyright in their contributions.

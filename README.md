# MeloX Android

[中文](README.zh-Hans.md)

[![Downloads](https://img.shields.io/github/downloads/lladlam/MeloX-Android/total?label=downloads&color=2ea44f)](https://github.com/lladlam/MeloX-Android/releases)
[![Release](https://img.shields.io/github/v/release/lladlam/MeloX-Android?display_name=release&label=release&color=ff2d55)](https://github.com/lladlam/MeloX-Android/releases/latest)
[![Last Commit](https://img.shields.io/github/last-commit/lladlam/MeloX-Android/main?label=last%20commit&color=007aff)](https://github.com/lladlam/MeloX-Android/commits/main)
[![QQ Group](https://img.shields.io/badge/QQ%20Group-MeloX--Android-12B7F5?logo=tencentqq&logoColor=white)](https://qm.qq.com/q/wbhFQxj7mo)
[![Telegram](https://img.shields.io/badge/Telegram-MeloX--Android-26A5E4?logo=telegram&logoColor=white)](https://t.me/+Eiy42NxIVNUwOWU1)
[![License](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

<p align="center">
  <img src="docs/icon.png" width="128" alt="MeloX Android icon" />
</p>

<p align="center">
  Native Android port of MeloX, built with Kotlin and Jetpack Compose
</p>

## Desktop editions

The **Windows** and **Linux** editions are in their final verification stage. If you would like to use the Windows edition, please Star both projects:

- [MeloX-Windows](https://github.com/lladlam/MeloX-Windows)
- [MeloX-Linux](https://github.com/lladlam/MeloX-Linux)

> [!IMPORTANT]
> **MeloX Android is still in development.** Core features for the current Android scope have been migrated and continue to follow the MeloX mainline. Third-party APIs, OEM protocols, and OS versions can still change compatibility.

> MeloX Android is an unofficial open-source project. It is not affiliated with, partnered with, or authorized by NetEase Cloud Music, Xiaomi, Apple, or their related companies.

## Current version: 0.7.2

`0.7.2` (versionCode **27**, 2026-11-06) adds the experimental Plugin API v1 music-source system and an optional MeloX Rear companion entry, while completing background page restoration, the reworked home quick actions, Listen Together solo mode and QQ bracket backing vocals.

- Experimental dynamic plugin sources: bounded package and signature validation, explicit permission confirmation, an isolated QuickJS runtime and account-scoped storage, wired through 12 capabilities from search and playback to lyrics, favorites, downloads and authentication. This is an RC engineering preview; API 24, complete UI/OAuth/background flows and physical-device acceptance are still pending. See [the plugin docs](docs/plugin-system/README.md).
- Background restoration now keeps the active page and loaded data across stopped-Activity destruction for home/explore collections and charts, search results and details, library subpages, podcasts, downloads, cloud drive, messages and the full-screen player. Daily recommendations and charts also stop coming back blank on the second open.
- Home quick actions use two half-width cards per snapped page with a left gutter and a 44dp right peek; card press ripples and rectangular shadows are gone. Added a configurable playback cache limit (256MB–2GB, LRU).
- Listen Together gains a solo mode that keeps the room online without sending local commands. QQ Music bracket lines can become backing vocals of the previous line (off by default). Fixed the blank YouTube Music sign-in WebView, Qishui QR sign-in, and completed zh-Hans/zh-Hant/ja translations.
- An optional experimental MeloX Rear companion entry appears under Settings → Experimental; the separate YTRear-derived app is not distributed with this release and its upstream license is still pending.

- Download and full notes: [GitHub Releases](https://github.com/lladlam/MeloX-Android/releases/tag/0.7.2)
- Version history: [CHANGELOG.md](CHANGELOG.md)
- This release: [docs/releases/0.7.2.md](docs/releases/0.7.2.md)

> [!WARNING]
> Third-party music sources are imported by the user and depend on external services. Sources used for testing before release have failed because of dead endpoints, rate limits, DNS or certificate errors, or unplayable URLs. MeloX cannot guarantee that any third-party source works. If you hit a problem, open Settings → About, scroll to the bottom, export all MeloX logs, and send them to `lladlam114@gmail.com`. Logs may contain account state, song metadata, request URLs, or other private data. Review them before sending.

## About

MeloX Android is a native Android port of the design, interaction, and business logic in [MeloX](https://github.com/youshen2/MeloX), the iOS / macOS client created by [youshen2](https://github.com/youshen2). The iOS version is not made by the Android maintainer.

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
- Multi-select download, per-download quality, offline lyrics, auto cache, storage repair, and pause / resume;
- App-private offline copies, optional MediaStore export, and a local library by song, artist, album, and folder;
- Play directly from the library or search results;
- Library-wide search across songs, playlists, albums, artists, and recent playback.

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
- Shared-element player transitions, expand and collapse, and landscape player;
- Tap the now-playing title for a glass menu to open the artist, album, or current queue;
- Lyrics picture-in-picture with previous / play-pause / next remote actions.
- In-app playback device picker for the phone speaker, Bluetooth, wired and USB outputs.
- Nearby MeloX over LAN: control, cast (MeloX PCM), transfer, and listen together; see `docs/MELOX-LAN.md`.

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
- System media lyrics, a dedicated lyric notification, and a draggable floating lyric window;
- ColorOS lockscreen lyrics (`lyricInfo`) and SuperLyricApi publishing share the same lyric timeline.

### HyperOS

- Standard Android media notification and MediaSession;
- Optional HyperOS Focus Notification / Super Island bridge;
- OEM features live in a separate adapter and are not required on stock Android.

### Social, recognition, and settings

- Listen Together rooms, members, progress, and queue sync;
- Contacts, conversations, text messages, and in-app sharing;
- Microphone recording, NetEase audio fingerprinting, and continuous recognition;
- First-run welcome and account pages, in-app release notes, in-app log viewer, sponsor acknowledgements, clipboard link detection, page ordering, and feature switches;
- GitHub version check, update prompt, license, and settings reset.

### Third-party music sources

- Third-party sources can be enabled separately in Music Services after reading their agreement. The switch is local and is not cloud-controlled.
- YouTube Music uses the dual-backend design from [lladlam/Square](https://github.com/lladlam/Square). The InnerTube module is vendored at `android/innertube/` from [Metrolist](https://github.com/mostafaalagamy/Metrolist) and keeps the `com.metrolist.innertube` package.
- Spotify follows the Square Spotify backend. Playback uses `librespot-java`; catalog and OAuth use the Spotify Web API. The user enters a Client ID in the app.
- Spotify, YouTube Music, NetEase, and the other sources share the provider-neutral download and offline path (`MeloXProviderDownloadStore`).
- vivo devices automatically try OriginOS Atomic Island. There is no separate switch. Unsupported devices keep the standard Android media notification. The adapter uses the documented local `notification.superx.*` extras and does not depend on EMAS push.
- LX Music compatible JavaScript sources can be imported and resolved in a restricted QuickJS runtime.
- A personal CHKSZ API key can be configured to resolve NetEase, QQ Music, and Kugou tracks **after** the current source and the built-in cross-provider fallback both fail. Register at `api.chksz.com`. Registration currently requires a LinuxDo account.
- Playback order is: the current source → a strict full-audio match from another built-in source (only when "fetch unavailable resources from other platforms" is on) → configured third-party resolvers (only when that switch and its agreement are on). If every stage fails, the track is reported as unavailable instead of playing a preview.

## Platform scope

iOS Live Activity and Dynamic Island map to the Android media notification, lyric notification, and optional HyperOS Focus Notification. iOS picture-in-picture lyrics map to Android's native picture-in-picture window, with the draggable floating lyric window as a fallback. Apple Watch, watchOS, and macOS targets are not part of the Android APK.

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
    android/app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
    android/app/build/outputs/apk/debug/app-armeabi-v7a-debug.apk
   ```

GitHub Actions builds a debug APK when `main` changes. Rolling development builds are published to the `dev-latest` prerelease; they are signed with Android's default debug key, so they cannot update an official release install in place. Official releases are signed with the MeloX release key described below.

## Experimental CLI

Enable **Settings → Experimental features → CLI mode** to control playback over ADB:

```bash
python3 tools/melox_cli.py --package com.lladlam.melox.android.dev status
```

Supports playback controls, queue/status queries, seeking, volume, shuffle/repeat, and provider search/play. See [CLI documentation](docs/CLI.md) for setup and commands.

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

- [youshen2/MeloX](https://github.com/youshen2/MeloX) — the original iOS / macOS MeloX, created by [youshen2](https://github.com/youshen2), and the main source of implementation, interface, interaction, and NetEase logic. Mostly GPLv3.

### Direct Android dependencies

- [AndroidX / Jetpack Compose](https://github.com/androidx/androidx) — Compose UI, Activity, Lifecycle, and Navigation. Mostly Apache License 2.0.
- [AndroidX Media3](https://github.com/androidx/media) — ExoPlayer, MediaSession, and background playback. Apache License 2.0.
- [Jellyfin androidx-media](https://github.com/jellyfin/jellyfin-androidx-media) — prebuilt Media3 FFmpeg audio decoder used as the software FLAC fallback on affected Android 8.1 devices; GPL-3.0.
- [Coil](https://github.com/coil-kt/coil) — Compose image loading. Apache License 2.0.
- [OkHttp](https://github.com/square/okhttp) — HTTP client. Apache License 2.0.
- [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) — coroutines. Apache License 2.0.
- `librespot-java` — Spotify session, playback, and offline cache. Apache License 2.0.
- [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) — anonymous YouTube Music search and stream URLs. GPL-3.0.
- [Metrolist InnerTube](https://github.com/mostafaalagamy/Metrolist) — signed-in YouTube Music catalog, library, and playlists. GPL-3.0, vendored at `android/innertube/`.
- [ONNX Runtime](https://github.com/microsoft/onnxruntime) — on-device AutoSync lyric-alignment inference. MIT.
- [QuickJS Android](https://github.com/HarlonWang/quickjs-wrapper) — isolated runtime for LX Music-compatible source scripts. See the wrapper and QuickJS licenses.
- [ZXing](https://github.com/zxing/zxing) — QR-code generation. Apache License 2.0.
- [Shizuku](https://github.com/RikkaApps/Shizuku) — optional non-root HyperOS compatibility integration. Apache License 2.0.

### Liquid Glass

The Android app depends on the `backdrop` component from [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass). `MeloXBackdropComponents.kt` adapts the official `LiquidButton` and `LiquidBottomTabs` samples while keeping MeloX iOS sizes and layout.

SF Pro / SF Symbols are Apple Inc. system fonts used only for the app's icon glyphs. Normal UI text stays on XiaomiLTPro / MiLanPro. They are not covered by GPLv3; see [SF-Pro-NOTICE.md](docs/SF-Pro-NOTICE.md).

The glass effect is still experimental.

### API and UI references

- [megadonkatsu/YTRear](https://github.com/megadonkatsu/YTRear) — source for the experimental optional [MeloX Rear fork](https://github.com/lladlam/YTRear); the upstream repository does not specify a license.
- [WXRIW/Lyricify-Lyrics-Helper](https://github.com/WXRIW/Lyricify-Lyrics-Helper) — QQ Music anonymous search and lyric protocols, instrumental-placeholder detection, NetEase YRC, Lyricify Lines and Lyricify Syllable format references (Apache-2.0). See [LYRICS.md](docs/LYRICS.md).
- [guowenye/qishui-api](https://github.com/guowenye/qishui-api) — Qishui Music native API, QR sign-in, catalog, lyric, and playback-field reference. MIT; scope and validation: [QISHUI.md](docs/QISHUI.md).
- [NEORUAA/MeiloX](https://github.com/NEORUAA/MeiloX) — Mei-based Apple Music style NetEase client used as a UI reference.
- [thlucas1/SpotifyWebApiPython](https://github.com/thlucas1/SpotifyWebApiPython) — Spotify Web API reference.
- [bromothymolb/bilibili-api-zoku](https://github.com/bromothymolb/bilibili-api-zoku) — Bilibili API reference.
- [MakcRe/KuGouMusicApi](https://github.com/MakcRe/KuGouMusicApi) — KuGou Lite protocol, request signing, daily VIP claims, and upgrade parameters. MIT; implementation notes: [KUGOU-LITE.md](docs/KUGOU-LITE.md).
- [MoeKoeMusic/MoeKoeMusic](https://github.com/MoeKoeMusic/MoeKoeMusic) — desktop KuGou Lite account and daily-VIP flow reference. GPL-2.0.
- [MoeKoeMusic/MoeKoeMusic-mobile](https://github.com/MoeKoeMusic/MoeKoeMusic-mobile) — mobile sign-in flow and activity-result handling reference. GPL-2.0. The MoeKoe projects were used for behavioural reference; their code and UI assets were not copied.
- [lladlam/Square](https://github.com/lladlam/Square) — Spotify / YouTube Music dual-backend architecture reference.
- [lladlam/MeloX-AutoSync](https://github.com/lladlam/MeloX-AutoSync) — local word-level lyric alignment and pronunciation-mapping reference; see the bundled component notices for individual licenses.
- [neteasecloudmusicapienhanced/api-enhanced](https://github.com/neteasecloudmusicapienhanced/api-enhanced) — song recognition and audio-fingerprint runtime reference.
- [mjhydri/BeatNet](https://github.com/mjhydri/BeatNet) — AutoMix beat, downbeat, and tempo analysis. CC BY 4.0.

### Upstream MeloX references

Upstream MeloX also credits these projects. The Android port inherits parts of their lyric and NetEase work through that implementation:

- [jayfunc/BetterLyrics](https://github.com/jayfunc/BetterLyrics) — word-synced lyric rendering, glow, and motion;
- [qier222/YesPlayMusic](https://github.com/qier222/YesPlayMusic) — NetEase API and player reference.
- [DanteAlighieri13210914/pv-tool](https://github.com/DanteAlighieri13210914/pv-tool) — original Text PV templates and effects, ported under the PV Tool Non-Commercial License.

Text PV / PV Tool, BeatNet, and other MeloX features keep their separate licenses and attribution as required upstream.

## Contributing

Welcome to MeloX! Contributions are warmly welcome, whether you hand-craft every line or let an AI ride shotgun.

### Reporting issues or suggestions

- Prefer opening an issue on [GitHub Issues](https://github.com/lladlam/MeloX-Android/issues).
- If GitHub is unreachable from your network, email **`lladlam@outlook.com`** instead. Include a clear **subject**, a **detailed description** of the problem or suggestion, and **screenshots and logs**.

### Contributing code

1. **Fork this repository.** Note that the target branch is **`contributor`**, not `main`.
2. Make your changes in your fork and commit them with clear messages.
3. Open a **pull request against the `contributor` branch**.
4. **Attach a screen recording or screenshots of the change running on a real device** in the PR comments.
5. After review we merge into `contributor`; once it has been verified again there, it is merged into `main`.

> Pull requests opened directly against `main` are detected automatically and redirected to `contributor`.

### Contribution requirements

Changes are accepted only if they meet all of the following:

1. **No useless features** — anything unrelated to music or the listening experience is not accepted.
2. **No illegal content** — nothing that violates national laws and regulations, such as providing pirated music services.
3. **No breaking changes.**

## Disclaimer

This project is for learning, research, and open-source exchange.

- MeloX Android is not intended to bypass payment, copyright, region locks, or NetEase service limits;
- Users must follow local law, the NetEase Cloud Music terms, and music copyright;
- Third-party interfaces can change, and continued availability is not guaranteed;
- The project is provided without warranty under its license. Use is at your own risk.

## License

The MeloX Android code is released under the same **GNU General Public License version 3 (GPLv3)** as upstream MeloX. See [LICENSE](LICENSE).

Copying, modifying, or distributing this project requires compliance with GPLv3, including source availability, copyright notices, change notices, and same-license distribution.

## Sponsors

Thank you to the following sponsors for supporting MeloX Android!

| Sponsor | Contribution |
| --- | --- |
| Afdian user\_26d40 | CNY 50 |
| Afdian user\_aec4c | CNY 6 |
| Bai | CNY 5 |
| 中末 | CNY 8.88 |
| Afdian user\_361ef | CNY 20 |
| Afdian user\_f8b67 | CNY 12.24 |

## Links

- [LINUX DO](https://linux.do/)

Third-party code, libraries, assets, and models keep their own licenses. Contributors retain copyright in their contributions.

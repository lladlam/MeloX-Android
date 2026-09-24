# Apple MusicKit for Android (optional)

[中文](../../../README.md)

MeloX uses Apple's official Android SDK for Apple Music sign-in and DRM playback.
Download the SDK AARs from the Apple Developer MusicKit download area and place
the original files in this directory:

- `musickitauth-release-*.aar`
- `mediaplayback-release-*.aar`

Do not copy classes or libraries from `AppleMusicAPK` or from a reverse-engineered
Apple Music APK. The catalog API can compile without these AARs, but full-song
playback and the official sign-in intent require them.

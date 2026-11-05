#!/usr/bin/env bash
# Physical-device preparation plus automated subset. UI steps remain operator-verified.
set -euo pipefail
: "${ANDROID_SERIAL:?Select a physical device explicitly with ANDROID_SERIAL}"
ADB="${ADB:-adb}"
app="${MELOX_APP_ID:-com.lladlam.melox.android.dev}"
out="${MELOX_RC_EVIDENCE:-/tmp/opencode/melox-rc-arm64}"
mkdir -p "$out"
qemu=$("$ADB" -s "$ANDROID_SERIAL" shell getprop ro.kernel.qemu | tr -d '\r')
[[ "$qemu" != 1 ]] || { echo 'Refusing to label emulator results as physical-device acceptance' >&2; exit 2; }
abis=$("$ADB" -s "$ANDROID_SERIAL" shell getprop ro.product.cpu.abilist | tr -d '\r')
[[ "$abis" == *arm64-v8a* ]] || { echo 'Physical ARM64 required' >&2; exit 2; }
"$ADB" -s "$ANDROID_SERIAL" shell getprop ro.build.version.sdk > "$out/api.txt"
printf '%s\n' "$abis" > "$out/abi.txt"
MELOX_RC_LOG="$out/automated-subset.log" bash scripts/plugin-rc-device-check.sh
"$ADB" -s "$ANDROID_SERIAL" shell am start -W -n "$app/com.lladlam.melox.MainActivity" > "$out/launch.txt"
"$ADB" -s "$ANDROID_SERIAL" shell dumpsys media_session > "$out/media-session-before-ui.txt"
cat > "$out/manual-status.md" <<'EOF'
# Physical device user-flow status — fill only from observed evidence
- Automated runtime/Keystore/Binder subset: see automated-subset.log; not overall acceptance.
- SAF import, cancel, signature/permissions, install/enable/source selection: NOT_RUN
- Search/page/playlist/album/artist: NOT_RUN
- Normal MeloXPlaybackService, MiniPlayer, lyrics, queue and notification: NOT_RUN
- Pause/seek/track switch/audio focus/background/screen off: NOT_RUN
- Process reclaim and restart/plugin/queue restore: NOT_RUN
- Disable/update/uninstall/history and favorites SourceId: NOT_RUN
- Controlled OAuth and isolation: NOT_RUN
- Native/resource/network/storage fault matrix: NOT_RUN
- Overall release verdict: NO_GO until all required evidence is complete
EOF
echo "Prepared physical-device evidence at $out. Continue the application checklist; no overall PASS declared."

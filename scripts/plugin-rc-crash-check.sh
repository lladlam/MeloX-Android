#!/usr/bin/env bash
# Requires an installed Debug APK + PluginRcDeviceRunner AndroidTest APK.
# Kills only the chosen app's instrumentation process; run on a disposable test installation.
set -euo pipefail
: "${ANDROID_SERIAL:?Choose a disposable test device explicitly}"
ADB="${ADB:-adb}"
app="${MELOX_APP_ID:-com.lladlam.melox.android.dev}"
out="${MELOX_RC_EVIDENCE:-/tmp/opencode/melox-rc-crash}"
mkdir -p "$out"
for kind in unsigned rotation downgrade; do
for stage in decision-durable archive-durable publisher-durable rollback-archive-durable rollback-publisher-durable; do
  "$ADB" -s "$ANDROID_SERIAL" shell am instrument -w -e storageCrashStage "$stage" -e storageCrashKind "$kind" \
    "$app.test/com.lladlam.melox.core.plugin.PluginRcDeviceRunner" > "$out/$kind-$stage-interrupted.log" 2>&1 || true
  marker=$("$ADB" -s "$ANDROID_SERIAL" shell run-as "$app" cat cache/plugin-rc-crash-marker | tr -d '\r')
  [[ "$marker" == "$stage" ]] || { echo "Checkpoint not reached: $stage" >&2; exit 1; }
  "$ADB" -s "$ANDROID_SERIAL" shell am instrument -w -e storageCrashRecover "$stage" -e storageCrashKind "$kind" \
    "$app.test/com.lladlam.melox.core.plugin.PluginRcDeviceRunner" | tee "$out/$kind-$stage-recovered.log"
  grep -q 'rcStatus=PASS' "$out/$kind-$stage-recovered.log" || exit 1
done
done
echo 'Package/publisher journal process-death subset passed; not physical power-loss or full application acceptance.'

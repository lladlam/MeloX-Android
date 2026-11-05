#!/usr/bin/env bash
# Run from the repository root after building with PluginRcDeviceRunner (see rc-acceptance.md).
set -euo pipefail
ADB="${ADB:-adb}"
serial="${ANDROID_SERIAL:-}"
if [[ -z "$serial" ]]; then
  mapfile -t devices < <("$ADB" devices | awk 'NR>1 && $2=="device" {print $1}')
  [[ "${#devices[@]}" == 1 ]] || { echo "Set ANDROID_SERIAL to exactly one connected device" >&2; exit 2; }
  serial="${devices[0]}"
fi
app="${MELOX_APP_ID:-com.lladlam.melox.android.dev}"
[[ "$app" =~ ^[a-zA-Z0-9_.]+$ ]] || { echo "Invalid application ID" >&2; exit 2; }
cleanup() { "$ADB" -s "$serial" shell run-as "$app" rm -f cache/plugin-rc-oauth-admin >/dev/null 2>&1 || true; }
trap cleanup EXIT
apk="${MELOX_TEST_APK:-android/app/build/outputs/apk/debug/app-arm64-v8a-debug.apk}"
test_apk="android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
"$ADB" -s "$serial" shell getprop ro.build.version.sdk
"$ADB" -s "$serial" shell getprop ro.product.cpu.abilist
"$ADB" -s "$serial" install -r "$apk"
"$ADB" -s "$serial" install -r "$test_apk"
log="${MELOX_RC_LOG:-/tmp/opencode/melox-plugin-rc-device-result.log}"
options=()
if [[ "${MELOX_RC_ONLINE:-0}" == 1 ]]; then options=(-e online true); fi
if [[ "${MELOX_RC_FAULTS:-0}" == 1 ]]; then options+=(-e faults true); fi
if [[ "${MELOX_RC_APP_SERVICE:-0}" == 1 ]]; then options+=(-e appService true); fi
if [[ "${MELOX_RC_WIRE_FIELDS:-0}" == 1 ]]; then
  "$ADB" -s "$serial" shell run-as "$app" mkdir -p cache
  "$ADB" -s "$serial" shell "run-as '$app' sh -c 'cat > cache/plugin-rc-wire-cases.json'" < plugin-sdk/test/fixtures/wire-cases.json
  options+=(-e wireFields true)
fi
if [[ -n "${MELOX_RC_OAUTH_ORIGIN:-}" ]]; then
  options+=(-e oauthOrigin "$MELOX_RC_OAUTH_ORIGIN")
  if [[ -n "${MELOX_RC_OAUTH_ADMIN:-}" ]]; then
    [[ "$app" =~ ^[a-zA-Z0-9_.]+$ ]] || exit 2
    "$ADB" -s "$serial" shell run-as "$app" mkdir -p cache
    printf '%s' "$MELOX_RC_OAUTH_ADMIN" | "$ADB" -s "$serial" shell "run-as '$app' sh -c 'umask 077; cat > cache/plugin-rc-oauth-admin'"
  fi
fi
if [[ -n "${MELOX_RC_CATALOG:-}" ]]; then
  [[ "$app" =~ ^[a-zA-Z0-9_.]+$ ]] || { echo "Invalid application ID" >&2; exit 2; }
  "$ADB" -s "$serial" shell run-as "$app" mkdir -p cache
  "$ADB" -s "$serial" shell "run-as '$app' sh -c 'cat > cache/plugin-rc-catalog.melox-plugin'" < "$MELOX_RC_CATALOG"
  options+=(-e catalog true)
fi
"$ADB" -s "$serial" shell am instrument -w "${options[@]}" "$app.test/com.lladlam.melox.core.plugin.PluginRcDeviceRunner" | tee "$log"
grep -q 'rcStatus=PASS' "$log" || { echo "RC device checks did not pass; see $log" >&2; exit 1; }
denial=$("$ADB" -s "$serial" shell am startservice -n "$app/com.lladlam.melox.core.plugin.PluginIsolatedService" 2>&1 || true)
grep -q 'not exported' <<< "$denial" || { echo "External service access was not rejected as expected" >&2; exit 1; }
echo "PASS external shell service start denied as not exported" | tee -a "$log"
echo "RC subset passed. SAF, OAuth, background playback and lifecycle UI still require the acceptance checklist."

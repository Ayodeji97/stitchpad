#!/usr/bin/env bash
# release-smoke.sh — install a MINIFIED build on a device/emulator, launch it,
# and fail if it dies or if R8 shrank away something loaded reflectively.
#
# Why this exists: R8 output is what ships, and nothing else in the pipeline
# executes it. assembleDebug is unminified; crash-check.sh is a static source
# scanner. 1.2.0 (versionCode 595) shipped to testers with every Firebase
# ComponentRegistrar constructor stripped — a 100% startup crash that produced
# NO Crashlytics report, because Crashlytics itself was one of the components
# R8 had removed. Only launching a real minified build catches that class.
#
# Exit 0 = clean, 1 = smoke failure, 2 = usage/environment error.
# Bash 3.2 compatible (macOS default shell).
set -u
set -o pipefail

PKG="com.danzucker.stitchpad"
ACTIVITY=".MainActivity"
APK=""
SERIAL=""
LAUNCH_TIMEOUT="${RELEASE_SMOKE_TIMEOUT:-60}"
# Time to keep watching after the activity is up, to catch a crash that happens
# a beat after first frame (Koin graph, Firebase init on a background thread).
SETTLE_SECONDS="${RELEASE_SMOKE_SETTLE:-8}"

usage() {
  echo "Usage: release-smoke.sh [--apk <path>] [--serial <device>]" >&2
  echo "  Defaults to the newest composeApp/build/outputs/apk/releaseSmoke/*.apk" >&2
  exit 2
}

while [ $# -gt 0 ]; do
  case "$1" in
    --apk)    [ $# -ge 2 ] || usage; APK="$2"; shift 2 ;;
    --serial) [ $# -ge 2 ] || usage; SERIAL="$2"; shift 2 ;;
    -h|--help) usage ;;
    *) usage ;;
  esac
done

command -v adb >/dev/null 2>&1 || { echo "release-smoke: adb not on PATH" >&2; exit 2; }

ADB="adb"
[ -n "$SERIAL" ] && ADB="adb -s $SERIAL"

if [ -z "$APK" ]; then
  APK=$(ls -t composeApp/build/outputs/apk/releaseSmoke/*.apk 2>/dev/null | head -1)
fi
[ -n "$APK" ] && [ -f "$APK" ] || {
  echo "release-smoke: no APK found. Build one first:" >&2
  echo "  ./gradlew :composeApp:assembleReleaseSmoke" >&2
  exit 2
}

# A debug APK here would defeat the entire purpose — it is not minified, so it
# cannot fail the way a shipped build fails. Guard against passing one by hand.
case "$APK" in
  *–debug.apk|*-debug.apk|*/debug/*)
    echo "release-smoke: '$APK' looks like a DEBUG apk." >&2
    echo "  This gate is only meaningful against minified (R8) output." >&2
    exit 2 ;;
esac

echo "release-smoke: device  $($ADB shell getprop ro.product.model 2>/dev/null | tr -d '\r')"
echo "release-smoke: apk     $APK"

$ADB wait-for-device || { echo "release-smoke: no device" >&2; exit 2; }

# Uninstall first: the smoke APK is debug-signed, so it will not install over a
# release-signed build of the same applicationId (INSTALL_FAILED_UPDATE_INCOMPATIBLE).
$ADB uninstall "$PKG" >/dev/null 2>&1 || true

if ! $ADB install -r "$APK" 2>&1 | tee /tmp/release-smoke-install.log | tail -2; then
  echo "release-smoke: FAIL — install failed" >&2
  exit 1
fi
grep -q "Success" /tmp/release-smoke-install.log || {
  echo "release-smoke: FAIL — install did not report Success" >&2
  cat /tmp/release-smoke-install.log >&2
  exit 1
}

$ADB logcat -c >/dev/null 2>&1 || true
$ADB shell am start -n "$PKG/$PKG$ACTIVITY" >/dev/null 2>&1 \
  || $ADB shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1

# Poll until the activity is drawn or the app dies. Whichever lands first wins,
# so a fast crash fails fast instead of burning the whole timeout.
LOG=/tmp/release-smoke-logcat.txt
displayed=0
elapsed=0
while [ "$elapsed" -lt "$LAUNCH_TIMEOUT" ]; do
  $ADB logcat -d > "$LOG" 2>/dev/null || true
  if grep -q "FATAL EXCEPTION" "$LOG"; then
    break
  fi
  if grep -q "Displayed $PKG/" "$LOG"; then
    displayed=1
    break
  fi
  sleep 2
  elapsed=$((elapsed + 2))
done

# Let it settle, then take the authoritative log (main + crash buffers).
sleep "$SETTLE_SECONDS"
$ADB logcat -d > "$LOG" 2>/dev/null || true
$ADB logcat -b crash -d >> "$LOG" 2>/dev/null || true

fail() {
  echo "" >&2
  echo "release-smoke: FAIL — $1" >&2
  echo "──────── relevant logcat ────────" >&2
  grep -E "FATAL EXCEPTION|AndroidRuntime|ComponentDiscovery|Could not instantiate|Caused by" "$LOG" \
    | head -40 >&2
  echo "─────────────────────────────────" >&2
  echo "Full log: $LOG" >&2
  exit 1
}

grep -q "FATAL EXCEPTION" "$LOG" && fail "the app crashed on launch"

# Reflective-instantiation failures. Firebase logs these as WARNINGS and then
# drops the component: the app may keep running with push, App Check or
# Crashlytics silently dead. Treat as blocking — a shipped build must not lose
# components to the shrinker.
grep -q "Could not instantiate" "$LOG" \
  && fail "R8 stripped something loaded reflectively (see 'Could not instantiate' below)"

[ "$displayed" -eq 1 ] || fail "MainActivity never displayed within ${LAUNCH_TIMEOUT}s"

$ADB shell pidof "$PKG" >/dev/null 2>&1 || fail "process was gone after ${SETTLE_SECONDS}s"

echo "release-smoke: PASS — minified build launched, no fatals, all reflective components loaded"
exit 0

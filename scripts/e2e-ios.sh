#!/usr/bin/env bash
#
# StitchPad — iOS E2E run against the LOCAL Firebase emulator.
#
#   ./scripts/e2e-ios.sh [SIM_UDID] [flow.yaml ...]
#
# Boots the Firestore + Auth emulators, seeds deterministic data, builds a debug
# app with USE_FIREBASE_EMULATOR flipped on, installs it, and runs the Maestro
# flows. Never touches production.
#
# The emulator flag lives in a tracked source file and MUST stay `false` on the
# branch. This script flips it for the build and restores it on exit — including
# on failure or Ctrl-C — via the trap below. See .maestro/README.md.

set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO"

UDID="${1:-}"
if [[ -z "$UDID" ]]; then
  echo "usage: $0 <SIM_UDID> [flow.yaml ...]" >&2
  echo "  list sims: xcrun simctl list devices available" >&2
  exit 2
fi
shift || true
FLOWS=("$@")
if [[ ${#FLOWS[@]} -eq 0 ]]; then
  FLOWS=(.maestro)
fi

APP_ID="com.danzucker.stitchpad"
FLAG_FILE="composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/config/EmulatorConfig.kt"
BUILD_DIR="iosApp/build/DerivedData"
APP_PATH="$BUILD_DIR/Build/Products/Debug-iphonesimulator/StitchPad.app"
LOG_DIR="${TMPDIR:-/tmp}/stitchpad-e2e"
mkdir -p "$LOG_DIR"

FLAG_BACKUP="$LOG_DIR/EmulatorConfig.kt.orig"
EMULATOR_PID=""

cleanup() {
  local rc=$?
  if [[ -f "$FLAG_BACKUP" ]]; then
    cp "$FLAG_BACKUP" "$FLAG_FILE"
    rm -f "$FLAG_BACKUP"
    echo "restored $FLAG_FILE (emulator flag back to its committed value)"
  fi
  if [[ -n "$EMULATOR_PID" ]] && kill -0 "$EMULATOR_PID" 2>/dev/null; then
    kill "$EMULATOR_PID" 2>/dev/null || true
    echo "stopped Firebase emulators"
  fi
  exit $rc
}
trap cleanup EXIT INT TERM

require() { command -v "$1" >/dev/null 2>&1 || { echo "missing dependency: $1" >&2; exit 1; }; }
require xcodebuild
require firebase
require node
[[ -x "$HOME/.maestro/bin/maestro" ]] || require maestro
export PATH="$PATH:$HOME/.maestro/bin"

# ---------------------------------------------------------------- emulators
# Wait for BOTH emulators. Firestore (:8080) and Auth (:9099) come up at
# different times, and the seed script needs Auth — waiting only on Firestore
# races and fails with ECONNREFUSED on :9099.
emulators_ready() {
  lsof -nP -iTCP:8080 -sTCP:LISTEN >/dev/null 2>&1 &&
    lsof -nP -iTCP:9099 -sTCP:LISTEN >/dev/null 2>&1
}

if emulators_ready; then
  echo "==> Firebase emulators already running, reusing them"
else
  echo "==> starting Firebase emulators"
  firebase emulators:start --config firebase.emulator.json > "$LOG_DIR/emulator.log" 2>&1 &
  EMULATOR_PID=$!
  for _ in $(seq 1 90); do
    emulators_ready && break
    sleep 1
  done
  emulators_ready || {
    echo "emulators failed to start; see $LOG_DIR/emulator.log" >&2; exit 1; }
fi

echo "==> seeding emulator data"
(cd functions && node scripts/emulatorSetupStaff.js)

# ---------------------------------------------------------------- build
echo "==> building debug app with USE_FIREBASE_EMULATOR=true"
cp "$FLAG_FILE" "$FLAG_BACKUP"
sed -i '' 's/^const val USE_FIREBASE_EMULATOR = false/const val USE_FIREBASE_EMULATOR = true/' "$FLAG_FILE"
grep -q '^const val USE_FIREBASE_EMULATOR = true' "$FLAG_FILE" || {
  echo "failed to flip the emulator flag - has the constant been renamed?" >&2; exit 1; }

# NOTE: piping xcodebuild swallows its exit status (see feedback_gradle_piped_exit_codes).
# Redirect to a file and check $? instead.
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -destination "platform=iOS Simulator,id=$UDID" \
  -derivedDataPath "$BUILD_DIR" build > "$LOG_DIR/build.log" 2>&1 || {
  echo "build FAILED; tail of $LOG_DIR/build.log:" >&2
  tail -30 "$LOG_DIR/build.log" >&2
  exit 1
}

# ---------------------------------------------------------------- install
echo "==> installing on $UDID"
xcrun simctl boot "$UDID" 2>/dev/null || true
# Uninstall first: a stale container keeps prod-cached data and queued writes
# that would otherwise flush to production the moment Firestore reconnects.
xcrun simctl uninstall "$UDID" "$APP_ID" 2>/dev/null || true
# Firebase Auth persists in the KEYCHAIN, which survives uninstall — without this
# the app restores the previous session and sign-in flows never see a login screen.
xcrun simctl keychain "$UDID" reset
xcrun simctl install "$UDID" "$APP_PATH"

# ---------------------------------------------------------------- run
echo "==> running Maestro flows: ${FLOWS[*]}"
maestro --device "$UDID" test "${FLOWS[@]}"

#!/usr/bin/env bash
#
# StitchPad — iOS E2E run against the LOCAL Firebase emulator.
#
#   ./scripts/e2e-ios.sh <SIM_UDID> [--reuse] [flow.yaml ...]
#
# Boots the Firestore + Auth emulators, wipes them to a clean slate, seeds
# deterministic data, builds a debug app with USE_FIREBASE_EMULATOR flipped on,
# installs it, and runs the Maestro flows. Never touches production.
#
# The emulator flag lives in a tracked source file and MUST stay `false` on the
# branch. This script flips it for the build and restores it on exit — including
# on failure or Ctrl-C — via the trap below. See .maestro/README.md.
#
# --reuse : proceed against emulators this script did not start. This WIPES their
#           data, so it is opt-in — a manual staff-smoke session on the same
#           emulator would otherwise be destroyed mid-run.

set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO"

UDID="${1:-}"
if [[ -z "$UDID" ]]; then
  echo "usage: $0 <SIM_UDID> [--reuse] [flow.yaml ...]" >&2
  echo "  list sims: xcrun simctl list devices available" >&2
  exit 2
fi
shift || true

REUSE=false
FLOWS=()
for arg in "$@"; do
  if [[ "$arg" == "--reuse" ]]; then REUSE=true; else FLOWS+=("$arg"); fi
done
if [[ ${#FLOWS[@]} -eq 0 ]]; then
  FLOWS=(.maestro)
fi

APP_ID="com.danzucker.stitchpad"
PROJECT_ID="stitchpad-30607"
FLAG_FILE="composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/config/EmulatorConfig.kt"
BUILD_DIR="iosApp/build/DerivedData"
APP_PATH="$BUILD_DIR/Build/Products/Debug-iphonesimulator/StitchPad.app"
LOG_DIR="${TMPDIR:-/tmp}/stitchpad-e2e"
mkdir -p "$LOG_DIR"

FLAG_BACKUP="$LOG_DIR/EmulatorConfig.kt.orig"
LOCK_FILE="$LOG_DIR/e2e.lock"
EMULATOR_PID=""
HOLD_LOCK=false

cleanup() {
  local rc=$?
  if [[ -f "$FLAG_BACKUP" ]]; then
    cp "$FLAG_BACKUP" "$FLAG_FILE"
    rm -f "$FLAG_BACKUP"
    echo "restored $FLAG_FILE (emulator flag back to its committed value)"
  fi
  # Only ever stop emulators THIS run started. A run that reused someone else's
  # emulators must not kill them out from under whoever owns them.
  if [[ -n "$EMULATOR_PID" ]] && kill -0 "$EMULATOR_PID" 2>/dev/null; then
    kill "$EMULATOR_PID" 2>/dev/null || true
    echo "stopped Firebase emulators"
  fi
  [[ "$HOLD_LOCK" == true ]] && rm -f "$LOCK_FILE"
  exit $rc
}
trap cleanup EXIT INT TERM

# ---------------------------------------------------------------- single-run lock
# Two concurrent runs would fight over the emulators: the first to finish would
# stop them mid-flight for the second, and both would seed over each other.
if [[ -f "$LOCK_FILE" ]] && kill -0 "$(cat "$LOCK_FILE" 2>/dev/null)" 2>/dev/null; then
  echo "another e2e run is already in progress (pid $(cat "$LOCK_FILE"))." >&2
  echo "wait for it to finish, or remove $LOCK_FILE if it died uncleanly." >&2
  exit 1
fi
echo $$ > "$LOCK_FILE"
HOLD_LOCK=true

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
  # We hold the run lock, so these are NOT another e2e run's emulators — they
  # belong to a manual session (e.g. the staff smoke runbook). Wiping and
  # re-seeding them would destroy that session's state, so require opt-in.
  if [[ "$REUSE" != true ]]; then
    echo "Firebase emulators are already running, but this run did not start them." >&2
    echo "Continuing would WIPE their data — a manual smoke session would be lost." >&2
    echo "Stop them first, or re-run with --reuse to wipe and continue." >&2
    exit 1
  fi
  echo "==> reusing already-running emulators (--reuse); their data will be wiped"
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

# Clean slate BEFORE seeding. The seed script only set()s its own seed-* docs, so
# without this, records created by a previous run survive — and an assertion like
# "the customer I just created is visible" would pass against the STALE row even
# if the create silently failed. Same false-green class as an occluded tap.
echo "==> wiping emulator data"
curl -sf -X DELETE \
  "http://127.0.0.1:8080/emulator/v1/projects/$PROJECT_ID/databases/(default)/documents" \
  >/dev/null || { echo "failed to clear Firestore emulator" >&2; exit 1; }
curl -sf -X DELETE \
  "http://127.0.0.1:9099/emulator/v1/projects/$PROJECT_ID/accounts" \
  >/dev/null || { echo "failed to clear Auth emulator" >&2; exit 1; }

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
# iOS offers "Save Password?" (iCloud Keychain) after a password sign-in, on its
# own heuristics. It renders ABOVE the app and swallows taps, so it turns any
# sign-in flow intermittently red. Disable it rather than guarding every flow.
xcrun simctl spawn "$UDID" defaults write com.apple.Preferences AutoFillPasswords -bool false 2>/dev/null || true
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

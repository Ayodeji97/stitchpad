#!/usr/bin/env bash
# Marks a Firebase Auth account's email as verified, for users blocked by a
# network that filters the firebaseapp.com action link.
# See docs/auth/verification-action-url-custom-domain.md
#
#   ./scripts/verify-user-email.sh <email>            # check current state
#   ./scripts/verify-user-email.sh <email> --unblock  # mark verified
#
# Only unblock a user who has contacted support from that address: this
# bypasses proof of email ownership.

set -euo pipefail

PROJECT="stitchpad-30607"
BASE="https://identitytoolkit.googleapis.com/v1/projects/${PROJECT}/accounts"

EMAIL="${1:-}"
MODE="${2:-}"

if [[ -z "$EMAIL" ]]; then
  echo "usage: $0 <email> [--unblock]" >&2
  exit 1
fi

TOKEN="$(gcloud auth print-access-token)"

api() {
  curl -sS -X POST \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "x-goog-user-project: ${PROJECT}" \
    -H "Content-Type: application/json" \
    -d "$2" \
    "${BASE}:$1"
}

lookup() {
  api lookup "{\"email\":[\"${EMAIL}\"]}"
}

RESPONSE="$(lookup)"

UID_VALUE="$(printf '%s' "$RESPONSE" | python3 -c '
import sys, json
users = json.load(sys.stdin).get("users", [])
print(users[0]["localId"] if users else "")
')"

if [[ -z "$UID_VALUE" ]]; then
  echo "No account found for ${EMAIL}"
  exit 1
fi

show() {
  printf '%s' "$1" | python3 -c '
import sys, json, datetime
u = json.load(sys.stdin)["users"][0]
def ts(ms):
    return datetime.datetime.fromtimestamp(int(ms) / 1000).strftime("%Y-%m-%d %H:%M")
print("  email    :", u.get("email"))
print("  uid      :", u.get("localId"))
print("  VERIFIED :", u.get("emailVerified"))
print("  created  :", ts(u["createdAt"]))
print("  last login:", ts(u["lastLoginAt"]))
'
}

echo "Current state:"
show "$RESPONSE"

if [[ "$MODE" != "--unblock" ]]; then
  echo
  echo "Read-only. Re-run with --unblock to mark this address verified."
  exit 0
fi

echo
echo "Marking ${EMAIL} verified..."
api update "{\"localId\":\"${UID_VALUE}\",\"emailVerified\":true}" > /dev/null

echo "New state:"
show "$(lookup)"

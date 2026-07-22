#!/usr/bin/env bash
# Sets the Firebase Auth email action URL (the %LINK% host in verification,
# password-reset and email-change emails). Project-wide — one setting covers
# all three templates.
#
# Fallback for when the Firebase Console's "Customize action URL" save fails.
# See docs/auth/verification-action-url-custom-domain.md
#
#   ./scripts/set-auth-action-url.sh                 # show current value
#   ./scripts/set-auth-action-url.sh <url>           # set it
#
# The URL's domain MUST already be in Authentication > Settings > Authorized
# domains, or the update is rejected.

set -euo pipefail

PROJECT="stitchpad-30607"
CONFIG_URL="https://identitytoolkit.googleapis.com/admin/v2/projects/${PROJECT}/config"

TOKEN="$(gcloud auth print-access-token)"

get_config() {
  curl -sS -H "Authorization: Bearer ${TOKEN}" \
    -H "x-goog-user-project: ${PROJECT}" \
    "${CONFIG_URL}"
}

report() {
  printf '%s' "$1" | python3 -c '
import sys, json
c = json.load(sys.stdin)
se = c["notification"]["sendEmail"]
domains = c.get("authorizedDomains", [])
print("  action URL :", se.get("callbackUri"))
print("  authorized :", ", ".join(domains))
'
}

NEW_URL="${1:-}"

if [[ -z "$NEW_URL" ]]; then
  echo "Current:"
  report "$(get_config)"
  echo
  echo "To change it: $0 https://auth.getstitchpad.com/__/auth/action"
  exit 0
fi

# Guard: the host must already be authorized, else the write fails server-side
# with an opaque error.
HOST="$(printf '%s' "$NEW_URL" | sed -E 's#^https?://([^/]+).*#\1#')"
if ! get_config | python3 -c "
import sys, json
sys.exit(0 if '${HOST}' in json.load(sys.stdin).get('authorizedDomains', []) else 1)
"; then
  echo "Refusing: ${HOST} is not in Authorized domains." >&2
  echo "Add it first: Authentication > Settings > Authorized domains." >&2
  exit 1
fi

echo "Setting action URL to ${NEW_URL} ..."

RESPONSE="$(curl -sS -X PATCH \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "x-goog-user-project: ${PROJECT}" \
  -H "Content-Type: application/json" \
  -d "{\"notification\":{\"sendEmail\":{\"callbackUri\":\"${NEW_URL}\"}}}" \
  "${CONFIG_URL}?updateMask=notification.sendEmail.callbackUri")"

if printf '%s' "$RESPONSE" | grep -q '"error"'; then
  echo "Update failed:" >&2
  printf '%s\n' "$RESPONSE" >&2
  exit 1
fi

echo "New state:"
report "$(get_config)"

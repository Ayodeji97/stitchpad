#!/usr/bin/env bash
# Test harness for crash-check.sh. Exit 0 = all pass, 1 = a test failed.
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
SCRIPT="$HERE/../crash-check.sh"
FIX="$HERE/fixtures"
fail=0

assert_contains() { # haystack needle label
  case "$1" in *"$2"*) ;; *) echo "FAIL: $3 (missing '$2')"; fail=1;; esac
}
assert_not_contains() { # haystack needle label
  case "$1" in *"$2"*) echo "FAIL: $3 (unexpected '$2')"; fail=1;; *) ;; esac
}
assert_eq() { # actual expected label
  [ "$1" = "$2" ] || { echo "FAIL: $3 (got '$1' want '$2')"; fail=1; }
}

# --- --all mode against fixtures ---
out="$(CRASH_CHECK_SCAN_ROOT="$FIX" bash "$SCRIPT" --all)"; code=$?
assert_eq "$code" "1" "all-mode exits 1 when blocking findings exist"
assert_contains "$out" "serializer-any"          "detects serializer-any"
assert_contains "$out" "koin-platformcontext"    "detects koin-platformcontext"
assert_contains "$out" "jvm-string-format"       "detects String.format in commonMain"
assert_contains "$out" "jvm-import"              "detects import java.*"
assert_contains "$out" "strings-backslash-apos"  "detects \\' in strings.xml"
assert_contains "$out" "arrayunion-dto"          "warns arrayUnion"
assert_contains "$out" "epoch-days-skew"         "warns toEpochDays"
assert_contains "$out" "peekaboo-maxselection"   "warns SelectionMode.Multiple"
assert_not_contains "$out" "Good.kt"             "Good.kt produces no findings"
assert_not_contains "$out" "androidMain"         "androidMain String.format not flagged (scope)"
assert_not_contains "$out" "Suppressed.kt"       "suppression comment honored"

# --- --diff mode against a throwaway repo (only introduced lines flagged) ---
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
(
  cd "$TMP"
  git init -q -b master && git config user.email t@t && git config user.name t
  mkdir -p composeApp/src/commonMain/kotlin
  printf 'val ok = 1\n' > composeApp/src/commonMain/kotlin/F.kt
  git add -A && git commit -qm base
  git checkout -qb feature
  printf 'val ok = 1\nval x = snap.data<Map<String, Any?>>()\n' \
    > composeApp/src/commonMain/kotlin/F.kt
  git add -A && git commit -qm change
)
dout="$(cd "$TMP" && CRASH_CHECK_SCAN_ROOT="composeApp/src" bash "$SCRIPT" --diff master)"; dcode=$?
assert_eq "$dcode" "1" "diff-mode exits 1 on introduced crash line"
assert_contains "$dout" "serializer-any" "diff-mode detects introduced serializer-any"

if [ "$fail" -eq 0 ]; then echo "All crash-check tests passed."; fi
exit "$fail"

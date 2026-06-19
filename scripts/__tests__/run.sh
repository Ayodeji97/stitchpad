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
assert_contains "$out" "clock-system-ios"        "warns Clock.System"
assert_contains "$out" "peekaboo-maxselection"   "warns SelectionMode.Multiple"
assert_not_contains "$out" "Good.kt"             "Good.kt produces no findings"
assert_not_contains "$out" "androidMain"         "androidMain String.format not flagged (scope)"
assert_not_contains "$out" "Suppressed.kt"       "suppression comment honored"
assert_not_contains "$out" "CommentMention.kt"   "anti-patterns mentioned in comments not flagged"

# --- tab-indented line detection ---
assert_contains "$out" "TabIndented.kt"          "tab-indented serializer-any line detected"

# --- warn-only mode: no blocking pattern → exit 0 ---
FIX_WARN="$HERE/fixtures-warn-only"
wout="$(CRASH_CHECK_SCAN_ROOT="$FIX_WARN" bash "$SCRIPT" --all)"; wcode=$?
assert_eq "$wcode" "0" "warn-only fixture exits 0 (no blocking patterns)"
assert_contains "$wout" "arrayunion-dto" "warn-only fixture still reports warning"

# --- --diff mode against a throwaway repo (only introduced lines flagged) ---
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
(
  cd "$TMP"
  git init -q && git symbolic-ref HEAD refs/heads/master
  git config user.email t@t && git config user.name t
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

# --- --staged mode ---
TMP2="$(mktemp -d)"; trap 'rm -rf "$TMP2"' EXIT
(
  cd "$TMP2"
  git init -q && git symbolic-ref HEAD refs/heads/master
  git config user.email t@t && git config user.name t
  mkdir -p composeApp/src/commonMain/kotlin
  printf 'val ok = 1\n' > composeApp/src/commonMain/kotlin/G.kt
  git add -A && git commit -qm base
  printf 'val ok = 1\nval x = snap.data<Map<String, Any?>>()\n' \
    > composeApp/src/commonMain/kotlin/G.kt
  git add composeApp/src/commonMain/kotlin/G.kt
)
sout="$(cd "$TMP2" && CRASH_CHECK_SCAN_ROOT="composeApp/src" bash "$SCRIPT" --staged)"; scode=$?
assert_eq "$scode" "1" "staged-mode exits 1 on staged crash line"
assert_contains "$sout" "serializer-any" "staged-mode detects staged serializer-any"

if [ "$fail" -eq 0 ]; then echo "All crash-check tests passed."; fi
exit "$fail"

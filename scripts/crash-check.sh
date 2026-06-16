#!/usr/bin/env bash
# crash-check.sh — scan for known crash-class patterns. Catalog: docs/crash-classes.md
# Exit 0 = clean, 1 = blocking finding, 2 = usage error.
# Bash 3.2 compatible (no associative arrays, no mapfile).
set -u

MODE="diff"
BASE="origin/main"
SCAN_ROOT="${CRASH_CHECK_SCAN_ROOT:-composeApp/src}"

usage() { echo "Usage: crash-check.sh [--diff [base] | --all | --staged]" >&2; exit 2; }

if [ $# -gt 0 ]; then
  case "$1" in
    --all)    MODE="all" ;;
    --staged) MODE="staged" ;;
    --diff)   MODE="diff"; [ $# -ge 2 ] && BASE="$2" ;;
    -h|--help) usage ;;
    *) usage ;;
  esac
fi

# Rules: id|severity|pathglob|ERE-pattern|excl-pattern|message
# severity: block|warn ; pathglob: substring matched against the file path.
# excl-pattern: if non-empty and the line also matches this ERE, skip the finding.
RULES="
serializer-any|block|commonMain|\.data<[^>]*Any\?||Firestore .data<...Any?> crashes iOS on first emit; use a typed @Serializable DTO (crash-classes.md#serializer-any)
koin-platformcontext|block|commonMain|single<PlatformContext>.*androidContext\(\)||PlatformContext self-recursion crashes at launch (crash-classes.md#koin-platformcontext)
jvm-string-format|block|commonMain|String\.format\(||JVM-only API fails iOS link (crash-classes.md#jvm-only-api)
jvm-import|block|commonMain|^[[:space:]]*import java\.||JVM-only import fails iOS link (crash-classes.md#jvm-only-api)
strings-backslash-apos|block|composeResources|\\\\'||Backslash-apostrophe renders literally on iOS; use &apos; (crash-classes.md#strings-backslash-apos)
arrayunion-dto|warn|commonMain|\.arrayUnion\(||arrayUnion() with a non-primitive crashes iOS; pass a primitive Map (crash-classes.md#arrayunion-dto)
epoch-days-skew|warn|commonMain|\.toEpochDays\(\)|\.toEpochDays\(\)\.toLong|toEpochDays() is Long on iOS / Int on JVM; cast .toLong() (crash-classes.md#epoch-days-skew)
clock-system-ios|warn|commonMain|Clock\.System||Clock.System may be unresolved on iOS; inject () -> Long (crash-classes.md#clock-system-ios)
peekaboo-maxselection|warn|commonMain|SelectionMode\.Multiple\(||Multiple(maxSelection<=1) crashes Android; ensure max>1 else Single (crash-classes.md#peekaboo-maxselection)
"

# Emit candidate lines as: path<TAB>lineno<TAB>content
candidates() {
  case "$MODE" in
    all)
      find "$SCAN_ROOT" -type f \( -name '*.kt' -o -name '*.xml' \) -print0 \
        | xargs -0 awk '{print FILENAME "\t" FNR "\t" $0}'
      ;;
    diff|staged)
      local range
      if [ "$MODE" = "staged" ]; then range="--cached"; else range="$BASE...HEAD"; fi
      git diff --unified=0 $range -- "$SCAN_ROOT" | awk '
        /^\+\+\+ b\// { file=substr($0,7); next }
        /^@@/ { match($0, /\+[0-9]+/); ln=substr($0, RSTART+1, RLENGTH-1)+0; next }
        /^\+/ && !/^\+\+\+/ { print file "\t" ln "\t" substr($0,2); ln++ }
      '
      ;;
  esac
}

TMP="$(mktemp)"; trap 'rm -f "$TMP" "$TMP.rules"' EXIT
candidates > "$TMP"

# Write rules to a temp file (pipe-delimiter) — awk reads it with getline,
# so patterns reach awk as literal file bytes with no shell backslash interpolation.
printf '%s\n' "$RULES" | grep -v '^[[:space:]]*$' > "$TMP.rules"

# Single awk pass over candidates. Rules are read in a BEGIN block via getline
# from the rules file — this avoids awk -v backslash interpolation for patterns.
# The candidates file uses TAB as separator; rules file uses |.
awk_out="$(awk -v rulesfile="$TMP.rules" '
  BEGIN {
    n = 0
    while ((getline line < rulesfile) > 0) {
      # Split on | but allow | in the message (split up to 6 fields)
      nf = split(line, f, "|")
      if (nf < 5) continue
      n++
      ids[n] = f[1]; sevs[n] = f[2]; globs[n] = f[3]
      pats[n] = f[4]; excls[n] = f[5]
      msg = ""
      for (i = 6; i <= nf; i++) msg = (i==6 ? f[i] : msg "|" f[i])
      msgs[n] = msg
    }
    close(rulesfile)
    FS = "\t"
  }
  {
    path = $1; ln = $2; content = $3
    for (i = 1; i <= n; i++) {
      if (index(path, globs[i]) == 0) continue
      if (index(content, "crash-check:ignore " ids[i]) > 0) continue
      if (excls[i] != "" && content ~ excls[i]) continue
      if (content ~ pats[i]) {
        print sevs[i] "|" path ":" ln "  [" ids[i] "] " msgs[i]
      }
    }
  }
' "$TMP")"

block_count=0; warn_count=0
block_out=""; warn_out=""

while IFS='|' read -r sev rest; do
  [ -z "${sev:-}" ] && continue
  line="  $rest"
  if [ "$sev" = "block" ]; then
    block_out="$block_out$line
"
    block_count=$((block_count + 1))
  elif [ "$sev" = "warn" ]; then
    warn_out="$warn_out$line
"
    warn_count=$((warn_count + 1))
  fi
done <<EOF
$awk_out
EOF

if [ "$block_count" -gt 0 ]; then
  echo "BLOCKING crash patterns introduced:"
  printf '%s' "$block_out"
fi
if [ "$warn_count" -gt 0 ]; then
  echo "Warnings (review manually):"
  printf '%s' "$warn_out"
fi

if [ "$block_count" -eq 0 ] && [ "$warn_count" -eq 0 ]; then
  echo "✓ no known crash patterns introduced"
else
  echo "✗ $block_count blocking, $warn_count warning(s)"
fi

[ "$block_count" -gt 0 ] && exit 1
exit 0

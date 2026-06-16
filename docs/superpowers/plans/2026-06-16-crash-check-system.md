# Crash-Check System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a crash-prevention system that automatically blocks PRs introducing known crash classes and provides a final pre-release crash audit.

**Architecture:** A canonical crash-class catalog (`docs/crash-classes.md`) drives a deterministic bash scan script (`scripts/crash-check.sh`) wired into the pre-push hook and a blocking CI job — the "always runs" gate. A Claude skill (`.claude/skills/crash-check/`) adds an AI reasoning layer with a PR-audit mode and a release-audit mode.

**Tech Stack:** Bash 3.2 (macOS-compatible — no associative arrays / mapfile), `git`, `grep -E`, GitHub Actions, Claude Code skill (markdown).

---

## File Structure

- `docs/crash-classes.md` — **Create.** Canonical catalog of crash classes (knowledge truth).
- `scripts/crash-check.sh` — **Create.** Deterministic scanner; regex-detectable subset of the catalog.
- `scripts/__tests__/run.sh` — **Create.** Bash test harness for the scanner.
- `scripts/__tests__/fixtures/...` — **Create.** Known-bad / known-good source snippets.
- `.github/workflows/ci.yml` — **Modify.** Add blocking `crash-check` job; harden iOS test gate.
- `.git/hooks/pre-push` — **Modify.** Prepend blocking crash-check before the advisory codex review.
- `.claude/skills/crash-check/SKILL.md` — **Create.** One skill, two modes.

---

## Task 1: Crash-class catalog

**Files:**
- Create: `docs/crash-classes.md`

- [ ] **Step 1: Write the catalog**

Create `docs/crash-classes.md` with this exact content:

```markdown
# Crash Classes

Canonical catalog of the ways StitchPad crashes. This is the single source of
truth. `scripts/crash-check.sh` implements the `detection: regex` subset below;
the `crash-check` skill reasons about the `detection: ai-only` ones.

When a new crash class is discovered (audit or real-world incident), append a
section here and — if regex-detectable — add a rule to `scripts/crash-check.sh`.

Each entry: **id** · detection · severity · symptom · why · fix.

## serializer-any
- detection: regex · severity: block
- Symptom: iOS crashes on the first Firestore emit.
- Why: `snap.data<Map<String, Any?>>()` has no serializer for `Any?` on Kotlin/Native.
- Fix: read into a typed `@Serializable` DTO.

## arrayunion-dto
- detection: regex (warn) + ai-only · severity: warn
- Symptom: iOS crash "Unsupported type".
- Why: `arrayUnion(DTO)` cannot serialize a domain/DTO object on Native.
- Fix: pass a primitive `Map`.

## koin-platformcontext
- detection: regex · severity: block
- Symptom: crash at launch (stack overflow).
- Why: `single<PlatformContext> { androidContext() }` — PlatformContext is a typealias
  for Context on Android, so the definition resolves itself recursively.
- Fix: do not register PlatformContext via androidContext().

## jvm-only-api
- detection: regex · severity: block
- Symptom: iOS link failure.
- Why: JVM-only stdlib APIs (`String.format`, `import java.*`) compile on Android
  but have no Native target.
- Fix: use a multiplatform API.

## epoch-days-skew
- detection: regex (warn) · severity: warn
- Symptom: iOS-only wrong values / overflow.
- Why: `LocalDate.toEpochDays()` returns `Long` on iOS Native, `Int` on JVM.
- Fix: cast `.toLong()` up front.

## clock-system-ios
- detection: regex (warn) · severity: warn
- Symptom: iOS compile/resolve failure on pinned datetime versions.
- Why: `Clock.System` unresolved on iOS in some kotlinx.datetime versions.
- Fix: inject `() -> Long` instead.

## peekaboo-maxselection
- detection: regex (warn) · severity: warn
- Symptom: Android crash opening the picker.
- Why: `SelectionMode.Multiple(maxSelection <= 1)` — PickMultipleVisualMedia needs > 1.
- Fix: guard `max > 1`, else use `SelectionMode.Single`.

## strings-backslash-apos
- detection: regex · severity: block
- Symptom: iOS renders `\'` literally in UI text.
- Why: Compose Multiplatform iOS does not process the backslash escape in strings.xml.
- Fix: use `&apos;` (or a typographic apostrophe).

## native-callback-selector
- detection: ai-only
- Symptom: Swift call site fails / wrong label (`value_`).
- Why: two fun-interface callbacks with the same method+param name collapse to one
  Obj-C selector. CI builds the framework, not the Swift target, so it slips through.
- Fix: give the callbacks distinct param names; verify with a clean Xcode build.

## gitlive-nonnull-token
- detection: ai-only
- Symptom: iOS SSO crash.
- Why: gitlive's Kotlin nullable signatures lie on iOS; providers require both tokens non-null.
- Fix: assert both tokens non-null before calling; test on a real iOS device.
```

- [ ] **Step 2: Commit**

```bash
git add docs/crash-classes.md
git commit -m "docs(crash-check): seed crash-class catalog"
```

---

## Task 2: Scanner script + test harness (TDD)

**Files:**
- Create: `scripts/__tests__/fixtures/commonMain/kotlin/Bad.kt`
- Create: `scripts/__tests__/fixtures/commonMain/kotlin/Good.kt`
- Create: `scripts/__tests__/fixtures/commonMain/kotlin/Suppressed.kt`
- Create: `scripts/__tests__/fixtures/androidMain/kotlin/LegalJvm.kt`
- Create: `scripts/__tests__/fixtures/commonMain/composeResources/values/strings.xml`
- Create: `scripts/__tests__/run.sh`
- Create: `scripts/crash-check.sh`

- [ ] **Step 1: Write the fixtures**

`scripts/__tests__/fixtures/commonMain/kotlin/Bad.kt`:

```kotlin
// Each line below is a deliberate crash pattern for the scanner tests.
val data = snap.data<Map<String, Any?>>()
single<PlatformContext> { androidContext() }
val s = String.format("%d", 1)
import java.text.SimpleDateFormat
val days = date.toEpochDays()
ref.arrayUnion(dto)
val now = Clock.System.now()
val mode = SelectionMode.Multiple(maxSelection = 1)
```

`scripts/__tests__/fixtures/commonMain/kotlin/Good.kt`:

```kotlin
// Clean file — must produce zero findings.
val data = snap.data<UserDto>()
single<HttpClient> { createClient() }
val s = buildString { append(1) }
val days = date.toEpochDays().toLong()
```

`scripts/__tests__/fixtures/commonMain/kotlin/Suppressed.kt`:

```kotlin
// A real String.format kept on purpose, suppressed with a reason.
val s = String.format("%d", 1) // crash-check:ignore jvm-string-format — Android-guarded expect/actual
```

`scripts/__tests__/fixtures/androidMain/kotlin/LegalJvm.kt`:

```kotlin
// String.format is legal in androidMain — the scanner must NOT flag it here.
val s = String.format("%d", 1)
```

`scripts/__tests__/fixtures/commonMain/composeResources/values/strings.xml`:

```xml
<resources>
    <string name="bad">It\'s broken</string>
    <string name="good">It&apos;s fine</string>
</resources>
```

- [ ] **Step 2: Write the test harness**

`scripts/__tests__/run.sh`:

```bash
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
  git init -q && git config user.email t@t && git config user.name t
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
```

- [ ] **Step 3: Run the harness to verify it fails (no script yet)**

Run: `bash scripts/__tests__/run.sh; echo "exit=$?"`
Expected: FAIL — the script file does not exist, harness exits non-zero.

- [ ] **Step 4: Write the scanner**

`scripts/crash-check.sh`:

```bash
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

# Rules: id|severity|pathglob|ERE-pattern|message
# severity: block|warn ; pathglob: substring matched against the file path.
RULES="
serializer-any|block|commonMain|\.data<[^>]*Any\?|Firestore .data<...Any?> crashes iOS on first emit; use a typed @Serializable DTO (crash-classes.md#serializer-any)
koin-platformcontext|block|commonMain|single<PlatformContext>.*androidContext\(\)|PlatformContext self-recursion crashes at launch (crash-classes.md#koin-platformcontext)
jvm-string-format|block|commonMain|String\.format\(|JVM-only API fails iOS link (crash-classes.md#jvm-only-api)
jvm-import|block|commonMain|^[[:space:]]*import java\.|JVM-only import fails iOS link (crash-classes.md#jvm-only-api)
strings-backslash-apos|block|composeResources|\\\\'|Backslash-apostrophe renders literally on iOS; use &apos; (crash-classes.md#strings-backslash-apos)
arrayunion-dto|warn|commonMain|\.arrayUnion\(|arrayUnion() with a non-primitive crashes iOS; pass a primitive Map (crash-classes.md#arrayunion-dto)
epoch-days-skew|warn|commonMain|\.toEpochDays\(\)|toEpochDays() is Long on iOS / Int on JVM; cast .toLong() (crash-classes.md#epoch-days-skew)
clock-system-ios|warn|commonMain|Clock\.System|Clock.System may be unresolved on iOS; inject () -> Long (crash-classes.md#clock-system-ios)
peekaboo-maxselection|warn|commonMain|SelectionMode\.Multiple\(|Multiple(maxSelection<=1) crashes Android; ensure max>1 else Single (crash-classes.md#peekaboo-maxselection)
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

TMP="$(mktemp)"; trap 'rm -f "$TMP"' EXIT
candidates > "$TMP"

block_count=0; warn_count=0
block_out=""; warn_out=""

printf '%s\n' "$RULES" | while IFS='|' read -r id sev glob pat msg; do
  [ -z "${id:-}" ] && continue
  echo "RULE|$id|$sev|$glob|$pat|$msg"
done > "$TMP.rules"

while IFS='|' read -r _tag id sev glob pat msg; do
  while IFS="$(printf '\t')" read -r path ln content; do
    case "$path" in *"$glob"*) ;; *) continue ;; esac
    case "$content" in *"crash-check:ignore $id"*) continue ;; esac
    if printf '%s' "$content" | grep -Eq -- "$pat"; then
      line="  $path:$ln  [$id] $msg"
      if [ "$sev" = block ]; then
        block_out="$block_out$line
"
        block_count=$((block_count + 1))
      else
        warn_out="$warn_out$line
"
        warn_count=$((warn_count + 1))
      fi
    fi
  done < "$TMP"
done < "$TMP.rules"
rm -f "$TMP.rules"

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
```

> **Note on the `while` subshell:** the counter loop reads from `"$TMP.rules"` via redirection (not a pipe), so `block_count`/`warn_count` survive in the current shell. The rules are pre-expanded to `$TMP.rules` first to avoid a pipe-into-while subshell. This is required for bash 3.2 correctness.

- [ ] **Step 5: Make scripts executable**

Run: `chmod +x scripts/crash-check.sh scripts/__tests__/run.sh`

- [ ] **Step 6: Run the harness to verify it passes**

Run: `bash scripts/__tests__/run.sh; echo "exit=$?"`
Expected: `All crash-check tests passed.` then `exit=0`

- [ ] **Step 7: Sanity-run against the real tree**

Run: `bash scripts/crash-check.sh --all`
Expected: exits 0 or 1 with a readable summary line; if it flags real pre-existing hits, confirm they are genuine (they likely are — that's the point) but do NOT fix app code in this task; note them for the team.

- [ ] **Step 8: Commit**

```bash
git add scripts/crash-check.sh scripts/__tests__/
git commit -m "feat(crash-check): deterministic crash-pattern scanner + tests"
```

---

## Task 3: Wire scanner into CI

**Files:**
- Modify: `.github/workflows/ci.yml`

- [ ] **Step 1: Add the `crash-check` job**

In `.github/workflows/ci.yml`, under `jobs:`, add this job immediately after the `detekt` job (before `build-android`):

```yaml
  # ── Crash Prevention ──────────────────────────────────────
  crash-check:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v6
        with:
          fetch-depth: 0
      - name: Run crash-pattern scanner tests
        run: bash scripts/__tests__/run.sh
      - name: Crash-pattern check (introduced lines only)
        run: |
          BASE="${{ github.base_ref || 'main' }}"
          git fetch --no-tags origin "$BASE"
          bash scripts/crash-check.sh --diff "origin/$BASE"
```

- [ ] **Step 2: Make the existing Android build wait on crash-check**

In the `build-android` job, change its `needs:` line from:

```yaml
    needs: [detekt, secrets-scan]
```

to:

```yaml
    needs: [detekt, secrets-scan, crash-check]
```

And in `build-ios`, change:

```yaml
    needs: [detekt, secrets-scan]
```

to:

```yaml
    needs: [detekt, secrets-scan, crash-check]
```

- [ ] **Step 3: Harden the iOS test gate**

In the `build-ios` job, the `Run iOS tests` step currently reads:

```yaml
      - name: Run iOS tests
        run: ./gradlew :composeApp:iosSimulatorArm64Test
        continue-on-error: true
```

Remove the `continue-on-error: true` line so iOS sim test failures block:

```yaml
      - name: Run iOS tests
        run: ./gradlew :composeApp:iosSimulatorArm64Test
```

> **Validation gate before committing this step:** if the team reports iOS sim tests are currently flaky, leave `continue-on-error: true` in place and record the deferral in the PR description (per the spec's noted tradeoff). Do not block the gate on a flaky suite.

- [ ] **Step 4: Validate the workflow locally**

Run: `python3 -c "import yaml,sys; yaml.safe_load(open('.github/workflows/ci.yml')); print('yaml ok')"`
Expected: `yaml ok`

- [ ] **Step 5: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "ci(crash-check): blocking crash-pattern job + iOS test gate"
```

---

## Task 4: Wire scanner into pre-push hook

**Files:**
- Modify: `.git/hooks/pre-push`

- [ ] **Step 1: Insert the crash-check block**

In `.git/hooks/pre-push`, immediately after the line `set -u` (and before the
`if [ "${SKIP_CODEX_REVIEW:-0}" = "1" ]` block), insert:

```bash
# --- Crash-pattern check (BLOCKING, unlike the advisory codex review below) ---
# Bypass once with: SKIP_CRASH_CHECK=1 git push   (or: git push --no-verify)
if [ "${SKIP_CRASH_CHECK:-0}" != "1" ] && [ -x scripts/crash-check.sh ]; then
  CC_BASE="${CRASH_CHECK_BASE:-origin/main}"
  if git rev-parse --verify "$CC_BASE" >/dev/null 2>&1; then
    echo "[pre-push] Running crash-pattern check against $CC_BASE..."
    if ! bash scripts/crash-check.sh --diff "$CC_BASE"; then
      echo "[pre-push] Blocking crash pattern(s) introduced. See docs/crash-classes.md."
      echo "[pre-push] Bypass once: SKIP_CRASH_CHECK=1 git push   (or: git push --no-verify)"
      exit 1
    fi
  else
    echo "[pre-push] Base '$CC_BASE' not found locally; skipping crash check (run: git fetch origin main)."
  fi
fi
```

- [ ] **Step 2: Verify the hook is still executable and parses**

Run: `bash -n .git/hooks/pre-push && echo "syntax ok"`
Expected: `syntax ok`

- [ ] **Step 3: Manually exercise the block (no push)**

Run: `SKIP_CRASH_CHECK=0 CRASH_CHECK_BASE=origin/main bash -c 'bash scripts/crash-check.sh --diff origin/main; echo exit=$?'`
Expected: prints a summary line and `exit=0` (assuming this branch introduces no crash patterns).

> **Note:** `.git/hooks/` is not version-controlled. There is no commit for this task — the change lives only in the local clone. Record in the PR description that contributors must add this block to their own `pre-push` (or that the repo's hook-install script, if any, should be updated separately).

---

## Task 5: Crash-check skill

**Files:**
- Create: `.claude/skills/crash-check/SKILL.md`

- [ ] **Step 1: Write the skill**

Create `.claude/skills/crash-check/SKILL.md` with this exact content:

````markdown
---
name: crash-check
description: Use before merging a PR or cutting a release to catch crashes this app is prone to — iOS-only runtime crashes that compile fine on Android and pass unit tests. Trigger on "check for crashes", "crash audit", "safe to merge", "pre-release check", "before we release".
---

# Crash Check

Two modes. The catalog of what crashes this app is `docs/crash-classes.md` — it is
the authority. Never hardcode a separate crash list here; read the catalog.

## Mode A — PR / diff audit (default)

Use when reviewing changes before a merge ("safe to merge?", "crash audit").

1. **Deterministic layer first.** Run:
   `bash scripts/crash-check.sh --diff origin/main`
   Report its output verbatim. A non-zero exit is already a BLOCK.
2. **AI reasoning layer.** Read the diff (`git diff origin/main...HEAD`). Walk every
   class in `docs/crash-classes.md` — both `regex` and `ai-only` — against the
   changed code, looking for instances the regex cannot see:
   - an untyped Firestore map returned through a helper (serializer-any)
   - a second fun-interface callback colliding on an Obj-C selector (native-callback-selector)
   - an `arrayUnion` whose argument is a DTO several call-hops away (arrayunion-dto)
   - a new JVM-only API in commonMain (jvm-only-api)
   - an unguarded `!!` on a gitlive nullable that lies on iOS (gitlive-nonnull-token)
3. **Verdict.** Output `SAFE TO MERGE` or `BLOCK` with a findings table:
   `file:line · class · why it crashes · fix`. Never claim safe without having
   actually run the script in step 1.

## Mode B — release audit

Use when cutting a release ("pre-release check", "before we release").

1. **Full-tree scan:** `bash scripts/crash-check.sh --all` (catches debt predating the gate).
2. **Build/test layer:** confirm green —
   `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64`,
   `./gradlew :composeApp:allTests`, and the iOS sim tests.
3. **Smoke walkthrough:** launch on an iOS sim AND an Android emulator (use the
   documented test sims + Fola/Gabby accounts + REST seeding). Walk: cold launch,
   login, dashboard, create customer, create order, style pick, upgrade sheet,
   settings. Report each flow pass/crash per device.
4. **Release verdict:** every layer must be green to call it release-ready.

## Closing step (both modes)

If the audit surfaces a NEW crash class, append it to `docs/crash-classes.md`, and
if it is regex-detectable, add a rule to `scripts/crash-check.sh` (with a fixture in
`scripts/__tests__/`). The gate strengthens with each incident.
````

- [ ] **Step 2: Verify the skill is discoverable**

Run: `test -f .claude/skills/crash-check/SKILL.md && head -5 .claude/skills/crash-check/SKILL.md`
Expected: prints the YAML frontmatter (name + description).

- [ ] **Step 3: Commit**

```bash
git add .claude/skills/crash-check/SKILL.md
git commit -m "feat(crash-check): add crash-check skill (PR + release modes)"
```

---

## Task 6: End-to-end validation on a seeded diff

**Files:** none (verification only)

- [ ] **Step 1: Create a throwaway branch with a deliberate crash**

```bash
git checkout -b tmp/crash-check-validation
printf '\nval bad = snap.data<Map<String, Any?>>()\n' >> composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/App.kt
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/App.kt
git commit -m "test: deliberate crash pattern (do not merge)"
```

- [ ] **Step 2: Confirm the scanner blocks it**

Run: `bash scripts/crash-check.sh --diff chore/crash-check-system; echo "exit=$?"`
Expected: lists `serializer-any` at the App.kt line and `exit=1`.

- [ ] **Step 3: Confirm a suppression comment clears it**

Edit that line in `App.kt` to append `// crash-check:ignore serializer-any — validation only`, then:
Run: `git commit -am "test: suppress"; bash scripts/crash-check.sh --diff chore/crash-check-system; echo "exit=$?"`
Expected: no `serializer-any` finding, `exit=0`.

- [ ] **Step 4: Tear down the throwaway branch**

```bash
git checkout chore/crash-check-system
git branch -D tmp/crash-check-validation
```

- [ ] **Step 5: Push and open the PR**

```bash
git push -u origin chore/crash-check-system
```

Then open a PR to `main`. Confirm in the GitHub Actions run that the `crash-check`
job appears and passes. Include manual smoke-test steps in the PR description
(per the QA workflow): "Pushed a branch with a deliberate `snap.data<...Any?>` line;
confirmed `crash-check` CI job failed it; confirmed suppression comment cleared it."

---

## Notes for the implementer

- **Bash 3.2 only** (macOS default): no associative arrays, no `mapfile`, no `${var^^}`.
  Indexed arrays and `<(...)` process substitution are fine.
- **`grep -E` not `-P`**: BSD grep on macOS has no `-P`. All patterns in the rules
  table are ERE and work on both BSD and GNU grep.
- **Scope is the false-positive control**: each rule's `pathglob` keeps it inside the
  source set where the pattern is actually dangerous (e.g. `String.format` is fine in
  `androidMain`, flagged only in `commonMain`).
- **`--diff` flags only introduced `+` lines**, so PRs are never blocked by pre-existing
  debt — exactly the "does not introduce a crash" requirement.
```

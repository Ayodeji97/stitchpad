# Crash-Check System — Design Spec

**Date:** 2026-06-16
**Status:** Approved (brainstorming) — ready for implementation plan
**Branch:** `chore/crash-check-system`

## Problem

StitchPad ships KMP + Compose Multiplatform. The crashes that actually bite this
app are **iOS-only runtime crashes that compile cleanly on Android and pass unit
tests** — so the existing CI (Android build, iOS framework link, unit tests,
detekt) does not catch them. We want a system that:

1. **Always runs before merge** — every PR is gated so it cannot introduce a
   known crash class.
2. **Provides a final pre-release crash check** before cutting a new app version.

A Claude skill alone cannot satisfy "always runs" — a skill only activates when
invoked in a conversation. True enforcement requires git hooks + CI. So the
system is two cooperating halves: the **knowledge** of how this app crashes (a
catalog + a skill) and the **enforcement** that runs it automatically (a script
wired into pre-push and CI).

## Decisions (locked during brainstorming)

- **Enforcement:** both a skill *and* an automated gate.
- **Gated branch:** `main` (matches existing CI; no `develop` branch introduced).
- **Detection layers:** all four — known-pattern lint, AI diff-audit, existing
  build/test gate, pre-release smoke checklist.
- **Lint mechanism:** a single standalone check script (not custom detekt rules)
  — zero new Gradle wiring, runs identically in pre-push, CI, and the skill.
- **Skill packaging:** one skill with two modes (PR audit + release audit).

## Architecture

```
docs/crash-classes.md          ← canonical knowledge catalog (single source of truth
                                  for WHAT crashes; one entry per crash class)
scripts/crash-check.sh         ← deterministic layer; implements the regex-detectable
                                  subset of the catalog (references each class by id)
.github/workflows/ci.yml       ← new `crash-check` job (blocking) + iOS gate hardening
.git/hooks/pre-push            ← calls crash-check.sh (BLOCKING, unlike codex/advisory)
.claude/skills/crash-check/
  SKILL.md                     ← one skill, two modes (PR diff-audit + release audit)
```

**Separation of concerns:**
- The **catalog** (`docs/crash-classes.md`) is the human-readable truth. Each
  entry: `id`, symptom, why-it-crashes, fix, and `detection:` tag
  (`regex` | `ai-only` | `build`).
- The **script** implements only the `detection: regex` subset, referencing
  catalog ids. No duplicated narrative.
- The **skill** reads the catalog to reason about the `ai-only` classes (the AI
  layer) and orchestrates the script. It never hardcodes a separate crash list.

This means no two artifacts hold a drifting copy of the pattern list.

## Component 1 — `docs/crash-classes.md` (catalog)

Seeded from real bug history (project memory). Each class is one section:

| id | detection | source |
|----|-----------|--------|
| `serializer-any` | regex | `snap.data<Map<String, Any?>>()` crashes iOS on first emit; use typed `@Serializable` DTO |
| `arrayunion-dto` | regex (warn) + ai | `arrayUnion(DTO)` → "Unsupported type" on iOS; pass a primitive Map |
| `koin-platformcontext` | regex | `single<PlatformContext> { androidContext() }` self-recurses, crashes at launch |
| `jvm-only-api` | regex | `String.format` and other JVM-only stdlib fail iOS link |
| `epoch-days-skew` | regex | `LocalDate.toEpochDays()` is Long on iOS / Int on JVM; cast `.toLong()` |
| `native-callback-selector` | ai-only | two fun-interface callbacks sharing method+param name collide on one Obj-C selector |
| `strings-backslash-apos` | regex | `\'` in strings.xml renders literally on iOS; use `&apos;` |
| `clock-system-ios` | regex | `Clock.System` unresolved on iOS in the pinned datetime version; inject `() -> Long` |
| `peekaboo-maxselection` | regex | `SelectionMode.Multiple(maxSelection<=1)` crashes Android; guard `max>1` else Single |
| `gitlive-nonnull-token` | ai-only | gitlive nullable signatures lie on iOS; SSO tokens must be non-null |

Catalog format is extensible: new incidents append a new section. Detection tag
drives whether the script gets a rule.

## Component 2 — `scripts/crash-check.sh`

**Contract:** one script, callable identically from pre-push, CI, and the skill.
Exit `0` = clean, `1` = blocking finding, `2` = usage/internal error.

**Modes:**
- `--diff <base>` (default base `origin/main`) — scans only **added/changed `+`
  lines** in `<base>...HEAD`. Used by pre-push and the CI PR job. A PR is blocked
  only by what it *introduces*, never by pre-existing debt. Fast, no build.
- `--all` — scans the whole tree (`commonMain` + `iosMain` + resources). Used by
  the release skill mode.
- `--staged` — scans staged hunks (reserved for a future pre-commit hook).

**Rule definition** (one per `detection: regex` catalog class):
`id | severity | path-glob | pattern | message(+catalog anchor)`

- **Severity:** `block` → non-zero exit; `warn` → printed, never fails. Fuzzy
  heuristics (e.g. `arrayunion-dto`) ship as `warn` to avoid false-positive
  merge blocks.
- **Scope by path-glob:** each rule fires only in its relevant source set (e.g.
  JVM-only-API only in `commonMain`/`iosMain`, never Android-only code where it
  is legal). Primary false-positive control.
- **Inline suppression:** a line carrying `// crash-check:ignore <id> — reason`
  (or `<!-- crash-check:ignore <id> -->` in XML) is skipped; the reason is
  required and echoed in output for visibility.
- **Output:** grouped by severity; each finding shows `file:line`, matched id,
  and catalog anchor. Ends with a summary line (`✗ 2 blocking, 1 warning` /
  `✓ no known crash patterns introduced`).
- **Engine portability:** pure bash + `git`. Prefers `ripgrep` (`rg`) when
  present; falls back to `grep -E`. Must run unchanged on macOS (pre-push) and
  ubuntu CI with no extra install.

## Component 3 — CI gate (`.github/workflows/ci.yml`)

- **New `crash-check` job:** runs `scripts/crash-check.sh --diff origin/main` on
  PRs to `main`. Blocking. Cheap (no build) so it can run early alongside
  `detekt`/`secrets-scan`.
- **iOS gate hardening:** keep `linkDebugFrameworkIosSimulatorArm64` blocking
  (already is); flip `iosSimulatorArm64Test` from `continue-on-error: true` to
  **blocking**. *Tradeoff noted:* iOS sim tests can be flakier than JVM tests; if
  flakiness proves disruptive, fall back to keeping them advisory and rely on the
  link step + crash-check + skill. Implementation plan should validate stability
  before committing to blocking.

## Component 4 — pre-push hook (`.git/hooks/pre-push`)

- Prepend a crash-check step **before** the existing advisory codex review.
- Unlike codex (advisory), crash-check is **blocking**: a known crash pattern
  stops the push.
- Bypass: `SKIP_CRASH_CHECK=1 git push` (or `--no-verify`), mirroring the
  existing `SKIP_CODEX_REVIEW` convention.
- Local + fast (diff scan, no build) so it doesn't slow pushes meaningfully.

## Component 5 — `.claude/skills/crash-check/SKILL.md`

One skill, two modes.

**Frontmatter trigger:** "check for crashes", "crash audit", "safe to merge",
"pre-release check", "before we release".

**Mode A — PR / diff audit** (`/crash-check`, or auto when reviewing pre-merge):
1. Run `scripts/crash-check.sh --diff origin/main` and report findings verbatim;
   a non-zero exit is already a blocker.
2. AI reasoning layer — read the diff and walk every `regex` and `ai-only`
   catalog class against the changed code, catching novel instances regex misses
   (untyped Firestore map through a helper, second selector-colliding callback,
   `arrayUnion` whose arg is a DTO several hops away, new JVM-only API, unguarded
   `!!` on a gitlive nullable-that-lies-on-iOS).
3. Verdict — `SAFE TO MERGE` / `BLOCK` with a findings table
   (`file:line · class · why it crashes · fix`). Never claim safe without having
   actually run the script.

**Mode B — release audit** (`/crash-check release`):
1. `scripts/crash-check.sh --all` (whole tree — catches debt predating the gate).
2. Confirm the build/test layer is green: iOS framework links, `:composeApp:allTests`
   and iOS sim tests pass.
3. Smoke walkthrough — launch on an iOS sim **and** Android emulator (documented
   test sims + Fola/Gabby accounts + REST seeding) and walk critical flows: cold
   launch, login, dashboard, create customer, create order, style pick, upgrade
   sheet, settings. Report each flow pass/crash per device.
4. Release verdict — every layer must be green to call it release-ready.

**Self-reinforcing loop:** when an audit (or a real-world crash) surfaces a new
crash class, the skill's closing step appends it to `docs/crash-classes.md` and,
if regex-detectable, adds the rule to `crash-check.sh`. The gate strengthens with
each incident.

## Data flow

- **Dev pushes** → pre-push runs crash-check.sh (blocking) + codex (advisory) → push.
- **PR opened** → CI `crash-check` + build/test/iOS-link (blocking) → merge gated.
- **Before release** → invoke skill Mode B → full scan + build confirmation +
  smoke walkthrough → release verdict.

## Out of scope (YAGNI)

- Custom detekt rule module (explicitly rejected — too heavy for single-module repo).
- Introducing a `develop` branch.
- Crash *reporting* / runtime telemetry (Crashlytics already covers Android
  non-fatals; this system is preventive, not observability).
- pre-commit hook (script supports `--staged` for later, not wired now).

## Testing strategy

- `crash-check.sh`: fixture-based — a `scripts/__tests__/` dir with known-bad and
  known-good snippets per rule; assert exit codes and that each rule fires only in
  its scoped path. Run in CI as part of the `crash-check` job.
- Suppression-comment handling and `--diff` vs `--all` behavior each get a fixture.
- Skill: validated manually against a deliberately crash-seeded diff before relying on it.

## Build sequence (for the plan)

1. `docs/crash-classes.md` catalog (seed entries above).
2. `scripts/crash-check.sh` + fixture tests (TDD: fixtures first).
3. Wire CI `crash-check` job + iOS test-gate hardening.
4. Prepend crash-check to pre-push hook.
5. `.claude/skills/crash-check/SKILL.md` (both modes).
6. Validate end-to-end on a seeded crash diff.

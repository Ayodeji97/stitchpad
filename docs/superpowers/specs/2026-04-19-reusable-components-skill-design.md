# Reusable Components Skill — Design

**Status:** Approved, ready for implementation plan
**Date:** 2026-04-19
**Author:** Daniel Ogunleye (with Claude)

## Problem

Reusable UI patterns are being duplicated across features in KMP/Compose projects. Concrete example in StitchPad: `AlertDialog` is implemented character-for-character identically in `CustomerDetailScreen.kt:245` and `StyleGalleryScreen.kt:207` — same title/body/destructive-confirm/cancel shape, differing only in string resource keys and action callbacks. Without guidance, each new feature rebuilds these generic patterns from scratch, causing drift, inconsistent UX, and duplicated maintenance.

The goal is a skill (or skill pair) that guides Claude to detect duplication, extract reusable components into a project's design system, and reuse them — automatically, with user approval, across any KMP/Compose project Daniel works on going forward.

## Goals

1. Prevent duplication of generic UI primitives (dialogs, empty states, section headers, chips, skeletons, loading indicators, form-field wrappers).
2. Make reuse the default behavior, not an afterthought.
3. Work across all KMP/Compose projects, not just StitchPad.
4. Respect existing project conventions (naming, placement) via a lightweight config mechanism.
5. Compose cleanly with existing skills (`android-compose-ui`, `android-presentation-mvi`, etc.) without overlapping their scope.

## Non-goals

- Extracting non-UI code (data-layer helpers, ViewModels, error types). Those domains are owned by `android-data-layer`, `android-presentation-mvi`, `android-error-handling` respectively.
- Extracting feature-specific composables that happen to look reusable (e.g. `MeasurementSheet` in one feature does not get extracted speculatively).
- Rebuilding Material3 primitives from scratch — the skill guides wrapping and constraining, not replacing.
- Auto-extraction without user approval.

## Architecture

Two skills, split by scope:

### Global skill: `android-reusable-components`
- **Location:** `~/.claude/skills/android-reusable-components/SKILL.md`
- **Scope:** Project-agnostic methodology — when to extract, how to extract, workflow with the user
- **Usable from:** Any KMP/Compose project on Daniel's machine
- **Loads alongside:** The project-specific companion (if one exists)

### Companion skill: `stitchpad-design-system`
- **Location:** `.claude/skills/stitchpad-design-system/SKILL.md` (committed to the StitchPad repo)
- **Scope:** StitchPad-specific design-system conventions — prefix, path, inventory, local anti-patterns
- **Size:** ~30 lines, mostly structured data
- **Pattern for other projects:** Each new KMP project gets its own tiny `{project}-design-system` companion — copy template, edit 5 values.

### Relationship

- Global skill activates on reusable-component work
- On activation, it looks for `.claude/skills/*-design-system/SKILL.md` in the current project
- If found → reads 5 fields (prefix, path, inventory, API style, anti-patterns) and uses them
- If absent → falls back to defaults: prefix = `App`, path = `ui/components/`
- Global skill defers to `android-compose-ui` for slot-API patterns, stability, previewability — no overlap

## Global skill contents

### Frontmatter

```yaml
name: android-reusable-components
description: Extract and reuse Compose composables and UI helpers across an Android/KMP project's design system. Triggers when writing a new composable, noticing duplication between features, or auditing reusability. Trigger phrases: "alert dialog", "empty state", "section header", "this looks similar to", "extract to design system", "reusable component", "audit components".
```

### Triggers

Four triggers — three always-on, one on-demand:

- **A. Pre-implementation check** *(always-on)* — before adding a new UI piece, check the design-system package for an existing reusable version. Use it if found.
- **B. Duplication detection** *(always-on, Rule of 2)* — when about to write a composable that already exists in another feature, pause and propose extraction before continuing.
- **D. New-component heuristic** *(always-on, Intent Check)* — when creating a new composable that is obviously generic (confirmation dialog, empty state, section header, skeleton, chip, loading indicator, form-field wrapper), place it in the design system on first use with the project prefix.
- **C. On-demand audit** *(user-invoked)* — when the user says "audit this feature for reusable components", scan and report candidates without extracting.

### Reading project conventions

First action on activation:

1. Look for `.claude/skills/*-design-system/SKILL.md` in the current project root
2. If found, parse its 5 structured fields (prefix, path, inventory, API style, anti-patterns)
3. If absent, fall back to: prefix = `App`, path = `ui/components/`, no inventory, generic API style

### Extraction workflow

- **Before writing a new composable:** grep the configured design-system path for existing matches by name or purpose. If found, use it. Stop.
- **If the new composable matches the "obviously generic" list:** propose placing it in the design system on first use. Do not bury it in the feature package.
- **If about to duplicate existing feature code (Rule of 2):** STOP. Surface it to the user with the template:
  > "I'm about to write a second [X]. The first one is at [path:line]. Want me to extract [X] to `{Prefix}{Name}` in `{path}` first, then use it in both places?"

  Wait for approval.
- **On approval:** extract as a **separate commit** from the feature work, using a slot-based API (refer to `android-compose-ui`), sensible defaults, `@Preview` with at least 2 states.

### What to extract (and what NOT to)

**Extract:**
- Generic UI primitives (dialogs, empty states, chips, skeletons, form-field wrappers)
- Layouts used in 2+ features
- Visual patterns that must stay consistent across the app

**Do not extract:**
- Feature-specific composables (unless a 2nd feature actually needs them)
- One-off screens
- Composables that deeply couple to a feature's state model

### API shape guidelines

Defers to `android-compose-ui` for slot APIs, stability, preview conventions. This skill only adds:

- Every design-system composable MUST have a `@Preview` with ≥2 states (default + edge case)
- No business logic inside
- Optional `modifier: Modifier = Modifier` as first parameter after content
- Sensible defaults so the simple call site stays short
- Prefer simple typed parameters (e.g. `title: String`) until a 3rd call site needs richer content, then upgrade to `@Composable () -> Unit` slots

### On-demand audit mode

When user asks to audit a feature, scan for:

- Composables >50 lines
- Composables with names matching the generic list (Dialog, EmptyState, SectionHeader, etc.)
- Composables whose structure duplicates anything in the configured design-system path

Report candidates with recommendations. Do not extract unilaterally.

### Commit & PR hygiene

Extractions go in their own commit, using the message template:

```
refactor(ui): extract {PrefixName} to design system
```

This matches Daniel's PR-per-concern workflow — feature PRs stay small and focused; design-system extractions are independently reviewable.

## Companion skill contents (StitchPad)

### Frontmatter

```yaml
name: stitchpad-design-system
description: StitchPad-specific design-system conventions — component naming, placement, inventory, and anti-patterns. Read by android-reusable-components skill when working in this project. Activates on Compose UI work in StitchPad.
```

### Five structured fields

1. **Prefix:** `StitchPad`
   - All shared components named `StitchPad{Name}` (e.g. `StitchPadAlertDialog`, `StitchPadEmptyState`)

2. **Path:** `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/`
   - Flat for now. Split into sub-folders (`dialogs/`, `states/`, `inputs/`) only once the folder exceeds ~15 files.

3. **Existing inventory:**
   - `CustomerAvatar` — initial-circle avatar with optional photo
   - `LoadingDots` — indeterminate loading indicator
   - *(new extractions get appended here as they land)*

4. **API style preferences:**
   - Slot-based content (`title: @Composable () -> Unit`) for anything with variable body — refer to `android-compose-ui` for the pattern
   - All user-visible strings via `compose.resources` (not hardcoded) — matches CLAUDE.md rule
   - Every component has a `@Preview` with light + dark states
   - Destructive actions use `StitchPadAlertDialog`, never Material3 `AlertDialog` directly (ties to notification-patterns feedback memory)

5. **Local anti-patterns (actively reject):**
   - Hardcoded hex colors — use `MaterialTheme.colorScheme.*` tied to `DesignTokens`
   - Hardcoded text strings — use string resources
   - Business logic inside design-system composables
   - Re-implementing Material3 primitives wholesale — wrap and constrain, don't rebuild

## Worked example: first extraction

**Trigger:** Adding delete-style to `StyleGalleryScreen`. Claude sees the existing `AlertDialog` in `CustomerDetailScreen.kt:245–285` and triggers the skill.

**Claude's message (template):**

> "Heads up — you have an `AlertDialog` in `CustomerDetailScreen.kt:245–285` that matches the shape of what I'd write here (title + body + destructive confirm + cancel). This is the Rule-of-2 trigger. I propose extracting `StitchPadAlertDialog` to `ui/components/StitchPadAlertDialog.kt` first, then replacing both call sites. Want me to proceed? Extraction will be a separate commit from the style-delete feature work."

**On approval — extracted component:**

```kotlin
@Composable
fun StitchPadAlertDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    title: String,
    message: String,
    confirmText: String,
    dismissText: String,
    modifier: Modifier = Modifier,
    isDestructive: Boolean = true,
)
```

**API choices:**
- Simple string params (not composable slots) because both current call sites pass plain text. Upgrade to slots when a 3rd site needs rich content.
- `isDestructive = true` default matches current usage (red confirm). Non-destructive usage sets it false.
- `@Preview` with light/dark × both `isDestructive` states.
- No business logic — callbacks only.

**Commit plan:**
1. `refactor(ui): extract StitchPadAlertDialog to design system` — creates component + updates both call sites
2. Feature work continues on top with clean dialog usage

**Inventory update:** companion skill's inventory gains `StitchPadAlertDialog — destructive confirmation dialog, Material3 wrapper`.

## Rollout

The implementation plan (produced next via `writing-plans`) will cover:

1. Create `~/.claude/skills/android-reusable-components/SKILL.md` with the full content above
2. Create `.claude/skills/stitchpad-design-system/SKILL.md` in the StitchPad repo with the 5 fields
3. Validate the skill activates correctly in a test conversation (synthetic "I'm adding a dialog" prompt)
4. Apply the worked example — extract `StitchPadAlertDialog` as the first real extraction, using the skill's own workflow
5. Update CLAUDE.md with a 1-line pointer to the companion skill (optional)

## Open questions

None at design time. Implementation plan to resolve:
- Exact wording of the skill's trigger-detection prompts (tune during step 3)
- Whether to add a 3rd skill or addendum for non-Android KMP surfaces (iOS-only, desktop-only) — deferred until needed

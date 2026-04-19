# Reusable Components Skill Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the `android-reusable-components` global skill + `stitchpad-design-system` companion skill, then apply the first real extraction (`StitchPadAlertDialog`) to prove the workflow end-to-end.

**Architecture:** Two-skill split — a global KMP methodology skill at `~/.claude/skills/android-reusable-components/SKILL.md` that triggers on Compose UI work, plus a per-project companion at `.claude/skills/stitchpad-design-system/SKILL.md` supplying 5 fields of project-specific config (prefix, path, inventory, API style, anti-patterns). Global skill reads the companion on activation and falls back to defaults if absent.

**Tech Stack:** Markdown skill files (no runtime); Kotlin + Compose Multiplatform for the worked example; Gradle for tests.

**Prerequisite:** Ensure you are on the `feature/reusable-components-skill` branch (branched off `feature/style-gallery`). The approved spec is committed there at `docs/superpowers/specs/2026-04-19-reusable-components-skill-design.md`.

```bash
git checkout feature/reusable-components-skill
git pull origin feature/reusable-components-skill
```

---

## File Structure

**Created (outside repo, user-level):**
- `~/.claude/skills/android-reusable-components/SKILL.md` — global methodology skill

**Created (inside repo):**
- `.claude/skills/stitchpad-design-system/SKILL.md` — StitchPad companion skill with 5 config fields
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/StitchPadAlertDialog.kt` — reusable destructive-confirmation dialog with `@Preview`

**Modified (inside repo):**
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/detail/CustomerDetailScreen.kt:244-286` — replace inline `AlertDialog` with `StitchPadAlertDialog`
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/style/presentation/gallery/StyleGalleryScreen.kt:206-248` — replace inline `AlertDialog` with `StitchPadAlertDialog`
- `.claude/skills/stitchpad-design-system/SKILL.md` — update inventory to include `StitchPadAlertDialog` after extraction
- `CLAUDE.md` — optional 1-line pointer to the companion skill

---

## Task 1: Create global skill `android-reusable-components`

**Files:**
- Create: `~/.claude/skills/android-reusable-components/SKILL.md`

- [ ] **Step 1: Create the skill directory**

Run:
```bash
mkdir -p ~/.claude/skills/android-reusable-components
```

- [ ] **Step 2: Write the skill file**

Create `~/.claude/skills/android-reusable-components/SKILL.md` with the content below verbatim:

````markdown
---
name: android-reusable-components
description: Extract and reuse Compose composables and UI helpers across an Android/KMP project's design system. Triggers when writing a new composable, noticing duplication between features, or auditing reusability. Trigger phrases - "alert dialog", "empty state", "section header", "this looks similar to", "extract to design system", "reusable component", "audit components".
---

# Android Reusable Components

Extract and reuse Compose composables + UI helpers into a project's design system. Prevents duplication, enforces consistency, composes with existing skills (`android-compose-ui` for slot APIs, `android-presentation-mvi` for ViewModels).

## When this skill fires

Four triggers — three always-on, one on-demand.

### A. Pre-implementation check (always-on)
Before adding a new UI piece, search the project's design-system path for an existing reusable version. If found, use it. Do not rebuild.

### B. Duplication detection — Rule of 2 (always-on)
When about to write a composable that already exists in another feature, STOP. Do not write the duplicate.

### D. New-component heuristic — Intent Check (always-on)
When creating a new composable that is obviously generic, place it in the design system on first use with the project prefix. Do not bury it in the feature package.

"Obviously generic" list:
- Confirmation dialog
- Empty state
- Section header
- Skeleton / shimmer placeholder
- Chip
- Loading indicator
- Form-field wrapper

### C. On-demand audit (user-invoked)
When the user says "audit this feature for reusable components" (or similar), scan the feature and report candidates without extracting.

## Reading project conventions

On activation, first look for a companion skill in the current project:

```
.claude/skills/*-design-system/SKILL.md
```

If found, read its 5 structured fields:
1. Prefix (e.g. `StitchPad`)
2. Path (e.g. `composeApp/src/commonMain/kotlin/.../ui/components/`)
3. Existing inventory
4. API style preferences
5. Local anti-patterns

If absent, fall back to:
- Prefix: `App`
- Path: `ui/components/`
- No inventory (grep the path fresh)
- Generic API style (refer to `android-compose-ui`)

## Extraction workflow

### Before writing a new composable
1. Grep the configured design-system path for matches by name or purpose
2. If found → use it. Stop.
3. If the new composable matches the "obviously generic" list → propose placing it in the design system on first use
4. If about to duplicate existing feature code → trigger the Rule-of-2 pause (below)

### Rule-of-2 pause template

Message the user:

> "I'm about to write a second [X]. The first one is at [path:line]. This is the Rule-of-2 trigger. I propose extracting [X] to `{Prefix}{Name}` in `{path}` first, then using it in both places. Want me to proceed? Extraction will be a separate commit from the feature work."

Wait for approval.

### On approval
- Extract as a **separate commit** from the feature work
- Use slot-based API (refer to `android-compose-ui`) — but simple typed params are fine until a 3rd call site needs richer content
- Sensible defaults so the simple call site stays short
- `@Preview` with ≥2 states (default + edge case, typically light/dark or enabled/disabled)
- No business logic inside — callbacks only
- `modifier: Modifier = Modifier` as first parameter after content

## What to extract

**Extract:**
- Generic UI primitives (dialogs, empty states, chips, skeletons, form-field wrappers)
- Layouts used in 2+ features
- Visual patterns that must stay consistent across the app

**Do NOT extract:**
- Feature-specific composables unless a 2nd feature actually needs them
- One-off screens
- Composables that deeply couple to a feature's state model
- Non-UI code — `android-data-layer`, `android-presentation-mvi`, `android-error-handling` own those domains

## On-demand audit mode

When user asks to audit a feature, scan for:
- Composables >50 lines
- Composables whose name matches the "obviously generic" list
- Composables whose structure duplicates anything in the configured design-system path

Report candidates with recommendations. Do not extract unilaterally.

## Commit & PR hygiene

Extractions go in their own commit:

```
refactor(ui): extract {PrefixName} to design system
```

Keeps feature PRs small and focused; design-system extractions are independently reviewable.

## After extraction

Remind the user to update the companion skill's inventory list with the newly-extracted component. This keeps future activations accurate.
````

- [ ] **Step 3: Verify the file is readable**

Run:
```bash
ls -la ~/.claude/skills/android-reusable-components/SKILL.md
head -5 ~/.claude/skills/android-reusable-components/SKILL.md
```
Expected: file exists, frontmatter starts with `---` and includes `name: android-reusable-components`.

---

## Task 2: Create companion skill `stitchpad-design-system`

**Files:**
- Create: `.claude/skills/stitchpad-design-system/SKILL.md`

- [ ] **Step 1: Create the skill directory**

Run:
```bash
mkdir -p .claude/skills/stitchpad-design-system
```

- [ ] **Step 2: Write the companion file**

Create `.claude/skills/stitchpad-design-system/SKILL.md` verbatim:

````markdown
---
name: stitchpad-design-system
description: StitchPad-specific design-system conventions — component naming, placement, inventory, and anti-patterns. Read by android-reusable-components skill when working in this project. Activates on Compose UI work in StitchPad.
---

# StitchPad Design System

Project-specific config for `android-reusable-components`. Supplies 5 fields the global skill needs to make extraction decisions in this codebase.

## 1. Prefix

`StitchPad`

All shared components named `StitchPad{Name}`. Examples:
- `StitchPadAlertDialog`
- `StitchPadEmptyState`
- `StitchPadSectionHeader`

## 2. Path

`composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/`

Flat for now. Split into sub-folders (`dialogs/`, `states/`, `inputs/`) once the folder exceeds ~15 files.

## 3. Inventory

- `CustomerAvatar` — initial-circle avatar with optional photo
- `LoadingDots` — indeterminate loading indicator

*(Append new extractions to this list as they land.)*

## 4. API style preferences

- Slot-based content (`title: @Composable () -> Unit`) for anything with variable body — refer to `android-compose-ui` for the pattern
- Simple typed params (e.g. `title: String`) are fine until a 3rd call site needs richer content
- All user-visible strings via `compose.resources` string resources (not hardcoded) — matches the CLAUDE.md rule
- Every component has a `@Preview` with light + dark states
- Destructive actions use `StitchPadAlertDialog`, never Material3 `AlertDialog` directly (ties to the notification-patterns memory — Snackbar for feedback, Dialog for destructive, Bottom Sheet for choices)

## 5. Local anti-patterns (actively reject)

- Hardcoded hex colors — use `MaterialTheme.colorScheme.*` tied to `DesignTokens`
- Hardcoded text strings — use string resources
- Business logic inside design-system composables
- Re-implementing Material3 primitives wholesale — wrap and constrain, don't rebuild
````

- [ ] **Step 3: Commit the companion skill**

```bash
git add .claude/skills/stitchpad-design-system/SKILL.md
git commit -m "feat(skill): add stitchpad-design-system companion skill"
```

---

## Task 3: Smoke-test skill activation

- [ ] **Step 1: Validate activation with a synthetic prompt**

Start a new Claude Code session in this repo. Prompt:

> "I want to add a delete-confirmation alert dialog to the measurement form screen."

Expected behavior:
- Claude activates `android-reusable-components` (announces it)
- Claude reads `stitchpad-design-system` companion
- Claude notices the existing `AlertDialog` duplication in `CustomerDetailScreen.kt:245` and `StyleGalleryScreen.kt:207`
- Claude triggers Rule-of-2 pause and proposes extracting `StitchPadAlertDialog`

If any of the above does NOT happen, the skill description is not catching the trigger. Fix the `description` field in the global skill's frontmatter (add missing trigger phrases) and retry. Do not proceed to Task 4 until activation works.

- [ ] **Step 2: Record the result**

Note in the PR description whether activation worked on first try or required description tuning. This informs future skill-authoring.

---

## Task 4: Extract `StitchPadAlertDialog` (worked example)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/StitchPadAlertDialog.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/detail/CustomerDetailScreen.kt:244-286`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/style/presentation/gallery/StyleGalleryScreen.kt:206-248`

- [ ] **Step 1: Create `StitchPadAlertDialog.kt`**

Create `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/StitchPadAlertDialog.kt`:

```kotlin
package com.danzucker.stitchpad.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme

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
) {
    val confirmContainer = if (isDestructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    val confirmContent = if (isDestructive) {
        MaterialTheme.colorScheme.onError
    } else {
        MaterialTheme.colorScheme.onPrimary
    }

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = confirmContainer,
                    contentColor = confirmContent,
                ),
                shape = RoundedCornerShape(DesignTokens.radiusMd),
            ) {
                Text(
                    text = confirmText,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = dismissText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        shape = RoundedCornerShape(DesignTokens.radiusXl),
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

@Preview
@Composable
private fun StitchPadAlertDialogDestructivePreview() {
    StitchPadTheme {
        StitchPadAlertDialog(
            onDismiss = {},
            onConfirm = {},
            title = "Delete customer?",
            message = "Remove Ada from your customer list? This cannot be undone.",
            confirmText = "Delete",
            dismissText = "Cancel",
            isDestructive = true,
        )
    }
}

@Preview
@Composable
private fun StitchPadAlertDialogNonDestructivePreview() {
    StitchPadTheme {
        StitchPadAlertDialog(
            onDismiss = {},
            onConfirm = {},
            title = "Discard draft?",
            message = "You'll lose the changes you just made.",
            confirmText = "Discard",
            dismissText = "Keep editing",
            isDestructive = false,
        )
    }
}
```

Notes on choices:
- Simple `String` params (not `@Composable` slots) because both call sites pass plain strings. Upgrade to slots when a 3rd site needs rich content.
- `isDestructive: Boolean = true` default matches current usage (red confirm button in both call sites). Non-destructive overrides set it false.
- Container shape, button shape, container color, text colors all copied verbatim from the two existing inline dialogs to guarantee visual parity.

- [ ] **Step 2: Verify it compiles**

Run:
```bash
./gradlew :composeApp:compileKotlinAndroid
```
Expected: BUILD SUCCESSFUL. If it fails, inspect the error — likely an import or preview dependency mismatch. Check against how `CustomerAvatar.kt` imports `StitchPadTheme` and `@Preview`.

- [ ] **Step 3: Replace the dialog in `CustomerDetailScreen.kt`**

Remove the `AlertDialog` import on line 27. Remove unused imports that become dead (`Button`, `ButtonDefaults`, `FontWeight`, `RoundedCornerShape`, `TextButton`) — only if they are not used elsewhere in the file; check first.

Replace lines 244-286 of `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/detail/CustomerDetailScreen.kt`:

**Before (existing, lines 244-286):**
```kotlin
    if (state.showDeleteDialog && state.measurementToDelete != null) {
        AlertDialog(
            onDismissRequest = { onAction(CustomerDetailAction.OnDismissDeleteDialog) },
            title = {
                Text(
                    text = stringResource(Res.string.measurement_delete_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(Res.string.measurement_delete_message),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = { onAction(CustomerDetailAction.OnConfirmDelete) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(DesignTokens.radiusMd)
                ) {
                    Text(
                        text = stringResource(Res.string.customer_delete_confirm),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(CustomerDetailAction.OnDismissDeleteDialog) }) {
                    Text(
                        text = stringResource(Res.string.customer_delete_cancel),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            shape = RoundedCornerShape(DesignTokens.radiusXl),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
```

**After:**
```kotlin
    if (state.showDeleteDialog && state.measurementToDelete != null) {
        StitchPadAlertDialog(
            onDismiss = { onAction(CustomerDetailAction.OnDismissDeleteDialog) },
            onConfirm = { onAction(CustomerDetailAction.OnConfirmDelete) },
            title = stringResource(Res.string.measurement_delete_title),
            message = stringResource(Res.string.measurement_delete_message),
            confirmText = stringResource(Res.string.customer_delete_confirm),
            dismissText = stringResource(Res.string.customer_delete_cancel),
        )
    }
```

Add the import at the top of the file (alphabetically in the `com.danzucker.stitchpad` block):
```kotlin
import com.danzucker.stitchpad.ui.components.StitchPadAlertDialog
```

- [ ] **Step 4: Replace the dialog in `StyleGalleryScreen.kt`**

Replace lines 206-248 of `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/style/presentation/gallery/StyleGalleryScreen.kt`:

**Before (existing, lines 206-248):**
```kotlin
    if (state.showDeleteDialog && state.styleToDelete != null) {
        AlertDialog(
            onDismissRequest = { onAction(StyleGalleryAction.OnDismissDeleteDialog) },
            title = {
                Text(
                    text = stringResource(Res.string.style_delete_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(Res.string.style_delete_message),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = { onAction(StyleGalleryAction.OnConfirmDelete) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(DesignTokens.radiusMd)
                ) {
                    Text(
                        text = stringResource(Res.string.style_delete_confirm),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(StyleGalleryAction.OnDismissDeleteDialog) }) {
                    Text(
                        text = stringResource(Res.string.style_delete_cancel),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            shape = RoundedCornerShape(DesignTokens.radiusXl),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
```

**After:**
```kotlin
    if (state.showDeleteDialog && state.styleToDelete != null) {
        StitchPadAlertDialog(
            onDismiss = { onAction(StyleGalleryAction.OnDismissDeleteDialog) },
            onConfirm = { onAction(StyleGalleryAction.OnConfirmDelete) },
            title = stringResource(Res.string.style_delete_title),
            message = stringResource(Res.string.style_delete_message),
            confirmText = stringResource(Res.string.style_delete_confirm),
            dismissText = stringResource(Res.string.style_delete_cancel),
        )
    }
```

Add the import:
```kotlin
import com.danzucker.stitchpad.ui.components.StitchPadAlertDialog
```

Remove the now-unused `AlertDialog` import (line 23) only if `AlertDialog` is not used elsewhere in the file — grep to confirm:
```bash
grep -n "AlertDialog" composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/style/presentation/gallery/StyleGalleryScreen.kt
```
If the only remaining hit is the import line, delete it. Same check for `CustomerDetailScreen.kt`.

- [ ] **Step 5: Run ViewModel tests to verify no behavior regression**

The extraction is a pure refactor — both call sites still dispatch the same actions. The existing ViewModel tests confirm behavior hasn't changed.

Run:
```bash
./gradlew :composeApp:allTests --tests "*CustomerDetailViewModelTest" --tests "*StyleGalleryViewModelTest"
```
Expected: BUILD SUCCESSFUL, all tests pass. If anything fails, the refactor changed behavior accidentally — investigate before proceeding.

- [ ] **Step 6: Run detekt**

Run:
```bash
./gradlew detekt
```
Expected: BUILD SUCCESSFUL, no new violations. Fix any issues inline.

- [ ] **Step 7: Manual smoke test**

Build and run the Android app:
```bash
./gradlew :composeApp:assembleDebug
```

Install and exercise both delete flows:
1. Open a customer → long-press a measurement → Delete → confirm dialog appears with correct title/message/buttons → tap Cancel → dismisses → tap Delete → measurement removed
2. Go to Styles → long-press a style → Delete → confirm dialog appears with correct title/message/buttons → tap Cancel → dismisses → tap Delete → style removed

Both dialogs should look visually identical to how they looked before the refactor.

- [ ] **Step 8: Commit the extraction as its own commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/StitchPadAlertDialog.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/detail/CustomerDetailScreen.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/style/presentation/gallery/StyleGalleryScreen.kt
git commit -m "refactor(ui): extract StitchPadAlertDialog to design system"
```

---

## Task 5: Update companion inventory

**Files:**
- Modify: `.claude/skills/stitchpad-design-system/SKILL.md` (inventory section)

- [ ] **Step 1: Add `StitchPadAlertDialog` to the inventory**

In `.claude/skills/stitchpad-design-system/SKILL.md`, replace the `## 3. Inventory` section:

**Before:**
```markdown
## 3. Inventory

- `CustomerAvatar` — initial-circle avatar with optional photo
- `LoadingDots` — indeterminate loading indicator

*(Append new extractions to this list as they land.)*
```

**After:**
```markdown
## 3. Inventory

- `CustomerAvatar` — initial-circle avatar with optional photo
- `LoadingDots` — indeterminate loading indicator
- `StitchPadAlertDialog` — destructive (or non-destructive) confirmation dialog, Material3 wrapper with project typography and shape tokens

*(Append new extractions to this list as they land.)*
```

- [ ] **Step 2: Commit**

```bash
git add .claude/skills/stitchpad-design-system/SKILL.md
git commit -m "docs(skill): add StitchPadAlertDialog to design-system inventory"
```

---

## Task 6: Add CLAUDE.md pointer to the companion skill

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: Add a line under "Patterns (MUST follow)"**

In `CLAUDE.md`, add a new numbered item to the "Patterns (MUST follow)" list:

```markdown
9. Reusable components: See `.claude/skills/stitchpad-design-system/SKILL.md` for prefix/path/inventory. Extract generic UI (dialogs, empty states, chips) to `ui/components/` with `StitchPad` prefix.
```

- [ ] **Step 2: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: point CLAUDE.md at stitchpad-design-system skill"
```

---

## Task 7: Open the PR

- [ ] **Step 1: Push the branch**

```bash
git push -u origin feature/reusable-components-skill
```

- [ ] **Step 2: Open PR**

Run:
```bash
gh pr create --base feature/style-gallery --title "feat(skill): reusable components skill + StitchPadAlertDialog" --body "$(cat <<'EOF'
## Summary
- Adds `stitchpad-design-system` companion skill (5-field config for the new global `android-reusable-components` skill)
- Extracts `StitchPadAlertDialog` — first real use of the skill's workflow; removes duplication between `CustomerDetailScreen` and `StyleGalleryScreen`
- Updates CLAUDE.md to point at the companion skill

## Test plan
- [ ] CustomerDetail delete-measurement dialog: appears with correct strings, Cancel dismisses, Delete removes measurement
- [ ] StyleGallery delete-style dialog: appears with correct strings, Cancel dismisses, Delete removes style
- [ ] Both dialogs look visually identical to pre-refactor (destructive red confirm, same typography, same shape)
- [ ] `./gradlew :composeApp:allTests` passes
- [ ] `./gradlew detekt` passes
- [ ] Skill activation smoke test: new session + "I want to add a new dialog" → `android-reusable-components` activates and reads the companion

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

Note: PR base is `feature/style-gallery` (since this branch was cut off it), not `main`. Adjust if your integration order has changed.

---

## Done

After Task 7, the skill pair is installed, the first real extraction ships alongside, and the pattern is ready for the next duplication you encounter. The companion-skill inventory will grow organically as extractions land.

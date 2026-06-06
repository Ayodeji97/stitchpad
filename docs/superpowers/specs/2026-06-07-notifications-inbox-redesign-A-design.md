# Notifications Inbox — Card Redesign (Variant A "Refined minimal") — design

> **Date:** 2026-06-07
> **Status:** approved (user picked A over B after testing B's dark-mode wash)
> **Branch:** `feature/notifications-inbox-redesign` (off `main`, which now has the merged inbox PR #126)
> **Scope:** presentation-only redesign of the in-app notifications inbox row + screen.

## Why

The shipped inbox row (PR #126) is a plain unread-dot + sentence list — functional but flat.
A first redesign attempt (variant B: accent-bar cards + type pills + a **full-card indigo
unread wash**) was built and tested, but in **dark mode** the unread wash turns every unread
card solid indigo — heavy and wrong. The user chose **variant A**: a quiet editorial list that
is "closest to the current screen, just polished" and has no full-card wash, so it has no
dark-mode failure mode.

## Hard constraints

- **Presentation-only.** No change to the `Notification` model, DTO, mapper, repository,
  ViewModel logic, Firestore rules, or Cloud Functions. The inbox keeps working exactly as
  today: tap → mark-read + navigate to order detail; "Mark all read"; live bell unread count.
- Must compile + render on **Android and iOS** (KMP). Light **and** dark mode both specced.
- No hardcoded user-facing strings — compose.resources only. No `\'` escapes (use `&apos;`).

## The look (variant A)

A grouped, airy list. No card surfaces, no accent bars, no pills, no full-card wash.

**Day grouping:** notifications split into **TODAY** and **EARLIER** sections, each with a
small uppercase, letter-spaced, muted section header (`labelMedium`, `onSurfaceVariant`).
Empty sections are omitted. Order within a section is preserved (newest-first, as delivered).

**Row anatomy** (left → right):
1. **Tinted type-icon** — a ~40dp rounded square (`RoundedCornerShape(radiusMd)`), filled with
   a soft type-tint, containing the type's icon in the type's color:
   - OVERDUE → `Icons.Filled.PriorityHigh`, color `error`, bg `errorContainer`
   - DUE_SOON → `Icons.Outlined.Schedule`, color = warning (dark-aware), bg = warning tint
     (light: `warning500` on `warning50`; dark: `warningDarkText` on `warningDarkBg`)
   - TO_COLLECT → `Icons.Outlined.Payments`, color `tertiary` (sienna), bg `tertiaryContainer`
   - UNKNOWN → `Icons.Outlined.Notifications`, color `onSurfaceVariant`, bg `surfaceVariant`
2. **Two text lines** (weight 1):
   - **Line 1 — customer name.** `titleSmall`/`bodyLarge`, `FontWeight.SemiBold` when unread
     (`onSurface`) / `Normal` when read (`onSurfaceVariant`).
   - **Line 2 — meta line**, `bodyMedium`, single line, ellipsized. Structured as
     `{garment} · {type-tag} · {relativeTime}` where:
     - **garment** + **relativeTime** are muted (`onSurfaceVariant`).
     - **type-tag** is the colored phrase in the type's color:
       - OVERDUE → "overdue" (`error`)
       - DUE_SOON → "due soon" (warning, dark-aware)
       - TO_COLLECT → "owes ₦{amount}" — the `₦{amount}` portion in **JetBrains Mono**,
         `tertiary` (sienna); if `amount == null`, the tag is omitted (meta line = `{garment} · {relativeTime}`).
       - UNKNOWN → no tag (meta line = `{garment} · {relativeTime}`).
3. **Unread dot** — an 8dp `primary` (indigo) circle on the trailing edge, only when unread.
   Reserve its space when read so text width stays stable.

**Dividers:** a hairline `HorizontalDivider` (`outlineVariant`) between consecutive rows
**within** a section, inset to start under the text column (after the icon + gap). No divider
after the last row of a section or before a section header.

**Read vs unread:** unread = SemiBold name + `onSurface` + dot; read = Normal name +
`onSurfaceVariant`, no dot. No background wash in either state (this is the whole point vs B).

## Pure helpers (new, unit-tested — design-agnostic, identical to what B used)

`feature/notification/presentation/inbox/NotificationDisplay.kt`:

- `fun notificationRelativeTime(createdAtMillis: Long, nowMillis: Long, tz: TimeZone): String`
  - Compute via `Instant.fromEpochMilliseconds(...).toLocalDateTime(tz)`.
  - `epochDayDiff = nowDate.toEpochDays().toLong() - createdDate.toEpochDays().toLong()`
    (**cast `.toLong()`** — iOS Native returns Long, JVM Int; see `feedback_kotlin_native_epoch_days`).
  - Future (`createdAt > now`) → `"now"`.
  - Same day (`diff == 0`): totalMinutes `< 1` → `"now"`; `< 60` → `"{m}m"`; else `"{h}h"`.
  - `diff in 1..6` → weekday abbrev of createdAt (`"Mon"`…`"Sun"`).
  - `diff >= 7` → `"{day} {monthAbbrev}"` (e.g. `"27 May"`).
  - Private `dayOfWeekAbbrev(DayOfWeek)` + `monthAbbrev(Month)` — exhaustive `when`, no `else`.
- `data class NotificationSection(val isToday: Boolean, val items: List<Notification>)`
- `fun groupNotificationsByDay(items, nowMillis, tz): List<NotificationSection>`
  - Split into today (`diff == 0`, future counts as today) vs earlier (`diff >= 1`), preserve
    input order, **omit empty sections**, today section first.

## Strings (new — `commonMain/composeResources/values/strings.xml`)

- `notifications_section_today` = "Today", `notifications_section_earlier` = "Earlier"
- `notification_tag_overdue` = "overdue", `notification_tag_due_soon` = "due soon",
  `notification_tag_owes` = "owes %1$s" (arg = "₦18,000")
- **Keep** the existing `notification_overdue` / `notification_due_soon` /
  `notification_to_collect` sentence strings — reused for the row's accessibility
  `contentDescription` (full sentence + relative time), so screen readers still get a clean phrase.

## Accessibility

Each row merges into a single spoken phrase AND stays operable: build the sentence from the
existing `notification_*` strings + relative time, then
`.semantics(mergeDescendants = true) { contentDescription = sentence }` **+**
`.clickable(onClick = …, role = Role.Button)` (NOT `clearAndSetSemantics`, which erases the
click action — see the B post-mortem). Icon `contentDescription = null` (decorative).

## Screen wiring

`NotificationsInboxScreen` gains a `now: Long` param (default supplied by the Root via
`Clock.System.now().toEpochMilliseconds()`, captured once on entry — documented limitation).
The `else` branch computes
`val tz = remember { TimeZone.currentSystemDefault() }` and
`val sections = remember(state.notifications, now, tz) { groupNotificationsByDay(...) }`, then
renders `section header → rows (with inset dividers between rows)` per section. Empty/loading/
error states unchanged. The Root (`NotificationsInboxRoot`) supplies `now`.

## Testing

- **Unit (commonTest):** `notificationRelativeTime` boundaries (now / m / h / weekday / date /
  future-clamp / 6-vs-7-day boundary) + `groupNotificationsByDay` (empty, only-today,
  only-earlier, split, order preserved). Fixed NOW anchor in Lagos tz.
- **detekt** clean (watch MultiLineIfElse → brace if/else; MagicNumber → extract dp/sp consts).
- **iOS compile** gate (`:composeApp:compileKotlinIosSimulatorArm64`).
- **Previews:** the row in light + dark, all four types, read + unread, null-amount TO_COLLECT;
  the screen with a Today + Earlier split (realistic `now`/`createdAt`).
- **Manual smoke (Daniel):** Debug → send digest → open inbox → verify light + **dark** mode
  look matches A (no solid-blue cards), grouping, relative times, tap → order, mark-all-read.

## Out of scope

Model/repo/VM/rules/functions changes; the deferred amount-refresh / retention / deep-linking
items (unchanged from the inbox spec). "due tomorrow" vs "due soon" precision (we render the
type word; `deadline` is available for a later refinement).

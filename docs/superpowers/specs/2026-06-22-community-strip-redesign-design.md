# Community Banner → Slim Strip Redesign — Design Spec

**Date:** 2026-06-22
**Branch:** `feat/whatsapp-community`
**Status:** Approved design, ready for implementation plan
**Supersedes:** the dashboard-banner portion of `2026-06-22-whatsapp-community-design.md`

## Problem

The shipped community banner is a tall, prominent indigo **card rendered above the
greeting** (dashboard section 0). On first load the dashboard opens on "join a
WhatsApp group" instead of the tailor's work, pushing the greeting and the
"action needed" hero down. It reads as the app's headline message and distorts
the layout. (Functionally it works — this is purely a visual/placement change.)

## Decision (locked during brainstorming)

Replace the card with a **slim, single-row outline strip** placed **below the
dashboard hero/focus card**, so the order becomes: greeting → focus/"action
needed" hero → community strip → the rest. Chosen treatment: **C2**.

- **Style:** outline (transparent fill, 1.dp border), not a filled card.
- **Icon:** the **WhatsApp logo glyph in WhatsApp green** (`#25D366`), in a subtle
  green-tinted rounded tile.
- **Title:** "Join our WhatsApp community".
- **Subtitle (short, one line):** "Updates, tips & other Nigerian tailors".
- **Affordances:** a trailing "Join →" hint + a dismiss **✕**.
- **Placement:** directly **below the `IllustratedFocusCard`** (the dynamic
  "action needed"/state hero), above the onboarding/NBA/weekly-goal sections.

## Scope: what changes vs. stays

**Changes (UI only):**
- The dashboard community surface becomes a slim strip, relocated below the hero.
- The `DashboardBannerPager` carousel is **removed** — it existed only to let the
  welcome-ending banner and the community banner share the top slot. With the
  community strip relocated, the top slot holds only the welcome banner, so the
  pager serves no purpose. The top reverts to rendering the welcome banner
  directly (its original behavior).

**Unchanged (no behavior change):**
- All state/logic: `DashboardState.showCommunityBanner` / `communityUrl`, the
  `DashboardAction.OnJoinCommunity` / `OnDismissCommunityBanner` actions, the
  `DashboardEvent.OpenCommunityLink`, `observeCommunity()`, the reactive
  `CommunityBannerDismissal` provider, fire-and-forget `CommunityJoinTracker`,
  the Settings row, the Firestore config layer, and the debug reset entry.
- The welcome-ending banner's appearance and behavior.

## Components

### New: `CommunityStrip` composable
Replaces `CommunityBanner` (the old card). File:
`feature/dashboard/presentation/components/CommunityStrip.kt` (rename/replace
`CommunityBanner.kt`).

```kotlin
@Composable
fun CommunityStrip(
    onJoin: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
)
```

Layout — a bordered `Surface` (transparent container, `outlineVariant` border,
`RoundedCornerShape(DesignTokens.radiusMd)`), containing a `Row` that is
**clickable → `onJoin`**:
- **Leading:** WhatsApp glyph (green `#25D366`) inside a ~30dp rounded tile with a
  green-at-~12%-alpha background. `contentDescription` = WhatsApp.
- **Middle (weight 1f):** Column —
  - Title: `Res.string.community_banner_title` ("Join our WhatsApp community"),
    `bodyMedium`/`labelLarge`, `SemiBold`, `onSurface`.
  - Subtitle: `Res.string.community_strip_subtitle`
    ("Updates, tips & other Nigerian tailors"), `bodySmall`, `onSurfaceVariant`,
    single line (`maxLines = 1`, ellipsis).
- **Trailing:** a small column — dismiss **✕** `IconButton` (top, `onDismiss`,
  `contentDescription` = `community_banner_dismiss_cd`) + a "Join →" caption
  (`Res.string.community_strip_join` = "Join", with a trailing arrow icon or "→"),
  `labelSmall`, indigo (`primary`). The "Join →" is a visual affordance for the
  row's tap action, not a separate control.

Light **and** dark `@Preview` (the strip uses theme colors + the green glyph,
which reads on both; the green tile alpha works on both backgrounds).

### Removed: `DashboardBannerPager`
Delete `feature/dashboard/presentation/components/DashboardBannerPager.kt` and its
usage. It is unused once the community surface leaves the top slot.

### New asset: WhatsApp glyph
Add a vector drawable `composeResources/drawable/ic_whatsapp_glyph.xml` (the
WhatsApp logo path), rendered with a green tint (`#25D366`). Referenced via
`Res.drawable.ic_whatsapp_glyph`. (A single-color logo path; tint applied in the
composable so the same asset works in light/dark.)

## DashboardScreen wiring

In `DashboardContent` (`DashboardScreen.kt`):

1. **Top slot (section 0):** replace the `buildList` + `DashboardBannerPager(...)`
   block with the original direct render:
   ```kotlin
   if (state.showWelcomeBanner && state.welcomeBannerDaysLeft != null) {
       WelcomeEndingBanner(
           daysLeft = state.welcomeBannerDaysLeft,
           onSeeUpgrade = { onAction(DashboardAction.OpenUpgrade) },
       )
   }
   ```
2. **New strip placement:** immediately **after** the `IllustratedFocusCard` block
   (after line ~756), before the BrandNew onboarding card:
   ```kotlin
   if (state.showCommunityBanner) {
       CommunityStrip(
           onJoin = { onAction(DashboardAction.OnJoinCommunity) },
           onDismiss = { onAction(DashboardAction.OnDismissCommunityBanner) },
       )
   }
   ```
   (The `Column`'s `verticalArrangement = spacedBy(space4)` provides the spacing;
   no manual spacers.)

Imports: drop `DashboardBannerPager` and `CommunityBanner`; add `CommunityStrip`.
Keep `WelcomeEndingBanner`.

## Strings

- **Keep:** `community_banner_title`, `community_banner_dismiss_cd`,
  `community_banner_icon_cd` (reused as the glyph contentDescription), and all
  Settings-row strings.
- **Add:**
  - `community_strip_subtitle` = `Updates, tips &amp; other Nigerian tailors`
  - `community_strip_join` = `Join`
- **Remove (now unused — the card is gone):** `community_banner_body`,
  `community_banner_cta`.

(`&amp;` / `&apos;`, never backslash escapes.)

## Error handling

Unchanged — the strip only renders when `showCommunityBanner` is true (remote
config enabled + valid `https://chat.whatsapp.com/` URL + not dismissed). Join
opens via the existing guarded `LocalUriHandler` path; dismiss/join go through the
reactive provider.

## Testing

- **No new ViewModel logic** → existing `DashboardCommunityBannerTest` stays valid
  (it asserts state + actions, not the composable). Verify it still compiles/passes
  after the rename.
- Remove any reference to `DashboardBannerPager`/`CommunityBanner` from tests/
  previews (the pager had no test).
- `CommunityStrip` gets light + dark `@Preview` as its visual verification.
- Gates: `:composeApp:testDebugUnitTest`, `detekt`, `compileKotlinIosSimulatorArm64`,
  `assembleDebug`.

## QA smoke test (manual)

1. With `config/app` enabled, open the dashboard → greeting and the "action
   needed" hero appear first; the **slim community strip** sits directly below the
   hero (outline, green WhatsApp glyph, title + "Updates, tips & other Nigerian
   tailors", "Join →", ✕).
2. Tap the strip (or "Join →") → WhatsApp opens to the invite; strip disappears.
3. Tap ✕ → strip disappears; debug "Reset community banner" → it returns.
4. Welcome-ending banner (if active) still renders at the top, unchanged.
5. Repeat on iOS.

## Out of scope (YAGNI)

- Swipeable multi-banner carousel (removed; re-add only if multiple top banners
  ever need to coexist again).
- Animated entrance/exit for the strip.
- Per-dashboard-state hiding of the strip (it shows in all states when enabled,
  consistent with the original design).

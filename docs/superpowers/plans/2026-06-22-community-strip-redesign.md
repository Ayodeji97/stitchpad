# Community Strip Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the prominent top-of-dashboard community card with a slim outline strip (WhatsApp-green glyph, title + short subtitle, "Join →", dismiss ✕) placed below the dashboard hero/focus card.

**Architecture:** Pure UI change. A new `CommunityStrip` composable replaces the `CommunityBanner` card; the `DashboardBannerPager` carousel is deleted (the top reverts to rendering only the welcome-ending banner) and the strip is rendered directly under the `IllustratedFocusCard`. No ViewModel/state/action/event/DI changes — `showCommunityBanner`, `OnJoinCommunity`, `OnDismissCommunityBanner`, the reactive `CommunityBannerDismissal` provider, and `CommunityJoinTracker` are untouched.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform (Material3), compose.resources (strings + vector drawable), Koin (unchanged).

## Global Constraints

- Shared code in `composeApp/src/commonMain`. Run `:composeApp:compileKotlinIosSimulatorArm64` before declaring done.
- No new ViewModel logic — existing MVI state/actions/events stay exactly as they are.
- Strings via `composeResources/values/strings.xml`; `&amp;` for `&`, `&apos;` for `'` — never backslash escapes.
- The strip composable has BOTH light AND dark `@Preview`.
- Use `DesignTokens` (`space*`, `radius*`) for spacing/shape, not magic dp (except small icon sizes, consistent with the existing `CommunityBanner`).
- WhatsApp green is exactly `#25D366`. Indigo "Join" affordance uses `MaterialTheme.colorScheme.primary`.
- Placement: the strip renders directly **after** the `IllustratedFocusCard` block in `DashboardContent`, gated on `state.showCommunityBanner`.
- `detekt` must stay clean (run `detektFormat` if you add/reorder imports).

---

### Task 1: Strings + WhatsApp glyph asset

**Files:**
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml` (community block, lines ~1286–1290)
- Create: `composeApp/src/commonMain/composeResources/drawable/ic_whatsapp_glyph.xml`

**Interfaces:**
- Produces: `Res.string.community_strip_subtitle`, `Res.string.community_strip_join`, `Res.drawable.ic_whatsapp_glyph`. Keeps `community_banner_title`, `community_banner_dismiss_cd`, `community_banner_icon_cd` (value changed to "WhatsApp"). **Leaves `community_banner_body`/`community_banner_cta` in place** — they are still used by `CommunityBanner.kt` and are removed in Task 2 when that file is deleted (removing them now breaks the build).

- [ ] **Step 1: Update the community strings**

In `strings.xml`, ADD the two strip strings and change the icon contentDescription value. Do NOT remove `community_banner_body`/`community_banner_cta` (still used until Task 2). Find:
```xml
    <string name="community_banner_title">Join our WhatsApp community</string>
    <string name="community_banner_body">Get product updates, tips &amp; early news. Connect with other Nigerian tailors on WhatsApp.</string>
    <string name="community_banner_cta">Join community</string>
    <string name="community_banner_dismiss_cd">Dismiss</string>
    <string name="community_banner_icon_cd">Community</string>
```
Replace with:
```xml
    <string name="community_banner_title">Join our WhatsApp community</string>
    <string name="community_strip_subtitle">Updates, tips &amp; other Nigerian tailors</string>
    <string name="community_strip_join">Join</string>
    <string name="community_banner_body">Get product updates, tips &amp; early news. Connect with other Nigerian tailors on WhatsApp.</string>
    <string name="community_banner_cta">Join community</string>
    <string name="community_banner_dismiss_cd">Dismiss</string>
    <string name="community_banner_icon_cd">WhatsApp</string>
```
(Adds the two strip strings; changes `community_banner_icon_cd` value to "WhatsApp"; keeps body/cta for now.)

- [ ] **Step 2: Create the WhatsApp glyph vector drawable**

`ic_whatsapp_glyph.xml` (fill color is black; the composable tints it green via `Icon(tint = …)`):
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF000000"
        android:pathData="M17.472,14.382c-0.297,-0.149 -1.758,-0.867 -2.03,-0.967 -0.273,-0.099 -0.471,-0.148 -0.67,0.15 -0.197,0.297 -0.767,0.966 -0.94,1.164 -0.173,0.199 -0.347,0.223 -0.644,0.075 -0.297,-0.15 -1.255,-0.463 -2.39,-1.475 -0.883,-0.788 -1.48,-1.761 -1.653,-2.059 -0.173,-0.297 -0.018,-0.458 0.13,-0.606 0.134,-0.133 0.298,-0.347 0.446,-0.52 0.149,-0.174 0.198,-0.298 0.298,-0.497 0.099,-0.198 0.05,-0.371 -0.025,-0.52 -0.075,-0.149 -0.669,-1.612 -0.916,-2.207 -0.242,-0.579 -0.487,-0.5 -0.669,-0.51 -0.173,-0.008 -0.371,-0.01 -0.57,-0.01 -0.198,0 -0.52,0.074 -0.792,0.372 -0.272,0.297 -1.04,1.016 -1.04,2.479 0,1.462 1.065,2.875 1.213,3.074 0.149,0.198 2.096,3.2 5.077,4.487 0.709,0.306 1.262,0.489 1.694,0.625 0.712,0.227 1.36,0.195 1.871,0.118 0.571,-0.085 1.758,-0.719 2.006,-1.413 0.248,-0.694 0.248,-1.289 0.173,-1.413 -0.074,-0.124 -0.272,-0.198 -0.57,-0.347M12.05,21.785h-0.004c-1.767,0 -3.5,-0.475 -5.031,-1.378l-0.361,-0.214 -3.741,0.982 0.998,-3.648 -0.235,-0.374c-0.99,-1.576 -1.516,-3.391 -1.51,-5.26 0.001,-5.45 4.436,-9.884 9.888,-9.884 2.64,0 5.122,1.03 6.988,2.898 1.866,1.869 2.893,4.352 2.893,6.994 -0.003,5.45 -4.437,9.884 -9.885,9.884M20.463,3.488C18.241,1.245 15.247,0 12.05,0 5.495,0 0.16,5.335 0.157,11.892c0,2.096 0.547,4.142 1.588,5.945L0.057,24l6.305,-1.654c1.733,0.945 3.683,1.444 5.667,1.445h0.005c6.554,0 11.89,-5.335 11.893,-11.893 0.001,-3.181 -1.236,-6.171 -3.483,-8.413"/>
</vector>
```

- [ ] **Step 3: Generate resource accessors**

Run: `./gradlew :composeApp:generateComposeResClass`
Expected: BUILD SUCCESSFUL — `Res.string.community_strip_subtitle`, `Res.string.community_strip_join`, `Res.drawable.ic_whatsapp_glyph` now exist; `community_banner_body`/`community_banner_cta` no longer generated.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/composeResources/values/strings.xml composeApp/src/commonMain/composeResources/drawable/ic_whatsapp_glyph.xml
git commit -m "feat(community): strip strings + WhatsApp glyph drawable"
```

---

### Task 2: `CommunityStrip` composable (replaces `CommunityBanner`)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/components/CommunityStrip.kt`
- Delete: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/components/CommunityBanner.kt`

**Interfaces:**
- Consumes: `Res.string.community_banner_title`, `community_strip_subtitle`, `community_strip_join`, `community_banner_dismiss_cd`, `community_banner_icon_cd`, `Res.drawable.ic_whatsapp_glyph` (Task 1); `DesignTokens`, `StitchPadTheme`.
- Produces: `@Composable fun CommunityStrip(onJoin: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier)`.

- [ ] **Step 1: Create `CommunityStrip.kt`**

```kotlin
package com.danzucker.stitchpad.feature.dashboard.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.community_banner_dismiss_cd
import stitchpad.composeapp.generated.resources.community_banner_icon_cd
import stitchpad.composeapp.generated.resources.community_banner_title
import stitchpad.composeapp.generated.resources.community_strip_join
import stitchpad.composeapp.generated.resources.community_strip_subtitle
import stitchpad.composeapp.generated.resources.ic_whatsapp_glyph

private val WhatsAppGreen = Color(0xFF25D366)

/**
 * Slim, low-key dashboard invite to the WhatsApp community. Outline (no filled
 * card), placed below the hero focus card so it never competes with the
 * tailor's work. The whole row is the join target; the trailing ✕ dismisses.
 */
@Composable
fun CommunityStrip(
    onJoin: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Transparent,
        shape = RoundedCornerShape(DesignTokens.radiusMd),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onJoin)
                .padding(horizontal = DesignTokens.space3, vertical = DesignTokens.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(DesignTokens.radiusSm))
                    .background(WhatsAppGreen.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_whatsapp_glyph),
                    contentDescription = stringResource(Res.string.community_banner_icon_cd),
                    tint = WhatsAppGreen,
                    modifier = Modifier.size(17.dp),
                )
            }
            Spacer(Modifier.width(DesignTokens.space3))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.community_banner_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(Res.string.community_strip_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(DesignTokens.space2))
            Column(horizontalAlignment = Alignment.End) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(Res.string.community_banner_dismiss_cd),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(Modifier.size(DesignTokens.space1))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(Res.string.community_strip_join),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun CommunityStripPreviewLight() {
    StitchPadTheme(darkTheme = false) {
        CommunityStrip(onJoin = {}, onDismiss = {})
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun CommunityStripPreviewDark() {
    StitchPadTheme(darkTheme = true) {
        CommunityStrip(onJoin = {}, onDismiss = {})
    }
}
```
> Verify `Icons.AutoMirrored.Filled.ArrowForward` resolves (it's in the bundled material-icons; the project already uses `Icons.AutoMirrored` elsewhere). `Surface(border = …)` is a standard Material3 param. If `Color.Transparent` + `Surface` shows an unwanted elevation shadow, leave `tonalElevation`/`shadowElevation` at their defaults (0) — they already are.

- [ ] **Step 2: Delete the old card + its now-unused strings**

```bash
git rm composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/components/CommunityBanner.kt
```
(Its only call site is `DashboardScreen.kt`, rewired in Task 3. Nothing else references it — confirmed: no test imports `CommunityBanner`.)

With `CommunityBanner.kt` gone, `community_banner_body` and `community_banner_cta` are no longer used — remove both lines from `composeApp/src/commonMain/composeResources/values/strings.xml`:
```xml
    <string name="community_banner_body">Get product updates, tips &amp; early news. Connect with other Nigerian tailors on WhatsApp.</string>
    <string name="community_banner_cta">Join community</string>
```
Then regenerate: `./gradlew :composeApp:generateComposeResClass` (expected: SUCCESSFUL; those two accessors gone).

- [ ] **Step 3: Build (will fail at DashboardScreen until Task 3 — verify only the strip compiles)**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: the ONLY errors are unresolved `CommunityBanner` / `DashboardBannerPager` references in `DashboardScreen.kt` (fixed in Task 3). `CommunityStrip.kt` itself must have no errors. If `CommunityStrip.kt` reports errors, fix them now.
> Because Task 2 and Task 3 are tightly coupled (deleting the old composable breaks the screen until rewired), the implementer may do Task 2 + Task 3 in one sitting and run the green build at the end of Task 3. Commit them separately as below.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/components/CommunityStrip.kt
git commit -m "feat(community): CommunityStrip slim outline composable (replaces card)"
```

---

### Task 3: Dashboard wiring — remove pager, place strip below hero

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/DashboardScreen.kt` (imports ~72–74; banner block ~706–730; focus-card block ~744–756)
- Delete: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/components/DashboardBannerPager.kt`

**Interfaces:**
- Consumes: `CommunityStrip` (Task 2); existing `WelcomeEndingBanner`, `state.showWelcomeBanner`, `state.welcomeBannerDaysLeft`, `state.showCommunityBanner`, `DashboardAction.OpenUpgrade`, `OnJoinCommunity`, `OnDismissCommunityBanner`.

- [ ] **Step 1: Replace the top banner-carousel block with welcome-only**

In `DashboardScreen.kt`, replace this block (the `// 0. Banner carousel …` comment through `DashboardBannerPager(banners = banners)`, currently lines ~708–730):
```kotlin
        // 0. Banner carousel — welcome-ending and community banners rendered
        //    in a swipeable pager so they coexist without competing for space.
        //    Placed above everything else so high-urgency messages get
        //    immediate attention on every dashboard load.
        val banners = buildList<@Composable () -> Unit> {
            if (state.showWelcomeBanner && state.welcomeBannerDaysLeft != null) {
                add {
                    WelcomeEndingBanner(
                        daysLeft = state.welcomeBannerDaysLeft,
                        onSeeUpgrade = { onAction(DashboardAction.OpenUpgrade) },
                    )
                }
            }
            if (state.showCommunityBanner) {
                add {
                    CommunityBanner(
                        onJoin = { onAction(DashboardAction.OnJoinCommunity) },
                        onDismiss = { onAction(DashboardAction.OnDismissCommunityBanner) },
                    )
                }
            }
        }
        DashboardBannerPager(banners = banners)
```
with the original direct welcome-banner render:
```kotlin
        // 0. Welcome-ending banner (top). The community invite is a separate
        //    slim strip rendered below the focus card, not here — so the
        //    dashboard never opens on the community message.
        if (state.showWelcomeBanner && state.welcomeBannerDaysLeft != null) {
            WelcomeEndingBanner(
                daysLeft = state.welcomeBannerDaysLeft,
                onSeeUpgrade = { onAction(DashboardAction.OpenUpgrade) },
            )
        }
```

- [ ] **Step 2: Render the strip below the focus card**

Immediately after the `IllustratedFocusCard` `if (focusTitle != null) { … }` block (currently ending ~line 756), before the `// BrandNew …` comment, insert:
```kotlin
        // Community invite — slim strip, directly below the hero focus card so
        // the tailor's work leads. Shown only when remote config enables it
        // with a valid invite and the user hasn't dismissed/joined.
        if (state.showCommunityBanner) {
            CommunityStrip(
                onJoin = { onAction(DashboardAction.OnJoinCommunity) },
                onDismiss = { onAction(DashboardAction.OnDismissCommunityBanner) },
            )
        }
```
(The enclosing `Column` uses `verticalArrangement = Arrangement.spacedBy(DesignTokens.space4)`, so spacing is automatic — no Spacers.)

- [ ] **Step 3: Fix imports**

In `DashboardScreen.kt` imports, remove:
```kotlin
import com.danzucker.stitchpad.feature.dashboard.presentation.components.CommunityBanner
import com.danzucker.stitchpad.feature.dashboard.presentation.components.DashboardBannerPager
```
and add:
```kotlin
import com.danzucker.stitchpad.feature.dashboard.presentation.components.CommunityStrip
```
(Keep the `WelcomeEndingBanner` import.)

- [ ] **Step 4: Delete the now-unused pager**

```bash
git rm composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/components/DashboardBannerPager.kt
```
(Confirmed: `DashboardBannerPager` is referenced only in `DashboardScreen.kt`, now rewired. No test references it.)

- [ ] **Step 5: Build + run the dashboard tests (unchanged logic must still pass)**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid && ./gradlew :composeApp:testDebugUnitTest --tests "*Dashboard*"`
Expected: BUILD SUCCESSFUL; all Dashboard tests PASS (`DashboardCommunityBannerTest` asserts state/actions, which are unchanged — it must still be green).

- [ ] **Step 6: Run detekt**

Run: `./gradlew detektFormat && ./gradlew detekt`
Expected: BUILD SUCCESSFUL (clean).

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/DashboardScreen.kt
git commit -m "feat(community): render slim strip below hero, remove banner carousel"
```

---

### Task 4: Full verification

**Files:** none (verification only).

- [ ] **Step 1: Full unit-test suite**

Run: `./gradlew :composeApp:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, 0 failures.

- [ ] **Step 2: iOS compile (REQUIRED KMP gate)**

Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL. (Catches any JVM-only API in the new composable and the drawable/resource wiring.)

- [ ] **Step 3: Android assemble**

Run: `./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Manual smoke test (Daniel — QA, `config/app` already enabled)**

1. Open the dashboard → greeting + "action needed" hero appear first; the **slim community strip** sits directly below the hero — outline border, green WhatsApp glyph, "Join our WhatsApp community" + "Updates, tips & other Nigerian tailors", "Join →", ✕. No big card above the greeting.
2. Tap the strip body (or "Join →") → WhatsApp opens to the invite; strip disappears.
3. Debug menu → "Reset community banner" → strip returns below the hero.
4. Tap ✕ → strip disappears (persists across relaunch).
5. If the welcome-ending banner is active, it still renders at the very top, unchanged.
6. Repeat on iOS (clean Xcode build; real device for the WhatsApp open).

- [ ] **Step 5: Final review + push**

Push the branch (pre-push runs `codex review`; Cursor Bugbot reviews the PR). Address any findings, then confirm the PR is green.

---

## Notes for the implementer

- This is a **UI-only** change. Do NOT touch `DashboardViewModel`, `DashboardState`, `DashboardAction`, the `CommunityBannerDismissal` provider, `CommunityJoinTracker`, the Settings row, the config layer, or the debug entry — they all stay exactly as they are.
- Tasks 2 and 3 are coupled (deleting `CommunityBanner` breaks `DashboardScreen` until rewired) — implement them together, get the green build at the end of Task 3, but keep the commits separate.
- The Settings row keeps its own strings (`settings_row_community*`) — those are untouched.

# Rebrand PR-B Preparation Checklist

Captured during PR-A smoke testing on `feature/rebrand-tokens` 2026-05-14/15. Each entry below is a screen-level cleanup that PR-B (the screen migration sweep) needs to address. PR-A intentionally avoided touching feature code; the 118 deprecated `DesignTokens.primary*` call sites still compile via aliases pointing to indigo, but they emit deprecation warnings that PR-B silences.

## What PR-A already cleaned up

Three rounds of bundled contrast fixes landed in PR-A and you do NOT need to repeat them in PR-B:

- Commit `1822d80` — replaced hardcoded `contentColor = DesignTokens.neutral{800,900}` / `Color(0xFF181615)` with `MaterialTheme.colorScheme.onPrimary` across 13 feature files (Sign In, Sign Up, Forgot Password, Workshop, Splash, Reports tab pill, Custom date picker, Custom range picker, Edit Profile, Change Email, Change Password, Delete Account, PlanCard).
- Commit `0d1cc96` — forced white wordmark + tagline on the auth fabric-photo hero.
- Commit `6afb67a` — fixed the dashboard top-right `UserAvatar` to use `MaterialTheme.colorScheme.primaryContainer` / `onPrimaryContainer` instead of a dark gradient that disappeared on the dark surface.

So the screens above are already migrated for *those specific text-color sites*. PR-B picks up everywhere else.

## Migration work for PR-B

### Mechanical: deprecation cleanup

The 118 `DesignTokens.primary*` callsites are the bulk of the work. Sweep pattern:

```bash
grep -rn "DesignTokens.primary" composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/ composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/
```

For each match:

| Old reference | Replace with |
|---|---|
| `DesignTokens.primary500` (container/fill) | `MaterialTheme.colorScheme.primary` |
| `DesignTokens.primary700`, `primary600`, `primary800` (deeper variants for CTAs) | `MaterialTheme.colorScheme.primary` if it's the brand color; usually nothing changes since `primary` already resolves correctly |
| `DesignTokens.primary50`, `primary100` (subtle tint backgrounds, e.g., section pills, hero card tints) | `MaterialTheme.colorScheme.primaryContainer` |
| `DesignTokens.primary300`, `primary400` (mid-brand uses) | `MaterialTheme.colorScheme.primary` in most cases; verify by reading context |
| `DesignTokens.primary900` (avatar backgrounds, deep dark uses) | Depends on context — often `MaterialTheme.colorScheme.primaryContainer` for chips/avatars. The dashboard UserAvatar fix in commit `6afb67a` is the reference pattern. |
| `DesignTokens.primaryButtonBorder` | Either `MaterialTheme.colorScheme.primary` (when it's the border on a brand button) or remove the override entirely and rely on Material's default outlined-button styling. |

After all callsites are migrated, **delete the 11 `@Deprecated` aliases from `DesignTokens.kt`**. CI build failures will catch any miss.

### Heritage saffron (`Color(0xFFE8A800)` literals)

Anywhere in the codebase that hardcodes the saffron hex value:

```bash
grep -rn "0xFFE8A800\|Color(0xFFE8A800" composeApp/src/commonMain/kotlin/
```

Replace with `LocalStitchPadColors.current.heritageAccent`. Verify each usage is a genuine *heritage* moment (PRO badge, ★ mark, Verified Tailor chip, achievement burst). If it's not — for example, if saffron was being used as a regular accent color on a feature CTA — that's a bug worth flagging and likely changing to indigo / sienna semantically.

### Hardcoded warm-neutral text colors

The PR-A fix only handled hardcoded text colors on **primary-fill** containers (where dark text used to read because saffron was light). There may still be hardcoded `DesignTokens.neutral{700,800,900}` text colors elsewhere that should route through `MaterialTheme.colorScheme.onBackground` / `onSurface` / `onSurfaceVariant`. Sweep:

```bash
grep -rn "color = DesignTokens.neutral" composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/
```

Audit each — most should migrate; some intentionally hardcoded warm-tone text against a specific photo / illustration background can stay (the auth hero pattern is the example — fixed in `0d1cc96` by going to `Color.White` directly).

### Bottom-nav selected-state

Verified working on Android smoke: the selected tab shows a small white circle bg with indigo text + icon. No fix needed. The state uses Material3's `NavigationBarItemDefaults` which picks up the new scheme automatically.

### Font family references outside `ui/theme/`

Verified clean: `grep -rn "FontFamily\|fontFamily =" composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/` returns no direct `PlusJakartaSansFamily` or `FrauncesFamily` calls inside feature code. Body / display fonts are picked up via `MaterialTheme.typography.*`. JetBrains Mono is referenced via `JetBrainsMonoFamily()` in a few measurement / mono-text spots — those are intentional and stay.

### Other contrast issues to verify after PR-B's mechanical sweep

These weren't fully audited during PR-A smoke testing — confirm during PR-B QA:

- [ ] Customer-list avatar chips (uses `CustomerAvatar` composable; consumes `DesignTokens.avatarColors[0]` which still has the saffron-era `darkBg` hex `#4F3800`). If the first-pair "default" avatar reads as a hole on dark mode, swap the `darkBg` to `#2D3B6B` or migrate to use `MaterialTheme.colorScheme.primaryContainer`.
- [ ] Order-detail hero card / order-detail avatar composables (`DashedAvatar`, `AvatarWithDot`, `AvatarBadge`) — same as above; verify in dark mode.
- [ ] Settings → Pro / Plan card — should use heritage saffron treatment per the brand rules; PR-A's commit `1822d80` updated the button colors but check the card itself isn't still saffron-styled in a way that breaks the "saffron is RARE" rule.
- [ ] Reports tab background tint on selected pill — the active tab fill now uses indigo. Verify the icons inside the pill (if any) have correct contrast.

## Out-of-scope-for-PR-B references

These are tracked elsewhere; do NOT bundle into PR-B:

- **Illustration regeneration.** All 13 image assets in `composeApp/composeResources/drawable/` are still saffron-era. Audit + regeneration prompts in `docs/rebrand-illustration-audit.md`. This is **PR-C** territory.
- **Terminology shifts** (Customers / Orders / Workshop, taglines, copy). Tracked in memory `[[project-rebrand-terminology]]`. Separate PR — can ship before or after PR-B.
- **Logo redesign** (no scissors; favor notebook + measuring-tape motif). Tracked in memory `[[project-logo-direction]]`. Separate Figma-led effort.

## Verification expectations for PR-B

- Grep verifies zero `DesignTokens.primary*` references in `feature/**` and `ui/components/**` (only the theme files themselves should reference primitives).
- All 11 `@Deprecated` aliases deleted from `DesignTokens.kt`.
- `./gradlew detekt` clean.
- `./gradlew :composeApp:assembleDebug` + `./gradlew :composeApp:compileKotlinIosSimulatorArm64` green.
- Manual smoke on ~10 representative screens (auth flow, dashboard, customer list, customer detail, order list, order form, measurement form, settings home + each settings sub-screen) on both Android + iOS in light + dark.
- No deprecation warnings emitted from feature code during build.

When all of the above pass, the alias deletion commit is the natural end of PR-B.

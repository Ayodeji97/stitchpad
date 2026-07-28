# Settings IA Restructure — Frequency Hybrid (Option A)

**Date:** 2026-07-28
**Status:** Design approved, spec under review
**Feature branch (suggested):** `feat/settings-ia-hub`
**Mockup:** `Preview/settings-ia-restructure.html`

## Problem

The Settings landing has grown to 6 grouped sections and ~17 rows across ~2.5
screens of scroll, and it is about to grow further (Staff / Team is a 9-slice
feature in progress; notifications are expanding to in-app + push). The single
flat scroll no longer scans at a glance and has no clean home for what is coming.

## Goal

Shorten the Settings landing to something takeable-in at a glance while keeping
frequently-flipped preferences one tap away, and create category slots that
future settings (Team, richer notifications) drop into without another redesign.

## Decision (approved with the user)

**Option A — Frequency hybrid.** Preferences stay inline on the landing;
low-frequency management collapses into a small set of category rows that drill
down to sub-screens.

Explicitly decided during brainstorming:

- **Notification toggles stay inline** on the landing (daily summary email +
  daily push reminder). No Notifications sub-screen yet — revisit when
  notification *types* multiply.
- **Invite + referral nest together** under a single "Invite & rewards" row.
- **Ship behind a remote flag, not an A/B test.** Settings has no clean success
  metric and too little traffic to power an experiment. Use the existing
  `AppConfig` remote-config layer for a gradual, reversible rollout.

## New landing structure

Top to bottom, on the Settings landing:

1. **Profile hero card** — unchanged. Still opens Edit Profile.
2. **Plan card** — unchanged.
3. **PREFERENCES** (section card, inline — unchanged from today):
   - Measurement units (value)
   - Appearance (value)
   - Receipt image style (value)
   - Daily summary email (toggle)
   - Daily push reminder (toggle, when `pushReminderSupported`)
4. **MANAGE** (new section card, four drill-down rows):
   - **Account & security** › — subtitle "Email, password, sign-in method"
   - **Invite & rewards** › — subtitle "Invite tailors, enter a referral code"
   - **Help & support** › — subtitle "Tutorials, contact, community"
   - **Legal & about** › — subtitle "Privacy, terms, founder's note"
5. **Delete account** — pinned, standalone danger card (unchanged position).
6. **Debug menu** — pinned below, debug builds only (unchanged).

Landing drops from ~17 rows to ~9. The **MANAGE** block is where a future
**Team** row lands.

## Sub-screens

Each is a new route with a standard back-arrow top bar. Contents are the exact
rows that exist today, relocated — no behavior changes to the rows themselves.

### 1. Account & security
- Sign-in method (non-clickable info row)
- Email › (when `showChangeEmailRow`)
- Change password › (when `showChangePasswordRow`)
- Sign out (renders the existing sign-out confirmation dialog here)

### 2. Invite & rewards
- Invite a tailor ›
- Have a referral code? ›
- (Gift share + redeem rows stay behind `GIFTING_ENABLED`, same as today)

### 3. Help & support
- Help & tutorials ›
- Contact us on WhatsApp ›
- Join our community › (when `showCommunityRow`)

### 4. Legal & about
- Privacy Policy (external)
- Terms of Service (external)
- About your plan › (founder's note)

## Architecture

Follows the existing Root/Screen + MVI pattern. Key choice: **reuse the single
`SettingsViewModel` as the source of truth**, shared across the landing and all
four sub-screens — no new ViewModels, one data load.

### State sharing
Introduce a nested settings navigation sub-graph so the four sub-screens share
the `SettingsViewModel` instance scoped to the graph's parent back-stack entry
(`koinViewModel(viewModelStoreOwner = parentEntry)`). This keeps a single source
of truth for toggles, the sign-out dialog, masked identifiers, and remote-flag
state, and avoids re-subscribing to settings flows on every drill-down.

If the nested-graph wiring proves noisy against the current flat
`composable<Route>` list in `MainScreen`, the fallback is an independent
`koinViewModel<SettingsViewModel>()` per sub-screen Root — functionally correct
(rows read/write the same underlying repositories/flows), at the cost of a
redundant flow subscription per screen. Prefer the shared graph.

### Screens (stateless, previewable)
- `SettingsScreen` (landing) — trimmed to Preferences + Manage + Delete + Debug.
- `SettingsAccountScreen`
- `SettingsInviteRewardsScreen`
- `SettingsHelpSupportScreen`
- `SettingsLegalAboutScreen`

Each new Screen takes the slice of `SettingsState` it needs and the shared
`onAction: (SettingsAction) -> Unit`. Each has its own `@Preview`.

### Roots (thin)
- Landing `SettingsRoot` keeps ownership of the full `ObserveAsEvents` wiring.
- Each sub-screen Root obtains the shared VM and renders its Screen. Event
  handling (WhatsApp/community/privacy/terms URL opening, snackbars) is shared —
  extract the current `ObserveAsEvents` block from `SettingsRoot` into a reusable
  `rememberSettingsEventHandler(...)`/composable so it is not copy-pasted four
  times.

### Actions / Events / Routes to add
- Actions: `OnAccountSecurityClick`, `OnInviteRewardsClick`, `OnHelpSupportClick`,
  `OnLegalAboutClick`.
- Events: `NavigateToAccountSecurity`, `NavigateToInviteRewards`,
  `NavigateToHelpSupport`, `NavigateToLegalAbout`.
- Routes (`Routes.kt`): `SettingsAccountRoute`, `SettingsInviteRewardsRoute`,
  `SettingsHelpSupportRoute`, `SettingsLegalAboutRoute`.
- Wire the four `composable<...>` entries + callbacks in `MainScreen`.

All existing per-row actions (`OnEmailRowClick`, `OnChangePasswordClick`,
`OnReferralCodeClick`, `OnInviteClick`, `OnContactClick`, `OnCommunityClick`,
`OnHelpTutorialsClick`, `OnFoundersNoteClick`, `OnPrivacyClick`, `OnTermsClick`,
`OnSignOutRowClick`, toggles) are unchanged — they are now dispatched from the
sub-screens instead of the landing.

## Rollout flag

Add a boolean to `AppConfig` (read from `config/app` Firestore) — e.g.
`settingsHubEnabled: Boolean = false`. Default false, fail-open to false on a
missing/unreadable config (matches `communityEnabled` convention).

`SettingsViewModel` exposes it in `SettingsState` (e.g. `settingsHubEnabled`).
The landing `SettingsScreen` branches:

- flag **off** → render today's flat six-section layout (keep the current
  composition intact behind the branch),
- flag **on** → render the new Preferences + Manage layout.

Rollout: enable in staging / allowlist first, then flip `config/app` for all
users. Instant kill switch by flipping the field back. No app release required to
enable, disable, or revert.

Keep the flag and the old flat layout for one release cycle after full rollout,
then remove the dead branch in a follow-up cleanup PR.

## Strings

New string resources (Manrope-cased, no backslash escapes — use `&apos;` or `’`
per project convention for the founder's-note subtitle):

- Section label: `settings_section_manage` = "Manage"
- Rows + subtitles: `settings_row_account_security` (+ subtitle),
  `settings_row_invite_rewards` (+ subtitle), `settings_row_help_support`
  (+ subtitle), `settings_row_legal_about` (+ subtitle)
- Sub-screen titles: reuse existing section labels where possible
  (`settings_section_account`, `settings_section_support`,
  `settings_section_legal`) or add `settings_account_title` etc. as needed.

No hardcoded strings (project rule).

## Testing

- **ViewModel unit tests** (existing `SettingsViewModelTest` family): the four new
  nav actions each emit their event; `settingsHubEnabled` reflects `AppConfig`;
  fail-open default is false.
- **Previews**: each of the four new Screens + the trimmed landing (light + dark),
  plus a landing preview with the flag off (old layout) to guard the fallback.
- **Manual smoke test** (Daniel is QA), flag ON:
  1. Settings landing shows Preferences inline + four Manage rows; toggles flip
     in place.
  2. Each Manage row opens its sub-screen; back returns to landing.
  3. Account & security: change email / change password / sign out (dialog shows,
     confirm signs out) all work.
  4. Invite & rewards: invite share sheet + referral code entry work.
  5. Help & support: tutorials, WhatsApp contact, community link open.
  6. Legal & about: privacy + terms open externally; founder's note opens.
  7. Delete account + Debug menu (debug build) still reachable on the landing.
  8. Flag OFF → old flat layout renders unchanged.
- iOS compile check before "done" (KMP), plus iOS device pass of the same steps
  (back navigation regressions have bitten Settings before — see
  `fix/ios-settings-back-navigation`).

## Out of scope

- Notifications sub-screen (deferred until types multiply).
- Search in Settings (revisit if the landing grows past ~12 rows again).
- Team / Staff row (lands with that feature; this design just reserves the slot).
- Any redesign of the individual destination screens (Change Email, Change
  Password, Referral, Help & Tutorials, Founder's Note, Edit Profile).

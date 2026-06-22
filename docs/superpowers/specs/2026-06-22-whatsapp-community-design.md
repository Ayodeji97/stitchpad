# WhatsApp Community — Design Spec

**Date:** 2026-06-22
**Branch:** `worktree-feat+whatsapp-community`
**Status:** Approved design, ready for implementation plan

## Goal

Let tailors join StitchPad's official WhatsApp community from inside the app. The
community is where we post product updates, tips, and member-only discounts, and
where tailors connect with each other — a way to keep our users engaged in one
place. The invite link must be changeable without an app release, and we want a
lightweight way to count how many users tap "Join."

## Decisions (locked during brainstorming)

| Question | Decision |
| --- | --- |
| Placement | **Both:** a permanent Settings row **and** a dismissible Dashboard banner |
| Link storage | **Firestore config doc** (`config/app`) — remote-controllable, seeds a future feature-flag layer. *Not* Firebase Remote Config (GitLive KMP SDK doesn't ship it; would need native bridging) |
| Audience | **Everyone** (all tiers) |
| Tracking | **Lightweight Firestore flag** on the user doc (`communityJoinTappedAt`). No analytics SDK — GitLive ships none and bridging is disproportionate. Real analytics is a separate future project |
| Banner behavior | **Hide after Join OR dismiss** — local flag; once joined or dismissed, banner never returns. Settings row stays forever |
| Banner look | **Variant A · Brand Indigo** — matches the existing `WelcomeEndingBanner` tertiary-container treatment |

## Architecture

### New: `core/config` — remote app-config layer

This is the one genuinely new piece of infrastructure. It is intentionally
generic so it can grow into the app's feature-flag system; the community fields
are simply its first occupants.

- **Firestore doc:** `config/app`
  ```json
  { "communityEnabled": true, "communityInviteUrl": "https://chat.whatsapp.com/XXXXXXXX" }
  ```
- **Domain model** `AppConfig` (`core/domain/config` or `core/config/domain`):
  ```kotlin
  data class AppConfig(
      val communityEnabled: Boolean,
      val communityInviteUrl: String?,
  )
  ```
  Default/fallback instance = everything off / null (feature simply hidden).
- **DTO** `AppConfigDto` — `@Serializable`, all fields nullable with defaults.
  Typed read (NOT `data<Map<String, Any?>>()`) to avoid the iOS Native
  `Any?`-serializer crash. Mapper `AppConfigDto.toDomain()` in the data layer.
- **Repository interface** `AppConfigRepository` (domain):
  ```kotlin
  interface AppConfigRepository {
      val config: Flow<AppConfig>          // snapshot-listener backed, hot
  }
  ```
  Implementation `FirestoreAppConfigRepository` (data) reads `config/app` via a
  snapshot listener, maps DTO → domain, emits `AppConfig` default on
  absence/error (never throws to the UI). Firestore's offline cache covers
  offline reads automatically.
- **Koin:** `singleOf(::FirestoreAppConfigRepository) bind AppConfigRepository`
  in the core/config module; module added to the app's module list.

### Settings row

- **Section:** Support (after the existing "Contact" row).
- **State:** `SettingsState` gains `communityUrl: String?` and
  `communityEnabled: Boolean`, hydrated by collecting `AppConfigRepository.config`.
- **Visibility:** row renders only when `communityEnabled && communityUrl != null`.
- **Action/Event:** `SettingsAction.OnCommunityClick` →
  `SettingsEvent.OpenCommunityLink(url)`.
- Always present (no dismiss). The permanent home for late joiners.

### Dashboard banner

- **Composable:** new `CommunityBanner` in
  `feature/dashboard/presentation/components/` (or alongside `WelcomeEndingBanner`
  pattern). Variant A · Brand Indigo: indigo icon tile, "StitchPad Community"
  pill, Fraunces title, body, indigo "Join community" button with WhatsApp glyph,
  and a dismiss **✕** top-right. Must have a `@Preview` (light + dark, per the
  spec-both-color-modes rule).
- **State:** `DashboardState.showCommunityBanner: Boolean`, computed as
  `config.communityEnabled && config.communityInviteUrl != null && !communityBannerDismissed`.
- **Actions:** `DashboardAction.OnJoinCommunity`, `DashboardAction.OnDismissCommunityBanner`.
- **Events:** `DashboardEvent.OpenCommunityLink(url)`.
- **Placement:** top of the scrollable dashboard content, in the same region as
  `WelcomeEndingBanner`. If both could show, the welcome/upgrade banner takes
  priority (community is lower urgency) — show at most one at a time.

### Open mechanism

- `LocalUriHandler.current.openUri(communityInviteUrl)` in the Root composable's
  event handler. A `https://chat.whatsapp.com/<code>` invite opens WhatsApp
  directly — **no** phone normalisation, **no** `wa.me`, so we do not reuse
  `buildWhatsAppUrl`.
- Wrap in `try/catch`; on failure `AppLogger.e(...)` (mirrors existing WhatsApp
  launch handling) and optionally a snackbar.

### Local dismiss flag

- Extend the existing onboarding/local-preferences store
  (`OnboardingPreferencesStore` pattern — SharedPreferences on Android,
  NSUserDefaults on iOS) with `hasDismissedCommunityBanner: Boolean`
  (get + set).
- Set to `true` on **either** dismiss or Join. The Dashboard ViewModel reads it
  on init into `communityBannerDismissed`.

### Tracking (lightweight)

- On Join tap (from **either** surface), **fire-and-forget** write to the user's
  Firestore doc: `users/{uid}.communityJoinTappedAt = <server/now timestamp>`
  (and optionally a monotonic `communityJoinTapCount`).
- Fire-and-forget per the GitLive "set() awaits server ACK" gotcha — never block
  the UI thread or the link-open on this write. Failures are ignored (it is only
  a metric). Counting joiners = a Firestore query / console filter on the field.

## Data flow

```
config/app doc ──(snapshot)──> FirestoreAppConfigRepository ──Flow<AppConfig>──┐
                                                                               ├─> SettingsViewModel ──> SettingsState (row)
                                                                               └─> DashboardViewModel ─> DashboardState (banner)

User taps Join ─> ViewModel:
   1. emit OpenCommunityLink(url)  ─> Root ─> LocalUriHandler.openUri(url)
   2. prefs.setCommunityBannerDismissed(true)   (banner won't return)
   3. fire-and-forget user-doc write communityJoinTappedAt = now

User taps ✕  ─> ViewModel: prefs.setCommunityBannerDismissed(true) ─> banner hides
```

## Error handling

- **Config offline / read error:** repository emits the default `AppConfig`
  (disabled, null url). Both surfaces hide. No broken link is ever shown.
- **`openUri` failure** (WhatsApp not installed / no handler): catch, `AppLogger.e`,
  optional snackbar ("Couldn't open WhatsApp"). The `chat.whatsapp.com` URL also
  works in a browser as a fallback.
- **Tracking write failure:** swallowed; it is a non-critical metric.

## Testing

- **Unit (ViewModel, JUnit5 + Turbine + fakes):**
  - Fake `AppConfigRepository` + fake prefs store.
  - Banner hidden when `communityEnabled == false`.
  - Banner hidden when url is null.
  - Banner hidden when `hasDismissedCommunityBanner == true`.
  - Banner shown when enabled + url present + not dismissed.
  - `OnDismissCommunityBanner` sets the dismiss flag and hides the banner.
  - `OnJoinCommunity` emits `OpenCommunityLink(url)`, sets dismiss flag, triggers
    the tracking write.
  - Settings row visibility mirrors enabled + url-present.
- **Mapper:** `AppConfigDto.toDomain()` — full doc, partial doc (missing fields →
  defaults), empty doc.
- **iOS compile** must pass before "done" (typed DTO + any datetime usage), per
  prior KMP-native gotchas.

## Firestore security rule

Add to `firestore.rules`: `config/app` is **read-only for authenticated users**,
no client writes.

```
match /config/{doc} {
  allow read: if request.auth != null;
  allow write: if false;   // managed from console / admin only
}
```

(Confirm this doesn't conflict with existing top-level matches; scope narrowly to
`config/{doc}` if rules use a catch-all.)

## Debug menu

Per the per-feature debug-menu convention, add a debug-build-only entry:
- "Reset community banner" → clears `hasDismissedCommunityBanner` so the banner
  re-appears for testing.
- (Optional) shows current `AppConfig` values (enabled + url) for quick verification.

## Strings (compose resources)

All copy via string resources; apostrophes as `&apos;` (never `\'`).

- Banner pill: **StitchPad Community**
- Banner title: **Join the tailors&apos; circle**
- Banner body: **Get product updates, tips & member-only discounts. Connect with
  other Nigerian tailors on WhatsApp.**
- Banner button: **Join community**
- Settings row label: **Join our community**
- Snackbar (open failure): **Couldn&apos;t open WhatsApp**

*(Copy is provisional — confirm "member-only discounts" is a promise we want to
make before shipping.)*

## QA smoke test (manual — Daniel)

1. In Firestore console, set `config/app` = `{ communityEnabled: true,
   communityInviteUrl: <real invite> }`.
2. Launch app → Dashboard shows the indigo community banner at the top.
3. Tap ✕ → banner disappears; kill + relaunch → still gone (local flag persists).
4. Debug menu → "Reset community banner" → banner returns.
5. Tap **Join community** → WhatsApp opens to the community invite; banner gone
   afterwards; `users/{uid}.communityJoinTappedAt` is set in Firestore.
6. Settings → Support → "Join our community" row present → tapping opens WhatsApp.
7. Set `communityEnabled: false` in Firestore → relaunch → banner **and** Settings
   row both hidden.
8. Repeat 2–7 on **iOS** (clean Xcode build, real device for the WhatsApp open).

## Out of scope (YAGNI)

- Onboarding/post-signup community prompt (revisit if join numbers are low).
- A full analytics pipeline / Firebase Analytics SDK (separate future project).
- In-app community feed — this just deep-links to WhatsApp.
- Banner re-show-after-N-days logic (we chose permanent hide).

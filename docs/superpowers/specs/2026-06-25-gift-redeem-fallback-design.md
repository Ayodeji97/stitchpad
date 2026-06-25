# Gift Redeem — Fallback paths, web claim, and deliverability

Date: 2026-06-25
Author: Daniel (with Claude)
Status: approved design

## Problem

Day 10 testers could not redeem code-based gifts. Three real failures surfaced:

1. **The in-app redeem screen is orphaned.** `RedeemGiftScreen` ("You've got a
   gift") exists and works, but the **only** caller is the email deep link
   (`MainScreen.kt` → `DeepLinkTarget.CLAIM_GIFT`). There is no Settings row,
   button, or menu that opens it. When the deep link does not fire, the user is
   stranded even though the code is in their email. Confirmed: `RedeemGiftRoute`
   is navigated to only from the deep-link branch in `MainScreen.kt:117-126`.
2. **The deep link is unreliable.** `https://link.getstitchpad.com/claim?code=...`
   is a correctly configured Universal Link / App Link (AASA + assetlinks.json
   serve HTTP 200 on `link.getstitchpad.com` with the right appID and SHA-256s,
   and the app side parses it). But App Links / Universal Links genuinely do not
   fire when tapped inside in-app browsers (WhatsApp, Gmail) or on installs that
   predate verification. Confirmed in QA: works on one iPhone, fails on Android
   and on some other iPhones. There is no recovery when it fails.
3. **The gift email lands in Gmail Promotions/Spam.** The email looks like
   marketing (gifty subject, large image + button, branded), so Gmail files it
   away from Primary. Recipients say "I didn't see anything."

A separate, correct behavior was being mistaken for a bug: the **personal gift
link** flow (`/gift/{token}`) auto-applies the subscription and sends a
notification with **no code** — that is by design and not in scope here.

## Redemption model

The gift code is a **bearer secret**. Two surfaces redeem it, with different
trust levels:

- **In-app (convenient path):** stays bearer. Any logged-in account that enters
  the code gets the gift. Forwardable on purpose (e.g. gifter pastes the code in
  WhatsApp; recipient redeems).
- **Web (verified path):** the claimer must sign in / sign up on the web, and
  the gift applies **only if their authenticated email matches the gift's
  recipient email**. The email match is a soft guardrail layered on top of the
  bearer code, to land the gift on the right person when claiming via the web.

## Workstreams

| # | Workstream | Owner | Repo |
|---|-----------|-------|------|
| 1 | In-app Settings → "Redeem a gift" entry | Claude Code | StitchPad (KMP) |
| 2 | Web `/claim` real claim surface (sign-in + email match) | Antigravity | stitchpad-web |
| 3 | Backend `redeemGift` callable: optional recipient-email match | Antigravity | StitchPad/functions |
| 4 | Gift email deliverability (out of Promotions) | Antigravity | StitchPad/functions |
| 5 | Tester / recipient communication | Daniel (content) | testing docs |

---

## Workstream 1 — In-app Settings → "Redeem a gift" (Claude Code)

Make the existing `RedeemGiftRoute` reachable manually. Mirror the existing
"Your gift link" wiring exactly.

- **String:** reuse `gift_redeem_title` = "Redeem a gift" (already exists). Add a
  subtitle string, e.g. `gift_redeem_settings_subtitle` = "Have a gift code?
  Enter it to unlock your plan."
- **Settings row:** in `SettingsScreen.kt`, add a `SettingsRow` directly under
  the existing "Your gift link" row (`SettingsScreen.kt:170`), using
  `Icons.Outlined.Redeem` (or `CardGiftcard`), `onClick =
  { onAction(SettingsAction.OnRedeemGiftClick) }`.
- **Action:** add `OnRedeemGiftClick` to `SettingsAction`.
- **Event:** add `NavigateToRedeemGift` to `SettingsEvent`.
- **ViewModel:** `SettingsAction.OnRedeemGiftClick -> emit(SettingsEvent.NavigateToRedeemGift)`.
- **Root:** add `onNavigateToRedeemGift: () -> Unit` param to `SettingsRoot`,
  handle `SettingsEvent.NavigateToRedeemGift -> onNavigateToRedeemGift()`.
- **Nav graph:** in `MainScreen.kt` where `SettingsRoot(...)` is constructed
  (`MainScreen.kt:499`), add `onNavigateToRedeemGift = {
  navController.navigate(RedeemGiftRoute) }`. A plain `navigate` (no `popUpTo`)
  is correct here — the manual entry has no deep-link code to consume, the user
  types it on screen.
- **Behavior:** opens `RedeemGiftScreen` with an empty code field. The user
  pastes the code → "Redeem gift" → applies to their account. No Accept sheet
  auto-shows (that only happens when a deep-link code is consumed).
- **Preview + test:** `RedeemGiftScreen` already has a preview. Add a
  `SettingsViewModel` unit test asserting `OnRedeemGiftClick` emits
  `NavigateToRedeemGift`.

**Out of scope for WS1:** no changes to the redeem screen itself, the deep-link
path, or the ViewModel redemption logic.

---

## Workstream 2 — Web `/claim` real claim surface (Antigravity)

Today `/claim` (`stitchpad-web/src/pages/claim.astro`) only shows the code and
says "open the app." Turn it into a two-path claim page.

**Path A — Claim on the web (verified):**
- Read `?code=` from the URL.
- Require Firebase Auth sign-in / sign-up (email-password + Google, matching the
  app's methods). Apple sign-in on web is optional/out of scope for v1.
- After auth, call the `redeemGift` callable with `{ code, requireRecipientMatch:
  true }`.
- On success: "Gift claimed — your StitchPad plan is now active. Open the app to
  start using it." (subscription is already on their account.)
- On email mismatch error: "This gift was sent to a••••@gmail.com. Sign in with
  that email to claim it, or use the in-app code below." (mask the recipient
  email — show first char + domain only.)

**Path B — Claim in the app (manual fallback):**
- Show the code large with a **Copy code** button.
- Instructions: "Open StitchPad → Settings → **Redeem a gift** → paste this
  code." (matches Workstream 1.)
- An "Open StitchPad" button that is currently a no-op placeholder. **Note in the
  UI / comment:** deep-linking from this button will not work until the app is
  published to the stores; for now it is manual instructions only. Do NOT promise
  auto-open.

**Auth state:** keep it minimal — sign in, claim, done. Do not build a full web
account dashboard.

---

## Workstream 3 — Backend `redeemGift` email match (Antigravity)

In `functions/src/billing/giftBilling.ts`, extend `redeemGiftHandler`:

- Accept an optional `requireRecipientMatch: boolean` param (default `false`).
- When `true` and the gift is a `public`-flow gift with a stored
  `recipientEmail`: compare (case-insensitive, trimmed) the authenticated user's
  email to `recipientEmail`. If they differ, reject with a typed error
  (`recipient_email_mismatch`) and do NOT apply the gift.
- When `false` (the app's existing calls): behavior unchanged — bearer.
- Keep all existing guards (auth required, gift status, idempotency).
- Add/extend unit tests in `functions/src/__tests__/billing/giftBilling.test.ts`:
  match succeeds, mismatch rejects without applying, app path (no flag) still
  bearer.
- Run `npm run lint` (eslint single-quote rule) + jest before declaring done.

---

## Workstream 4 — Gift email deliverability (Antigravity)

In `functions/src/billing/giftEmailTemplate.ts`, make the **code/claim** email
(`buildGiftClaimEmail`) read as transactional, not promotional:

- Plainer subject, e.g. "Your StitchPad gift code" (drop "My guy sent you a
  gift!" marketing tone).
- Reduce image weight; keep one clear primary link; raise text-to-HTML ratio.
- Remove any `List-Unsubscribe` / bulk-mail headers from this transactional send
  (those signal "promotion").
- Verify SPF / DKIM / DMARC alignment for `send.getstitchpad.com` in Resend.
- Accept that classification is fuzzy — this reduces but does not guarantee
  Primary placement, which is why Workstream 5 exists.

---

## Workstream 5 — Communication (Daniel, content)

- Add to the Day 10 WhatsApp message + doc: "The gift email may land in your
  **Promotions** or **Spam** tab — please check there. The code also works if
  you open StitchPad → Settings → **Redeem a gift** and paste it."
- Encourage the email-free path: the `/gift/success` page already has a WhatsApp
  share with the code + link. Tell gifters to paste that straight to the
  recipient in the group; the recipient then redeems via Settings → Redeem a
  gift.

---

## Testing / acceptance

- **WS1 (app):** Settings shows "Redeem a gift" under "Your gift link"; tapping
  opens the redeem screen; pasting a valid code unlocks the plan; ViewModel test
  green; iOS + Android build green.
- **WS2/3 (web+backend):** web claim with matching email applies the plan; with a
  mismatched email it is rejected and nothing changes; app redeem (no flag) still
  works as bearer.
- **WS4 (email):** new gift email renders correctly; lands in Primary in a manual
  Gmail test (best-effort).
- **Smoke:** gift self → tap email link → if it deep-links, redeem in app; if it
  doesn't, (a) open app → Settings → Redeem a gift → paste code, and (b) on web
  /claim sign in with the recipient email → claim. Both succeed.

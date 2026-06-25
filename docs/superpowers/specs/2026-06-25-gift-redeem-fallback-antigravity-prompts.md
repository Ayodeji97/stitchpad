# Antigravity prompts — Gift redeem web + backend + email

Companion to `2026-06-25-gift-redeem-fallback-design.md`. Paste each prompt into
Antigravity. They cover the **web** (`stitchpad-web`) and **backend/email**
(`StitchPad/functions`) work. The in-app Settings entry is done separately by
Claude Code.

Recommended order: **Prompt B (backend) → Prompt C (email) → Prompt A (web)** —
the web claim depends on the backend callable change.

---

## Prompt B — Backend: recipient-email match on `redeemGift`

```
Repo: StitchPad (the KMP app repo), folder: functions/ (Firebase Cloud
Functions, TypeScript).

Goal: let the web claim a code-based gift only when the claimer's signed-in
email matches the gift's intended recipient, while keeping the in-app flow
unchanged (bearer code).

File: functions/src/billing/giftBilling.ts — function redeemGiftHandler.

1. Add an optional request param `requireRecipientMatch: boolean` (default
   false). Validate it like the other inputs.
2. When requireRecipientMatch is true AND the gift is a `public`-flow gift that
   has a stored recipientEmail:
   - Compare the authenticated caller's email (context.auth) to the gift's
     recipientEmail, case-insensitive and trimmed.
   - If they do not match, throw a typed HttpsError (code
     'permission-denied' or 'failed-precondition') with a stable message key
     'recipient_email_mismatch'. Do NOT apply the gift, do NOT change any
     document.
3. When requireRecipientMatch is false (the existing app calls), behavior is
   completely unchanged — still bearer.
4. Keep every existing guard: auth required, gift status checks, idempotency,
   subscription stacking logic. Do not regress them.
5. Tests: extend functions/src/__tests__/billing/giftBilling.test.ts with:
   - requireRecipientMatch true + matching email → gift applied.
   - requireRecipientMatch true + mismatched email → rejected, user doc and gift
     doc unchanged.
   - requireRecipientMatch absent/false → still applies (bearer), unchanged.
6. Before declaring done, run `npm run lint` (the CI runs eslint with a
   single-quote rule and fails fast) AND the jest suite. Both must pass.

Do not touch the personal-link (gift_me) auto-apply flow. Do not change the
client-callable name.
```

---

## Prompt C — Backend: make the gift-code email transactional (out of Promotions)

```
Repo: StitchPad, folder: functions/. File:
functions/src/billing/giftEmailTemplate.ts — function buildGiftClaimEmail (the
code/claim email for the public gift flow). Do NOT change buildGiftReceivedEmail
(the personal-link "already active" email).

Problem: Gmail files this email under Promotions/Spam because it reads as
marketing (gifty subject, large hero image + big button, heavy branding).

Make it read as a transactional receipt:
1. Subject: plain and transactional, e.g. "Your StitchPad gift code". Drop
   marketing phrasing like "My guy sent you a gift!".
2. Body: reduce image weight (no large hero image; small or no logo), raise the
   text-to-HTML ratio, keep ONE clear primary link to
   https://link.getstitchpad.com/claim?code=CODE, and keep the code shown in
   monospace for manual entry.
3. Add a short line: "If the button doesn't open the app, open StitchPad ->
   Settings -> Redeem a gift and paste this code." (This matches a new in-app
   entry point.)
4. Remove any List-Unsubscribe or other bulk-mail headers from THIS send if
   present — transactional mail should not carry them.
5. Keep the plain-text alternative part in sync with the HTML.

Also: verify SPF, DKIM, and DMARC are aligned for the send.getstitchpad.com
sending domain in Resend, and report the current status (do not weaken auth).

Run `npm run lint` and jest before finishing.
```

---

## Prompt A — Web: turn `/claim` into a real two-path claim page

```
Repo: stitchpad-web (Astro 5 static site, Firebase JS SDK already used by the
gift pages, deployed to Vercel). File: src/pages/claim.astro plus any small
helpers under src/lib and public/scripts.

Today /claim only shows the gift code and tells the user to open the app. Rebuild
it as a two-path claim page. The URL is
https://link.getstitchpad.com/claim?code=CODE (read the code client-side from
the query string).

PATH A — Claim on the web (primary, verified):
- Add Firebase Auth sign-in / sign-up using email-password + Google (match the
  app's auth methods; Apple sign-in on web is out of scope for now). Reuse the
  existing Firebase app/config the gift pages already initialize.
- After the user is authenticated, call the existing `redeemGift` callable with
  { code, requireRecipientMatch: true }.
- On success: show "Gift claimed — your StitchPad plan is now active. Open
  StitchPad on your phone to start using it."
- On the 'recipient_email_mismatch' error: show "This gift was sent to
  a••••@gmail.com. Sign in with that email to claim it, or use the code in the
  app (below)." Mask the recipient email to first character + domain. (The
  backend returns the mismatch error; do not expose the full recipient email if
  the backend doesn't return it — just show generic guidance if unknown.)
- Handle other errors (already claimed, invalid code, expired) with clear
  messages.

PATH B — Claim in the app (fallback, always visible):
- Show the gift code large with a "Copy code" button (keep the existing
  public/scripts/claim-code.js approach for reading ?code).
- Instructions: "Open StitchPad -> Settings -> Redeem a gift -> paste this
  code."
- Include an "Open StitchPad" button BUT it must be a placeholder/no-op for now
  with a code comment explaining: deep-linking from here will not work until the
  app is published to the App Store / Play Store; until then this is manual
  instructions only. Do not claim it auto-opens the app.

Keep it minimal: sign in, claim, done. Do NOT build a web dashboard or account
area. Match the existing site's styling/components (StoreBadges, etc.). Keep CSP
compliance (external scripts as static files under public/, like the current
claim-code.js).

Verify locally that: (1) a matching-email sign-in claims successfully, (2) a
mismatched email is rejected with the guidance message, (3) Copy code works.
```

---

## Notes for Daniel

- The store badges on `/claim` are currently `#` placeholders
  (`StoreBadges.astro`) — fine for now (app not published). Revisit when live.
- The `link.getstitchpad.com` AASA + assetlinks.json are already correct and
  live; no DNS/well-known changes are needed for this work.
- Communication (Workstream 5) is content you add to the Day 10 WhatsApp message
  + testing doc; no Antigravity prompt needed.

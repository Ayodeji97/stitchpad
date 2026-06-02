# Verification Email — Adire Atelier rebrand

## Context
The verification email (shipped in PR #105) was styled with **saffron** as the dominant color (header band + button). But StitchPad has since rebranded to **"Adire Atelier"**, whose primary is **indigo** (`#2C3E7C`, CTA fill `#1E2B5C`), with **sienna** (`#B85A30`) as a warm accent, a **warm-paper** background (`#FAF6EC`), and **saffron `#E8A800` demoted to a single heritage accent**. So the email is off-brand. This restyles it to match the current identity and swaps the styled-text header for the real logo mark.

## Scope
Visual restyle of one file — `functions/src/auth/verificationEmailTemplate.ts` (pure HTML builder). No change to the function logic, signature, throttle, or the app. Same `buildVerificationEmailHtml({ displayName, verifyLink })`.

## Design
Email-safe: table layout, inline styles only, max-width ~500px, single light design (warm-paper card renders fine in dark clients — no dark-mode media queries).

- **Header:** warm-paper `#FAF6EC` band with the **logo mark** (hosted PNG, ~40px) + **"StitchPad"** wordmark in indigo `#2C3E7C` bold. A thin **saffron `#E8A800`** rule under the header as the single heritage accent.
- **Page bg:** `#FAF6EC`; message on a **white card**, rounded 16px.
- **Heading:** "Verify your email" in indigo `#2C3E7C`, extra-bold.
- **Body:** ink `#252320`; secondary `#57534C`.
- **Button:** indigo fill `#1E2B5C`, white bold text, rounded 12px (was saffron).
- **Fallback link + footer:** indigo links; muted footer with `support@getstitchpad.com` + © StitchPad.
- **Fonts:** `'Plus Jakarta Sans'` then web-safe fallback (`-apple-system, … Helvetica, Arial, sans-serif`).
- **Logo:** `LOGO_URL` constant = the Firebase Storage public download URL
  (`https://firebasestorage.googleapis.com/v0/b/stitchpad-30607.firebasestorage.app/o/stitchpad-email-logo.png?alt=media&token=…`).
  PNG generated from `stitchpad-web/public/logo.svg` via ImageMagick (512px, transparent). `<img>` has `alt="StitchPad"` so it degrades to alt text if images are blocked.

## Testing
Keep `functions/src/__tests__/auth/sendVerificationEmail.test.ts` green: still asserts the verify link is in a button + fallback link, the personalized greeting, and HTML-escaping of the name. Add an assertion that the logo `<img>` (LOGO_URL) is present. Visual confirmation = send a real test email and check Gmail render (inbox, button, logo).

## Ship
Feature branch + PR (separate from the unrelated uncommitted offline-upload WIP). Deploy via `cd functions && npm run deploy`; confirm with `firebase functions:list`. Then fresh-email signup to visually verify.

# `getstitchpad.com` Static Site — Design Spec

**Date:** 2026-05-12
**Branch:** `feature/web-static-site`
**Owner:** Daniel Ogunleye
**Related:** Settings redesign (PR #35, in flight — currently uses `stitchpad.app/...` URLs that need updating to `getstitchpad.com/...`)

## Goal

Ship the minimum public website at `https://getstitchpad.com` so that `/privacy` and `/terms` resolve to real pages — unblocking App Store + Play Store submission and PR #35's `LocalUriHandler` wiring. Root URL gets a tiny placeholder so the domain doesn't 404.

## Motivation

PR #35 (Settings redesign) hardcodes `https://stitchpad.app/privacy`, `https://stitchpad.app/terms`, `https://stitchpad.app/upgrade` for the in-app legal-row taps. Two problems:

1. `stitchpad.app` is taken by an unrelated party — registration would require an aftermarket purchase ($100-1000+).
2. `stitchpad.com` is also taken (registered 2014, aftermarket $500-5000+).

Decision: switch the canonical domain to `getstitchpad.com` (~$13/year, available, established "get" prefix pattern used by Linear, ClickUp, Cursor, Cal.com, etc.). PR #35's three URL constants will be updated to `getstitchpad.com/...` as part of its rebase.

App Store + Play Store submission both require functional privacy policy URLs. We also need terms of service for paid features (the future ₦500/₦1000 freemium model). Today neither URL resolves; this PR fixes that.

## Out of scope

- **`getstitchpad.com/upgrade` paywall page.** Deferred with the rest of paywall work — no payment integration yet.
- **Full marketing landing.** Root URL gets "Coming soon" placeholder only. A real landing page (hero, features, screenshots, pricing) ships in a separate PR once we have an app to demo.
- **Localization beyond English.** Nigerian English is the medium of business for our target market; Yoruba/Igbo/Hausa policy localization is a launch-scale concern, not V1.
- **In-app "Policies updated" banner / forced re-acceptance.** Closed beta is small enough to email testers directly on material changes.
- **Lawyer review.** Strongly recommended before public Play Store launch; defer for V1 closed beta.
- **Email infrastructure beyond a forwarder** (`privacy@getstitchpad.com` → personal Gmail). Custom inbox + auth via Workspace can ship later.
- **CMS / static site generator.** Three static HTML files don't need a build step; the simplest thing that works.
- **Analytics on the marketing site.** No GA / Plausible / etc. — first ship the legal coverage, add tracking once there's actual marketing traffic.
- **Custom 404 page.** Firebase Hosting's default 404 is fine for V1.

## Architecture

### Hosting

Firebase Hosting under the existing `stitchpad-30607` project. Free with the existing Blaze plan. Deploys via `firebase deploy --only hosting` from a new `web/` folder at repo root. Reuses the same Firebase CLI + project credentials we set up for the `onAuthUserDeleted` Cloud Function.

### Domain

`getstitchpad.com`, registered separately by Daniel via Namecheap (or similar). After registration:

1. Firebase Console → Hosting → "Add custom domain" → enter `getstitchpad.com`
2. Firebase emits 1-2 DNS records (typically a TXT for ownership verification, then A records for routing)
3. Daniel adds those records at the registrar — ~5 min
4. DNS propagates (5-60 min). Firebase auto-provisions a free Let's Encrypt TLS cert. HTTPS works automatically.

This step is documented in the deployment runbook but Daniel does it manually outside the PR.

### Pages

Three static HTML files at `getstitchpad.com`:

| URL | Page | Content |
|---|---|---|
| `/` | `index.html` | One-line "Coming soon" placeholder with logo. Prevents the domain from 404-ing during V1 closed beta. |
| `/privacy` | `privacy.html` | Privacy policy text generated via Termly (or PrivacyPolicies.com), adapted into our HTML template. Covers data collected, why, processors, retention, user rights including account deletion, contact email. |
| `/terms` | `terms.html` | Terms of service generated similarly. Covers eligibility, account responsibility, content ownership, freemium pricing (placeholder ₦500/₦1000), termination, liability disclaimer, governing law (Nigeria), contact email. |

`firebase.json` is updated to add a `hosting` block alongside the existing `functions` block; `cleanUrls: true` strips the `.html` extension so `/privacy` resolves to `privacy.html` automatically.

### File structure

```
web/
├── public/
│   ├── index.html         # tiny placeholder landing
│   ├── privacy.html       # privacy policy
│   ├── terms.html         # terms of service
│   ├── styles.css         # one shared minimal stylesheet (~80 lines)
│   └── logo.svg           # StitchPad logo (saffron, simple)
firebase.json              # MODIFIED: add hosting block alongside existing functions block
.firebaserc                # already exists, no change
docs/web/                  # NEW DIR
└── deployment.md          # runbook: register domain → connect to Firebase Hosting → deploy → custom-domain DNS
```

## Content + page format

### Layout

Each page uses the same shell — saffron header bar with logo, constrained content (~700px), readable line-height, light background. One shared `styles.css` powers all three.

```html
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Privacy Policy — StitchPad</title>
  <link rel="stylesheet" href="/styles.css">
  <link rel="icon" type="image/svg+xml" href="/logo.svg">
</head>
<body>
  <header>
    <a href="/" class="logo-link"><img src="/logo.svg" alt="StitchPad" /></a>
  </header>
  <main>
    <h1>Privacy Policy</h1>
    <p class="effective-date">Last updated: 2026-05-12</p>
    <!-- Termly-generated content adapted -->
  </main>
  <footer>
    <p>&copy; 2026 StitchPad. <a href="/privacy">Privacy</a> · <a href="/terms">Terms</a></p>
  </footer>
</body>
</html>
```

### Styling

`styles.css` (~80 lines) using StitchPad brand tokens:

- Header background: `#E8A800` (Deep Saffron — matches app primary)
- Body background: `#FFFFFF`
- Text: `#0F172A` (slate-900)
- Links: `#E8A800` underlined
- Plus Jakarta Sans web font for body (Google Fonts CDN); system fonts as fallback
- Max content width 700px, centered
- Mobile responsive via single media query at 640px

No JavaScript. No build step. No framework. Just HTML + one CSS file.

### Content generation workflow

For each policy:

1. Open Termly free tier (https://termly.io/products/privacy-policy-generator/)
2. Answer ~20 yes/no questions about StitchPad's data flows:
   - Data collected: email, business name, phone/WhatsApp number, customer names + measurements + photos, etc.
   - Why: providing the service, communicating with users
   - Third parties: Firebase / Google Cloud (data processors), no advertising networks, no analytics
   - Retention: data retained as long as account exists; deleted within 24h of account deletion (per the onAuthUserDeleted function)
   - User rights: access, correction, deletion (in-app via Settings)
   - Contact: `privacy@getstitchpad.com`
   - Governing law: Nigeria (NDPR — Nigeria Data Protection Regulation)
3. Termly outputs HTML — copy the policy `<body>` content into `privacy.html`'s `<main>` block
4. Repeat for terms of service (separate Termly generator)

This is a manual content step Daniel does once. The HTML template, styles, and hosting infra ship in this PR; the actual policy text gets pasted in.

### Contact email

`privacy@getstitchpad.com` — set up at Namecheap as a free email forward to `danielayodeji97@gmail.com`. Namecheap includes 1 free email forwarder with every domain registration; takes 5 min in the dashboard. Looks professional in policies, doesn't expose Daniel's personal address.

### Effective date

Each policy starts with `<p class="effective-date">Last updated: 2026-05-12</p>`. When content changes, manually update the date string. No automated versioning.

## Deployment

Initial deploy:

```bash
cd /path/to/repo
firebase deploy --only hosting
```

Takes ~30 seconds. Firebase prints the hosting URL (`https://stitchpad-30607.web.app` and the custom `https://getstitchpad.com` once DNS is configured).

The function deploy (`firebase deploy --only functions:onAuthUserDeleted`) and the hosting deploy are independent — they share `firebase.json` but `--only` flags isolate them. Future PRs to either can deploy without touching the other.

A new runbook at `docs/web/deployment.md` covers:

- First-time domain registration + Firebase custom-domain setup + DNS
- Subsequent deploys (just `firebase deploy --only hosting`)
- How to view the deployed site (Firebase Console → Hosting → view live)
- How to update content (edit HTML, redeploy)
- Email forwarder setup

## Testing + verification

This PR has no automated tests — it's three static HTML files. Verification is manual smoke:

1. Build / lint: `firebase serve --only hosting` from repo root runs a local server at `http://localhost:5000`. Open all three URLs in browser; verify rendering.
2. HTML validation: paste each page into https://validator.w3.org/ → no errors.
3. CSS validation: paste `styles.css` into https://jigsaw.w3.org/css-validator/ → no errors.
4. Mobile responsiveness: Chrome DevTools → toggle device toolbar → check iPhone + small Android viewport.
5. Light-mode + dark-mode preview (browsers don't auto-flip these pages; we use white background regardless. Verify it's readable in both browser themes by checking with `prefers-color-scheme: dark` simulated.)
6. After production deploy: open `https://getstitchpad.com/`, `/privacy`, `/terms` on phone + desktop; verify TLS cert, no mixed-content warnings.

## CI

No new CI job needed. The functions-tests job from PR #38 doesn't touch hosting. Hosting deploys are manual via `firebase deploy --only hosting`.

Optionally we could add a one-line "validate hosting config" step to CI later but it adds value only after we have more pages.

## Coordination with PR #35

PR #35 currently hardcodes:

```kotlin
private const val PRIVACY_URL = "https://stitchpad.app/privacy"
private const val TERMS_URL = "https://stitchpad.app/terms"
private const val UPGRADE_URL = "https://stitchpad.app/upgrade"
```

These need updating to `getstitchpad.com/...`. Two paths:

1. **Update during PR #35's rebase** — preferred. The other agent owning that worktree changes the constants as part of the rebase against main.
2. **Land PR #35 with the wrong URLs, fix in follow-up** — acceptable but means `/privacy` taps would 404 briefly until this PR + the URL fix both land.

Daniel coordinates path 1 by mentioning this to the Settings worktree agent.

## Success criteria

- `https://getstitchpad.com/` returns 200 with the "Coming soon" page
- `https://getstitchpad.com/privacy` returns 200 with the privacy policy
- `https://getstitchpad.com/terms` returns 200 with the terms of service
- All three pages render correctly on iPhone Safari, Android Chrome, desktop Chrome, desktop Safari, desktop Firefox
- HTTPS works with a valid Let's Encrypt cert
- `privacy@getstitchpad.com` email forwarder reaches Daniel's inbox
- PR #35's Settings → Privacy and Settings → Terms taps open the right URLs (verified after PR #35 lands + the URL constants are updated)

## Risks and mitigations

- **Termly free tier limits / requires attribution.** Their free tier may require a "Powered by Termly" link or limit refresh frequency. Mitigation: read their ToS before publishing; if attribution is required, include it in the footer. If their limits are unworkable, fall back to a known-good open-source template (Basecamp's open policies, Auth0's example).
- **DNS misconfiguration delays.** Custom domain setup can take up to 24 hours to fully propagate. Mitigation: deploy to `stitchpad-30607.web.app` (Firebase's default) FIRST, verify the site works there, THEN add the custom domain. That way the underlying hosting is verified before DNS is a variable.
- **Content drift.** Policies generated today won't reflect future product changes (new data collected, new third parties, new payment processors). Mitigation: review on each major feature ship; full lawyer review before public Play Store launch.
- **Email forwarder reliability.** Namecheap's free forwarder occasionally drops mail. For V1 with rare privacy inquiries, acceptable. Mitigation: monitor; switch to a paid mailbox if drops become a problem.
- **Visual / accessibility regressions** as we add pages later. Mitigation: keep `styles.css` simple and review at each addition. Use lighthouse audits.

## Future-compatibility notes

When you want to build the real marketing landing page:

1. Replace `web/public/index.html` with the new landing content
2. Add new HTML files (`/pricing`, `/about`, etc.) to `web/public/`
3. `firebase deploy --only hosting`
4. URLs for `/privacy` and `/terms` remain stable (good — they're now in App Store + Play Store listings and can't easily change)

When you want a build step (Astro, Eleventy, etc.) for the marketing site:

1. Add a build step that outputs to `web/public/` (or change `firebase.json` to point at a different output dir)
2. Existing `privacy.html` + `terms.html` can either stay raw or migrate into the generator
3. Same domain, same hosting target — no breaking changes

When you eventually buy `stitchpad.com` or `stitchpad.app`:

1. Update PR #35-merged URL constants from `getstitchpad.com` to the new domain
2. Add the new domain as a Firebase Hosting custom domain alongside `getstitchpad.com`
3. Use Firebase Hosting redirects to send the old `getstitchpad.com` URLs to the new domain — or keep both alive forever
4. Update App Store + Play Store listings to the new domain URLs (this requires an app update for the listing's privacy URL field)

# `getstitchpad.com` Static Site — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a minimum public website at `https://getstitchpad.com` with `/privacy` and `/terms` resolving to real pages. Unblocks App Store + Play Store submission and PR #35's `LocalUriHandler` wiring.

**Architecture:** Three static HTML files + one shared CSS + the StitchPad logo, deployed via Firebase Hosting under the existing `stitchpad-30607` project. No build step, no framework, no JavaScript. Privacy and terms content generated via Termly free tier and pasted in.

**Tech Stack:** Plain HTML5 + CSS, Firebase Hosting, Termly (for content), Namecheap (DNS).

---

### Task 1: Scaffold `web/` + add hosting block to `firebase.json`

**Files:**
- Create: `web/public/.gitkeep` (placeholder so the empty dir is tracked)
- Modify: `firebase.json` (add a `hosting` block alongside existing `functions` block)

- [ ] **Step 1: Create the `web/public/` directory by adding a placeholder**

```bash
mkdir -p web/public
touch web/public/.gitkeep
```

- [ ] **Step 2: Read `firebase.json` to understand current structure**

The file currently has only a `functions` array. We'll add a `hosting` object alongside it.

- [ ] **Step 3: Modify `firebase.json` to add hosting**

Final content:

```json
{
  "functions": [
    {
      "source": "functions",
      "codebase": "default",
      "ignore": [
        "node_modules",
        ".git",
        "firebase-debug.log",
        "firebase-debug.*.log",
        "*.local"
      ],
      "predeploy": [
        "npm --prefix \"$RESOURCE_DIR\" run lint",
        "npm --prefix \"$RESOURCE_DIR\" run build"
      ]
    }
  ],
  "hosting": {
    "public": "web/public",
    "cleanUrls": true,
    "trailingSlash": false,
    "ignore": [
      "firebase.json",
      "**/.*",
      "**/node_modules/**"
    ],
    "headers": [
      {
        "source": "**/*.@(html|css|svg)",
        "headers": [
          { "key": "Cache-Control", "value": "public, max-age=3600" }
        ]
      }
    ]
  }
}
```

Key choices:
- `"public": "web/public"` — Firebase serves files from this directory.
- `"cleanUrls": true` — strips `.html` extension so `/privacy` resolves to `privacy.html` automatically.
- `"trailingSlash": false` — `/privacy/` (with slash) redirects to `/privacy` (without). Standard SEO hygiene.
- `headers` — short 1-hour cache for HTML/CSS/SVG. Long enough to feel fast, short enough that content updates propagate without manual cache invalidation.

- [ ] **Step 4: Verify firebase.json is valid JSON**

Run: `python3 -c "import json; json.load(open('firebase.json')); print('valid')"`
Expected: `valid`

- [ ] **Step 5: Commit**

```bash
git add firebase.json web/public/.gitkeep
git commit -m "chore(web): add hosting block to firebase.json + scaffold web/public/"
```

---

### Task 2: Build the shared stylesheet and logo

**Files:**
- Create: `web/public/styles.css`
- Create: `web/public/logo.svg`

- [ ] **Step 1: Create `web/public/styles.css`**

```css
:root {
  --saffron: #E8A800;
  --saffron-ink: #5C4500;
  --ink: #0F172A;
  --muted: #475569;
  --bg: #FFFFFF;
  --rule: #E2E8F0;
  --link: #C48F00;
  --link-hover: #8A6300;
  --max-width: 700px;
}

* {
  box-sizing: border-box;
}

html, body {
  margin: 0;
  padding: 0;
  background: var(--bg);
  color: var(--ink);
  font-family: 'Plus Jakarta Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  font-size: 16px;
  line-height: 1.6;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

header {
  background: var(--saffron);
  padding: 16px 24px;
}

header .logo-link {
  display: inline-flex;
  align-items: center;
  text-decoration: none;
  color: var(--saffron-ink);
  font-weight: 700;
  font-size: 18px;
  letter-spacing: -0.01em;
}

header .logo-link img {
  width: 28px;
  height: 28px;
  margin-right: 10px;
}

main {
  max-width: var(--max-width);
  margin: 0 auto;
  padding: 48px 24px 96px;
}

main h1 {
  font-size: 32px;
  line-height: 1.2;
  margin: 0 0 8px;
  letter-spacing: -0.02em;
}

main h2 {
  font-size: 22px;
  line-height: 1.3;
  margin: 40px 0 12px;
  letter-spacing: -0.01em;
}

main h3 {
  font-size: 18px;
  line-height: 1.4;
  margin: 28px 0 8px;
}

main p {
  margin: 0 0 16px;
}

main ul, main ol {
  padding-left: 24px;
  margin: 0 0 16px;
}

main li {
  margin-bottom: 8px;
}

main a {
  color: var(--link);
  text-decoration: underline;
  text-underline-offset: 2px;
}

main a:hover {
  color: var(--link-hover);
}

main .effective-date {
  color: var(--muted);
  font-size: 14px;
  margin: 0 0 32px;
}

main hr {
  border: 0;
  border-top: 1px solid var(--rule);
  margin: 32px 0;
}

footer {
  border-top: 1px solid var(--rule);
  padding: 24px;
  text-align: center;
  color: var(--muted);
  font-size: 14px;
}

footer a {
  color: var(--muted);
  text-decoration: underline;
  text-underline-offset: 2px;
  margin: 0 8px;
}

footer a:hover {
  color: var(--ink);
}

/* Landing-page styles (index.html) */
.landing {
  max-width: 600px;
  margin: 0 auto;
  padding: 120px 24px;
  text-align: center;
}

.landing h1 {
  font-size: 40px;
  line-height: 1.1;
  margin: 24px 0 12px;
  letter-spacing: -0.02em;
}

.landing p {
  font-size: 18px;
  color: var(--muted);
}

.landing .brand-mark {
  width: 72px;
  height: 72px;
  margin: 0 auto;
}

@media (max-width: 640px) {
  main {
    padding: 32px 20px 64px;
  }
  main h1 {
    font-size: 26px;
  }
  main h2 {
    font-size: 19px;
  }
  .landing {
    padding: 80px 20px;
  }
  .landing h1 {
    font-size: 32px;
  }
}
```

- [ ] **Step 2: Create `web/public/logo.svg`**

Simple saffron diamond mark. Replace later with the actual brand logo if you have an SVG export; this is a placeholder good enough for V1.

```xml
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32" fill="none">
  <rect x="4" y="4" width="24" height="24" rx="6" fill="#E8A800"/>
  <path d="M11 16 L16 21 L21 11" stroke="#FFFFFF" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
</svg>
```

A saffron rounded square with a stylized stitch / checkmark inside. Matches the app's primary color.

- [ ] **Step 3: Commit**

```bash
git add web/public/styles.css web/public/logo.svg
git commit -m "feat(web): shared styles.css + placeholder logo.svg in StitchPad brand colors"
```

---

### Task 3: Build the index (Coming Soon) page

**Files:**
- Create: `web/public/index.html`

- [ ] **Step 1: Create `web/public/index.html`**

```html
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>StitchPad — Workshop manager for Nigerian tailors</title>
  <meta name="description" content="StitchPad helps Nigerian tailors manage customers, measurements, orders, and payments in one app.">
  <link rel="icon" type="image/svg+xml" href="/logo.svg">
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700&display=swap">
  <link rel="stylesheet" href="/styles.css">
</head>
<body>
  <main class="landing">
    <img src="/logo.svg" alt="StitchPad" class="brand-mark">
    <h1>StitchPad</h1>
    <p>Workshop manager for Nigerian tailors. Coming soon.</p>
  </main>
  <footer>
    <p>&copy; 2026 StitchPad. <a href="/privacy">Privacy</a> · <a href="/terms">Terms</a></p>
  </footer>
</body>
</html>
```

- [ ] **Step 2: Commit**

```bash
git add web/public/index.html
git commit -m "feat(web): index.html with Coming Soon placeholder + footer links to /privacy and /terms"
```

---

### Task 4: Build the privacy.html and terms.html page shells

**Files:**
- Create: `web/public/privacy.html`
- Create: `web/public/terms.html`

These pages have the template wired up (header, footer, styles, fonts) with a clearly-marked placeholder body. The actual policy content gets generated via Termly and pasted in during Task 5. Shipping with a placeholder lets us verify the rendering pipeline before the content step.

- [ ] **Step 1: Create `web/public/privacy.html`**

```html
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Privacy Policy — StitchPad</title>
  <meta name="description" content="StitchPad's privacy policy.">
  <link rel="icon" type="image/svg+xml" href="/logo.svg">
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700&display=swap">
  <link rel="stylesheet" href="/styles.css">
</head>
<body>
  <header>
    <a href="/" class="logo-link"><img src="/logo.svg" alt="StitchPad" />StitchPad</a>
  </header>
  <main>
    <h1>Privacy Policy</h1>
    <p class="effective-date">Last updated: 2026-05-12</p>

    <!-- ============================================================
         REPLACE THIS PLACEHOLDER WITH TERMLY-GENERATED CONTENT.
         See Task 5 of the implementation plan + the deployment runbook.
         ============================================================ -->
    <p><strong>Policy content pending.</strong> This page is currently a placeholder while we finalize our privacy policy. The full policy will be published before any user-visible launch.</p>

    <p>For privacy questions in the meantime, contact <a href="mailto:privacy@getstitchpad.com">privacy@getstitchpad.com</a>.</p>
  </main>
  <footer>
    <p>&copy; 2026 StitchPad. <a href="/privacy">Privacy</a> · <a href="/terms">Terms</a></p>
  </footer>
</body>
</html>
```

- [ ] **Step 2: Create `web/public/terms.html`**

```html
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Terms of Service — StitchPad</title>
  <meta name="description" content="StitchPad's terms of service.">
  <link rel="icon" type="image/svg+xml" href="/logo.svg">
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700&display=swap">
  <link rel="stylesheet" href="/styles.css">
</head>
<body>
  <header>
    <a href="/" class="logo-link"><img src="/logo.svg" alt="StitchPad" />StitchPad</a>
  </header>
  <main>
    <h1>Terms of Service</h1>
    <p class="effective-date">Last updated: 2026-05-12</p>

    <!-- ============================================================
         REPLACE THIS PLACEHOLDER WITH TERMLY-GENERATED CONTENT.
         See Task 5 of the implementation plan + the deployment runbook.
         ============================================================ -->
    <p><strong>Terms content pending.</strong> This page is currently a placeholder while we finalize our terms of service. The full terms will be published before any user-visible launch.</p>

    <p>For questions in the meantime, contact <a href="mailto:hello@getstitchpad.com">hello@getstitchpad.com</a>.</p>
  </main>
  <footer>
    <p>&copy; 2026 StitchPad. <a href="/privacy">Privacy</a> · <a href="/terms">Terms</a></p>
  </footer>
</body>
</html>
```

- [ ] **Step 3: Commit**

```bash
git add web/public/privacy.html web/public/terms.html
git commit -m "feat(web): privacy.html and terms.html templates with placeholder bodies"
```

---

### Task 5: Generate Termly content and paste into privacy.html + terms.html

This is a **manual content step** for Daniel. The plan's job is to document exactly what to do; the agent executing this task should pause and ask Daniel to perform these steps if he hasn't already done them, then commit the resulting content.

**Files:**
- Modify: `web/public/privacy.html` (replace placeholder `<main>` content)
- Modify: `web/public/terms.html` (replace placeholder `<main>` content)

- [ ] **Step 1: Generate the Privacy Policy via Termly**

1. Open https://app.termly.io/dashboard/website/new (Termly free tier — sign up with `danielayodeji97@gmail.com` if needed)
2. Add a website: Name `StitchPad`, URL `https://getstitchpad.com`
3. Click "Privacy Policy" → "Create New"
4. Answer the questions truthfully. Key answers:
   - Business type: Sole proprietorship (or LLC if you have one registered)
   - Country: Nigeria
   - Industry: Software / mobile app
   - Data collected: Email address, name, business name, phone number, customer information (names, measurements, photos), order details, usage data
   - Why: Provide and operate the service, communicate with users, improve the product
   - Third-party processors: Google Firebase (Auth, Firestore, Cloud Storage, Functions)
   - Analytics: No (we don't have analytics yet)
   - Ads / tracking: No
   - Cookies: Only essential / authentication cookies via Firebase Auth
   - Sell data: No
   - Children: No, service is for adult tailors (18+)
   - User rights: Access, correction, deletion (in-app), export (manual on request)
   - Data retention: Account data retained while account exists; deleted within 24 hours of account deletion via automated Cloud Function
   - Contact email: `privacy@getstitchpad.com`
   - Governing law: Nigeria
   - Mentions of NDPR (Nigeria Data Protection Regulation): Yes if asked
5. Generate the policy. Copy the policy content (the body, NOT the HTML wrapper).

- [ ] **Step 2: Paste the Privacy Policy content into `web/public/privacy.html`**

Open `web/public/privacy.html`. Replace the entire HTML comment block AND the two placeholder `<p>` tags below it (everything between the `<p class="effective-date">` line and the `</main>` closing tag) with the Termly-generated policy content.

Termly's output is already HTML-formatted with `<h2>`, `<h3>`, `<p>`, `<ul>` tags that match our CSS — no further styling needed.

If Termly's free tier requires attribution, leave their "Powered by Termly" link in place (usually a small line at the bottom of the content). It's a fair trade for a $0 policy.

- [ ] **Step 3: Generate the Terms of Service via Termly**

Same workflow as Step 1, but pick "Terms of Service" / "Terms & Conditions" generator. Key answers:

- Eligibility: 18+
- Account responsibility: User keeps password safe, responsible for activity on their account
- User content: User owns their customer data; StitchPad has a license to process it to operate the service
- Prohibited uses: Illegal activity, scraping, reverse-engineering, abuse
- Pricing: Freemium — free tier limited to ~15 customers; paid tier at ₦500/month or ₦1,000/year (placeholder — adjust when paywall ships)
- Termination: Either party can terminate; user can delete account in-app
- Liability: Service "as is", no warranties beyond minimum required by law
- Governing law: Nigeria
- Disputes: Lagos jurisdiction (or wherever you're based — Porto, Portugal per your Namecheap profile, may need a different jurisdiction; check with Termly's suggestion)
- Contact: `hello@getstitchpad.com`

- [ ] **Step 4: Paste the Terms of Service content into `web/public/terms.html`**

Same approach as Step 2 — replace the placeholder block between `effective-date` and `</main>`.

- [ ] **Step 5: Commit the policies**

```bash
git add web/public/privacy.html web/public/terms.html
git commit -m "content(web): privacy policy + terms of service generated via Termly"
```

---

### Task 6: Local smoke via `firebase serve`

**Files:** none (verification only)

- [ ] **Step 1: Run the Firebase Hosting emulator locally**

From repo root:

```bash
firebase serve --only hosting
```

The CLI prints something like:

```
hosting: Local server: http://localhost:5000
```

- [ ] **Step 2: Open all three URLs in a browser and verify rendering**

- http://localhost:5000/ → "Coming soon" page renders with saffron header, logo, tagline
- http://localhost:5000/privacy → policy renders with header + footer + content (real content from Termly if Task 5 is done; placeholder otherwise)
- http://localhost:5000/terms → same shape as privacy

For each page:
- Header bar is saffron (#E8A800) with logo
- Body content is centered, max 700px wide
- Footer at the bottom links to /privacy and /terms
- Fonts load from Google Fonts CDN (Plus Jakarta Sans)
- Mobile viewport (Chrome DevTools → toggle device → iPhone) shows readable text without horizontal scroll

- [ ] **Step 3: Verify clean URLs work**

- `http://localhost:5000/privacy` (no extension) → renders privacy.html ✓
- `http://localhost:5000/privacy.html` → should redirect to `/privacy` (cleanUrls behaviour)
- `http://localhost:5000/privacy/` (trailing slash) → should redirect to `/privacy` (trailingSlash: false)

If any of the above fails, recheck `firebase.json` config and the file paths in `web/public/`.

- [ ] **Step 4: Stop the emulator**

`Ctrl+C` in the terminal.

This task has no commit — it's verification only.

---

### Task 7: Deployment runbook

**Files:**
- Create: `docs/web/deployment.md`

- [ ] **Step 1: Create the runbook**

```markdown
# `getstitchpad.com` Static Site — Deployment + Operations Runbook

The public website at `https://getstitchpad.com` is served by Firebase Hosting under the existing `stitchpad-30607` project. Three static pages: `/`, `/privacy`, `/terms`.

## Prerequisites

- Firebase CLI installed and logged in (see `docs/auth/firebase-functions.md` for setup)
- `getstitchpad.com` registered (done — Daniel, 2026-05-12)
- Namecheap dashboard access (registrar for DNS records)

## First-time deploy

From repo root:

\`\`\`bash
firebase deploy --only hosting
\`\`\`

Takes ~30 seconds. Firebase prints the live URL:

\`\`\`
Hosting URL: https://stitchpad-30607.web.app
\`\`\`

Open that URL — the three pages should be live at the Firebase default domain immediately.

## Connect the custom domain

After the first deploy succeeds:

1. Firebase Console → Hosting → click **Add custom domain**
2. Enter `getstitchpad.com` → continue
3. Firebase shows a TXT verification record. Copy it.
4. In Namecheap → Domain List → `getstitchpad.com` → **Advanced DNS** tab → **Add New Record** → type `TXT`, host `@`, value (paste). Save.
5. Wait 5–15 min, click "Verify" in Firebase Console. Once verified:
6. Firebase shows two `A` records (IPs). Add both at Namecheap as `A` records with host `@`.
7. Firebase also asks about `www.getstitchpad.com` — add the same A records with host `www` (or skip if you don't want www to resolve).
8. **Delete the Namecheap parking-page records** that came with registration:
   - The `CNAME www → parkingpage.namecheap.com.` record
   - The `URL Redirect Record @ → http://www.getstitchpad.com/`
   - These conflict with Firebase's records.
9. Wait for DNS propagation (5 min to 24 hours, usually < 30 min). Firebase auto-provisions a Let's Encrypt TLS cert once DNS resolves.
10. Visit `https://getstitchpad.com` — should serve the StitchPad site over HTTPS.

## Subsequent deploys

After any change to `web/public/*`:

\`\`\`bash
firebase deploy --only hosting
\`\`\`

That's it. Cache headers in `firebase.json` ensure new content propagates within 1 hour worldwide. For urgent invalidations (e.g., a typo in the privacy policy), Firebase Console → Hosting → Release History → click the release → has a "purge cache" option.

## Update privacy / terms content

1. Edit `web/public/privacy.html` or `web/public/terms.html` (or both)
2. Update the `<p class="effective-date">Last updated: YYYY-MM-DD</p>` line
3. `git commit` the changes
4. `firebase deploy --only hosting`
5. For material policy changes (data practices, payment terms), email closed-beta users so they're aware

## View deployed pages / debug

- Production: https://getstitchpad.com/, /privacy, /terms
- Firebase default: https://stitchpad-30607.web.app/, /privacy, /terms (always available even if custom DNS breaks)
- Firebase Console → Hosting → shows release history, rollback options, traffic graphs

## Rollback a bad deploy

\`\`\`bash
firebase hosting:channel:list      # list channels
firebase hosting:rollback          # interactive: pick a previous release
\`\`\`

Or in Console: Hosting → Release History → click an older release → "Rollback to this version".

## Email forwarders (reference)

These were set up in Namecheap on 2026-05-12 (not via Firebase):
- `privacy@getstitchpad.com` → `danielayodeji97@gmail.com`
- `hello@getstitchpad.com` → `danielayodeji97@gmail.com`
- `support@getstitchpad.com` → `danielayodeji97@gmail.com`

To change forwarders: Namecheap → Domain List → `getstitchpad.com` → **Domain** tab → **Redirect Email** section.

If migrating to Google Workspace later, see Path 2 in the implementation spec (replace Namecheap forwarders with custom MX records).

## Updating the URLs in the Kotlin app

PR #35 (Settings redesign, in flight) hardcodes:

\`\`\`kotlin
private const val PRIVACY_URL = "https://stitchpad.app/privacy"
private const val TERMS_URL = "https://stitchpad.app/terms"
private const val UPGRADE_URL = "https://stitchpad.app/upgrade"
\`\`\`

These need updating to `getstitchpad.com` as part of PR #35's rebase against main. After this PR merges and the site is live, coordinate with the PR #35 agent to flip those constants.

## Adding a new page later

E.g., a marketing landing or a pricing page:

1. Create `web/public/<new-page>.html` following the existing template (header + footer + styles.css)
2. Add a link in the footer or wherever appropriate
3. `firebase deploy --only hosting`

URLs `/privacy` and `/terms` are permanent contracts (referenced in App Store + Play Store listings). Don't rename them.
```

(Replace escaped backticks with real triple backticks when writing the file.)

- [ ] **Step 2: Commit**

```bash
git add docs/web/deployment.md
git commit -m "docs(web): deployment + operations runbook for getstitchpad.com"
```

---

### Task 8: Final local verification + push + open PR

- [ ] **Step 1: Run the Kotlin gauntlet defensively (we didn't touch Kotlin but verify nothing's broken)**

```bash
./gradlew :composeApp:testDebugUnitTest :composeApp:compileKotlinIosSimulatorArm64 detekt
```

Expected: all green.

- [ ] **Step 2: Re-run the functions tests defensively**

```bash
cd functions && npm test && cd ..
```

Expected: 10 tests pass.

- [ ] **Step 3: Final hosting smoke**

```bash
firebase serve --only hosting
```

Open `http://localhost:5000/`, `/privacy`, `/terms`. Confirm everything renders.

`Ctrl+C` when done.

- [ ] **Step 4: Push the branch**

```bash
git push -u origin feature/web-static-site
```

- [ ] **Step 5: Open the PR**

```bash
gh pr create --title "feat(web): getstitchpad.com static site — Privacy + Terms + Coming Soon" --body "$(cat <<'EOF'
## Summary

Adds the public website at `https://getstitchpad.com`. Three static HTML pages on Firebase Hosting, no build step, no framework. Unblocks App Store + Play Store submission and PR #35's `LocalUriHandler` wiring for Privacy/Terms taps.

- `/` — minimal "Coming soon" placeholder
- `/privacy` — privacy policy (content generated via Termly, NDPR-aware)
- `/terms` — terms of service (Nigerian governing law, freemium pricing placeholder)
- Shared `styles.css` (~150 lines), `logo.svg`, Plus Jakarta Sans via Google Fonts CDN
- `firebase.json` extended with a `hosting` block alongside the existing `functions` block
- `cleanUrls: true` so `/privacy` resolves without `.html`
- `docs/web/deployment.md` runbook covers first-deploy, custom domain DNS, subsequent deploys, content updates, rollback, email forwarders

Design spec: `docs/superpowers/specs/2026-05-12-web-static-site-design.md`
Plan: `docs/superpowers/plans/2026-05-12-web-static-site.md`

## Domain context

`stitchpad.com` and `stitchpad.app` are both taken (aftermarket prices $500-5,000+). Daniel registered `getstitchpad.com` (~$7 first year via Namecheap promo). The `get` prefix is the standard pattern for SaaS products whose `.com` is squatted (getlinear, getclay, getcursor, getcal.com, etc.).

Email forwarders set up at Namecheap: `privacy@`, `hello@`, `support@` all forward to `danielayodeji97@gmail.com`.

## Coordination with PR #35

PR #35 (Settings redesign, in flight) hardcodes `https://stitchpad.app/...` URLs. These need updating to `https://getstitchpad.com/...` as part of PR #35's rebase. Three constants in `SettingsViewModel.kt`.

## Deferred to follow-up PRs

- Real marketing landing page at `/` (currently "Coming soon" only)
- `getstitchpad.com/upgrade` paywall landing (deferred with the rest of paywall work)
- Localization beyond English (Yoruba/Igbo/Hausa) — launch-scale concern, not V1
- In-app "Policies updated" forced re-acceptance flow
- Lawyer review of the policies (strongly recommended before public Play Store launch)
- Analytics on the marketing site (no GA / Plausible / etc. yet)
- Custom 404 page

## Manual steps after merge

Per `docs/web/deployment.md`:

1. \`firebase deploy --only hosting\` to push the pages live at the Firebase default URL
2. Firebase Console → Hosting → Add custom domain → `getstitchpad.com`
3. Add TXT verification + A records at Namecheap (Firebase shows exact values)
4. Delete Namecheap parking-page records (CNAME www, URL Redirect @)
5. Wait for DNS + TLS provisioning
6. Verify `https://getstitchpad.com/privacy` and `/terms` are live

## Test plan

- [x] Local smoke via \`firebase serve --only hosting\` — all 3 pages render at localhost:5000
- [x] HTML validates (manual paste into validator.w3.org if changes seem material)
- [x] Mobile responsive at 320px+ widths (DevTools)
- [x] cleanUrls works: \`/privacy\` resolves to privacy.html without extension in URL
- [x] Kotlin gauntlet still green (defensive — no Kotlin touched)
- [x] Functions tests still green (defensive — no functions touched)
- [ ] After-merge: production smoke at https://getstitchpad.com/privacy + /terms

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 6: Verify CI passes on the PR**

Wait for all checks to land green (secrets-scan, detekt, build-android, build-ios, Unit Tests, functions-tests). This PR doesn't add new CI jobs — the existing 6 should all pass since we only added static HTML files in a new `web/` folder.

- [ ] **Step 7: After CI green, ready to merge**

Squash and merge via GitHub UI or:

```bash
gh pr merge --squash --delete-branch
```

Then follow `docs/web/deployment.md` "First-time deploy" + "Connect the custom domain" sections to push the site live and wire up DNS.

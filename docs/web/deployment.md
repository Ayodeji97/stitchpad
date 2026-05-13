# getstitchpad.com — Deployment Runbook

Static marketing + legal site for StitchPad. Hosted on Firebase Hosting under the existing `stitchpad-30607` project.

## Site contents

- `/` — Coming Soon landing
- `/privacy` — meta-refresh redirect to Termly-hosted Privacy Policy
- `/terms` — meta-refresh redirect to Termly-hosted Terms of Service

Source lives at `web/public/`. Hosting config is in the root `firebase.json` under the `hosting` block (alongside the existing `functions` block — they share the same `stitchpad-30607` project).

## Termly source-of-truth URLs

The redirect pages point at Termly's hosted policy viewer. If we re-publish on Termly, the policy UUID stays the same — no redeploy needed.

- Privacy: `https://app.termly.io/policy-viewer/policy.html?policyUUID=a916dfb2-805d-45fd-973b-ae2438a598ca`
- Terms: `https://app.termly.io/policy-viewer/policy.html?policyUUID=2db4f6e4-ed44-4164-8c90-29f3edc78030`

## Local preview

```bash
firebase serve --only hosting
```

Open http://localhost:5000. Verify:
- `/` renders the landing with logo + footer links
- `/privacy` redirects to the Termly Privacy URL
- `/terms` redirects to the Termly Terms URL
- Footer links resolve without trailing `.html`

## Deploy

```bash
firebase deploy --only hosting
```

This deploys only the static site — Cloud Functions are untouched. Firebase prints the live URL on success (initially `stitchpad-30607.web.app` until the custom domain is connected).

## Custom domain (getstitchpad.com)

1. **Firebase Console** → Hosting → **Add custom domain** → `getstitchpad.com`
2. Firebase issues a **TXT record** for domain ownership verification — add it at Namecheap.
3. After verification, Firebase issues **two A records** (IPv4) for the apex domain — add both at Namecheap.
4. Optional: add a `www` CNAME pointing at Firebase's hosting hostname so `www.getstitchpad.com` works too.
5. Wait for SSL provisioning (usually < 1 hour, can take up to 24 hours). Firebase Console shows status.

### Namecheap DNS quick reference

| Type | Host | Value |
|------|------|-------|
| TXT  | @    | (provided by Firebase, one-time verification) |
| A    | @    | (IPv4 #1 from Firebase) |
| A    | @    | (IPv4 #2 from Firebase) |
| CNAME (optional) | www | (hostname from Firebase) |

Remove any leftover Namecheap parking records before adding the Firebase records — Namecheap defaults a URL Redirect / CNAME parking record at `@` that conflicts.

## Email forwarding (Namecheap)

Namecheap "Email Forwarding" tab on the domain dashboard. Forward to Daniel's Gmail:

- `privacy@getstitchpad.com` → `danielayodeji97@gmail.com`
- `support@getstitchpad.com` → `danielayodeji97@gmail.com`
- `hello@getstitchpad.com` → `danielayodeji97@gmail.com`

Once forwarding is set up, add Namecheap's MX records (Namecheap shows them on the same tab). MX records do not conflict with the A records used for hosting.

## Updating policies

If we change Termly content: re-publish on Termly. The hosted policy viewer URL stays the same, so no redeploy is required. If we ever rotate Termly UUIDs (e.g. moving providers), update the `meta http-equiv="refresh"` URLs in `web/public/privacy.html` and `web/public/terms.html` and redeploy.

## Rollback

Firebase Hosting retains the previous release. From the Firebase Console: Hosting → Release history → **Rollback** on the prior version. Or from CLI:

```bash
firebase hosting:clone <SOURCE_SITE>:<VERSION> stitchpad-30607
```

## Coordinating with the app

In-app URL constants live in `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/Urls.kt` (or wherever PR #35 lands them). They must point at `https://getstitchpad.com/privacy` and `https://getstitchpad.com/terms` — not the Termly URLs directly. This keeps the app linking at our domain so we can swap policy hosting later without an app update.

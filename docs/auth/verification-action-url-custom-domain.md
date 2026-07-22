# Email action links — move off `firebaseapp.com` onto our own domain

Status: implemented (2026-07-22). `auth.getstitchpad.com` is attached to Firebase
Hosting and authorized; the host is rewritten in the functions that send the
emails. No app release required.

## The project-wide setting could not be used

Firebase's own "Customize action URL" — the clean, documented fix — is rejected
server-side, from both the console and the Identity Toolkit admin API:

```
400 EMAIL_TEMPLATE_UPDATE_NOT_ALLOWED (INVALID_ARGUMENT)
```

Not a console bug and not specific to this project: there is a Google support
thread with the same title from June 2026, so it reads as a recent Google-side
restriction. Steps 1-4 below still ran (the domain is live and authorized);
step 5 is the one that fails.

So the host is rewritten in our own send path instead —
`functions/src/auth/actionLinkHost.ts`, applied in `sendVerificationEmail` and
`processPasswordResetEmail`. Firebase Hosting serves a byte-identical
`/__/auth/action` handler on every domain attached to the site, and the link
carries its own `apiKey` and `oobCode`, so only the host differs.

**If Google lifts the restriction:** set the action URL project-wide (step 5),
then delete `actionLinkHost.ts` and its two call sites. The rewrite is a
workaround, not the intended design.

## Problem

Every verification and password-reset link we send points at Firebase Auth's
default action handler:

```
https://stitchpad-30607.firebaseapp.com/__/auth/action?mode=verifyEmail&oobCode=…&apiKey=…&lang=en
```

`sendVerificationEmail` calls `admin.auth().generateEmailVerificationLink(email)`
with no `ActionCodeSettings` (`functions/src/auth/sendVerificationEmail.ts`), so
the link inherits the project-level action URL. Confirmed in the Identity
Toolkit config:

```
notification.sendEmail.callbackUri = "https://stitchpad-30607.firebaseapp.com/__/auth/action"
notification.sendEmail.dnsInfo.customDomainState = "NOT_STARTED"
```

`*.firebaseapp.com` is one of the most heavily phishing-abused hostnames on the
web. Carrier DNS filters, "safe browsing" data plans, and device-level content
filters — common on Nigerian mobile networks — blocklist it wholesale. A user
behind such a filter taps the link and gets Chrome's **"This site can't be
reached"** (a DNS/connection failure, *not* a Firebase error page — an expired
or consumed `oobCode` renders a styled Firebase page instead).

Reported 2026-07-22 by a live user who reproduced it with two different email
addresses. Signup verification is a hard gate (`NavGraph.kt:156`), so an
affected user is permanently locked out at the front door — and it is invisible
in metrics, indistinguishable from ordinary signup drop-off.

## Fix

Serve the action handler from a domain we control. Firebase Hosting serves
`/__/auth/action` automatically on *any* domain attached to the project's
hosting site.

`getstitchpad.com` and `link.getstitchpad.com` are on Vercel (both 404 on
`/__/auth/action`), so this must be a **new subdomain on Firebase Hosting**, not
a repoint of an existing one.

### Steps

Steps 1-4 are done. Step 5 is blocked by Google (see above); the code rewrite
stands in for it.

1. **Attach the domain to Firebase Hosting.** ✅ done 2026-07-22
   Firebase Console → Hosting → site `stitchpad-30607` → Add custom domain →
   `auth.getstitchpad.com`. Quick setup offers a single CNAME
   (`auth` → `stitchpad-30607.web.app`). DNS is Cloudflare: set the record to
   **DNS only** (grey cloud) — a proxied record breaks the cert challenge with
   no useful error. Cert minted in ~40 minutes.

2. **Verify the handler is live before switching anything.** ✅ Must be 200:

   ```bash
   curl -sI https://auth.getstitchpad.com/__/auth/action | head -1
   ```

3. **Add it to Auth's authorized domains.** ✅ done 2026-07-22
   Console → Authentication → Settings → Authorized domains → add
   `auth.getstitchpad.com`. **Keep `stitchpad-30607.firebaseapp.com`** — links
   already sitting in users' inboxes point at it, and Google Sign-In uses it.
   Required: without it the rewritten links would be rejected as an
   unauthorized domain.

4. **Rewrite the host in our send path.** ✅ done — `actionLinkHost.ts`,
   applied in `sendVerificationEmail` and `processPasswordResetEmail`. Covers
   every email we actually send, since both go out through Resend rather than
   Firebase's built-in sender.

5. **Point the action URL at it.** ❌ blocked — `EMAIL_TEMPLATE_UPDATE_NOT_ALLOWED`.
   Console → Authentication → Templates → Email address verification → edit →
   "Customize action URL" → `https://auth.getstitchpad.com/__/auth/action`.
   This is project-wide: it would apply to password reset and email-change links
   too. `scripts/set-auth-action-url.sh` performs the same update via the admin
   API — retry it occasionally to see whether Google has lifted the restriction.

   Verify:

   ```bash
   curl -s -H "Authorization: Bearer $(gcloud auth print-access-token)" \
     -H "x-goog-user-project: stitchpad-30607" \
     "https://identitytoolkit.googleapis.com/admin/v2/projects/stitchpad-30607/config" \
     | grep callbackUri
   ```

6. **Smoke test end to end.** Sign up with a throwaway address, open the email,
   confirm the link host is `auth.getstitchpad.com` and that tapping it verifies
   the account. Then do the same for password reset. On a Nigerian mobile data
   connection if you can — that's the network that surfaced the bug.

### Rollback

Revert the `actionLinkHost` call in the two handlers and redeploy those
functions; links go back to `firebaseapp.com` immediately. Links already sent on
the custom domain keep working as long as the hosting domain stays attached, so
don't detach it for ~24h after a rollback (codes live ~1h, but be generous).

Scoped deploy:

```bash
cd functions && npm run build && cd ..
firebase deploy --only functions:sendVerificationEmail,functions:processPasswordResetEmail
```

## Unblocking an individual user meanwhile

Support workaround for anyone who reports the same symptom — mark the address
verified directly:

```bash
curl -s -X POST \
  -H "Authorization: Bearer $(gcloud auth print-access-token)" \
  -H "x-goog-user-project: stitchpad-30607" \
  -H "Content-Type: application/json" \
  -d '{"localId":"<UID>","emailVerified":true}' \
  "https://identitytoolkit.googleapis.com/v1/projects/stitchpad-30607/accounts:update"
```

Look the UID up with `accounts:lookup` (`{"email":["<address>"]}`). Only do this
for a user who has actually contacted support from that address — it bypasses
proof of ownership.

Self-serve alternatives to offer first: open the link on Wi-Fi instead of mobile
data, or switch the phone's DNS to 8.8.8.8.

## Considered and not chosen

- **Self-host the handler** on the Vercel app (`applyActionCode` with the
  `oobCode`). Full control over the page, but it means new routes, CSP changes,
  and owning an auth-critical page. The hosting custom domain gets the same
  outcome with zero code.
- **6-digit code instead of a link.** Removes the browser from the flow
  entirely, which is the most robust answer for users on filtered mobile
  networks — but it's a real feature (new function, new UI, new tests), not a
  hotfix. Worth its own backlog item; the domain move should ship first.

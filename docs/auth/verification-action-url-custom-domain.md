# Email action links — move off `firebaseapp.com` onto our own domain

Status: proposed (2026-07-22). Ops + one config change; no app release required.

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
hosting site, so this needs no new code — only a hosting custom domain plus a
one-line Auth config change.

`getstitchpad.com` and `link.getstitchpad.com` are on Vercel (both 404 on
`/__/auth/action`), so this must be a **new subdomain on Firebase Hosting**, not
a repoint of an existing one.

### Steps

1. **Attach the domain to Firebase Hosting.**
   Firebase Console → Hosting → site `stitchpad-30607` → Add custom domain →
   `auth.getstitchpad.com`. Add the A/TXT records Firebase gives you at the DNS
   provider, then wait for the cert to provision (minutes to ~24h).

2. **Verify the handler is live before switching anything.** Must be 200:

   ```bash
   curl -sI https://auth.getstitchpad.com/__/auth/action | head -1
   ```

3. **Add it to Auth's authorized domains.**
   Console → Authentication → Settings → Authorized domains → add
   `auth.getstitchpad.com`. **Keep `stitchpad-30607.firebaseapp.com`** — links
   already sitting in users' inboxes point at it, and Google Sign-In uses it.

4. **Point the action URL at it.**
   Console → Authentication → Templates → Email address verification → edit →
   "Customize action URL" → `https://auth.getstitchpad.com/__/auth/action`.
   This is project-wide: it applies to password reset and email-change links
   too, no per-template change needed.

   Verify:

   ```bash
   curl -s -H "Authorization: Bearer $(gcloud auth print-access-token)" \
     -H "x-goog-user-project: stitchpad-30607" \
     "https://identitytoolkit.googleapis.com/admin/v2/projects/stitchpad-30607/config" \
     | grep callbackUri
   ```

5. **Smoke test end to end.** Sign up with a throwaway address, open the email,
   confirm the link host is `auth.getstitchpad.com` and that tapping it verifies
   the account. Do this on a Nigerian mobile data connection if you can — that's
   the network that surfaced the bug.

### Rollback

Set the action URL back to `https://stitchpad-30607.firebaseapp.com/__/auth/action`
in the Templates screen. Links already sent from the custom domain keep working
as long as the hosting domain stays attached, so don't detach it for ~24h after
a rollback (links live ~1h by default, but be generous).

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

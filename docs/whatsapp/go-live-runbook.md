# StitchPad WhatsApp Support Bot — Go-Live Runbook

Everything needed to take the bot from merged code to a live support line.
Project: `stitchpad-30607` · Region: `europe-west1` · Function: `whatsappWebhook`.

---

## Part 1 — Your end (Meta Business Manager)

These are manual and gate everything. The **business verification** step can take
several days, so start it first.

### 1.1 Get the dedicated number
- Buy a **new Nigerian mobile SIM** (MTN/Airtel/Glo/9mobile), NIN-registered.
- **Never install WhatsApp / WhatsApp Business on it.** (If a number is already on
  WhatsApp, delete that account first: WhatsApp → Settings → Account → Delete my
  account.)
- You only need to **receive one SMS or voice call** on it for the verification
  code. Keep the SIM topped up occasionally so it isn't recycled.

### 1.2 Meta app + WhatsApp product
1. **business.facebook.com** → create / confirm a Business Portfolio (use the
   StitchPad business details).
2. **developers.facebook.com → My Apps → Create App → "Business"** → add the
   **WhatsApp** product. This creates a test WhatsApp Business Account (WABA) and
   a free **test number** you can dry-run with immediately.
3. **WhatsApp → API Setup → Add phone number** → enter your dedicated number →
   verify with the SMS/voice code. Set a **display name** (Meta reviews it).
4. **Business Settings → Security Center → Start verification** → upload business
   docs. (Lifts you off the test tier; the slow part — start early.)

### 1.3 Collect the 5 credentials
| Where in Meta | Becomes the secret |
|---|---|
| WhatsApp → API Setup → your number's **Phone number ID** | `WHATSAPP_PHONE_NUMBER_ID` |
| App → Settings → Basic → **App secret** (Show) | `WHATSAPP_APP_SECRET` |
| Business Settings → Users → **System Users** → add (Admin) → **Assign assets** (this App + the WABA, with `whatsapp_business_messaging` + `whatsapp_business_management`) → **Generate token** → **no expiry** | `WHATSAPP_TOKEN` |
| A random string you invent (e.g. from a password manager) | `WHATSAPP_VERIFY_TOKEN` |
| Your own WhatsApp number(s), E.164 digits, comma-separated, e.g. `2348012345678` | `WHATSAPP_FOUNDER_NUMBERS` |

`RESEND_API_KEY` already exists project-wide (used by the email functions) — no
action unless it's somehow missing.

---

## Part 2 — Deploy (me / you, once credentials are in hand)

Run from the `functions/` directory, with the Firebase CLI logged in
(`firebase login`) and the project selected (`firebase use stitchpad-30607`).

### 2.1 Set the secrets (each command prompts for the value — paste + Enter)
```bash
firebase functions:secrets:set WHATSAPP_TOKEN
firebase functions:secrets:set WHATSAPP_VERIFY_TOKEN
firebase functions:secrets:set WHATSAPP_APP_SECRET
firebase functions:secrets:set WHATSAPP_PHONE_NUMBER_ID
firebase functions:secrets:set WHATSAPP_FOUNDER_NUMBERS
# RESEND_API_KEY should already be set; confirm with:
firebase functions:secrets:access RESEND_API_KEY
```

### 2.2 Deploy the webhook
Before the branch is merged, deploy just the webhook so other functions aren't
touched by branch code:
```bash
firebase deploy --only functions:whatsappWebhook
```
After PR #141 is merged to `main`, the normal `npm run deploy` includes it.

The deploy prints the function URL. It will be:
```
https://europe-west1-stitchpad-30607.cloudfunctions.net/whatsappWebhook
```

### 2.3 Wire the Meta webhook
Meta App → **WhatsApp → Configuration → Webhook → Edit**:
- **Callback URL:** the function URL above
- **Verify token:** the exact value you set for `WHATSAPP_VERIFY_TOKEN`
- Click **Verify and save** (Meta calls the GET handshake — should succeed)
- **Subscribe** to the **`messages`** field.

### 2.4 Seed the knowledge base
Add the starter Q&A from `docs/whatsapp/support-knowledge-base.md` to the
`supportKnowledge` Firestore collection (console, or the JSON seed via an admin
script). The bot reads it live — no deploy needed for later edits.
**Verify prices, Free-plan limits, and exact screen labels against the live app
before launch.**

---

## Part 3 — Live smoke test
From a normal phone (during Meta test mode, add it to the WABA's allowed
recipients first):
1. **Handshake** — step 2.3 shows "verified". ✅
2. **Onboarding** — message the support number → bot asks you to reply **YES**
   (with terms/privacy links) → reply YES → it offers **1 English / 2 Pidgin** →
   pick one.
3. **Answer** — ask a seeded question (e.g. "how do I add a customer?") in English
   and again in Pidgin → grounded answers.
4. **Off-topic** — "what's the weather" → it declines / points to support, no
   made-up answer.
5. **Account** — from a confirmed StitchPad number, "what plan am I on?" → consent
   prompt → YES → your plan.
6. **Escalation + takeover** — "I want a refund" or "let me talk to a human" → bot
   says it's connecting you; `support@getstitchpad.com` gets a ticket and your
   `WHATSAPP_FOUNDER_NUMBERS` phone gets a relay → reply `#reply <number> <message>`
   from that phone → the user receives it → `#resolve <number>` → bot resumes.
7. **Idempotency** — no double replies on a retried delivery.

Watch **Cloud Logging** (Functions → `whatsappWebhook`) for any errors.

---

## Quick reference — the 6 secrets
| Secret | Value |
|---|---|
| `WHATSAPP_PHONE_NUMBER_ID` | ID of the dedicated support number |
| `WHATSAPP_APP_SECRET` | Meta app secret (signature verification) |
| `WHATSAPP_TOKEN` | permanent system-user token |
| `WHATSAPP_VERIFY_TOKEN` | random string, also entered in Meta webhook config |
| `WHATSAPP_FOUNDER_NUMBERS` | your own number(s), for relay + `#reply`/`#resolve` |
| `RESEND_API_KEY` | already set (escalation ticket emails) |

# StitchPad Cloud Functions

Firebase Cloud Functions (1st gen, Node 20, region `europe-west1`, project
`stitchpad-30607`). Source in `src/`, compiled to `lib/` by `npm run build`.

## Commands

```bash
npm run build       # tsc → lib/
npm run lint        # eslint (single-quote rule; runs in CI before jest)
npm test            # jest
npm run test:rules  # firestore.rules tests under the emulator
npm run deploy      # build + firebase deploy --only the allow-listed functions
```

`npm run deploy` only deploys functions in its `--only` allow-list — **add any new
function there or it silently won't deploy.**

## Deploy-time configuration

### Secrets (Secret Manager — `.runWith({ secrets: [...] })`)

Set once per project; never commit. Used by billing/email/auth functions.

| Secret | Used by |
| --- | --- |
| `PAYSTACK_SECRET_KEY` | `initializeSubscriptionCheckout`, `paystackWebhook`, `initializeGiftCheckout` |
| `RESEND_API_KEY` | digest/verification/password-reset emails, `paystackWebhook` (gift emails) |

```bash
firebase functions:secrets:set PAYSTACK_SECRET_KEY
firebase functions:secrets:set RESEND_API_KEY
```

### Non-secret config (env vars via `.env`)

The functions read non-secret config from `process.env` first. `.env` and `.env.*`
are **gitignored** (see `.gitignore`), so they are NOT committed — a fresh clone or
a CI deploy must recreate them, or the value silently falls back to a default.

Required for the gift flow (Paystack redirect after a gift payment):

```
# functions/.env  (gitignored — recreate on a fresh clone / in CI)
PAYSTACK_GIFT_CALLBACK_URL=https://getstitchpad.com/gift/success
```

`initializeGiftCheckout` reads `PAYSTACK_GIFT_CALLBACK_URL` (then
`PAYSTACK_CALLBACK_URL`) and passes it to Paystack as the post-payment redirect.
Without it, Paystack uses its dashboard default redirect — payment + entitlement
still work, but the buyer won't land on `/gift/success`.

### Legacy runtime config (`functions.config()`) — deprecated

Older values still resolved at runtime as a fallback (e.g. `paystack.secret_key`,
`paystack.callback_url`). The legacy `functions:config:*` CLI is deprecated and the
Runtime Config service shuts down in March 2027 — migrate these to `.env` / params
before then. Prefer `.env` (above) for any NEW non-secret config.

# Apple root CA certificates

`appleVerifier.ts` loads every `*.cer` / `*.der` file in this directory as the
trusted root set for verifying Apple's signed transactions and App Store Server
Notifications. These are **public** certificates (safe to commit) — they are
*not* secrets.

Before deploying the Apple IAP functions, download the four current Apple root
CAs from <https://www.apple.com/certificateauthority/> and drop the `.cer` files
here:

- `AppleRootCA-G3.cer` (required — the StoreKit signing chain roots here)
- `AppleRootCA-G2.cer`
- `AppleComputerRootCertificate.cer`
- `AppleIncRootCertificate.cer`

If this directory has no `.cer`/`.der` files at runtime, the verifier throws and
`verifyAppleTransaction` / `appStoreServerNotifications` fail closed (no grant) —
intentional, so a misconfigured deploy never trusts unverified data.

## Required secrets / env (set before deploy)

- `APPLE_BUNDLE_ID` — the app bundle id (e.g. `com.danzucker.stitchpad`).
- `APPLE_APP_APPLE_ID` — numeric App Store app id (production notification
  verification needs it; omit in sandbox).
- `APPLE_IAP_PRIVATE_KEY` — contents of the App Store Connect `.p8` key
  (reconciliation cron / App Store Server API).
- `APPLE_IAP_KEY_ID`, `APPLE_IAP_ISSUER_ID` — the key + issuer ids for that key.

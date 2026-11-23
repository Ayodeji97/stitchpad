# Paid weekly digest smoke — 23 November 2026

Source: Daniel's urgent notifications-first spec in handoff PR #402
(`docs/handoff/2026-11-23-notifications-first-email-paid-only.md`).

## Result

- 1,034 backend tests pass (76 suites), including the existing Resend account-quota
  and hard-bounce regressions. TypeScript build and lint pass (existing warnings).
- Real Auth + Firestore emulator smoke passes using `productionDigestIO`, not a
  duplicate recipient mapper. Email and FCM are captured in memory: no real sends.
- Launch-grant owners and expired payers receive no email; a paid staff account is
  not classified as a paid owner. Owner/staff daily push and inbox writes continue.
- Active Paystack owner receives one useful summary on the assigned weekday;
  same-day reruns, off-days and a five-day-old manual-send stamp suppress email.
  The next assigned week sends. Runtime `none` and `all` config changes work.
- Empty summaries do not turn into setup/first-order/quiet email nudges.
- English/French weekly subject and heading are covered. French still requires
  the native-speaker review already recorded in the handoff.

## Reproduce locally

Start Auth on 9099 and Firestore on 8085 with the local emulator rules. Do not
change production configuration. From the repository root:

```sh
npm run build --prefix functions
node functions/scripts/emulatorSmokeWeeklyDigest.js
```

The smoke script pins both SDKs to loopback emulator hosts, seeds only named
`weekly-digest-smoke-*` test accounts, and replaces email/FCM transports before
running the production adapter. Other local emulator accounts are filtered out.

## Release

Only `dailyDigest` needs deployment. Daniel deploys after review/merge, starting
with `git pull --ff-only` on local main, then the backend build. No deployment,
production config writes or real email sends were performed during this work.
Missing/invalid `config/app.digestEmailAudience` defaults to `paid`; `none` disables
scheduled digest email without a deployment. `all` broadens the audience but keeps
weekly cadence, opt-outs, verification, bounce suppression and empty-email suppression.

The Settings toggle follow-up remains with the paywall build; this change is
functions-only. The one-off free-period notice campaign is unchanged.

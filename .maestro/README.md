# Maestro E2E — StitchPad

Spike result (2026-08-07, Maestro 2.8.0, CMP 1.11.1, iOS 26.4 sim): **Maestro works
on StitchPad's iOS build.** Compose Multiplatform exposes its semantics tree to
UIAccessibility, so Maestro sees every label with correct bounds — it is not an
opaque canvas. Verified end to end: sign-in, navigation, text input, and a
customer write confirmed present in Firestore at the database level.

## Running

```bash
./scripts/e2e-ios.sh <SIM_UDID>                            # all flows
./scripts/e2e-ios.sh <SIM_UDID> .maestro/login.yaml        # one flow
xcrun simctl list devices available                        # find a UDID
```

`manual/offline-sync-indicator.yaml` is excluded from the default suite because it
needs the Firestore backend killed mid-flow, which `e2e-ios.sh` never does. Run it
by hand — the header of that file has the three commands.

The script boots the Firestore + Auth emulators, **wipes them to a clean slate**,
seeds deterministic data, builds a debug app with `USE_FIREBASE_EMULATOR` flipped
on, wipes the app + keychain, installs, and runs the flows.

The wipe is load-bearing, not hygiene. The seed script only writes its own
`seed-*` documents, so records created by a previous run would survive — and an
assertion like "the customer I just created is visible" would then pass against
the stale row even if the create silently failed. Same false-green class as
gotcha 1 below.

Two guards come with that:

- **A run lock.** Two concurrent runs would fight over the emulators — the first
  to finish would stop them mid-flight for the second. A second run exits with a
  clear message instead.
- **`--reuse` is required to touch emulators this script did not start.** Those
  belong to a manual session (e.g. the staff smoke runbook), and the wipe would
  destroy that session's data. Emulators started elsewhere are never stopped by
  this script.

**Flows never run against production.** That is deliberate, not just tidiness —
see "Silent offline mode" below.

Setup, once:

```bash
curl -fsSL "https://get.maestro.mobile.dev" | bash   # installs to ~/.maestro
```

Needs Java and `idb_companion` (`brew install idb-companion`).

While writing flows, `maestro --device <UDID> hierarchy` dumps the live
accessibility tree — that is how you find selectors.

## The emulator flag

`USE_FIREBASE_EMULATOR` in `EmulatorConfig.kt` is a tracked constant that must
stay `false` on the branch. `e2e-ios.sh` flips it for the build and restores it
on exit via a trap, including on failure or Ctrl-C. If a run is killed in a way
that skips the trap (`kill -9`), check that file before committing.

The seed (`functions/scripts/emulatorSetupStaff.js`) must set `businessName` —
`UserRepository.hasWorkshopProfile()` gates purely on it being non-blank, so
without it every sign-in lands on "Set up your workshop" instead of the dashboard.

## Gotchas — all hit during the spike

**1. An occluded tap still reports `COMPLETED`.**
The most dangerous failure mode. Compose semantic nodes report their layout
bounds regardless of what the system draws on top. When the soft keyboard covers
a button, Maestro taps those coordinates anyway, the keyboard eats the tap, and
the step logs as passed. In the spike this typed a stray digit into the phone
field instead of saving, and reported success.

> Always follow a tap with `assertVisible` on the *destination*. Never trust a
> green step log alone. Treat this as a review rule on any `.maestro/` diff.

**2. Silent offline mode.**
Against production the app hit `[FirebaseFirestore] WriteStream Stream error` and
ran entirely on local cache — reads served stale data, writes queued locally, and
the UI behaved as if everything succeeded. A flow can pass end to end while
nothing reaches the server. Two consequences: assert at the database level when
you care, and prefer the emulator, which is on loopback and cannot silently
degrade this way. Queued writes also flush to production whenever connectivity
returns, so `simctl uninstall` the app rather than leaving a stale container.

**3. `hideKeyboard` is not supported.**
Fails with "app uses a custom input or doesn't expose a standard dismiss action".
Dismiss by tapping a static label instead (e.g. `- tapOn: "EMAIL (OPTIONAL)"`).

**4. The Keychain survives `simctl uninstall`.**
Firebase Auth persists there, so reinstalling does *not* sign the user out and
sign-in flows never see a login screen. Use `xcrun simctl keychain <UDID> reset`.

**5. Interstitials interrupt flows — including nondeterministic system ones.**
Three seen so far: the app's notification-permission sheet, an iOS
bilingual-keyboard dialog, and iOS **"Save Password?"** (iCloud Keychain), which
appears on Apple's own heuristics after a password sign-in. That last one made
the login flow pass on one run and fail on the very next with identical inputs.

`e2e-ios.sh` disables the password prompt on the simulator
(`defaults write com.apple.Preferences AutoFillPasswords -bool false`) rather
than racing it. Flows also guard defensively with `runFlow: when: visible:`.

Watch the casing when guarding: the system dialog's button is **"Not Now"**, the
app's sheet button is **"Not now"**. Key conditions off a sheet's *title* rather
than its button text so the two cannot be confused.

**6. No stable selectors anywhere.**
`resource-id` and `text` are empty on every app node; everything lands in
`accessibilityText`. Fields are addressable only by placeholder copy, so any
string change breaks flows. Adding `Modifier.testTag` (plus
`testTagsAsResourceId = true` on Android) is the durable fix and doubles as
accessibility work — but add it reactively, where flows actually break, rather
than as an upfront codebase-wide sweep.

**7. Merged parent + leaf child carry the same text.**
Tappable cards expose a concatenated parent node and individual children, and
form labels share text with their field. One string can match several nodes —
pin with `index:`.

**8. Plain-text selectors require a FULL match, not a substring.**
`visible: "Offline"` against a banner whose actual accessibility text is
"Offline — saved on this phone, will sync later" fails — silently and
indefinitely, right up to the configured timeout — even though the banner is
on screen the entire time. Maestro compiles the string as a regex and matches
it with `Pattern.matches` (whole string), not `.find`/`.contains`. Confirmed by
hand: the bare string failed for 60s straight against an app that had been
showing the target text continuously with no relaunch, while `.*Offline.*`
against the identical screen passed on the very first poll. Any assertion
against formatted or composed UI text (a sentence, not a short exact label)
needs `.*...*` wrapping. Short exact-match labels ("Add Customer", "Not
synced", a customer's plain name) are unaffected — they equal the whole node
text already.

## Debugging

On failure Maestro writes a screenshot *and* the full hierarchy JSON to
`~/.maestro/tests/<timestamp>/`, which usually makes a failure diagnosable
without re-running.

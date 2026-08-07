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

The script boots the Firestore + Auth emulators, seeds deterministic data,
builds a debug app with `USE_FIREBASE_EMULATOR` flipped on, wipes the app +
keychain, installs, and runs the flows.

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

**5. Interstitials interrupt flows.**
A notification-permission sheet and an iOS bilingual-keyboard dialog both
appeared mid-flow and covered controls. Guard with `runFlow: when: visible:`.

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

## Debugging

On failure Maestro writes a screenshot *and* the full hierarchy JSON to
`~/.maestro/tests/<timestamp>/`, which usually makes a failure diagnosable
without re-running.

package com.danzucker.stitchpad.core.config

/**
 * QA-ONLY local Firebase-emulator switch.
 *
 * Flip [USE_FIREBASE_EMULATOR] to `true` and rebuild a **debug** app to point
 * Firebase Auth + Firestore + Storage at the LOCAL emulators instead of production. This is
 * how the Owner + Staff experience is smoke-tested against real, seeded data
 * (including the future Slice-8e `allow list` rule) safely, before any prod change.
 *
 * Doubly guarded: the actual connection ([connectFirebaseEmulatorsIfEnabled]) also
 * requires [com.danzucker.stitchpad.core.debug.isDebugBuild], so it can NEVER
 * connect in a release build even if this constant is accidentally left `true`.
 * Keep it `false` on `main`; flip locally, test, flip back. See
 * `docs/staff/slice8-emulator-smoke-runbook.md`.
 */
const val USE_FIREBASE_EMULATOR = false

/** Emulator ports — must match `firebase.emulator.json`. */
const val FIRESTORE_EMULATOR_PORT = 8080
const val AUTH_EMULATOR_PORT = 9099
const val STORAGE_EMULATOR_PORT = 9199

/**
 * Host the running app reaches the emulators on. The iOS simulator shares the
 * Mac's loopback (`127.0.0.1`); the Android emulator reaches the host machine via
 * the special alias `10.0.2.2`.
 */
expect fun firebaseEmulatorHost(): String

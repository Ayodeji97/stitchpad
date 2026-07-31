package com.danzucker.stitchpad.core.config

// The Android emulator reaches the host machine (where the Firebase emulators run)
// via the special loopback alias 10.0.2.2, not 127.0.0.1 (which is the guest).
actual fun firebaseEmulatorHost(): String = "10.0.2.2"

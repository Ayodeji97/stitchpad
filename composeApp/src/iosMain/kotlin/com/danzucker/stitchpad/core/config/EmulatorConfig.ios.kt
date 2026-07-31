package com.danzucker.stitchpad.core.config

// The iOS simulator shares the host Mac's network stack, so the Firebase emulators
// running on the Mac are reachable at 127.0.0.1.
actual fun firebaseEmulatorHost(): String = "127.0.0.1"

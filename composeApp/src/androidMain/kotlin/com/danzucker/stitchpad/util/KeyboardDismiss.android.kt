package com.danzucker.stitchpad.util

// Android dismisses the keyboard through Compose focus (clearFocus); nothing extra needed.
actual fun dismissNativeKeyboard() = Unit

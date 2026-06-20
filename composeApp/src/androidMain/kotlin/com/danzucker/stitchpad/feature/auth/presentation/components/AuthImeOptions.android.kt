package com.danzucker.stitchpad.feature.auth.presentation.components

import androidx.compose.ui.text.input.PlatformImeOptions

// Android autofill works via Compose contentType semantics + AutofillManager.commit();
// no platform IME options are needed.
actual fun authImeOptions(autofill: AuthAutofill): PlatformImeOptions? = null

package com.danzucker.stitchpad.feature.notebookimport.presentation

import androidx.compose.runtime.Immutable

/** The existing customer-cap sheet is open with these numbers. Held in ViewModel state. */
@Immutable
data class CapSheet(val activeCount: Int, val customerCap: Int)

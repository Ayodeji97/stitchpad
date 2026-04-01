package com.danzucker.stitchpad.feature.auth.data

import com.danzucker.stitchpad.feature.auth.domain.PatternValidator

class EmailPatternValidator : PatternValidator {
    private val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    override fun matches(value: String): Boolean = emailRegex.matches(value)
}

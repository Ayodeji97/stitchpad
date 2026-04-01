package com.danzucker.stitchpad.feature.auth.data

import com.danzucker.stitchpad.feature.auth.domain.PatternValidator

class FakePatternValidator(
    private val shouldMatch: Boolean = true
) : PatternValidator {
    override fun matches(value: String): Boolean = shouldMatch
}

package com.danzucker.stitchpad.feature.auth.domain

interface PatternValidator {
    fun matches(value: String): Boolean
}

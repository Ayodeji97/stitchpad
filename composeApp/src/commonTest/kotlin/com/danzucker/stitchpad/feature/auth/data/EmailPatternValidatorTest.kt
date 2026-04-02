package com.danzucker.stitchpad.feature.auth.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmailPatternValidatorTest {

    private val validator = EmailPatternValidator()

    // --- Valid emails ---

    @Test
    fun simpleValidEmail() {
        assertTrue(validator.matches("user@example.com"))
    }

    @Test
    fun emailWithDotsInLocalPart() {
        assertTrue(validator.matches("first.last@example.com"))
    }

    @Test
    fun emailWithPlusTag() {
        assertTrue(validator.matches("user+tag@example.com"))
    }

    @Test
    fun emailWithSubdomain() {
        assertTrue(validator.matches("user@mail.example.com"))
    }

    @Test
    fun emailWithTwoLetterTld() {
        assertTrue(validator.matches("user@example.ng"))
    }

    @Test
    fun emailWithNumbers() {
        assertTrue(validator.matches("user123@example456.com"))
    }

    @Test
    fun emailWithUnderscore() {
        assertTrue(validator.matches("user_name@example.com"))
    }

    @Test
    fun emailWithHyphenInDomain() {
        assertTrue(validator.matches("user@my-domain.com"))
    }

    // --- Invalid emails ---

    @Test
    fun emptyString() {
        assertFalse(validator.matches(""))
    }

    @Test
    fun missingAtSymbol() {
        assertFalse(validator.matches("userexample.com"))
    }

    @Test
    fun missingDomain() {
        assertFalse(validator.matches("user@"))
    }

    @Test
    fun missingLocalPart() {
        assertFalse(validator.matches("@example.com"))
    }

    @Test
    fun missingTld() {
        assertFalse(validator.matches("user@example"))
    }

    @Test
    fun singleLetterTld() {
        assertFalse(validator.matches("user@example.c"))
    }

    @Test
    fun spaceInEmail() {
        assertFalse(validator.matches("user @example.com"))
    }

    @Test
    fun multipleAtSymbols() {
        assertFalse(validator.matches("user@@example.com"))
    }

    @Test
    fun plainText() {
        assertFalse(validator.matches("not-an-email"))
    }
}

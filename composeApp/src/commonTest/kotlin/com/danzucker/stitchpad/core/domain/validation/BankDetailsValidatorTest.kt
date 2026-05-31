package com.danzucker.stitchpad.core.domain.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BankDetailsValidatorTest {

    @Test
    fun `hasAnyInput is false when all three blank`() {
        assertFalse(BankDetailsValidator.hasAnyInput("", "", ""))
        assertFalse(BankDetailsValidator.hasAnyInput("   ", "\t", " "))
    }

    @Test
    fun `hasAnyInput is true when any field has content`() {
        assertTrue(BankDetailsValidator.hasAnyInput("GTBank", "", ""))
        assertTrue(BankDetailsValidator.hasAnyInput("", "Fola Joy", ""))
        assertTrue(BankDetailsValidator.hasAnyInput("", "", "0123456789"))
    }

    @Test
    fun `valid bank trio passes all three checks`() {
        val r = BankDetailsValidator.validate("GTBank", "Fola Joy Enterprises", "0123456789")
        assertTrue(r.isValid)
        assertTrue(r.isBankNameValid)
        assertTrue(r.isAccountNameValid)
        assertTrue(r.isAccountNumberValid)
    }

    @Test
    fun `bank name shorter than minimum fails just that field`() {
        val r = BankDetailsValidator.validate("G", "Fola Joy", "0123456789")
        assertFalse(r.isBankNameValid)
        assertTrue(r.isAccountNameValid)
        assertTrue(r.isAccountNumberValid)
        assertFalse(r.isValid)
    }

    @Test
    fun `account name shorter than minimum fails just that field`() {
        val r = BankDetailsValidator.validate("GTBank", "F", "0123456789")
        assertTrue(r.isBankNameValid)
        assertFalse(r.isAccountNameValid)
        assertTrue(r.isAccountNumberValid)
        assertFalse(r.isValid)
    }

    @Test
    fun `account number must be exactly 10 digits`() {
        assertFalse(
            BankDetailsValidator.validate("GTBank", "Fola Joy", "12345").isAccountNumberValid,
        )
        assertFalse(
            BankDetailsValidator.validate("GTBank", "Fola Joy", "12345678901").isAccountNumberValid,
        )
        assertFalse(
            BankDetailsValidator.validate("GTBank", "Fola Joy", "abcdefghij").isAccountNumberValid,
        )
        assertTrue(
            BankDetailsValidator.validate("GTBank", "Fola Joy", "0123456789").isAccountNumberValid,
        )
    }

    @Test
    fun `whitespace is stripped before length and regex checks`() {
        val r = BankDetailsValidator.validate("  GTBank  ", "  Fola Joy  ", " 0123456789 ")
        assertTrue(r.isValid)
    }

    @Test
    fun `validator constants match V1 NUBAN expectations`() {
        // Locks the public contract — the workshop / edit-profile take()
        // limits read these directly.
        assertEquals(40, BankDetailsValidator.MAX_BANK_NAME_LEN)
        assertEquals(60, BankDetailsValidator.MAX_ACCOUNT_NAME_LEN)
        assertEquals(10, BankDetailsValidator.ACCOUNT_NUMBER_LEN)
        assertEquals(2, BankDetailsValidator.MIN_BANK_NAME_LEN)
        assertEquals(2, BankDetailsValidator.MIN_ACCOUNT_NAME_LEN)
    }
}

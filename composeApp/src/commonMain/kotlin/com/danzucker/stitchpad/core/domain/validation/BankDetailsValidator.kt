package com.danzucker.stitchpad.core.domain.validation

/**
 * Validation rules for the bank-details trio surfaced on Invoice and
 * Deposit Receipt documents.
 *
 * NUBAN is the 10-digit Nigerian account number standard used by every
 * retail bank — no support for SWIFT, IBAN, or non-Nigerian accounts.
 * Group-required semantics (all three set, or all three blank) is enforced
 * by the caller via [hasAnyInput].
 */
object BankDetailsValidator {
    const val MIN_BANK_NAME_LEN: Int = 2
    const val MIN_ACCOUNT_NAME_LEN: Int = 2
    const val MAX_BANK_NAME_LEN: Int = 40
    const val MAX_ACCOUNT_NAME_LEN: Int = 60
    const val ACCOUNT_NUMBER_LEN: Int = 10

    private val ACCOUNT_NUMBER_REGEX = Regex("^\\d{10}$")

    data class Result(
        val isBankNameValid: Boolean,
        val isAccountNameValid: Boolean,
        val isAccountNumberValid: Boolean,
    ) {
        val isValid: Boolean
            get() = isBankNameValid && isAccountNameValid && isAccountNumberValid
    }

    /** True when at least one of the three fields is non-blank. */
    fun hasAnyInput(bankName: String, accountName: String, accountNumber: String): Boolean =
        bankName.isNotBlank() || accountName.isNotBlank() || accountNumber.isNotBlank()

    /** Per-field validation result. Whitespace is stripped before length / regex checks. */
    fun validate(bankName: String, accountName: String, accountNumber: String): Result = Result(
        isBankNameValid = bankName.trim().length >= MIN_BANK_NAME_LEN,
        isAccountNameValid = accountName.trim().length >= MIN_ACCOUNT_NAME_LEN,
        isAccountNumberValid = ACCOUNT_NUMBER_REGEX.matches(accountNumber.trim()),
    )
}

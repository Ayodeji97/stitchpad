package com.danzucker.stitchpad.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the caret placement used by [rememberSanitizedTextFieldValue]. The
 * scenarios are written as "what the IME proposed" -> "what came back from the
 * ViewModel", which is exactly the pair the composable has to reconcile.
 */
class CaretAfterSanitizeTest {

    @Test
    fun unchangedTextLeavesTheCaretAlone() {
        assertEquals(2, caretAfterSanitize(proposed = "1234", caret = 2, sanitized = "1234"))
    }

    @Test
    fun aRejectedCharacterMidStringHoldsTheCaretInPlace() {
        // "50|0" + "a" -> IME proposes "50a0" with the caret at 3; the filter drops
        // the "a", so the caret belongs back at 2 — not at 3, which is the drift.
        assertEquals(2, caretAfterSanitize(proposed = "50a0", caret = 3, sanitized = "500"))
    }

    @Test
    fun aRejectedCharacterAtTheStartKeepsTheCaretAtZero() {
        assertEquals(0, caretAfterSanitize(proposed = "a500", caret = 1, sanitized = "500"))
    }

    @Test
    fun aLeadingZeroStrippedByTheViewModelPullsTheCaretBack() {
        // trimStart('0'): typing "0" in front of "500" is a no-op edit.
        assertEquals(0, caretAfterSanitize(proposed = "0500", caret = 1, sanitized = "500"))
    }

    @Test
    fun charactersAcceptedBeforeTheCaretStillAdvanceIt() {
        assertEquals(1, caretAfterSanitize(proposed = "600", caret = 1, sanitized = "600"))
        assertEquals(3, caretAfterSanitize(proposed = "1a2b3c", caret = 6, sanitized = "123"))
    }

    @Test
    fun tailTruncationLeavesAnEarlierCaretUntouched() {
        // A length cap drops from the END, so a caret before the cut doesn't move.
        assertEquals(4, caretAfterSanitize(proposed = "123456789", caret = 4, sanitized = "12345678"))
    }

    @Test
    fun aWholesaleRewriteParksTheCaretAtTheEnd() {
        // Payment capping replaces the typed amount with the outstanding balance.
        // The result isn't a subsequence of what was typed, so there is no
        // meaningful position to preserve.
        assertEquals(5, caretAfterSanitize(proposed = "60000", caret = 5, sanitized = "50000"))
    }

    @Test
    fun clearingTheFieldCollapsesTheCaretToZero() {
        assertEquals(0, caretAfterSanitize(proposed = "abc", caret = 3, sanitized = ""))
    }

    @Test
    fun caretAtTheStartStaysAtTheStart() {
        assertEquals(0, caretAfterSanitize(proposed = "a123", caret = 0, sanitized = "123"))
    }

    @Test
    fun caretPastTheProposedTextClampsToTheSanitizedEnd() {
        assertEquals(3, caretAfterSanitize(proposed = "123", caret = 99, sanitized = "123"))
    }

    @Test
    fun repeatedCharactersAlignByPositionNotByValue() {
        // "111|1" + "a": the dropped char sits between two identical digits, so a
        // value-based match would be ambiguous; position-based alignment is not.
        assertEquals(3, caretAfterSanitize(proposed = "111a1", caret = 4, sanitized = "1111"))
    }
}

package com.danzucker.stitchpad.core.domain.model

enum class Currency(val code: String, val symbol: String, val decimals: Int) {
    NGN("NGN", "₦", 2),
    GHS("GHS", "GH₵", 2),
    KES("KES", "KSh", 2),
    XOF("XOF", "F CFA", 0),
    XAF("XAF", "FCFA", 0),
    GNF("GNF", "FG", 0),
    CDF("CDF", "FC", 2),
    ;

    companion object {
        fun fromCode(code: String?): Currency? =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) }
    }
}

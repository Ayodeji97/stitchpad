package com.danzucker.stitchpad.navigation

/**
 * Reduces a type-safe nav route string (fully-qualified class name, possibly with
 * path/query args) to a clean, PII-free screen name for analytics.
 * e.g. "com...OrderDetailRoute/123" -> "OrderDetail".
 */
fun screenNameFor(route: String?): String {
    if (route.isNullOrBlank()) return "Unknown"
    val classOnly = route
        .substringBefore('/')
        .substringBefore('?')
        .substringAfterLast('.')
    return classOnly.removeSuffix("Route").ifBlank { "Unknown" }
}

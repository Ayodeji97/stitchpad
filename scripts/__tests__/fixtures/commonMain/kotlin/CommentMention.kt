/**
 * Documenting the anti-pattern must NOT trip the scanner. We deliberately
 * avoid snap.data<Map<String, Any?>>() because it crashes iOS on first emit.
 */
// Also never use String.format( in commonMain — JVM-only, fails iOS link.
val safe = snap.data<UserDto>()

package com.danzucker.stitchpad.core.data

import com.danzucker.stitchpad.core.logging.AppLogger

/**
 * Decodes a single Firestore document, logging (and dropping) any document that
 * fails to decode instead of silently swallowing it.
 *
 * The previous `runCatching { ... }.getOrNull()` pattern made corrupt or
 * schema-mismatched documents vanish from list observers with no signal. This
 * helper keeps that resilience — one bad document never breaks the whole list —
 * but records the failure so it surfaces in production: [AppLogger.w] carries
 * the throwable, which the release CrashReportingAntilog reports as a
 * Crashlytics non-fatal.
 *
 * Only a stable *hash* of the document id is logged, never the raw id. Some
 * collections derive their id from user input (e.g. `customGarmentTypes` uses
 * the tailor's garment label via `sanitizeCustomGarmentDocId`), and AppLogger's
 * contract forbids logging user-provided content. The hash still lets distinct
 * failing documents be told apart in the Crashlytics dashboard.
 *
 * @param tag log tag of the calling repository
 * @param docId stable Firestore document id; hashed before logging
 * @param decode maps the raw document to a domain/DTO value; may throw
 * @return the decoded value, or `null` if [decode] threw
 */
internal fun <T> decodeDocOrLog(
    tag: String,
    docId: String,
    decode: () -> T,
): T? =
    runCatching { decode() }
        .onFailure { throwable ->
            AppLogger.w(tag = tag, throwable = throwable) {
                "Dropped Firestore document that failed to decode: idHash=${docIdHash(docId)}"
            }
        }
        .getOrNull()

/**
 * Stable, non-reversible fingerprint of a document id for privacy-safe logging.
 * Uses Kotlin's platform-consistent [String.hashCode] rendered as unsigned hex,
 * so the raw id — which may be user-provided — never reaches the log.
 */
internal fun docIdHash(docId: String): String = docId.hashCode().toUInt().toString(16)

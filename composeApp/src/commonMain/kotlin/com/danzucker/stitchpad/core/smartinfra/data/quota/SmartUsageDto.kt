package com.danzucker.stitchpad.core.smartinfra.data.quota

import kotlinx.serialization.Serializable

/**
 * Typed view of `users/{uid}/usage/smart_drafts` for client-side reads.
 *
 * Server is the source of truth for these fields (see `functions/src/smart/draftMessage.ts`);
 * the client only reads them to keep PlanCard's chip in sync with real Smart consumption
 * — `bonusBalance` drives the First Month bonus chip, `count` drives the post-First-Month
 * monthly chip. The user-doc `bonusCoins` field and the in-process `SmartUsageStore`
 * cannot drive a live counter on their own (bonusCoins seeded once at signup; the
 * in-process store starts null on cold boot).
 *
 * Only the fields the client needs are modeled. A typed DTO (not `Map<String, Any?>`)
 * is mandatory on Kotlin/Native — see `feedback_kmp_native_serializer_any` memory.
 */
@Serializable
data class SmartUsageDto(
    val bonusBalance: Int? = null,
    val count: Int? = null,
)

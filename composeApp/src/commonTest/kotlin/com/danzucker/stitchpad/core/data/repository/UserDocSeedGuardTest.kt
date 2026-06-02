package com.danzucker.stitchpad.core.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for the one-time billing/entitlement seed guard.
 *
 * [FirebaseUserRepository.createUserProfile] seeds `buildInitialUserDoc()`
 * (subscriptionTier, welcomeBonusAppliedAt, bonusCoins, …) through a guarded
 * transaction so re-entering Workshop Setup — skip-then-complete, reinstall, or
 * a doc already on the server — can never reset a paying user to FREE or
 * re-grant their welcome window. The decision is the pure internal function
 * [shouldSeedInitialUserDoc]; tests target it directly so we avoid faking
 * [dev.gitlive.firebase.firestore.FirebaseFirestore].
 */
class UserDocSeedGuardTest {

    @Test
    fun seeds_when_doc_does_not_exist_yet() {
        // Brand-new user, no doc on the server.
        assertEquals(
            true,
            shouldSeedInitialUserDoc(docExists = false, existingSubscriptionTier = null),
        )
    }

    @Test
    fun seeds_when_doc_exists_but_has_no_tier() {
        // The fire-and-forget profile write may have created the doc first with
        // only businessName/whatsapp/updatedAt — that doc has never been seeded.
        assertEquals(
            true,
            shouldSeedInitialUserDoc(docExists = true, existingSubscriptionTier = null),
        )
    }

    @Test
    fun does_not_reseed_a_returning_free_user() {
        // Already seeded — re-completing Workshop Setup must not reset anything.
        assertEquals(
            false,
            shouldSeedInitialUserDoc(docExists = true, existingSubscriptionTier = "free"),
        )
    }

    @Test
    fun does_not_reseed_a_paying_user() {
        // The regression this guard fixes: a PRO user re-entering setup must keep
        // their tier rather than being demoted to FREE.
        assertEquals(
            false,
            shouldSeedInitialUserDoc(docExists = true, existingSubscriptionTier = "pro"),
        )
    }
}

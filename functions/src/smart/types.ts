/**
 * Request/response types for the smartDraftMessage callable function.
 *
 * Kept in a shared module so promptBuilder + draftMessage handler + tests
 * all reference the same shapes.
 */

export type IntentType =
  | 'balance_reminder'
  | 'pickup_ready'
  | 'follow_up'
  | 'custom_note';

export type Language = 'en' | 'pcm'; // pcm = Nigerian Pidgin (ISO 639-3)

/**
 * Wire-name string for each Smart-feature consumer of the shared monthly
 * quota counter. Stored as keys in FreeTierUsageDoc.perFeature so we can
 * report which feature is consuming quota without a schema migration.
 *
 * Keep in sync with the Kotlin SmartFeatureKey enum in
 * core/smartinfra/domain/quota/SmartFeatureKey.kt.
 */
export type SmartFeatureKey =
  | 'draft'
  | 'postcaption'
  | 'referral_msg'
  | 'referral_bio'
  | 'contentplan_regen';

export interface DraftMessageRequest {
  intentType: IntentType;
  customerId: string;
  orderId: string;
  language: Language;
  customNotes?: string;
}

export interface DraftMessageResponse {
  draftText: string;
  remainingFreeQuota: number | null; // null = atelier tier
}

/**
 * Snapshot of customer + order data fetched server-side and passed to the
 * prompt builder. Decouples the prompt logic from Firestore document shapes.
 */
export interface DraftContext {
  customerFirstName: string;
  garmentLabel: string;
  depositFormatted: string;
  balanceFormatted: string;
  deadlineFormatted: string; // already-formatted string per the tailor's locale
}

export type Tier = 'free' | 'pro' | 'atelier';

/**
 * User profile fields the Smart layer cares about. Read from the user
 * document itself at `users/{uid}` — there is no separate `profile` subdoc.
 * Missing `tier` field defaults to 'free'.
 */
export interface UserProfileSummary {
  tier: Tier;
}

/**
 * Atelier-only intents that will be promoted to full Smart features in V1.5.
 * The server gate is in place today so callers below Atelier get
 * permission-denied immediately, before any quota is reserved.
 */
export type AtelierOnlyIntent = 'pricing_help' | 'reply_help';

/**
 * Free-tier usage doc at `users/{uid}/usage/smart_drafts`.
 *
 * `perFeature` is optional on read for back-compat with docs created
 * before the Smart Grow rollout. New writes always include it.
 */
export interface FreeTierUsageDoc {
  monthYear: string; // YYYY-MM
  count: number;
  limit: number;
  perFeature?: Record<string, number>;
  /**
   * Bonus coin balance (welcome bonus + future sponsored coins). Consumed
   * BEFORE the monthly `count` increments. Persists across month rollovers
   * (does not reset). Defaults to 0 for users created before V1.0.
   */
  bonusBalance?: number;
}

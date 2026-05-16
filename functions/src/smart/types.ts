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

export interface DraftMessageRequest {
  intentType: IntentType;
  customerId: string;
  orderId: string;
  language: Language;
  customNotes?: string;
}

export interface DraftMessageResponse {
  draftText: string;
  remainingFreeQuota: number | null; // null = premium tier
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

/**
 * User profile fields the Smart layer cares about. Read from
 * `users/{uid}/profile` doc.
 */
export interface UserProfileSummary {
  tier: 'free' | 'premium';
}

/**
 * Free-tier usage doc at `users/{uid}/usage/smart_drafts`.
 */
export interface FreeTierUsageDoc {
  monthYear: string; // YYYY-MM
  count: number;
  limit: number;
}

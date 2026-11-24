/**
 * Types for the evening reminder push (see eveningReminders.ts / runEveningReminders.ts).
 *
 * One push per owner per evening at most. It carries, in priority order:
 *   1. due tomorrow and not Ready yet     (deadline risk)
 *   2. delivered, balance still owed      (money)
 *   3. Ready, not collected               (collection)
 * and, only when none of those apply, the end-of-day "record today's jobs" nudge.
 */
import { OrderScanDoc } from './types';

export type ReminderKind = 'due' | 'owed' | 'ready';

/** What we remember per (kind, order) so repeats follow the once + one-weekly-repeat rule. */
export interface ReminderEntry {
  /** Sends so far for this episode. due: max 1. owed/ready: max 2. */
  count: number;
  /** Epoch ms of the last send. */
  lastSentAt: number;
  /**
   * What the episode is anchored to: the deadline's Lagos day index (due), the
   * Delivered moment (owed) or the Ready moment (ready). A different anchor means
   * a NEW episode — the deadline moved, or the order went back and became Ready again.
   */
  anchor: number;
}

/** `users/{uid}/private/reminderState` — server-only (no client rule matches it). */
export interface ReminderState {
  /** Lagos 'YYYY-MM-DD' of the last evening push. The 1-per-evening cap. */
  lastEveningPushDate: string | null;
  /** Lagos day index of the last end-of-day nudge. */
  lastNudgeDayIndex: number | null;
  /** Keyed `${kind}:${orderId}`. */
  entries: Record<string, ReminderEntry>;
  /**
   * Lagos 'YYYY-MM-DD' of the last ENGAGEMENT push (read from digestState, never
   * written here). The nudge stands down on a day the engagement push already spoke.
   */
  engagementLastSentDate: string | null;
}

export interface ReminderItem {
  kind: ReminderKind;
  orderId: string;
  customerName: string;
  garmentSummary: string;
  /** See ReminderEntry.anchor. */
  anchor: number;
  /** 1 for the first send, 2 for the weekly repeat (due is always 1). */
  round: number;
  /** due: the deadline (epoch ms). */
  deadline?: number;
  /** owed: balance in major units. ready: balance when one exists, else undefined. */
  amount?: number;
  currency?: string;
  /** Whole days since the anchor (owed/ready). */
  daysSince?: number;
}

export interface ReminderPlan {
  due: ReminderItem[];
  owed: ReminderItem[];
  ready: ReminderItem[];
}

export interface ReminderRecipient {
  uid: string;
  email: string;
  locale?: string;
  /** dailyPushEnabled, inheriting from dailyDigestEmailEnabled exactly like the digest. */
  pushEnabled: boolean;
  /** `activityPushEnabled` from #409. false only when explicitly switched off. */
  activityPushEnabled: boolean;
}

export interface ReminderPushPayload {
  title: string;
  body: string;
  /** Existing app target: 'order' (with orderId), 'inbox', 'to_collect' or 'dashboard'. */
  target: 'order' | 'inbox' | 'to_collect' | 'dashboard';
  orderId?: string;
}

export interface ReminderIO {
  /** `config/app.reminderPushEnabled`. Missing = true. Read once per run. */
  isEnabled(): Promise<boolean>;
  /** Workshop OWNERS only — active staff are excluded. */
  listRecipients(): Promise<ReminderRecipient[]>;
  isAllowed(uid: string, email: string): boolean;
  loadState(uid: string): Promise<ReminderState>;
  saveState(uid: string, state: ReminderState): Promise<void>;
  /** Orders with /private/money joined on. Archived orders included; the detector drops them. */
  loadOrders(uid: string): Promise<OrderScanDoc[]>;
  /** Any customer created or edited at/after `sinceMs`. Only asked when a nudge is possible. */
  hasCustomerActivitySince(uid: string, sinceMs: number): Promise<boolean>;
  /** In-app inbox entries for the items in a push (create-once, deterministic ids). */
  writeInbox(uid: string, items: ReminderItem[]): Promise<void>;
  loadPushTokens(uid: string): Promise<string[]>;
  sendPush(tokens: string[], payload: ReminderPushPayload): Promise<{ successCount: number; invalidTokens: string[] }>;
  deletePushTokens(uid: string, tokens: string[]): Promise<void>;
}

export interface ReminderRunResult {
  considered: number;
  /** Users who got an evening push (any kind). */
  sent: number;
  /** Users whose push LED with each kind, plus nudges. */
  sentDue: number;
  sentOwed: number;
  sentReady: number;
  sentNudge: number;
  /** Order items mentioned across all pushes. */
  items: number;
  skippedKillSwitch: number;
  skippedOptedOut: number;
  skippedNotAllowed: number;
  skippedAlreadyPushedToday: number;
  skippedNothingToSay: number;
  skippedNoTokens: number;
  failed: number;
}

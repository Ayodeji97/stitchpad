// functions/src/notifications/types.ts
import { PushSummary } from './pushSummary';

/** A tailor's order as read from `users/{uid}/orders` (raw Admin SDK shape). */
export interface OrderScanDoc {
  id: string;
  customerName: string;
  status: 'PENDING' | 'IN_PROGRESS' | 'READY' | 'DELIVERED' | string;
  deadline: number | null;     // epoch millis; null = no deadline set
  archivedAt: number | null;   // epoch millis; non-null = archived (excluded)
  totalPrice: number;          // subtotal before the whole-order discount
  discount?: number;           // whole-order discount; payable = max(0, totalPrice - discount)
  payments: { amount: number }[];
  /** From /private/money. Empty means no cost recorded — profit cannot be computed. */
  costs?: { amount: number }[];
  depositPaid?: number; // legacy deposit field; only meaningful when payments is empty
  items: { garmentType?: string; customGarmentName?: string; description?: string }[];
  statusHistory?: { status: string; changedAt: number }[];
  /** Roster member id the order is assigned to; for STAFF members this is their auth uid. */
  assignedMemberId?: string | null;
  assignedMemberName?: string | null;
  updatedAt?: number;
  createdAt?: number;
}

export interface DigestItem {
  orderId: string;
  customerName: string;
  garmentSummary: string;
  deadline?: number; // present for dueSoon / overdue
  amount?: number;   // present for outstanding (naira)
  isOverdue?: boolean; // TO_COLLECT: money owed >= 7 days since Ready/Delivered
  /** Still PENDING — nobody has begun the work. Only meaningful for dueSoon/overdue. */
  notStarted?: boolean;
  /** Roster name of whoever it is assigned to, when it is assigned at all. */
  assigneeName?: string | null;
}

export interface DigestModel {
  dueSoon: DigestItem[];      // FULL, sorted soonest-first
  overdue: DigestItem[];      // FULL, sorted most-overdue-first
  outstanding: DigestItem[];  // FULL, sorted biggest-owed-first
}

export interface DigestRecipient {
  uid: string;
  email: string;
  /**
   * Whether `email` is a verified Firebase Auth address. Gates the EMAIL digest
   * ONLY — the in-app inbox, the owner's push, and the staff digests are all
   * independent of it. Do not turn this back into a filter in `listRecipients`:
   * staff digests are derived from the owner's recipient row, so dropping an
   * unverified owner silences every staff push in their workshop.
   */
  emailVerified: boolean;
  name: string;          // businessName || displayName || email prefix
  digestEnabled: boolean; // false only when explicitly opted out
  pushEnabled: boolean;  // false only when explicitly opted out of push
  /**
   * One-click unsubscribe from `emailPrefs/{uid}`. Kept in a TOP-LEVEL collection,
   * not on `users/{uid}`, because 41 of 154 accounts have no users doc at all —
   * they never finished workshop setup. Storing the flag on the users doc would
   * leave exactly those people with no way to opt out.
   */
  emailOptOut: boolean;
  /** Resend returned a permanent 4xx for this address — never send to it again. */
  hardBounce: boolean;
  /** Store to link to. Derived from the newest notificationTokens row; null = unknown. */
  platform: 'ios' | 'android' | null;
  /** HMAC-signed one-click unsubscribe URL for this recipient. */
  unsubscribeUrl: string;
}

export interface DigestIO {
  listRecipients(): Promise<DigestRecipient[]>;
  loadOrders(uid: string): Promise<OrderScanDoc[]>;
  getLastSentDate(uid: string): Promise<string | null>;
  setLastSentDate(uid: string, dateKey: string): Promise<void>;
  writeNotifications(uid: string, model: DigestModel): Promise<void>;
  sendEmail(p: { to: string; subject: string; html: string; text: string; headers?: Record<string, string> }): Promise<void>;
  isAllowed(uid: string, email: string): boolean;
  /** True when the tailor has at least one customer. Only called when nothing is due,
   *  so it costs a `limit(1)` read for quiet users and nothing for busy ones. */
  hasCustomers(uid: string): Promise<boolean>;
  /** Record a permanent Resend rejection so tomorrow's run skips this address. */
  markHardBounce(uid: string): Promise<void>;
  loadPushTokens(uid: string): Promise<string[]>;
  sendPush(
    tokens: string[],
    payload: PushSummary & { target?: string },
  ): Promise<{ successCount: number; invalidTokens: string[] }>;
  deletePushTokens(uid: string, tokens: string[]): Promise<void>;
  getLastPushDate(uid: string): Promise<string | null>;
  setLastPushDate(uid: string, dateKey: string): Promise<void>;
  /** Auth uids of ACTIVE staff in this workshop. Empty for a solo tailor. */
  listStaffUids(ownerUid: string): Promise<string[]>;
  /** True when this staff member has not opted out of push. */
  isStaffPushEnabled(staffUid: string): Promise<boolean>;
}

export interface DigestRunResult {
  considered: number;
  sent: number;
  /** Staff members who received a digest of their own assigned work. */
  staffPushed: number;
  /** Kept for continuity in the run logs. Now only counts sends we chose NOT to make. */
  suppressedEmpty: number;
  /** Emails sent on a day with no work due — the habit nudge. */
  nudged: number;
  /** Recipient used the one-click unsubscribe link. */
  skippedOptedOut: number;
  /** Address previously hard-bounced. */
  skippedBounced: number;
  skippedDisabled: number;
  /** Owner's email address is unverified — email suppressed, push/inbox unaffected. */
  skippedUnverified: number;
  skippedAlreadySent: number;
  skippedNotAllowed: number;
  failed: number;
}

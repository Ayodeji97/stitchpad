// functions/src/notifications/types.ts

/** A tailor's order as read from `users/{uid}/orders` (raw Admin SDK shape). */
export interface OrderScanDoc {
  id: string;
  customerName: string;
  status: 'PENDING' | 'IN_PROGRESS' | 'READY' | 'DELIVERED' | string;
  deadline: number | null;     // epoch millis; null = no deadline set
  archivedAt: number | null;   // epoch millis; non-null = archived (excluded)
  totalPrice: number;
  payments: { amount: number }[];
  depositPaid?: number; // legacy deposit field; only meaningful when payments is empty
  items: { garmentType?: string; customGarmentName?: string; description?: string }[];
}

export interface DigestItem {
  orderId: string;
  customerName: string;
  garmentSummary: string;
  deadline?: number; // present for dueSoon / overdue
  amount?: number;   // present for outstanding (naira)
}

export interface DigestModel {
  dueSoon: DigestItem[];      // FULL, sorted soonest-first
  overdue: DigestItem[];      // FULL, sorted most-overdue-first
  outstanding: DigestItem[];  // FULL, sorted biggest-owed-first
}

export interface DigestRecipient {
  uid: string;
  email: string;
  name: string;          // businessName || displayName || email prefix
  digestEnabled: boolean; // false only when explicitly opted out
}

export interface DigestIO {
  listRecipients(): Promise<DigestRecipient[]>;
  loadOrders(uid: string): Promise<OrderScanDoc[]>;
  getLastSentDate(uid: string): Promise<string | null>;
  setLastSentDate(uid: string, dateKey: string): Promise<void>;
  writeNotifications(uid: string, model: DigestModel): Promise<void>;
  sendEmail(p: { to: string; subject: string; html: string; text: string }): Promise<void>;
  isAllowed(uid: string, email: string): boolean;
}

export interface DigestRunResult {
  considered: number;
  sent: number;
  suppressedEmpty: number;
  skippedDisabled: number;
  skippedAlreadySent: number;
  skippedNotAllowed: number;
  failed: number;
}

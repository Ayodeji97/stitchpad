/**
 * Workshop activity alerts: tell one person about something ANOTHER person did.
 *
 *   STAFF_STATUS   — an active staff member moved an order's status → the OWNER hears.
 *   ORDER_ASSIGNED — the owner assigned an order to an active staff member → that STAFF
 *                    member hears. Staff never see money, so this carries none.
 *
 * Two events from the brief are deliberately absent because the data model makes them
 * impossible, not because they were skipped:
 *   - "staff recorded a payment": payments live in /private/money, which firestore.rules
 *     keep isOwner-only. Only the owner can record a payment, and we never notify the
 *     actor.
 *   - "staff created an order": order create is activityCreateOk() = isOwner-only.
 *
 * ACTOR ATTRIBUTION. The order doc had no "who did this" field, so the app (1.4.0+)
 * now writes one:
 *   - statusHistory[].changedBy = the auth uid that appended the entry
 *   - assignedBy + assignedAt   = written ONLY by the owner's assign path. The staff
 *     self-claim branch in firestore.rules forbids any extra key, so a claim can never
 *     carry them, and `assignedAt` must change in THIS write for the event to count.
 *     That is what stops a stale assignedBy from an earlier owner assignment from
 *     dressing up a staff member's own claim as "assigned to you".
 * Writes from older clients carry neither, and produce no notification. Never guess.
 *
 * Everything here except {@link runOrderActivity}'s IO is pure and unit-tested.
 */
import { serverLanguage } from '../localization/language';
import { summariseGarments } from './digestDetector';
import type { PushPayload, PushResult } from './fcm';

export type ActivityKind = 'STAFF_STATUS' | 'ORDER_ASSIGNED';
export type OrderStatus = 'PENDING' | 'IN_PROGRESS' | 'READY' | 'DELIVERED';
const STATUSES: ReadonlySet<string> = new Set(['PENDING', 'IN_PROGRESS', 'READY', 'DELIVERED']);

export interface OrderActivityEvent {
  kind: ActivityKind;
  orderId: string;
  /** Who is told. Owner for STAFF_STATUS, the assignee for ORDER_ASSIGNED. */
  recipientUid: string;
  /** Who did it. Never equal to recipientUid. */
  actorUid: string;
  customerName: string;
  garmentSummary: string;
  /** STAFF_STATUS only. */
  status?: OrderStatus;
  /** ORDER_ASSIGNED only — the due date is work info, not money, so staff may see it. */
  deadline?: number | null;
  /** changedAt / assignedAt — makes the inbox doc id deterministic across trigger retries. */
  eventAt: number;
}

interface StatusEntry {
  status?: unknown;
  changedAt?: unknown;
  changedBy?: unknown;
}

function str(v: unknown): string {
  return typeof v === 'string' ? v.trim() : '';
}

function history(doc: any): StatusEntry[] {
  return Array.isArray(doc?.statusHistory) ? doc.statusHistory : [];
}

function entryKey(e: StatusEntry): string {
  return `${str(e?.status)}|${typeof e?.changedAt === 'number' ? e.changedAt : ''}`;
}

/**
 * Staff moved the status → owner. Requires: the status actually changed, the entry
 * appended in THIS write names an actor, and that actor is not the owner. Whether the
 * actor is genuinely an active staff member is checked later with IO.
 */
export function staffStatusEvent(
  ownerUid: string, orderId: string, before: any, after: any,
): OrderActivityEvent | null {
  const afterStatus = str(after?.status);
  if (!STATUSES.has(afterStatus) || afterStatus === str(before?.status)) return null;

  const seen = new Set(history(before).map(entryKey));
  const added = history(after)
    .filter((e) => !seen.has(entryKey(e)))
    .filter((e) => str(e?.status) === afterStatus && typeof e?.changedAt === 'number');
  if (added.length === 0) return null;
  const entry = added.reduce((a, b) => ((b.changedAt as number) > (a.changedAt as number) ? b : a));

  const actorUid = str(entry.changedBy);
  if (!actorUid || actorUid === ownerUid) return null; // old client, or the owner did it

  return {
    kind: 'STAFF_STATUS',
    orderId,
    recipientUid: ownerUid,
    actorUid,
    customerName: str(after?.customerName),
    garmentSummary: summariseGarments(after?.items ?? []),
    status: afterStatus as OrderStatus,
    eventAt: entry.changedAt as number,
  };
}

/**
 * Owner assigned the order → the assignee. Requires the owner's markers written in
 * THIS change (see the module comment), and an assignee that is somebody else.
 */
export function assignmentEvent(
  ownerUid: string, orderId: string, before: any, after: any,
): OrderActivityEvent | null {
  const assignee = str(after?.assignedMemberId);
  if (!assignee || assignee === str(before?.assignedMemberId)) return null;
  if (assignee === ownerUid) return null;
  if (str(after?.assignedBy) !== ownerUid) return null;
  const assignedAt = after?.assignedAt;
  if (typeof assignedAt !== 'number' || assignedAt === before?.assignedAt) return null;

  return {
    kind: 'ORDER_ASSIGNED',
    orderId,
    recipientUid: assignee,
    actorUid: ownerUid,
    customerName: str(after?.customerName),
    garmentSummary: summariseGarments(after?.items ?? []),
    deadline: typeof after?.deadline === 'number' ? after.deadline : null,
    eventAt: assignedAt,
  };
}

/** Every qualifying event for one order update. Pure; no reads. */
export function orderActivityEvents(
  ownerUid: string, orderId: string, before: unknown, after: unknown,
): OrderActivityEvent[] {
  return [
    staffStatusEvent(ownerUid, orderId, before, after),
    assignmentEvent(ownerUid, orderId, before, after),
  ].filter((e): e is OrderActivityEvent => e !== null && e.recipientUid !== e.actorUid);
}

/** Deterministic per event, so a retried trigger hits ALREADY_EXISTS and does not re-push. */
export function activityInboxDocId(e: OrderActivityEvent): string {
  return `${e.orderId}__${e.kind}__${e.eventAt}`;
}

/**
 * Inbox doc body. Deliberately money-free for BOTH kinds — an assignee is staff and
 * must never see money, and the owner's status alert has no reason to carry any.
 */
export function activityInboxDoc(e: OrderActivityEvent, actorName: string, now: number): Record<string, unknown> {
  return {
    orderId: e.orderId,
    type: e.kind,
    customerName: e.customerName,
    garmentSummary: e.garmentSummary,
    amount: null,
    deadline: e.kind === 'ORDER_ASSIGNED' ? e.deadline ?? null : null,
    status: e.status ?? null,
    actorUid: e.actorUid,
    actorName,
    isOverdue: false,
    isRead: false,
    createdAt: now,
  };
}

/**
 * Push opt-out for activity alerts. Its own switch (Settings → "Workshop activity"),
 * inheriting the daily-push choice and then the email-digest choice when unset, exactly
 * like announcementsPushEnabled — so anyone who already said "no pushes" stays opted out.
 * A missing user doc means we know nothing about them: don't push.
 */
export function isActivityPushEnabled(user: Record<string, unknown> | undefined | null): boolean {
  if (!user) return false;
  if (typeof user.activityPushEnabled === 'boolean') return user.activityPushEnabled;
  if (typeof user.dailyPushEnabled === 'boolean') return user.dailyPushEnabled;
  return user.dailyDigestEmailEnabled !== false;
}

const STATUS_LABEL: Record<'en' | 'fr', Record<OrderStatus, string>> = {
  // Mirrors order_status_* in composeResources values/ and values-fr/.
  en: { PENDING: 'Pending', IN_PROGRESS: 'In Progress', READY: 'Ready', DELIVERED: 'Delivered' },
  fr: { PENDING: 'En attente', IN_PROGRESS: 'En cours', READY: 'Prête', DELIVERED: 'Livrée' },
};

const STATUS_TITLE: Record<'en' | 'fr', Record<OrderStatus, string>> = {
  en: { PENDING: 'Order back to pending', IN_PROGRESS: 'Order in progress', READY: 'Order ready', DELIVERED: 'Order delivered' },
  fr: { PENDING: 'Commande remise en attente', IN_PROGRESS: 'Commande en cours', READY: 'Commande prête', DELIVERED: 'Commande livrée' },
};

function firstName(name: string): string {
  return name.trim().split(/\s+/)[0] || name;
}

function formatDay(ms: number, lang: 'en' | 'fr'): string {
  return new Intl.DateTimeFormat(lang === 'fr' ? 'fr-FR' : 'en-GB', {
    day: 'numeric', month: 'short', timeZone: 'Africa/Lagos',
  }).format(new Date(ms));
}

/** Localized push copy. `actorName` is already resolved (with fallback) by the caller. */
export function activityPushCopy(
  e: OrderActivityEvent, actorName: string, locale?: string,
): { title: string; body: string } {
  const lang = serverLanguage(locale);
  const who = firstName(e.customerName);
  const garment = e.garmentSummary;

  if (e.kind === 'STAFF_STATUS') {
    const status = e.status ?? 'IN_PROGRESS';
    const label = STATUS_LABEL[lang][status];
    const body = lang === 'fr'
      ? (who ? `${actorName} a passé ${garment} de ${who} à « ${label} »` : `${actorName} a passé ${garment} à « ${label} »`)
      : (who ? `${actorName} marked ${who}'s ${garment} ${label}` : `${actorName} marked ${garment} ${label}`);
    return { title: STATUS_TITLE[lang][status], body };
  }

  // ORDER_ASSIGNED — never any money: the recipient is staff.
  const due = typeof e.deadline === 'number' ? formatDay(e.deadline, lang) : null;
  if (lang === 'fr') {
    const base = who ? `${garment} de ${who}` : garment;
    return {
      title: 'Nouvelle commande pour vous',
      body: due ? `${base} — à livrer le ${due}` : base,
    };
  }
  const base = who ? `${who}'s ${garment}` : garment;
  return {
    title: 'New order assigned to you',
    body: due ? `${base} — due ${due}` : base,
  };
}

/** Fallback when the actor's roster name cannot be read. */
export function fallbackActorName(locale?: string): string {
  return serverLanguage(locale) === 'fr' ? 'Un membre de l’équipe' : 'A team member';
}

/** One tag per order on both platforms: five quick status taps leave ONE alert, the latest. */
export function orderNotificationTag(orderId: string): string {
  return `stitchpad_order_${orderId}`;
}

// ── Runner ────────────────────────────────────────────────────────────────────

export interface RecipientProfile {
  pushEnabled: boolean;
  locale?: string;
}

export interface ActivityIO {
  /** null when the user doc does not exist. */
  loadRecipient(uid: string): Promise<RecipientProfile | null>;
  /** True only for a membership doc with status 'active' under this owner. */
  isActiveStaff(ownerUid: string, staffUid: string): Promise<boolean>;
  /** Roster display name (users/{owner}/team/{uid}.name), or null. */
  loadMemberName(ownerUid: string, memberUid: string): Promise<string | null>;
  /** create-if-absent; 'exists' means a previous attempt already handled this event. */
  createInbox(uid: string, docId: string, data: Record<string, unknown>): Promise<'created' | 'exists'>;
  loadTokens(uid: string): Promise<string[]>;
  send(tokens: string[], payload: PushPayload): Promise<PushResult>;
  deleteTokens(uid: string, tokens: string[]): Promise<void>;
  now(): number;
  log(message: string, data: Record<string, unknown>): void;
}

export type ActivityOutcome =
  | 'sent'
  | 'not_staff'        // actor/assignee is not an active staff member → nothing written
  | 'duplicate'        // inbox doc already existed → retry, push already handled
  | 'push_disabled'
  | 'no_tokens'
  | 'error';

/**
 * Runs every qualifying event. Errors are caught PER EVENT and reported as 'error', so
 * one failing event can never stop another, and nothing here can fail the order write
 * that fired the trigger (the trigger itself never rethrows).
 */
export async function runOrderActivity(
  ownerUid: string, orderId: string, before: unknown, after: unknown, io: ActivityIO,
): Promise<{ event: OrderActivityEvent; outcome: ActivityOutcome }[]> {
  const results: { event: OrderActivityEvent; outcome: ActivityOutcome }[] = [];
  for (const event of orderActivityEvents(ownerUid, orderId, before, after)) {
    let outcome: ActivityOutcome;
    try {
      outcome = await runOne(ownerUid, event, io);
    } catch (err) {
      io.log('orderActivity: event failed', {
        ownerUid, orderId, kind: event.kind, error: err instanceof Error ? err.message : String(err),
      });
      outcome = 'error';
    }
    results.push({ event, outcome });
  }
  return results;
}

async function runOne(ownerUid: string, e: OrderActivityEvent, io: ActivityIO): Promise<ActivityOutcome> {
  // The staff side of the event must be a CURRENT active member. For STAFF_STATUS that
  // is the actor (changedBy is client-written; this stops a stale or forged uid); for
  // ORDER_ASSIGNED it is the assignee (owner-created "named" members have no account).
  const staffUid = e.kind === 'STAFF_STATUS' ? e.actorUid : e.recipientUid;
  if (!(await io.isActiveStaff(ownerUid, staffUid))) return 'not_staff';

  const recipient = await io.loadRecipient(e.recipientUid);
  const locale = recipient?.locale;
  const actorName = e.kind === 'STAFF_STATUS'
    ? (await io.loadMemberName(ownerUid, e.actorUid))?.trim() || fallbackActorName(locale)
    : '';

  // Inbox first, ungated by the push switch (like TO_COLLECT): muting pushes must not
  // also blank the in-app record.
  const created = await io.createInbox(e.recipientUid, activityInboxDocId(e), activityInboxDoc(e, actorName, io.now()));
  if (created === 'exists') return 'duplicate';

  if (!recipient || !recipient.pushEnabled) return 'push_disabled';
  const tokens = await io.loadTokens(e.recipientUid);
  if (tokens.length === 0) return 'no_tokens';

  const { title, body } = activityPushCopy(e, actorName, locale);
  const tag = orderNotificationTag(e.orderId);
  const { invalidTokens } = await io.send(tokens, {
    title,
    body,
    data: { target: 'order', orderId: e.orderId },
    androidTag: tag,
    apnsCollapseId: tag,
  });
  if (invalidTokens.length > 0) await io.deleteTokens(e.recipientUid, invalidTokens);
  return 'sent';
}

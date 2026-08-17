/**
 * Per-staff view of a workshop's work.
 *
 * Staff have never received a notification of any kind. The daily digest goes to the
 * workshop OWNER, and the engagement push deliberately excludes staff because their own
 * data tree is always empty — they work inside the owner's. So the person actually
 * sewing the garment hears nothing about their own deadlines.
 *
 * This slices the owner's already-loaded orders by assignee, so a staff member gets a
 * digest of THEIR work and nothing else. It costs no extra reads: the owner's run has
 * already fetched the orders and joined the money.
 *
 * Pure — no IO, no clock beyond the `now` passed through to digestDetector.
 */
import { digestDetector, isDigestEmpty } from './digestDetector';
import { DigestModel, OrderScanDoc } from './types';

export interface StaffDigest {
  /** The staff member's auth uid — also their roster member id. */
  staffUid: string;
  model: DigestModel;
}

/**
 * A digest per active staff member who has actionable work assigned.
 *
 * The join works because a STAFF roster row's document id IS their auth uid (see
 * approveStaffMember's teamMemberDocPath), so an order's `assignedMemberId` matches the
 * membership id directly. Owner-created "named" members have arbitrary ids and no auth
 * account, so they simply never match — which is correct: there is nobody to notify.
 *
 * Staff with nothing actionable are omitted entirely rather than sent an empty digest.
 */
export function staffDigests(
  orders: OrderScanDoc[],
  staffUids: string[],
  now: number,
): StaffDigest[] {
  if (staffUids.length === 0) return [];
  const digests: StaffDigest[] = [];

  for (const staffUid of staffUids) {
    const mine = orders.filter((o) => o.assignedMemberId === staffUid);
    if (mine.length === 0) continue;

    const full = digestDetector(mine, now);
    // Money is the owner's business, not the sewist's: a staff member is told what to
    // MAKE and when, never what a customer owes. Dropping `outstanding` keeps that
    // boundary in the notification even though the orders array carries the figures.
    const model: DigestModel = { dueSoon: full.dueSoon, overdue: full.overdue, outstanding: [] };
    if (isDigestEmpty(model)) continue;

    digests.push({ staffUid, model });
  }

  return digests;
}

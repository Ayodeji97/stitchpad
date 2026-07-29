import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';
import { REGION, MembershipStatus, membershipDocPath } from './staffConstants';
import { StaffClaimsDeps } from './approveStaffMember';

export interface CancelStaffMembershipRequest {
  workshopUid?: unknown;
}

export interface CancelStaffMembershipResponse {
  workshopUid: string;
  status: MembershipStatus;
}

/**
 * Staff-initiated cancel of their OWN membership in [workshopUid] — the "leave
 * workshop" action. Works for a pending request (before approval) and for an
 * active member leaving. The caller can only ever affect their own membership:
 * the doc id is `context.auth.uid`, so a caller cannot cancel anyone else's.
 */
export async function cancelStaffMembershipHandler(
  data: CancelStaffMembershipRequest,
  context: functions.https.CallableContext,
  deps: StaffClaimsDeps,
): Promise<CancelStaffMembershipResponse> {
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'auth_required');
  }
  const staffAuthUid = context.auth.uid;
  const workshopUid = typeof data.workshopUid === 'string' ? data.workshopUid : '';
  if (!workshopUid) {
    throw new functions.https.HttpsError('invalid-argument', 'invalid_workshop_uid');
  }

  const ref = deps.db.doc(membershipDocPath(workshopUid, staffAuthUid));
  const nowMs = deps.now().getTime();
  // Doc first (revoked, in a transaction), then clear any claim — same fail-safe
  // ordering as revokeStaffMember. The transaction serialises against a concurrent
  // approveStaffMember so the two can't overwrite each other after stale reads
  // (Firestore retries the loser, which then sees the committed status). A pending
  // member has no claim yet; an active member leaving does, and clearing it removes
  // their access.
  await deps.db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (!snap.exists) {
      throw new functions.https.HttpsError('not-found', 'membership_not_found');
    }
    tx.update(ref, { status: 'revoked', revokedAt: nowMs, claimsRefreshAt: nowMs });
  });
  await deps.setClaims(staffAuthUid, null);

  return { workshopUid, status: 'revoked' };
}

export const cancelStaffMembership = functions
  .region(REGION)
  .https.onCall(
    async (data, context): Promise<CancelStaffMembershipResponse> =>
      cancelStaffMembershipHandler(data as CancelStaffMembershipRequest, context, {
        db: admin.firestore(),
        setClaims: (uid, claims) => admin.auth().setCustomUserClaims(uid, claims),
        now: () => new Date(),
      }),
  );

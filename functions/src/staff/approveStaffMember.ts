import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';
import { REGION, STAFF_ROLE, MembershipStatus, membershipDocPath } from './staffConstants';

export interface ApproveStaffMemberRequest {
  staffAuthUid?: unknown;
}

export interface ApproveStaffMemberResponse {
  staffAuthUid: string;
  status: MembershipStatus;
}

export interface StaffClaimsDeps {
  db: admin.firestore.Firestore;
  setClaims: (uid: string, claims: Record<string, unknown> | null) => Promise<void>;
  now: () => Date;
}

export async function approveStaffMemberHandler(
  data: ApproveStaffMemberRequest,
  context: functions.https.CallableContext,
  deps: StaffClaimsDeps,
): Promise<ApproveStaffMemberResponse> {
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'auth_required');
  }
  const ownerUid = context.auth.uid;
  const staffAuthUid = typeof data.staffAuthUid === 'string' ? data.staffAuthUid : '';
  if (!staffAuthUid) {
    throw new functions.https.HttpsError('invalid-argument', 'invalid_staff_uid');
  }

  const ref = deps.db.doc(membershipDocPath(ownerUid, staffAuthUid));
  const snap = await ref.get();
  if (!snap.exists) {
    throw new functions.https.HttpsError('not-found', 'membership_not_found');
  }
  const status = (snap.data() as { status?: MembershipStatus }).status;
  if (status === 'revoked') {
    // A revoked person must re-redeem an invite (re-consent), not be silently
    // re-approved.
    throw new functions.https.HttpsError('failed-precondition', 'membership_revoked');
  }

  const nowMs = deps.now().getTime();
  // Claim first, then doc. The client watches the membership doc and force-
  // refreshes its token when it turns active; setting the claim first guarantees
  // that refresh returns the claim, so the app promotes to the owner's tree
  // without a denied-read window or a refresh loop. Still fail-safe: if the doc
  // update fails after, the claim alone grants nothing because isActiveMember
  // also requires status == 'active'.
  await deps.setClaims(staffAuthUid, { workshopUid: ownerUid, role: STAFF_ROLE });
  await ref.update({ status: 'active', approvedAt: nowMs, claimsRefreshAt: nowMs });

  return { staffAuthUid, status: 'active' };
}

export const approveStaffMember = functions
  .region(REGION)
  .https.onCall(
    async (data, context): Promise<ApproveStaffMemberResponse> =>
      approveStaffMemberHandler(data as ApproveStaffMemberRequest, context, {
        db: admin.firestore(),
        setClaims: (uid, claims) => admin.auth().setCustomUserClaims(uid, claims),
        now: () => new Date(),
      }),
  );

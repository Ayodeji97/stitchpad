import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';
import { REGION, MembershipStatus, membershipDocPath, teamMemberDocPath } from './staffConstants';
import { StaffClaimsDeps } from './approveStaffMember';

export interface RevokeStaffMemberRequest {
  staffAuthUid?: unknown;
}

export interface RevokeStaffMemberResponse {
  staffAuthUid: string;
  status: MembershipStatus;
}

export async function revokeStaffMemberHandler(
  data: RevokeStaffMemberRequest,
  context: functions.https.CallableContext,
  deps: StaffClaimsDeps,
): Promise<RevokeStaffMemberResponse> {
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

  const nowMs = deps.now().getTime();
  // Doc first, then claim. isActiveMember requires BOTH a staff claim AND an
  // active membership doc, and setCustomUserClaims does NOT refresh an
  // already-minted token — so it is the doc flip to revoked that cuts access
  // immediately for a member still holding a stale active token. Flip the doc
  // first: if clearing the claim fails after, the revoked doc already denies
  // access. (Claim-first would leave a stale token with claim + active doc =
  // continued access until token expiry if the doc update failed.)
  await ref.update({ status: 'revoked', revokedAt: nowMs, claimsRefreshAt: nowMs });
  await deps.setClaims(staffAuthUid, null);

  try {
    await deps.db.doc(teamMemberDocPath(ownerUid, staffAuthUid)).set(
      { status: 'archived', updatedAt: nowMs },
      { merge: true },
    );
  } catch { /* roster archive is best-effort; attribution stays resolvable either way */ }

  return { staffAuthUid, status: 'revoked' };
}

export const revokeStaffMember = functions
  .region(REGION)
  .https.onCall(
    async (data, context): Promise<RevokeStaffMemberResponse> =>
      revokeStaffMemberHandler(data as RevokeStaffMemberRequest, context, {
        db: admin.firestore(),
        setClaims: (uid, claims) => admin.auth().setCustomUserClaims(uid, claims),
        now: () => new Date(),
      }),
  );

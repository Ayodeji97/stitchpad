import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';
import { REGION, MembershipStatus, membershipDocPath, teamMemberDocPath } from './staffConstants';
import { StaffClaimsDeps } from './approveStaffMember';
import {
  grantLaunchFreeOnStaffDeparture,
  LaunchGrantFlagDeps,
  productionLaunchGrantDeps,
} from '../freemium/launchGrant';

export interface RevokeStaffMemberRequest {
  staffAuthUid?: unknown;
}

export interface RevokeStaffMemberResponse {
  staffAuthUid: string;
  status: MembershipStatus;
}

export type RevokeStaffMemberDeps = StaffClaimsDeps & LaunchGrantFlagDeps;

export async function revokeStaffMemberHandler(
  data: RevokeStaffMemberRequest,
  context: functions.https.CallableContext,
  deps: RevokeStaffMemberDeps,
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
  // Owners also use this callable to DECLINE a still-pending request, so the prior
  // status decides whether the departure grant applies (see below).
  const priorStatus = snap.data()?.status as MembershipStatus | undefined;

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
    await deps.db.doc(teamMemberDocPath(ownerUid, staffAuthUid)).update(
      { status: 'archived', updatedAt: nowMs },
    );
  } catch {
    // Roster archive is best-effort. If the roster doc is missing (e.g. member
    // was revoked before ever being approved), update() throws and we swallow
    // it rather than create a stub. Attribution for never-approved members isn't
    // resolvable anyway, and a stub roster doc would pollute the team namespace.
  }

  // Gated on the membership having actually been ACTIVE — mirrors cancelStaffMembership.
  // Declining a PENDING request never cost the requester anything (they were never
  // staff, never lost their own tree), so it must not hand out Atelier; otherwise an
  // owner + a friend could farm grants by redeeming and declining on repeat.
  if (priorStatus === 'active') {
    try {
      await grantLaunchFreeOnStaffDeparture(deps.db, deps, staffAuthUid, deps.now());
    } catch (err) {
      // Best-effort, same as the roster archive above: a revoked staffer who never
      // gets the launch grant just falls back to FREE (the pre-existing behavior),
      // it must never fail the revocation itself.
      functions.logger.error('launch-free grant on staff revoke failed', { staffAuthUid, err });
    }
  }

  return { staffAuthUid, status: 'revoked' };
}

export const revokeStaffMember = functions
  .region(REGION)
  .https.onCall(
    async (data, context): Promise<RevokeStaffMemberResponse> => {
      const db = admin.firestore();
      return revokeStaffMemberHandler(data as RevokeStaffMemberRequest, context, {
        db,
        setClaims: (uid, claims) => admin.auth().setCustomUserClaims(uid, claims),
        now: () => new Date(),
        ...productionLaunchGrantDeps(db),
      });
    },
  );

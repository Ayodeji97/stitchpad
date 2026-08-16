import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';
import { REGION, MembershipStatus, membershipDocPath, teamMemberDocPath } from './staffConstants';
import { StaffClaimsDeps } from './approveStaffMember';
import { buildLaunchGrantFields, grantLaunchFreeOnStaffDeparture, LaunchGrantFlagDeps } from '../freemium/launchGrant';

export interface CancelStaffMembershipRequest {
  workshopUid?: unknown;
}

export interface CancelStaffMembershipResponse {
  workshopUid: string;
  status: MembershipStatus;
}

export type CancelStaffMembershipDeps = StaffClaimsDeps & LaunchGrantFlagDeps;

/**
 * Staff-initiated cancel of their OWN membership in [workshopUid] — the "leave
 * workshop" action. Works for a pending request (before approval) and for an
 * active member leaving. The caller can only ever affect their own membership:
 * the doc id is `context.auth.uid`, so a caller cannot cancel anyone else's.
 */
export async function cancelStaffMembershipHandler(
  data: CancelStaffMembershipRequest,
  context: functions.https.CallableContext,
  deps: CancelStaffMembershipDeps,
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

  // Delete the owner's deterministic "staff pending" notification (best-effort).
  // redeemStaffInvite dedups on this id, so leaving it behind would suppress the
  // notification for a later re-redeem by the same staffer.
  try {
    await deps.db.doc(`users/${workshopUid}/notifications/staff_pending__${staffAuthUid}`).delete();
  } catch {
    // Best-effort: a missing/failed delete must not fail the leave.
  }

  try {
    await deps.db.doc(teamMemberDocPath(workshopUid, staffAuthUid)).update(
      { status: 'archived', updatedAt: nowMs },
    );
  } catch {
    // Roster archive is best-effort. If the roster doc is missing (e.g. member
    // cancelled before ever being approved), update() throws and we swallow
    // it rather than create a stub. Attribution for never-approved members isn't
    // resolvable anyway, and a stub roster doc would pollute the team namespace.
  }

  try {
    await grantLaunchFreeOnStaffDeparture(deps.db, deps, staffAuthUid, deps.now());
  } catch (err) {
    // Best-effort, same as the roster archive above: a departing staffer who never
    // gets the launch grant just falls back to FREE (the pre-existing behavior),
    // it must never fail the leave itself.
    functions.logger.error('launch-free grant on staff cancel failed', { staffAuthUid, err });
  }

  return { workshopUid, status: 'revoked' };
}

export const cancelStaffMembership = functions
  .region(REGION)
  .https.onCall(
    async (data, context): Promise<CancelStaffMembershipResponse> => {
      const db = admin.firestore();
      return cancelStaffMembershipHandler(data as CancelStaffMembershipRequest, context, {
        db,
        setClaims: (uid, claims) => admin.auth().setCustomUserClaims(uid, claims),
        now: () => new Date(),
        isGrantEnabled: async () => {
          const snap = await db.doc('config/app').get();
          return snap.get('launchFreeGrantEnabled') === true;
        },
        writeGrant: async (uid, now) => {
          await db.doc(`users/${uid}`).set(buildLaunchGrantFields(now), { merge: true });
        },
      });
    },
  );

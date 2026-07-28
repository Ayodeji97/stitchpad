import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';
import {
  REGION,
  DEFAULT_STAFF_SEAT_CAP,
  MembershipStatus,
  inviteDocPath,
  membershipDocPath,
  membershipsCollectionPath,
  occupiesSeat,
} from './staffConstants';

export interface RedeemStaffInviteRequest {
  code?: unknown;
}

export interface RedeemStaffInviteResponse {
  workshopUid: string;
  workshopName: string;
  status: MembershipStatus;
}

export interface RedeemStaffInviteDeps {
  db: admin.firestore.Firestore;
  now: () => Date;
}

export async function redeemStaffInviteHandler(
  data: RedeemStaffInviteRequest,
  context: functions.https.CallableContext,
  deps: RedeemStaffInviteDeps,
): Promise<RedeemStaffInviteResponse> {
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'auth_required');
  }
  const staffUid = context.auth.uid;
  const code = typeof data.code === 'string' ? data.code.trim() : '';
  if (!code) {
    throw new functions.https.HttpsError('invalid-argument', 'invalid_code');
  }

  const nowMs = deps.now().getTime();
  const staffEmail = (context.auth.token.email as string | undefined) ?? '';
  const staffName = (context.auth.token.name as string | undefined) ?? '';
  const inviteRef = deps.db.doc(inviteDocPath(code));

  // All the read-checks + writes run in one transaction so two people redeeming
  // the same code can't both pass the status/seat checks: Firestore retries the
  // loser once the winner flips the invite to 'redeemed', and it re-reads
  // 'redeemed' -> invite_not_open. Single-use + seat cap hold under concurrency.
  const result = await deps.db.runTransaction(async (tx) => {
    const inviteSnap = await tx.get(inviteRef);
    if (!inviteSnap.exists) {
      throw new functions.https.HttpsError('not-found', 'invite_not_found');
    }
    const invite = inviteSnap.data() as {
      workshopUid: string;
      workshopName?: string;
      status: string;
      expiresAt: number;
    };
    if (invite.status !== 'open') {
      throw new functions.https.HttpsError('failed-precondition', 'invite_not_open');
    }
    if (typeof invite.expiresAt === 'number' && nowMs > invite.expiresAt) {
      throw new functions.https.HttpsError('failed-precondition', 'invite_expired');
    }
    const workshopUid = invite.workshopUid;
    if (workshopUid === staffUid) {
      throw new functions.https.HttpsError('failed-precondition', 'cannot_join_own_workshop');
    }

    const membershipRef = deps.db.doc(membershipDocPath(workshopUid, staffUid));
    const existing = await tx.get(membershipRef);
    if (existing.exists && occupiesSeat((existing.data() as { status?: MembershipStatus }).status ?? 'pending')) {
      throw new functions.https.HttpsError('already-exists', 'already_member');
    }

    const membersSnap = await tx.get(deps.db.collection(membershipsCollectionPath(workshopUid)));
    const seatsUsed = membersSnap.docs.filter((d) =>
      occupiesSeat((d.data() as { status?: MembershipStatus }).status ?? 'pending'),
    ).length;
    if (seatsUsed >= DEFAULT_STAFF_SEAT_CAP) {
      throw new functions.https.HttpsError('failed-precondition', 'seat_cap_reached');
    }

    tx.set(membershipRef, {
      staffAuthUid: staffUid,
      staffEmail,
      staffName,
      role: 'staff',
      status: 'pending' as MembershipStatus,
      workshopUid,
      redeemedAt: nowMs,
      approvedAt: null,
      revokedAt: null,
    });
    tx.update(inviteRef, { status: 'redeemed', redeemedByAuthUid: staffUid, redeemedAt: nowMs });
    return { workshopUid, workshopName: invite.workshopName ?? '' };
  });

  // Owner "staff pending" notification — best-effort, outside the transaction
  // (create-if-absent: dedup on the deterministic id, preserve read-state).
  const notifRef = deps.db.doc(`users/${result.workshopUid}/notifications/staff_pending__${staffUid}`);
  try {
    await notifRef.create({
      type: 'STAFF_PENDING',
      staffAuthUid: staffUid,
      staffEmail,
      staffName,
      isRead: false,
      createdAt: nowMs,
    });
  } catch (err) {
    if ((err as { code?: number }).code !== 6) throw err; // 6 = ALREADY_EXISTS
  }

  return { workshopUid: result.workshopUid, workshopName: result.workshopName, status: 'pending' };
}

export const redeemStaffInvite = functions
  .region(REGION)
  .https.onCall(
    async (data, context): Promise<RedeemStaffInviteResponse> =>
      redeemStaffInviteHandler(data as RedeemStaffInviteRequest, context, {
        db: admin.firestore(),
        now: () => new Date(),
      }),
  );

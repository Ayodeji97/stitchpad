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

  const inviteRef = deps.db.doc(inviteDocPath(code));
  const inviteSnap = await inviteRef.get();
  if (!inviteSnap.exists) {
    throw new functions.https.HttpsError('not-found', 'invite_not_found');
  }
  const invite = inviteSnap.data() as {
    workshopUid: string;
    workshopName?: string;
    status: string;
    expiresAt: number;
  };
  const nowMs = deps.now().getTime();
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
  const existing = await membershipRef.get();
  if (existing.exists && occupiesSeat((existing.data() as { status?: MembershipStatus }).status ?? 'pending')) {
    throw new functions.https.HttpsError('already-exists', 'already_member');
  }

  const membersSnap = await deps.db.collection(membershipsCollectionPath(workshopUid)).get();
  const seatsUsed = membersSnap.docs.filter((d) =>
    occupiesSeat((d.data() as { status?: MembershipStatus }).status ?? 'pending'),
  ).length;
  if (seatsUsed >= DEFAULT_STAFF_SEAT_CAP) {
    throw new functions.https.HttpsError('failed-precondition', 'seat_cap_reached');
  }

  const staffEmail = (context.auth.token.email as string | undefined) ?? '';
  const staffName = (context.auth.token.name as string | undefined) ?? '';

  await membershipRef.set({
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
  await inviteRef.update({ status: 'redeemed', redeemedByAuthUid: staffUid, redeemedAt: nowMs });

  // Owner "staff pending" notification — create-if-absent (dedup on the
  // deterministic id; preserve read-state if it somehow already exists).
  const notifRef = deps.db.doc(`users/${workshopUid}/notifications/staff_pending__${staffUid}`);
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

  return { workshopUid, workshopName: invite.workshopName ?? '', status: 'pending' };
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

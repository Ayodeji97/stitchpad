import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';
import * as crypto from 'crypto';
import {
  REGION,
  DEFAULT_STAFF_SEAT_CAP,
  INVITE_CODE_LENGTH,
  INVITE_TTL_MS,
  MembershipStatus,
  inviteDocPath,
  membershipsCollectionPath,
  occupiesSeat,
} from './staffConstants';

// Crockford-ish alphabet (no 0/O/1/I/L), matching billing/giftBilling generateCode.
const CODE_ALPHABET = '23456789ABCDEFGHJKMNPQRSTUVWXYZ';
const MAX_CODE_ATTEMPTS = 5;

export function generateInviteCode(length: number): string {
  const buf = crypto.randomBytes(length);
  let out = '';
  for (const b of buf) out += CODE_ALPHABET[b % CODE_ALPHABET.length];
  return out;
}

export interface GenerateStaffInviteResponse {
  code: string;
  expiresAt: number;
}

export interface GenerateStaffInviteDeps {
  db: admin.firestore.Firestore;
  now: () => Date;
  randomCode: () => string;
}

export async function generateStaffInviteHandler(
  _data: unknown,
  context: functions.https.CallableContext,
  deps: GenerateStaffInviteDeps,
): Promise<GenerateStaffInviteResponse> {
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'auth_required');
  }
  const ownerUid = context.auth.uid;

  const membersSnap = await deps.db.collection(membershipsCollectionPath(ownerUid)).get();
  const seatsUsed = membersSnap.docs.filter((d) =>
    occupiesSeat(((d.data() as { status?: MembershipStatus }).status ?? 'pending')),
  ).length;
  if (seatsUsed >= DEFAULT_STAFF_SEAT_CAP) {
    throw new functions.https.HttpsError('failed-precondition', 'seat_cap_reached');
  }

  const ownerSnap = await deps.db.doc(`users/${ownerUid}`).get();
  const workshopName = (ownerSnap.data() as { businessName?: string } | undefined)?.businessName ?? '';

  const nowMs = deps.now().getTime();
  const expiresAt = nowMs + INVITE_TTL_MS;

  for (let attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt += 1) {
    const code = deps.randomCode();
    try {
      await deps.db.doc(inviteDocPath(code)).create({
        code,
        workshopUid: ownerUid,
        workshopName,
        status: 'open',
        createdAt: nowMs,
        expiresAt,
        redeemedByAuthUid: null,
      });
      return { code, expiresAt };
    } catch (err) {
      if ((err as { code?: number }).code === 6) continue; // ALREADY_EXISTS → new code
      throw err;
    }
  }
  throw new functions.https.HttpsError('internal', 'code_generation_failed');
}

export const generateStaffInvite = functions
  .region(REGION)
  .https.onCall(
    async (data, context): Promise<GenerateStaffInviteResponse> =>
      generateStaffInviteHandler(data, context, {
        db: admin.firestore(),
        now: () => new Date(),
        randomCode: () => generateInviteCode(INVITE_CODE_LENGTH),
      }),
  );

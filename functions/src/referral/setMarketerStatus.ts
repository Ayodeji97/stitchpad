import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';
import { REGION, MARKETERS } from './referralConstants';
import type { MarketerStatus } from './referralConstants';

// setMarketerStatus — admin toggle for a marketer's referral link.
// 'disabled' stops NEW attributions (recordAttribution skips disabled
// marketers); everything already earned keeps progressing and stays payable
// via markReferralPaid. 'active' re-enables the link. Idempotent — setting
// the current status again just bumps updatedAt.
//
// Gated by the Firebase Auth `admin: true` custom claim, like the other
// referral admin callables.

// marketerId is interpolated into a doc path, so constrain it to the id
// charset createMarketer mints — never let a '/' or empty value through.
const MARKETER_ID_RE = /^[A-Za-z0-9_-]{1,128}$/;

export interface SetMarketerStatusRequest {
  marketerId?: unknown;
  status?: unknown;
}

export interface SetMarketerStatusResponse {
  marketerId: string;
  status: MarketerStatus;
}

export interface SetMarketerStatusDeps {
  db: admin.firestore.Firestore;
  now: () => Date;
}

export async function setMarketerStatusHandler(
  data: SetMarketerStatusRequest,
  context: functions.https.CallableContext,
  deps: SetMarketerStatusDeps,
): Promise<SetMarketerStatusResponse> {
  if (context.auth?.token?.admin !== true) {
    throw new functions.https.HttpsError('permission-denied', 'admin_only');
  }
  const marketerId = typeof data.marketerId === 'string' ? data.marketerId : '';
  if (!MARKETER_ID_RE.test(marketerId)) {
    throw new functions.https.HttpsError('invalid-argument', 'invalid_marketer_id');
  }
  const status = data.status;
  if (status !== 'active' && status !== 'disabled') {
    throw new functions.https.HttpsError('invalid-argument', 'invalid_status');
  }

  const nowTs = admin.firestore.Timestamp.fromDate(deps.now());
  const ref = deps.db.doc(`${MARKETERS}/${marketerId}`);
  await deps.db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (!snap.exists) {
      throw new functions.https.HttpsError('not-found', 'marketer_not_found');
    }
    tx.set(ref, { status, updatedAt: nowTs }, { merge: true });
  });

  const result: SetMarketerStatusResponse = { marketerId, status };
  functions.logger.info('setMarketerStatus complete', { ...result });
  return result;
}

export const setMarketerStatus = functions
  .region(REGION)
  .https.onCall(
    async (data, context): Promise<SetMarketerStatusResponse> =>
      setMarketerStatusHandler(data as SetMarketerStatusRequest, context, {
        db: admin.firestore(),
        now: () => new Date(),
      }),
  );

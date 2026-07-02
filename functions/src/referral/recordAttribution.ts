import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';
import * as crypto from 'crypto';
import {
  REGION,
  MARKETERS,
  REFERRAL_CODES,
  REFERRALS,
  REFERRAL_DEVICES,
  QUALIFY_WINDOW_DAYS,
  DAY_MS,
  ERR_REFERRAL_CODE_NOT_FOUND,
} from './referralConstants';
import type { ReferrerType, ReferralFlag } from './referralConstants';

// recordReferralAttribution — server-authoritative capture of which marketer
// gets credit for a signup. Called once at first authenticated launch with the
// captured code + a stable device hash. All fraud checks + the payout lifecycle
// are server-side; the client is never trusted with any of this.

export interface RecordAttributionRequest {
  code?: unknown;
  deviceHash?: unknown;
  source?: unknown; // 'install_referrer' | 'clipboard' | 'manual' (analytics only)
}

export interface RecordAttributionResponse {
  status: 'attributed' | 'already_attributed';
  marketerId: string;
  // NOTE: fraud flags (self_referral, device_reuse, …) are deliberately NOT
  // returned. They live only in referrals/{uid} server-side — echoing them would
  // hand an attacker a fraud-evasion oracle (retry until the response is clean).
}

export interface RecordAttributionDeps {
  db: admin.firestore.Firestore;
  now: () => Date;
  // Server-authoritative signup instant (ms) for uid, or null if unavailable.
  userCreationTimeMs: (uid: string) => Promise<number | null>;
}

export const recordReferralAttribution = functions
  .region(REGION)
  .https.onCall(async (data, context): Promise<RecordAttributionResponse> =>
    recordReferralAttributionHandler(data as RecordAttributionRequest, context, {
      db: admin.firestore(),
      now: () => new Date(),
      userCreationTimeMs: async (uid) => {
        try {
          const t = (await admin.auth().getUser(uid)).metadata.creationTime;
          const ms = t ? Date.parse(t) : NaN;
          return Number.isFinite(ms) ? ms : null;
        } catch {
          return null;
        }
      },
    }));

export async function recordReferralAttributionHandler(
  data: RecordAttributionRequest,
  context: functions.https.CallableContext,
  deps: RecordAttributionDeps,
): Promise<RecordAttributionResponse> {
  const uid = context.auth?.uid;
  if (!uid) throw new functions.https.HttpsError('unauthenticated', 'Sign in required.');

  const code = asCode(data.code);
  if (!code) throw new functions.https.HttpsError('invalid-argument', 'missing_code');

  // Idempotent: one attribution per install. A second call returns the first.
  const referralRef = deps.db.doc(`${REFERRALS}/${uid}`);
  const existing = (await referralRef.get()).data() as
    | { marketerId?: string; flags?: ReferralFlag[] }
    | undefined;
  if (existing?.marketerId) {
    return { status: 'already_attributed', marketerId: existing.marketerId };
  }

  // Resolve the code → marketer. Unknown/disabled codes look identical to a
  // client (don't leak which codes exist).
  const codeSnap = await deps.db.doc(`${REFERRAL_CODES}/${code}`).get();
  const marketerId = (codeSnap.data() as { marketerId?: string } | undefined)?.marketerId;
  if (!codeSnap.exists || !marketerId) {
    throw new functions.https.HttpsError('invalid-argument', ERR_REFERRAL_CODE_NOT_FOUND);
  }
  const marketerSnap = await deps.db.doc(`${MARKETERS}/${marketerId}`).get();
  const marketer = marketerSnap.data() as
    | { type?: ReferrerType; referrerUid?: string | null; email?: string; status?: string }
    | undefined;
  if (!marketerSnap.exists || marketer?.status === 'disabled') {
    throw new functions.https.HttpsError('invalid-argument', ERR_REFERRAL_CODE_NOT_FOUND);
  }

  const flags: ReferralFlag[] = [];

  // Self-referral: a user-referrer redeeming their own link, or the referred
  // account's email matching the marketer's. Flagged → never payable.
  const referredEmail = normEmail(context.auth?.token?.email);
  const referredEmailHash = referredEmail ? sha256(referredEmail) : null;
  if (marketer?.referrerUid && marketer.referrerUid === uid) {
    flags.push('self_referral');
  } else if (referredEmail && normEmail(marketer?.email) === referredEmail) {
    flags.push('self_referral');
  }

  const deviceHash = asDeviceHash(data.deviceHash);

  // Honest qualification window: measure from the user's real signup instant
  // when we can read it, else attribution time.
  const nowDate = deps.now();
  const nowTs = admin.firestore.Timestamp.fromDate(nowDate);
  const userRef = deps.db.doc(`users/${uid}`);
  // Server-authoritative signup instant: the Firebase Auth account creation time
  // (immutable, not client-writable). users/{uid}.createdAt is client-editable, so
  // trusting it would let a referred user extend their own real-money
  // qualification window. Fall back to attribution time if unavailable.
  const authCreatedMs = await deps.userCreationTimeMs(uid);
  const signupMs = authCreatedMs ?? nowDate.getTime();
  const signupTs = admin.firestore.Timestamp.fromMillis(signupMs);
  const windowEndsTs = admin.firestore.Timestamp.fromMillis(signupMs + QUALIFY_WINDOW_DAYS * DAY_MS);
  const referrerType: ReferrerType = marketer?.type === 'user' ? 'user' : 'affiliate';

  // The device read + claim MUST happen inside the transaction: two concurrent
  // signups with the same fresh deviceHash would otherwise both see it unclaimed
  // pre-tx, and neither would be flagged. Reading the device doc in-tx makes the
  // claim conflict, so exactly one caller wins ("first install wins") and any
  // other is flagged device_reuse. Best-effort — the hash resets on reinstall.
  const deviceRef = deviceHash ? deps.db.doc(`${REFERRAL_DEVICES}/${deviceHash}`) : null;
  // Set if another call created referrals/{uid} between the pre-tx read and the
  // transaction — we then return the PERSISTED attribution, not this call's, so
  // logs never claim credit for a write that didn't happen.
  let raceWinner: { marketerId: string; flags: ReferralFlag[] } | null = null;

  await deps.db.runTransaction(async (tx) => {
    const current = await tx.get(referralRef);
    if (current.exists) {
      const won = current.data() as { marketerId?: string; flags?: ReferralFlag[] };
      raceWinner = { marketerId: won.marketerId ?? marketerId, flags: won.flags ?? [] };
      return;
    }

    const finalFlags = [...flags];
    let claimDevice = false;
    if (deviceRef) {
      const devSnap = await tx.get(deviceRef);
      const owner = (devSnap.data() as { referredUid?: string } | undefined)?.referredUid;
      if (devSnap.exists && owner && owner !== uid) {
        finalFlags.push('device_reuse');
      } else if (!devSnap.exists) {
        claimDevice = true;
      }
      // exists && owner === uid → idempotent re-run, leave as-is
    }

    // Only stamp referredBy on an EXISTING user doc. Attribution can run before
    // the client creates users/{uid} (SSO signup → Workshop Setup); a merge-set
    // here would create a partial doc carrying referredBy, and the serverOnlyField
    // rule would then reject the client's own profile create. referrals/{uid} is
    // the canonical attribution record, so skipping the denormalized stamp when
    // the doc is absent is safe.
    const userExists = (await tx.get(userRef)).exists;

    tx.set(referralRef, {
      marketerId,
      code,
      referrerType,
      milestone: 'attributed',
      payoutState: 'none',
      payoutAmount: 0,
      deviceHash: deviceHash ?? null,
      referredEmailHash,
      attributionSource: asSource(data.source),
      signupAt: signupTs,
      qualificationWindowEndsAt: windowEndsTs,
      activeDays: 0,
      activeDayKeys: [],
      flags: finalFlags,
      createdAt: nowTs,
      updatedAt: nowTs,
    });
    if (claimDevice && deviceRef) {
      tx.set(deviceRef, { referredUid: uid, marketerId, createdAt: nowTs });
    }
    // Server-owned stamp (firestore.rules deny any client write of referredBy).
    // Never CREATES the user doc — see the userExists note above.
    if (userExists) {
      tx.set(userRef, { referredBy: marketerId, updatedAt: nowTs }, { merge: true });
    }
  });

  if (raceWinner) {
    return {
      status: 'already_attributed',
      marketerId: (raceWinner as { marketerId: string }).marketerId,
    };
  }
  return { status: 'attributed', marketerId };
}

// ── Helpers ──────────────────────────────────────────────────────────────────

// Referral codes are uppercase over the Crockford-ish alphabet. Manual iOS entry
// may arrive lowercased or spaced — normalize, then constrain to [0-9A-Z] so the
// value is always a safe Firestore doc-id (no '/', '.', path traversal) before we
// build `referralCodes/${code}`. Anything outside the alphabet can't match a real
// code anyway, so rejecting it early just yields a clean invalid-argument.
function asCode(value: unknown): string | null {
  if (typeof value !== 'string') return null;
  const s = value.replace(/[\s-]/g, '').toUpperCase();
  return /^[0-9A-Z]{1,32}$/.test(s) ? s : null;
}

// Client-generated stable hash. Constrain to a URL-safe alphabet + length so it's
// a safe Firestore doc-id for `referralDevices/${deviceHash}`. A malformed hash is
// treated as absent (best-effort dedupe) rather than failing the whole attribution.
function asDeviceHash(value: unknown): string | null {
  return typeof value === 'string' && /^[A-Za-z0-9_-]{1,128}$/.test(value) ? value : null;
}

function asSource(value: unknown): string | null {
  return value === 'install_referrer' || value === 'clipboard' || value === 'manual'
    ? value
    : null;
}

function normEmail(value: unknown): string | null {
  return typeof value === 'string' && value.trim() ? value.trim().toLowerCase() : null;
}

function sha256(input: string): string {
  return crypto.createHash('sha256').update(input).digest('hex');
}

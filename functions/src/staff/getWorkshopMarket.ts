import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';
import { REGION, STAFF_ROLE, membershipDocPath } from './staffConstants';

interface MarketDefinition { currency: string; timeZones: readonly string[] }
const MARKETS: Readonly<Record<string, MarketDefinition>> = {
  NG: { currency: 'NGN', timeZones: ['Africa/Lagos'] },
  GH: { currency: 'GHS', timeZones: ['Africa/Accra'] },
  KE: { currency: 'KES', timeZones: ['Africa/Nairobi'] },
  CI: { currency: 'XOF', timeZones: ['Africa/Abidjan'] },
  SN: { currency: 'XOF', timeZones: ['Africa/Dakar'] },
  BJ: { currency: 'XOF', timeZones: ['Africa/Porto-Novo'] },
  TG: { currency: 'XOF', timeZones: ['Africa/Lome'] },
  ML: { currency: 'XOF', timeZones: ['Africa/Bamako'] },
  BF: { currency: 'XOF', timeZones: ['Africa/Ouagadougou'] },
  NE: { currency: 'XOF', timeZones: ['Africa/Niamey'] },
  CM: { currency: 'XAF', timeZones: ['Africa/Douala'] },
  GA: { currency: 'XAF', timeZones: ['Africa/Libreville'] },
  CG: { currency: 'XAF', timeZones: ['Africa/Brazzaville'] },
  GN: { currency: 'GNF', timeZones: ['Africa/Conakry'] },
  CD: { currency: 'CDF', timeZones: ['Africa/Kinshasa', 'Africa/Lubumbashi'] },
};

export interface WorkshopMarketResponse {
  workshopUid: string;
  country: string;
  currency: string;
  timeZone: string;
}

/** Membership-checked market projection. Never return the owner's private profile. */
export async function getWorkshopMarketHandler(
  data: { workshopUid?: unknown },
  context: functions.https.CallableContext,
  db: admin.firestore.Firestore,
): Promise<WorkshopMarketResponse> {
  if (!context.auth) throw new functions.https.HttpsError('unauthenticated', 'auth_required');
  const auth = context.auth;
  const isStaff = auth.token.role === STAFF_ROLE;
  const workshopUid = isStaff ? auth.token.workshopUid : auth.uid;
  if (typeof workshopUid !== 'string' || !workshopUid || data?.workshopUid !== workshopUid) {
    throw new functions.https.HttpsError('permission-denied', 'workshop_market_denied');
  }
  return db.runTransaction(async (tx) => {
    if (isStaff) {
      const membership = await tx.get(db.doc(membershipDocPath(workshopUid, auth.uid)));
      if (!membership.exists || membership.data()?.status !== 'active') {
        throw new functions.https.HttpsError('permission-denied', 'workshop_market_denied');
      }
    }
    const user = await tx.get(db.doc(`users/${workshopUid}`));
    if (isStaff && !user.exists) throw new functions.https.HttpsError('not-found', 'workshop_market_missing');
    const profile = user.data();
    const rawCountry = typeof profile?.country === 'string' ? profile.country.toUpperCase() : 'NG';
    const country = Object.prototype.hasOwnProperty.call(MARKETS, rawCountry) ? rawCountry : 'NG';
    const definition = MARKETS[country];
    const timeZone = typeof profile?.timeZone === 'string' && definition.timeZones.includes(profile.timeZone)
      ? profile.timeZone : definition.timeZones[0];
    // Explicit allowlist: no email, contact, bank or entitlement fields.
    return { workshopUid, country, currency: definition.currency, timeZone };
  });
}

export const getWorkshopMarket = functions.region(REGION).https.onCall(
  (data, context) => getWorkshopMarketHandler(data, context, admin.firestore()),
);

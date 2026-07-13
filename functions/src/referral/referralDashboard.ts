import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';
import { REGION, MARKETERS } from './referralConstants';
import type { ReferrerType, PayoutKind, MarketerStatus } from './referralConstants';

// getReferralDashboard — admin-only read of the marketer payout book for the
// web dashboard (getstitchpad.com/admin, in the stitchpad-web repo). The
// marketers/ + referrals/ collections are Admin-SDK-only in firestore.rules,
// so the browser can never read them
// directly; this callable is the only door, gated by the Firebase Auth
// `admin: true` custom claim (set via scripts/setAdminClaim.js). All amounts are
// integer kobo — the client divides by 100 for ₦ display.

export interface MarketerRow {
  id: string;
  name: string;
  email: string;
  phone: string | null;
  code: string;
  type: ReferrerType;
  payoutKind: PayoutKind;
  status: MarketerStatus;
  bankName: string | null;
  bankAccountName: string | null;
  bankAccountNumber: string | null;
  payoutRatePerUser: number;
  installs: number;
  activated: number;
  qualified: number;
  pendingAmount: number;
  confirmedAmount: number;
  paidAmount: number;
}

export interface DashboardTotals {
  marketers: number;
  installs: number;
  activated: number;
  qualified: number;
  pendingAmount: number;
  confirmedAmount: number;
  paidAmount: number;
}

export interface ReferralDashboardResponse {
  generatedAtMs: number;
  totals: DashboardTotals;
  marketers: MarketerRow[];
}

export interface DashboardDeps {
  db: admin.firestore.Firestore;
  now: () => Date;
}

const asNum = (v: unknown): number => (typeof v === 'number' && Number.isFinite(v) ? v : 0);
const asStr = (v: unknown): string => (typeof v === 'string' ? v : '');
const asStrOrNull = (v: unknown): string | null => (typeof v === 'string' && v ? v : null);

export async function getReferralDashboardHandler(
  context: functions.https.CallableContext,
  deps: DashboardDeps,
): Promise<ReferralDashboardResponse> {
  if (context.auth?.token?.admin !== true) {
    throw new functions.https.HttpsError('permission-denied', 'admin_only');
  }

  const snap = await deps.db.collection(MARKETERS).get();
  const marketers: MarketerRow[] = snap.docs.map((d) => {
    const m = d.data();
    return {
      id: d.id,
      name: asStr(m.name),
      email: asStr(m.email),
      phone: asStrOrNull(m.phone),
      code: asStr(m.code),
      type: (m.type === 'user' ? 'user' : 'affiliate') as ReferrerType,
      payoutKind: (m.payoutKind === 'credit' ? 'credit' : 'cash') as PayoutKind,
      status: (m.status === 'disabled' ? 'disabled' : 'active') as MarketerStatus,
      bankName: asStrOrNull(m.bankName),
      bankAccountName: asStrOrNull(m.bankAccountName),
      bankAccountNumber: asStrOrNull(m.bankAccountNumber),
      payoutRatePerUser: asNum(m.payoutRatePerUser),
      installs: asNum(m.installs),
      activated: asNum(m.activated),
      qualified: asNum(m.qualified),
      pendingAmount: asNum(m.pendingAmount),
      confirmedAmount: asNum(m.confirmedAmount),
      paidAmount: asNum(m.paidAmount),
    };
  });

  // Most-owed first — the confirmed column is the payout run.
  marketers.sort((a, b) => b.confirmedAmount - a.confirmedAmount || a.name.localeCompare(b.name));

  const totals = marketers.reduce<DashboardTotals>((t, m) => ({
    marketers: t.marketers + 1,
    installs: t.installs + m.installs,
    activated: t.activated + m.activated,
    qualified: t.qualified + m.qualified,
    pendingAmount: t.pendingAmount + m.pendingAmount,
    confirmedAmount: t.confirmedAmount + m.confirmedAmount,
    paidAmount: t.paidAmount + m.paidAmount,
  }), { marketers: 0, installs: 0, activated: 0, qualified: 0, pendingAmount: 0, confirmedAmount: 0, paidAmount: 0 });

  return { generatedAtMs: deps.now().getTime(), totals, marketers };
}

export const getReferralDashboard = functions
  .region(REGION)
  .https.onCall(async (_data, context): Promise<ReferralDashboardResponse> =>
    getReferralDashboardHandler(context, { db: admin.firestore(), now: () => new Date() }));

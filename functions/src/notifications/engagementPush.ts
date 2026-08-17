/**
 * Twice-weekly engagement push — the activation nudge.
 *
 * The daily digest only speaks to tailors who already have work in flight: it
 * suppresses when the digest model is empty, so a tailor who installed and never
 * created an order hears nothing, ever. This job fills that hole. It tells each
 * tailor about the ONE thing they have not discovered yet (see segmentDetector)
 * and carries announcements, with copy and cadence driven from
 * `config/engagementPush` so neither needs a deploy.
 *
 * Scheduled at 10:00 rather than early morning ON PURPOSE: the digest runs at
 * 07:00 and stamps `lastPushDate`, so running afterwards makes "did this user
 * already hear from us today?" answerable. Running first, we could not know.
 */
import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';
import type { DocumentData, Firestore } from 'firebase-admin/firestore';
import { digestDetector, isDigestEmpty } from './digestDetector';
import { EngagementCampaign, parseEngagementConfig } from './engagementConfig';
import { selectCampaign } from './engagementSelector';
import {
  EngagementCounts,
  EngagementIO,
  EngagementRecipient,
  EngagementUserState,
} from './engagementTypes';
import {
  ANNOUNCEMENTS_CHANNEL_ID,
  deletePushTokens,
  loadPushTokens,
  sendMulticast,
} from './fcm';
import { lagosDateKey, lagosDayIndex } from './lagosTime';
import { isDigestTester, isEngagementAllowed } from './rollout';
import { runEngagementPush } from './runEngagementPush';
import { detectSegment, Tier } from './segmentDetector';
import { mapOrderScanDoc } from './orderScan';

const REGION = 'europe-west1';
/** Tue + Fri at 10:00 Lagos — 3-4 days apart, never adjacent, always after the digest. */
const SCHEDULE = '0 10 * * 2,5';
const TIMEZONE = 'Africa/Lagos';

const CONFIG_DOC = 'config/engagementPush';

function digestStateRef(db: Firestore, uid: string) {
  return db.collection('users').doc(uid).collection('private').doc('digestState');
}

/** Mirrors SubscriptionTier.fromWire on the client, legacy 'premium' included. */
function resolveTier(raw: unknown): Tier {
  switch (typeof raw === 'string' ? raw.toLowerCase() : '') {
    case 'pro':
    case 'premium': // legacy V1.0 value
      return 'pro';
    case 'atelier':
      return 'atelier';
    default:
      return 'free';
  }
}

/**
 * Opt-out resolution. A dedicated `announcementsPushEnabled` flag, falling back
 * through the two older preferences so no migration is needed: a tailor who
 * already opted out of daily push is not silently opted into tips.
 */
function resolveAnnouncementsEnabled(u: DocumentData): boolean {
  if (u.announcementsPushEnabled !== undefined) return u.announcementsPushEnabled !== false;
  if (u.dailyPushEnabled !== undefined) return u.dailyPushEnabled !== false;
  return u.dailyDigestEmailEnabled !== false;
}

function recipientFromDoc(uid: string, u: DocumentData): EngagementRecipient {
  return {
    uid,
    email: typeof u.email === 'string' ? u.email : '',
    announcementsEnabled: resolveAnnouncementsEnabled(u),
    tier: resolveTier(u.subscriptionTier),
    hasReferralLink: typeof u.referralCode === 'string' && u.referralCode.trim().length > 0,
  };
}

/**
 * Active, non-owner roster size.
 *
 * Read in full rather than counted with an aggregation, deliberately. Every
 * workshop gets a lazily-created OWNER row, and archived members are never hard
 * deleted (status flips to 'archived'), so a raw .count() would be >= 1 for
 * anyone who has ever opened the team screen — which would silently mean
 * `busy_no_team` never fires for anybody. Filtering needs two equality clauses,
 * which would need a composite index; team rosters are a handful of docs, so
 * reading them is cheaper than that migration.
 */
async function countActiveTeam(db: Firestore, uid: string): Promise<number> {
  const snap = await db.collection('users').doc(uid).collection('team').get();
  return snap.docs.filter((d) => {
    const data = d.data();
    const kind = typeof data.kind === 'string' ? data.kind.toLowerCase() : 'named';
    const status = typeof data.status === 'string' ? data.status.toLowerCase() : 'active';
    return kind !== 'owner' && status !== 'archived';
  }).length;
}

async function loadCounts(db: Firestore, uid: string): Promise<EngagementCounts> {
  const userRef = db.collection('users').doc(uid);
  // Aggregations bill roughly one read per 1000 documents counted, so customers
  // and orders cost ~nothing however large the workshop.
  //
  // Counts are raw and include archived rows on purpose: the ladder only asks
  // "have they ever done this at all", and `where('archivedAt','==',null)` would
  // silently miss docs where the field is absent rather than null.
  const [customers, orders, teamCount] = await Promise.all([
    userRef.collection('customers').count().get(),
    userRef.collection('orders').count().get(),
    countActiveTeam(db, uid),
  ]);
  return {
    customerCount: customers.data().count,
    orderCount: orders.data().count,
    teamCount,
  };
}

function stateFromDoc(data: DocumentData | undefined): EngagementUserState {
  const engagement = (data?.engagement ?? {}) as DocumentData;
  const counts = engagement.campaigns;
  return {
    lastPushDate: typeof data?.lastPushDate === 'string' ? data.lastPushDate : null,
    lastSentDayIndex: typeof engagement.lastSentDayIndex === 'number'
      ? engagement.lastSentDayIndex
      : null,
    sendCount: typeof engagement.sendCount === 'number' ? engagement.sendCount : 0,
    campaignCounts: (counts && typeof counts === 'object' && !Array.isArray(counts))
      ? counts as Record<string, number>
      : {},
  };
}

function pushPayloadFor(campaign: EngagementCampaign) {
  return {
    title: campaign.title,
    body: campaign.body,
    data: { target: campaign.target },
    androidChannelId: ANNOUNCEMENTS_CHANNEL_ID,
    // iOS has no channel concept, so this is the only way to make a tip land
    // more quietly than an overdue-order alert there.
    passive: true,
  };
}

export function productionEngagementIO(): EngagementIO {
  const db = admin.firestore();
  return {
    loadConfig: async () => (await db.doc(CONFIG_DOC).get()).data(),

    /**
     * One users collection read. Deliberately NOT dailyDigest's listRecipients:
     * that does a serial admin.auth().getUser() per user to obtain a VERIFIED
     * email for sending mail (the N+1 flagged in dailyDigest.ts). This job sends
     * no email, so it only needs an address for the allowlist gate and falls back
     * to Auth solely for legacy docs with a blank email field — the same pattern
     * onOrderCollectible already uses.
     */
    listRecipients: async () => {
      const snap = await db.collection('users').get();
      const recipients: EngagementRecipient[] = [];
      for (const doc of snap.docs) {
        const r = recipientFromDoc(doc.id, doc.data());
        if (!r.email) {
          r.email = await admin.auth().getUser(doc.id)
            .then((u) => u.email ?? '')
            .catch(() => '');
        }
        recipients.push(r);
      }
      return recipients;
    },

    loadState: async (uid) => stateFromDoc((await digestStateRef(db, uid).get()).data()),
    loadCounts: (uid) => loadCounts(db, uid),

    loadOrders: async (uid) => {
      const snap = await db.collection('users').doc(uid).collection('orders').get();
      return snap.docs.map((d) => mapOrderScanDoc(d.id, d.data()));
    },

    loadPushTokens: (uid) => loadPushTokens(db, uid),
    sendPush: (tokens, campaign) => sendMulticast(tokens, pushPayloadFor(campaign), 'engagement push'),
    deletePushTokens: (uid, tokens) => deletePushTokens(db, uid, tokens),

    /**
     * Stamps `lastPushDate` alongside the engagement state so the one-scheduled-
     * push-per-user-per-day invariant holds in both directions. Safe: by 10:00 the
     * digest has already run, so this suppresses nothing today, and tomorrow's key
     * differs so the digest still fires.
     */
    recordSent: async (uid, campaignId, dateKey, dayIndex) => {
      await digestStateRef(db, uid).set({
        lastPushDate: dateKey,
        engagement: {
          lastSentDate: dateKey,
          lastSentDayIndex: dayIndex,
          sendCount: admin.firestore.FieldValue.increment(1),
          campaigns: { [campaignId]: admin.firestore.FieldValue.increment(1) },
        },
      }, { merge: true });
    },

    isAllowed: isEngagementAllowed,
  };
}

export const engagementPush = functions
  .region(REGION)
  .pubsub.schedule(SCHEDULE)
  .timeZone(TIMEZONE)
  .onRun(async () => {
    await runEngagementPush(productionEngagementIO(), Date.now());
  });

/**
 * Debug/QA trigger: sends the caller their own engagement push right now,
 * bypassing the weekday, cadence, and already-pushed-today gates so copy can be
 * checked on a real device without waiting for Tuesday. Still honours the config
 * being enabled, the opt-out, and the per-campaign cap — those are the things a
 * tester actually wants to verify.
 */
export const debugSendMyEngagementPush = functions
  .region(REGION)
  .https.onCall(async (_data, context) => {
    const uid = context.auth?.uid;
    if (!uid) throw new functions.https.HttpsError('unauthenticated', 'Sign in required.');

    const db = admin.firestore();
    const authUser = await admin.auth().getUser(uid);
    if (!authUser.email || !isDigestTester(authUser.email)) {
      throw new functions.https.HttpsError('permission-denied', 'not_a_tester');
    }

    const io = productionEngagementIO();
    const config = parseEngagementConfig(await io.loadConfig());
    if (!config.enabled) return { sent: false, reason: 'config_disabled' };
    if (config.campaigns.length === 0) return { sent: false, reason: 'no_valid_campaigns' };

    const now = Date.now();
    const userSnap = await db.collection('users').doc(uid).get();
    const recipient = recipientFromDoc(uid, userSnap.data() ?? {});
    if (!recipient.announcementsEnabled) return { sent: false, reason: 'opted_out' };

    const state = await io.loadState(uid);
    const counts = await io.loadCounts(uid);
    const digestEmpty = isDigestEmpty(digestDetector(await io.loadOrders(uid), now));
    const segment = detectSegment({ ...counts, hasReferralLink: recipient.hasReferralLink, digestEmpty, tier: recipient.tier });

    const campaign = selectCampaign(
      config,
      { segment, sendCount: state.sendCount, campaignCounts: state.campaignCounts },
      now,
    );
    if (!campaign) return { sent: false, reason: 'no_campaign_for_segment', segment };

    const tokens = await io.loadPushTokens(uid);
    if (tokens.length === 0) return { sent: false, reason: 'no_tokens', segment };

    const { successCount, invalidTokens } = await io.sendPush(tokens, campaign);
    if (invalidTokens.length > 0) await io.deletePushTokens(uid, invalidTokens);
    if (successCount > 0) {
      await io.recordSent(uid, campaign.id, lagosDateKey(now), lagosDayIndex(now));
    }

    return {
      sent: successCount > 0,
      segment,
      campaignId: campaign.id,
      title: campaign.title,
      body: campaign.body,
      successCount,
      tokenCount: tokens.length,
    };
  });

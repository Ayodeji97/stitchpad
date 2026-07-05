import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';
import { BigQuery } from '@google-cloud/bigquery';
import { sendResendEmail } from '../email/resendClient';
import { isDigestTester } from '../notifications/rollout';
import {
  DailyOnboardingMetricsDoc,
  MetricsIO,
  OnboardingRow,
  runOnboardingMetrics,
} from './runOnboardingMetrics';
import {
  BQ_DATASET,
  BQ_LOCATION,
  BQ_PROJECT,
  MAX_METRICS_WINDOW_DAYS,
  METRICS_EMAIL_TO,
  METRICS_WINDOW_DAYS,
  SETUP_EVENT,
  SIGN_UP_EVENT,
} from './metricsConstants';

const REGION = 'europe-west1';
const SCHEDULE = '0 6 * * *';
const TIMEZONE = 'Africa/Lagos';

// BigQuery client is stateless + auth is lazy, so construct it once at module
// scope and reuse it across warm invocations. (admin.firestore() stays lazy
// inside the factory — at module load it would run before index.ts calls
// admin.initializeApp().)
const bq = new BigQuery({ projectId: BQ_PROJECT });

/** metrics/onboarding_daily/days/{YYYY-MM-DD} — one upserted doc per calendar day. */
function dailyMetricsRef(db: admin.firestore.Firestore, date: string) {
  return db.collection('metrics').doc('onboarding_daily').collection('days').doc(date);
}

function productionMetricsIO(apiKey: string): MetricsIO {
  const db = admin.firestore();
  return {
    async queryOnboardingRows(windowDays: number): Promise<OnboardingRow[]> {
      // windowDays is inlined into the INTERVAL (BigQuery rejects a query param
      // there), so bound it hard: an out-of-range value would turn the rolling
      // window into a full-dataset scan. Event names stay parameterized.
      if (!Number.isInteger(windowDays) || windowDays < 1 || windowDays > MAX_METRICS_WINDOW_DAYS) {
        throw new Error(`invalid windowDays: ${windowDays}`);
      }
      const query = `
        SELECT
          event_date AS eventDate,
          platform,
          event_name AS eventName,
          COUNT(DISTINCT user_pseudo_id) AS users
        FROM \`${BQ_PROJECT}.${BQ_DATASET}.events_*\`
        WHERE event_name IN (@signUp, @setup)
          -- Only date-suffixed daily tables; excludes events_intraday_* (suffix
          -- 'intraday_…') so a future streaming-export enable can't double-count.
          AND _TABLE_SUFFIX NOT LIKE 'intraday%'
          -- windowDays tables inclusive of today: today - (windowDays - 1). The
          -- CURRENT_DATE timezone must match the GA4 property's reporting timezone
          -- that _TABLE_SUFFIX is keyed to (Nigeria property = Africa/Lagos).
          AND _TABLE_SUFFIX >= FORMAT_DATE('%Y%m%d', DATE_SUB(CURRENT_DATE('${TIMEZONE}'), INTERVAL ${windowDays - 1} DAY))
        GROUP BY eventDate, platform, eventName`;
      const [rows] = await bq.query({
        query,
        location: BQ_LOCATION,
        params: { signUp: SIGN_UP_EVENT, setup: SETUP_EVENT },
      });
      return (rows as Record<string, unknown>[]).map((r) => ({
        eventDate: String(r.eventDate ?? ''),
        platform: r.platform == null ? null : String(r.platform),
        eventName: String(r.eventName ?? ''),
        users: Number(r.users) || 0,
      }));
    },
    async writeDailyMetrics(metrics: DailyOnboardingMetricsDoc): Promise<void> {
      await dailyMetricsRef(db, metrics.date).set(metrics, { merge: true });
    },
    sendEmail(params) {
      return sendResendEmail(apiKey, params);
    },
  };
}

/**
 * Daily onboarding metrics — 06:00 Africa/Lagos. Re-computes a rolling window (so
 * the lagging GA4 daily export self-heals), upserts one Firestore doc per day, and
 * emails the ops summary. Thin cron shell over the pure handler in
 * runOnboardingMetrics.ts.
 */
export const dailyOnboardingMetrics = functions
  .region(REGION)
  .runWith({ secrets: ['RESEND_API_KEY'] })
  .pubsub.schedule(SCHEDULE)
  .timeZone(TIMEZONE)
  .onRun(async () => {
    const apiKey = process.env.RESEND_API_KEY;
    if (!apiKey) {
      // Metrics can still be persisted without email; log and continue emailless.
      functions.logger.warn('RESEND_API_KEY not configured — writing metrics without email');
    }
    await runOnboardingMetrics(productionMetricsIO(apiKey ?? ''), Date.now(), {
      windowDays: METRICS_WINDOW_DAYS,
      emailTo: apiKey ? METRICS_EMAIL_TO : undefined,
    });
  });

/**
 * Debug/QA trigger: runs the metrics job on demand (any signed-in caller) and
 * returns the run result, so you can verify the Firestore docs + email without
 * waiting for the 06:00 cron.
 */
export const debugRunOnboardingMetrics = functions
  .region(REGION)
  .runWith({ secrets: ['RESEND_API_KEY'] })
  .https.onCall(async (data, context) => {
    const uid = context.auth?.uid;
    if (!uid) {
      throw new functions.https.HttpsError('unauthenticated', 'Sign in required.');
    }
    // Gate to the internal tester allowlist: this triggers a BigQuery job and an
    // email to the ops address, so it must not be callable by arbitrary users.
    const authUser = await admin.auth().getUser(uid);
    if (!authUser.email || !isDigestTester(authUser.email)) {
      throw new functions.https.HttpsError('permission-denied', 'not_a_tester');
    }
    const apiKey = process.env.RESEND_API_KEY;
    // Clamp the caller-supplied window to [1, MAX] so a tester can't request a
    // full-dataset scan (queryOnboardingRows also hard-rejects out-of-range).
    const requested = Number.isInteger(data?.windowDays) ? data.windowDays : METRICS_WINDOW_DAYS;
    const windowDays = Math.min(Math.max(1, requested), MAX_METRICS_WINDOW_DAYS);
    return runOnboardingMetrics(productionMetricsIO(apiKey ?? ''), Date.now(), {
      windowDays,
      emailTo: apiKey ? METRICS_EMAIL_TO : undefined,
    });
  });

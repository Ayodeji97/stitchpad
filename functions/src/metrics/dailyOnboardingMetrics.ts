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
  METRICS_EMAIL_TO,
  METRICS_WINDOW_DAYS,
  SETUP_EVENT,
  SIGN_UP_EVENT,
} from './metricsConstants';

const REGION = 'europe-west1';
const SCHEDULE = '0 6 * * *';
const TIMEZONE = 'Africa/Lagos';

/** metrics/onboarding_daily/days/{YYYY-MM-DD} — one upserted doc per calendar day. */
function dailyMetricsRef(db: admin.firestore.Firestore, date: string) {
  return db.collection('metrics').doc('onboarding_daily').collection('days').doc(date);
}

function productionMetricsIO(apiKey: string): MetricsIO {
  const db = admin.firestore();
  const bq = new BigQuery({ projectId: BQ_PROJECT });
  return {
    async queryOnboardingRows(windowDays: number): Promise<OnboardingRow[]> {
      // windowDays is a trusted internal constant; still assert it's a plain
      // integer before inlining it into the INTERVAL (BigQuery does not accept a
      // query parameter inside INTERVAL). Event names stay parameterized.
      if (!Number.isInteger(windowDays) || windowDays < 1) {
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
          AND _TABLE_SUFFIX >= FORMAT_DATE('%Y%m%d', DATE_SUB(CURRENT_DATE('${TIMEZONE}'), INTERVAL ${windowDays} DAY))
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
    const windowDays = Number.isInteger(data?.windowDays) ? data.windowDays : METRICS_WINDOW_DAYS;
    return runOnboardingMetrics(productionMetricsIO(apiKey ?? ''), Date.now(), {
      windowDays,
      emailTo: apiKey ? METRICS_EMAIL_TO : undefined,
    });
  });

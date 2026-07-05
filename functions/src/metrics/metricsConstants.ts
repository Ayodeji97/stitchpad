/**
 * Config for the daily onboarding-metrics job (see runOnboardingMetrics.ts).
 *
 * Source of truth is the GA4 → BigQuery export (enabled 2026-06). The app fires
 * `sign_up` and `workshop_setup_completed` analytics events; GA4 attaches the
 * platform ('IOS'/'ANDROID') and event date automatically, which is the only
 * server-side signal that carries platform per user.
 */

/** GCP project that owns the analytics export dataset. */
export const BQ_PROJECT = 'stitchpad-30607';

/** GA4 export dataset. The numeric suffix is the GA4 property id. */
export const BQ_DATASET = 'analytics_530817992';

/** Dataset location — BigQuery jobs MUST run in the dataset's region. */
export const BQ_LOCATION = 'europe-west1';

/**
 * How many days back each run re-computes. The GA4 daily export lags ~1–2 days
 * (up to ~72h to finalize a day) and there is no streaming/intraday export, so a
 * single "yesterday" query would often hit a missing table. Re-computing a rolling
 * window and upserting each day makes late-landing data self-heal on the next run.
 */
export const METRICS_WINDOW_DAYS = 7;

/**
 * Hard cap on the window, enforced when the value is inlined into the BigQuery
 * INTERVAL. Stops a debug caller (or a bad constant) from turning the rolling
 * window into a full-dataset scan of every GA4 export table.
 */
export const MAX_METRICS_WINDOW_DAYS = 90;

/** Where the daily summary email is sent (ops recipient). */
export const METRICS_EMAIL_TO = 'danielayodeji97@gmail.com';

/** The two GA4 events that define the onboarding funnel. */
export const SIGN_UP_EVENT = 'sign_up';
export const SETUP_EVENT = 'workshop_setup_completed';

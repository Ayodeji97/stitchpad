import * as functions from 'firebase-functions/v1';
import { METRICS_WINDOW_DAYS, SETUP_EVENT, SIGN_UP_EVENT } from './metricsConstants';

/**
 * Daily onboarding metrics — how many people signed up / completed workshop setup,
 * grouped by date and platform (iOS/Android).
 *
 * This module is the PURE, dependency-injected core (mirrors runDailyDigest /
 * abandonStalePendingCheckoutsHandler): all IO goes through {@link MetricsIO} so
 * the aggregation + email shaping are unit-testable with fakes. The BigQuery /
 * Firestore / Resend wiring lives in dailyOnboardingMetrics.ts.
 */

/** One row as returned by the BigQuery rolling-window query. */
export interface OnboardingRow {
  /** GA4 event date, 'YYYYMMDD'. */
  eventDate: string;
  /** GA4 platform, typically 'IOS' / 'ANDROID'; null/other tolerated. */
  platform: string | null;
  /** 'sign_up' or 'workshop_setup_completed'. */
  eventName: string;
  /** Distinct users (user_pseudo_id) for this date/platform/event. */
  users: number;
}

/** Per-platform user counts for one event on one day. `total` includes `other`. */
export interface PlatformCounts {
  ios: number;
  android: number;
  other: number;
  total: number;
}

/** Aggregated onboarding numbers for a single calendar day. */
export interface DailyOnboardingMetrics {
  /** 'YYYY-MM-DD'. */
  date: string;
  signups: PlatformCounts;
  setups: PlatformCounts;
}

/** What gets persisted per day — the aggregate plus a write timestamp. */
export type DailyOnboardingMetricsDoc = DailyOnboardingMetrics & { generatedAt: number };

export interface EmailParams {
  to: string;
  subject: string;
  html: string;
  text: string;
}

export interface MetricsIO {
  /** Runs the rolling-window query and returns raw per-day/platform/event rows. */
  queryOnboardingRows(windowDays: number): Promise<OnboardingRow[]>;
  /** Upserts one day's aggregate (idempotent — safe to re-run as late data lands). */
  writeDailyMetrics(metrics: DailyOnboardingMetricsDoc): Promise<void>;
  /** Sends the summary email (best-effort — a failure never fails the run). */
  sendEmail(params: EmailParams): Promise<void>;
}

export interface RunMetricsResult {
  windowDays: number;
  rows: number;
  days: number;
  emailSent: boolean;
}

export interface RunMetricsOptions {
  windowDays?: number;
  /** Ops email recipient. When absent, the email step is skipped. */
  emailTo?: string;
}

function emptyCounts(): PlatformCounts {
  return { ios: 0, android: 0, other: 0, total: 0 };
}

/** GA4 'IOS'/'ANDROID' → our bucket keys; anything else (WEB, null) → 'other'. */
function platformKey(raw: string | null): 'ios' | 'android' | 'other' {
  switch ((raw ?? '').toUpperCase()) {
    case 'IOS': return 'ios';
    case 'ANDROID': return 'android';
    default: return 'other';
  }
}

/** '20260703' → '2026-07-03'. */
function toIsoDate(yyyymmdd: string): string {
  return `${yyyymmdd.slice(0, 4)}-${yyyymmdd.slice(4, 6)}-${yyyymmdd.slice(6, 8)}`;
}

/**
 * Folds raw rows into one {@link DailyOnboardingMetrics} per day, sorted ascending
 * by date. Pure — the write timestamp is stamped by the handler, not here, so the
 * output is deterministic for tests.
 */
export function aggregateRows(rows: OnboardingRow[]): DailyOnboardingMetrics[] {
  const byDate = new Map<string, DailyOnboardingMetrics>();
  for (const row of rows) {
    if (!/^\d{8}$/.test(row.eventDate)) continue; // ignore malformed suffixes
    const date = toIsoDate(row.eventDate);
    let day = byDate.get(date);
    if (!day) {
      day = { date, signups: emptyCounts(), setups: emptyCounts() };
      byDate.set(date, day);
    }
    const bucket = row.eventName === SIGN_UP_EVENT ? day.signups
      : row.eventName === SETUP_EVENT ? day.setups
        : null;
    if (!bucket) continue;
    const count = Number.isFinite(row.users) ? Math.max(0, Math.trunc(row.users)) : 0;
    bucket[platformKey(row.platform)] += count;
    bucket.total += count;
  }
  return [...byDate.values()].sort((a, b) => a.date.localeCompare(b.date));
}

function counts(c: PlatformCounts): string {
  return `${c.total} (iOS ${c.ios} · Android ${c.android}${c.other ? ` · other ${c.other}` : ''})`;
}

/** Builds the ops summary email. Pure — no IO, no clock. */
export function buildMetricsEmail(days: DailyOnboardingMetrics[], windowDays: number): EmailParams {
  const subject = `StitchPad onboarding — last ${windowDays} days`;
  const recent = [...days].sort((a, b) => b.date.localeCompare(a.date)); // newest first

  const totalSignups = days.reduce((n, d) => n + d.signups.total, 0);
  const totalSetups = days.reduce((n, d) => n + d.setups.total, 0);

  // Each cell uses the same counts() helper as the plain-text body, so the two
  // halves of the email always agree and Total always equals iOS + Android + other.
  const cell = 'padding:6px 12px;border-bottom:1px solid #eee;';
  const rowsHtml = recent.map((d) => `
    <tr>
      <td style="${cell}">${d.date}</td>
      <td style="${cell}">${counts(d.signups)}</td>
      <td style="${cell}">${counts(d.setups)}</td>
    </tr>`).join('');

  const html = `
    <div style="font-family:Arial,Helvetica,sans-serif;color:#14110E;">
      <h2 style="margin:0 0 4px;">Onboarding — last ${windowDays} days</h2>
      <p style="margin:0 0 16px;color:#555;">
        ${totalSignups} sign-ups · ${totalSetups} workshop setups completed
      </p>
      <table style="border-collapse:collapse;font-size:14px;">
        <thead>
          <tr style="text-align:left;color:#555;">
            <th style="padding:6px 12px;">Date</th>
            <th style="padding:6px 12px;">Signups (iOS · Android)</th>
            <th style="padding:6px 12px;">Setups (iOS · Android)</th>
          </tr>
        </thead>
        <tbody>${rowsHtml || '<tr><td style="padding:6px 12px;color:#999;">No onboarding activity in window.</td></tr>'}</tbody>
      </table>
    </div>`;

  const textLines = recent.map((d) => `${d.date}  signups ${counts(d.signups)}  |  setups ${counts(d.setups)}`);
  const text = [
    `StitchPad onboarding — last ${windowDays} days`,
    `${totalSignups} sign-ups · ${totalSetups} workshop setups completed`,
    '',
    ...(textLines.length ? textLines : ['No onboarding activity in window.']),
  ].join('\n');

  return { to: '', subject, html, text };
}

/**
 * Queries the rolling window, upserts each day's aggregate, and emails the summary.
 * The email is best-effort: metrics are persisted first, so a Resend outage never
 * loses data or fails the scheduled run.
 */
export async function runOnboardingMetrics(
  io: MetricsIO,
  nowMs: number,
  options: RunMetricsOptions = {},
): Promise<RunMetricsResult> {
  const windowDays = options.windowDays ?? METRICS_WINDOW_DAYS;
  const rows = await io.queryOnboardingRows(windowDays);
  const days = aggregateRows(rows);

  // Per-day upserts are independent and idempotent, so write them concurrently.
  await Promise.all(days.map((day) => io.writeDailyMetrics({ ...day, generatedAt: nowMs })));

  let emailSent = false;
  if (options.emailTo) {
    try {
      const email = buildMetricsEmail(days, windowDays);
      await io.sendEmail({ ...email, to: options.emailTo });
      emailSent = true;
    } catch (err) {
      functions.logger.warn('onboarding metrics: email failed', {
        error: err instanceof Error ? err.message : String(err),
      });
    }
  }

  const result: RunMetricsResult = { windowDays, rows: rows.length, days: days.length, emailSent };
  functions.logger.info('onboarding metrics run complete', { ...result });
  return result;
}

/**
 * Parses `config/engagementPush` — the Firestore doc that holds engagement-push
 * copy and cadence — into a validated shape.
 *
 * This is the app's only hand-edited-in-production input: copy is changed in the
 * Firebase console with no deploy and no review, which is the feature but also
 * makes this parser the safety boundary. Two rules follow from that:
 *
 *   1. FAIL SAFE IS SILENCE. A missing, unreadable, or nonsense doc yields
 *      `enabled: false` and zero campaigns. We never guess at intent, and we
 *      never send a half-understood message to real users.
 *   2. ONE BAD CAMPAIGN MUST NOT KILL THE RUN. Malformed entries are dropped
 *      individually with a warning; their valid siblings still send. A typo in
 *      next month's announcement should not silence this week's nudge.
 *
 * Every rejection is logged with a reason, because "nothing sent" is otherwise
 * indistinguishable from "nothing to send".
 */
import * as functions from 'firebase-functions/v1';
import { unknownTemplateVariables } from './campaignTemplate';
import { Segment, SEGMENTS } from './segmentDetector';

/** Deep-link targets the client knows how to route. Keep in sync with PushTargetParser.kt. */
export const CAMPAIGN_TARGETS = ['inbox', 'to_collect', 'dashboard', 'founding_tailors'] as const;
export type CampaignTarget = (typeof CAMPAIGN_TARGETS)[number];

export const DEFAULT_DAYS_OF_WEEK = [2, 5]; // Tue + Fri
export const DEFAULT_MIN_DAYS_BETWEEN = 3;

/**
 * Generous cap on copy length. FCM payloads are limited to ~4KB and the OS
 * ellipsizes long bodies anyway, so anything past this is a paste accident.
 * Dropped rather than truncated: a message cut mid-sentence reads as a bug to
 * the user, and silence is the safer failure.
 */
const MAX_COPY_LENGTH = 500;

export interface EngagementCampaign {
  id: string;
  segment: Segment;
  title: string;
  body: string;
  target: CampaignTarget;
  /** >0 jumps the rotation queue; highest wins. Used for time-boxed announcements. */
  priority: number;
  /** Epoch millis window, inclusive. null = unbounded on that side. */
  startAt: number | null;
  endAt: number | null;
  /** Per-user lifetime cap. 0 = unlimited. */
  maxSendsPerUser: number;
}

export interface EngagementConfig {
  enabled: boolean;
  /** Cron-style weekdays, 0=Sun … 6=Sat. */
  daysOfWeek: number[];
  minDaysBetween: number;
  campaigns: EngagementCampaign[];
}

/** The fail-safe result: valid, parseable, and sends nothing. */
export function disabledConfig(): EngagementConfig {
  return {
    enabled: false,
    daysOfWeek: [...DEFAULT_DAYS_OF_WEEK],
    minDaysBetween: DEFAULT_MIN_DAYS_BETWEEN,
    campaigns: [],
  };
}

function isRecord(v: unknown): v is Record<string, unknown> {
  return typeof v === 'object' && v !== null && !Array.isArray(v);
}

/** Non-blank string, trimmed, within the copy cap. Returns null when unusable. */
function cleanString(v: unknown, maxLength = MAX_COPY_LENGTH): string | null {
  if (typeof v !== 'string') return null;
  const trimmed = v.trim();
  if (trimmed.length === 0 || trimmed.length > maxLength) return null;
  return trimmed;
}

/** Finite non-negative integer, or `fallback` when absent/invalid. */
function cleanCount(v: unknown, fallback: number): number {
  if (typeof v !== 'number' || !Number.isFinite(v) || v < 0) return fallback;
  return Math.trunc(v);
}

/** Epoch-millis bound. Anything non-numeric becomes null (= unbounded). */
function cleanBound(v: unknown): number | null {
  if (typeof v !== 'number' || !Number.isFinite(v)) return null;
  return v;
}

function parseCampaign(raw: unknown, index: number): EngagementCampaign | null {
  const reject = (reason: string, extra: Record<string, unknown> = {}) => {
    functions.logger.warn('engagementConfig: campaign dropped', { index, reason, ...extra });
    return null;
  };

  if (!isRecord(raw)) return reject('not_an_object');

  const id = cleanString(raw.id, 120);
  if (!id) return reject('missing_or_invalid_id');

  const segment = raw.segment;
  if (typeof segment !== 'string' || !SEGMENTS.includes(segment as Segment)) {
    return reject('unknown_segment', { id, segment });
  }

  const target = raw.target;
  if (typeof target !== 'string' || !CAMPAIGN_TARGETS.includes(target as CampaignTarget)) {
    return reject('unknown_target', { id, target });
  }

  const title = cleanString(raw.title);
  if (!title) return reject('missing_or_oversized_title', { id });

  const body = cleanString(raw.body);
  if (!body) return reject('missing_or_oversized_body', { id });

  // A {{variable}} we cannot fill would render literally on a lock screen. Catch the
  // typo here — where it silences one campaign and logs why — rather than at send
  // time, where it would reach real users.
  const unknownVars = [...unknownTemplateVariables(title), ...unknownTemplateVariables(body)];
  if (unknownVars.length > 0) {
    return reject('unknown_template_variable', { id, variables: [...new Set(unknownVars)] });
  }

  const startAt = cleanBound(raw.startAt);
  const endAt = cleanBound(raw.endAt);
  // An inverted window would silently never match; that is almost certainly a
  // typo in the console, so surface it rather than letting the campaign sit dead.
  if (startAt !== null && endAt !== null && endAt < startAt) {
    return reject('end_before_start', { id, startAt, endAt });
  }

  return {
    id,
    segment: segment as Segment,
    title,
    body,
    target: target as CampaignTarget,
    priority: cleanCount(raw.priority, 0),
    startAt,
    endAt,
    maxSendsPerUser: cleanCount(raw.maxSendsPerUser, 0),
  };
}

/** Weekdays 0-6, de-duplicated and sorted. Falls back to Tue+Fri. */
function parseDaysOfWeek(raw: unknown): number[] {
  if (!Array.isArray(raw)) return [...DEFAULT_DAYS_OF_WEEK];
  const days = [...new Set(
    raw.filter((d): d is number => typeof d === 'number' && Number.isInteger(d) && d >= 0 && d <= 6),
  )].sort((a, b) => a - b);
  // An array that parsed to nothing means every entry was junk — fall back rather
  // than resolving to "never sends", which would be indistinguishable from off.
  return days.length > 0 ? days : [...DEFAULT_DAYS_OF_WEEK];
}

/**
 * Validates the raw Firestore doc. Never throws — every failure path returns a
 * usable config, and the disabled one sends nothing.
 */
export function parseEngagementConfig(raw: unknown): EngagementConfig {
  if (!isRecord(raw)) {
    functions.logger.warn('engagementConfig: doc missing or not an object — sending nothing');
    return disabledConfig();
  }

  // Strict `=== true`: the string "true", 1, and "yes" are all typos, and each
  // would otherwise switch on a live send to every user.
  const enabled = raw.enabled === true;

  const parsed: EngagementCampaign[] = [];
  const seenIds = new Set<string>();
  const rawCampaigns = Array.isArray(raw.campaigns) ? raw.campaigns : [];
  if (!Array.isArray(raw.campaigns)) {
    functions.logger.warn('engagementConfig: campaigns is not an array — treating as empty');
  }

  rawCampaigns.forEach((c, i) => {
    const campaign = parseCampaign(c, i);
    if (!campaign) return;
    // Duplicate ids would corrupt the per-user send counters (two campaigns
    // sharing one tally) and make rotation non-deterministic. First wins.
    if (seenIds.has(campaign.id)) {
      functions.logger.warn('engagementConfig: campaign dropped', {
        index: i, reason: 'duplicate_id', id: campaign.id,
      });
      return;
    }
    seenIds.add(campaign.id);
    parsed.push(campaign);
  });

  const config: EngagementConfig = {
    enabled,
    daysOfWeek: parseDaysOfWeek(raw.daysOfWeek),
    minDaysBetween: cleanCount(raw.minDaysBetween, DEFAULT_MIN_DAYS_BETWEEN),
    campaigns: parsed,
  };

  functions.logger.info('engagementConfig parsed', {
    enabled: config.enabled,
    campaignCount: config.campaigns.length,
    droppedCount: rawCampaigns.length - config.campaigns.length,
    daysOfWeek: config.daysOfWeek,
    minDaysBetween: config.minDaysBetween,
  });

  return config;
}

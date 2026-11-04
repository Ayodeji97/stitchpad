/** Engine-neutral, dormant request lifecycle. No production endpoint imports this module.
 * A future adapter must resolve actor identity, workshop write access, published-profile
 * ownership and customer-record ownership on the server, then transact every transition.
 */
export type MeasurementJourney = 'existing_customer' | 'designer_discovery';
export type MeasurementStage = 'awaiting_consent' | 'capturing' | 'processing' | 'review' | 'approved' | 'cancelled';
export type MeasurementFailure = 'invalid_request' | 'not_authorised' | 'expired' | 'invalid_transition' |
  'stale_run' | 'invalid_estimate' | 'manual_verification_required' | 'workshop_read_only';
export type WorkflowResult = { ok: true; request: RemoteMeasurementRequest } | { ok: false; error: MeasurementFailure };

export interface RemoteMeasurementRequest {
  id: string;
  workshopUid: string;
  customerUid: string;
  journey: MeasurementJourney;
  /** Existing journey must bind an owner-verified customer record; discovery waits for approval. */
  customerRecordId: string | null;
  createdAt: number;
  expiresAt: number;
  locale: 'en' | 'fr';
  requiredFields: readonly string[];
  stage: MeasurementStage;
  consentRevision: string | null;
  runId: string | null;
  engineVersion: string | null;
  estimatesMm: Readonly<Record<string, number>>;
  reviewedMm: Readonly<Record<string, number>>;
  manuallyVerifiedFields: readonly string[];
}

export type MeasurementAction =
  | { kind: 'consent'; actorUid: string; consentRevision: string }
  | { kind: 'submit_capture'; actorUid: string; runId: string }
  | { kind: 'engine_result'; runId: string; engineVersion: string; estimatesMm: Readonly<Record<string, number>> }
  | { kind: 'review_field'; actorUid: string; field: string; valueMm: number; manuallyVerified: boolean }
  | { kind: 'approve'; actorUid: string }
  | { kind: 'cancel'; actorUid: string };

export interface MeasurementPolicy {
  workshopCanWrite: boolean;
  /** Exact engine + field definitions validated on held-out real data. Never supplied by a browser/provider. */
  validatedFieldsByEngine: Readonly<Record<string, readonly string[]>>;
}

const fail = (error: MeasurementFailure): WorkflowResult => ({ ok: false, error });
const validMm = (value: unknown): value is number => typeof value === 'number' && Number.isFinite(value) && value > 0;
const validId = (value: string): boolean => typeof value === 'string' && value.trim().length > 0;

export function createMeasurementRequest(
  input: Omit<RemoteMeasurementRequest, 'stage' | 'consentRevision' | 'runId' | 'engineVersion' |
  'estimatesMm' | 'reviewedMm' | 'manuallyVerifiedFields'>,
  policy: MeasurementPolicy,
): WorkflowResult {
  if (!policy.workshopCanWrite) return fail('workshop_read_only');
  if (![input.id, input.workshopUid, input.customerUid].every(validId) ||
      !Number.isFinite(input.createdAt) || !Number.isFinite(input.expiresAt) || input.expiresAt <= input.createdAt ||
      !['existing_customer', 'designer_discovery'].includes(input.journey) || !['en', 'fr'].includes(input.locale) ||
      input.requiredFields.length === 0 || !input.requiredFields.every(validId) ||
      new Set(input.requiredFields).size !== input.requiredFields.length ||
      (input.journey === 'existing_customer' && !validId(input.customerRecordId ?? ''))) {
    return fail('invalid_request');
  }
  return { ok: true, request: { ...input, requiredFields: [...input.requiredFields], stage: 'awaiting_consent',
    consentRevision: null, runId: null, engineVersion: null, estimatesMm: {}, reviewedMm: {}, manuallyVerifiedFields: [] } };
}

export function transitionMeasurementRequest(
  current: RemoteMeasurementRequest,
  action: MeasurementAction,
  now: number,
  policy: MeasurementPolicy,
): WorkflowResult {
  // Cancellation stays possible when unpaid or expired; it does not delete approved records.
  if (action.kind === 'cancel') {
    if (action.actorUid !== current.customerUid && action.actorUid !== current.workshopUid) return fail('not_authorised');
    if (current.stage === 'approved') return fail('invalid_transition');
    return { ok: true, request: { ...current, stage: 'cancelled' } };
  }
  if (!Number.isFinite(now) || now < current.createdAt || now >= current.expiresAt) return fail('expired');
  if (current.stage === 'cancelled' || current.stage === 'approved') return fail('invalid_transition');
  switch (action.kind) {
    case 'consent':
      if (action.actorUid !== current.customerUid) return fail('not_authorised');
      if (current.stage !== 'awaiting_consent' || !validId(action.consentRevision)) return fail('invalid_transition');
      return { ok: true, request: { ...current, stage: 'capturing', consentRevision: action.consentRevision } };
    case 'submit_capture':
      if (action.actorUid !== current.customerUid) return fail('not_authorised');
      if (current.stage !== 'capturing' || !validId(action.runId)) return fail('invalid_transition');
      if (!policy.workshopCanWrite) return fail('workshop_read_only');
      return { ok: true, request: { ...current, stage: 'processing', runId: action.runId } };
    case 'engine_result': {
      if (current.stage !== 'processing' || action.runId !== current.runId) return fail('stale_run');
      if (!validId(action.engineVersion) || Object.entries(action.estimatesMm)
        .some(([field, value]) => !current.requiredFields.includes(field) || !validMm(value))) return fail('invalid_estimate');
      return { ok: true, request: { ...current, stage: 'review', engineVersion: action.engineVersion,
        estimatesMm: { ...action.estimatesMm }, reviewedMm: {}, manuallyVerifiedFields: [] } };
    }
    case 'review_field': {
      if (action.actorUid !== current.workshopUid) return fail('not_authorised');
      if (current.stage !== 'review') return fail('invalid_transition');
      if (!policy.workshopCanWrite) return fail('workshop_read_only');
      if (!current.requiredFields.includes(action.field) || !validMm(action.valueMm)) return fail('invalid_estimate');
      const manual = new Set(current.manuallyVerifiedFields);
      if (action.manuallyVerified) manual.add(action.field); else manual.delete(action.field);
      return { ok: true, request: { ...current,
        reviewedMm: { ...current.reviewedMm, [action.field]: action.valueMm }, manuallyVerifiedFields: [...manual] } };
    }
    case 'approve': {
      if (action.actorUid !== current.workshopUid) return fail('not_authorised');
      if (current.stage !== 'review') return fail('invalid_transition');
      if (!policy.workshopCanWrite) return fail('workshop_read_only');
      const validated = policy.validatedFieldsByEngine[current.engineVersion ?? ''] ?? [];
      const acceptable = current.requiredFields.every(field => validMm(current.reviewedMm[field]) &&
        (current.manuallyVerifiedFields.includes(field) ||
          (validated.includes(field) && current.reviewedMm[field] === current.estimatesMm[field])));
      if (!acceptable) return fail('manual_verification_required');
      // Future adapter imports a NEW version atomically, never overwriting prior measurements.
      return { ok: true, request: { ...current, stage: 'approved' } };
    }
  }
}

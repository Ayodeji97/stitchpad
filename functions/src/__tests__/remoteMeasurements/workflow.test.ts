import { createMeasurementRequest, MeasurementAction, MeasurementPolicy, RemoteMeasurementRequest,
  transitionMeasurementRequest, WorkflowResult } from '../../remoteMeasurements/workflow';

const policy: MeasurementPolicy = { workshopCanWrite: true, validatedFieldsByEngine: {} };
function unwrap(result: WorkflowResult): RemoteMeasurementRequest {
  if (!result.ok) throw new Error(result.error);
  return result.request;
}
function request(journey: 'existing_customer' | 'designer_discovery' = 'existing_customer') {
  return unwrap(createMeasurementRequest({ id: 'request', workshopUid: 'owner', customerUid: 'customer',
    journey, customerRecordId: journey === 'existing_customer' ? 'owned-record' : null,
    createdAt: 1000, expiresAt: 2000, locale: 'fr', requiredFields: ['waist_v1', 'hip_v1'] }, policy));
}
function move(current: RemoteMeasurementRequest, action: MeasurementAction, rules = policy) {
  return unwrap(transitionMeasurementRequest(current, action, 1500, rules));
}
function review() {
  let value = move(request(), { kind: 'consent', actorUid: 'customer', consentRevision: 'consent-v1' });
  value = move(value, { kind: 'submit_capture', actorUid: 'customer', runId: 'capture-1' });
  return move(value, { kind: 'engine_result', runId: 'capture-1', engineVersion: 'experimental-v1', estimatesMm: { waist_v1: 800, hip_v1: 1000 } });
}

describe('dormant remote measurement lifecycle', () => {
  it.each(['existing_customer', 'designer_discovery'] as const)('supports %s with consent pending', journey => {
    expect(request(journey).stage).toBe('awaiting_consent');
  });
  it('does not accept capture before customer consent', () => {
    expect(transitionMeasurementRequest(request(), { kind: 'submit_capture', actorUid: 'customer', runId: 'capture' }, 1500, policy))
      .toEqual({ ok: false, error: 'invalid_transition' });
  });
  it('does not let another customer consent', () => {
    expect(transitionMeasurementRequest(request(), { kind: 'consent', actorUid: 'intruder', consentRevision: 'v1' }, 1500, policy))
      .toEqual({ ok: false, error: 'not_authorised' });
  });
  it('expires exactly at the cutoff', () => {
    expect(transitionMeasurementRequest(request(), { kind: 'consent', actorUid: 'customer', consentRevision: 'v1' }, 2000, policy))
      .toEqual({ ok: false, error: 'expired' });
  });
  it('rejects stale callbacks and cannot reopen cancelled requests', () => {
    const cancelled = move(review(), { kind: 'cancel', actorUid: 'customer' });
    expect(transitionMeasurementRequest(cancelled, { kind: 'engine_result', runId: 'capture-1', engineVersion: 'v2', estimatesMm: {} }, 1500, policy))
      .toEqual({ ok: false, error: 'invalid_transition' });
    expect(transitionMeasurementRequest(review(), { kind: 'engine_result', runId: 'old-capture', engineVersion: 'v2', estimatesMm: {} }, 1500, policy))
      .toEqual({ ok: false, error: 'stale_run' });
  });
  it('never approves an unvalidated estimate automatically', () => {
    let value = review();
    for (const [field, valueMm] of Object.entries(value.estimatesMm)) {
      value = move(value, { kind: 'review_field', actorUid: 'owner', field, valueMm, manuallyVerified: false });
    }
    expect(transitionMeasurementRequest(value, { kind: 'approve', actorUid: 'owner' }, 1500, policy))
      .toEqual({ ok: false, error: 'manual_verification_required' });
  });
  it('requires every essential field and explicit tailor verification', () => {
    let value = review();
    value = move(value, { kind: 'review_field', actorUid: 'owner', field: 'waist_v1', valueMm: 805, manuallyVerified: true });
    expect(transitionMeasurementRequest(value, { kind: 'approve', actorUid: 'owner' }, 1500, policy).ok).toBe(false);
    value = move(value, { kind: 'review_field', actorUid: 'owner', field: 'hip_v1', valueMm: 1005, manuallyVerified: true });
    expect(move(value, { kind: 'approve', actorUid: 'owner' }).stage).toBe('approved');
  });
  it('does not approve an edited estimate using engine validation alone', () => {
    let value = review();
    value = move(value, { kind: 'review_field', actorUid: 'owner', field: 'waist_v1', valueMm: 805, manuallyVerified: false });
    value = move(value, { kind: 'review_field', actorUid: 'owner', field: 'hip_v1', valueMm: 1000, manuallyVerified: false });
    const validated = { ...policy, validatedFieldsByEngine: { 'experimental-v1': ['waist_v1', 'hip_v1'] } };
    expect(transitionMeasurementRequest(value, { kind: 'approve', actorUid: 'owner' }, 1500, validated).ok).toBe(false);
  });
  it('keeps cancellation available when expired and unpaid', () => {
    const result = transitionMeasurementRequest(request(), { kind: 'cancel', actorUid: 'customer' }, 3000, { ...policy, workshopCanWrite: false });
    expect(result.ok && result.request.stage).toBe('cancelled');
  });
  it('blocks approval while workshop access is read-only', () => {
    expect(transitionMeasurementRequest(review(), { kind: 'approve', actorUid: 'owner' }, 1500, { ...policy, workshopCanWrite: false }))
      .toEqual({ ok: false, error: 'workshop_read_only' });
  });
  it.each([0, -1, NaN, Infinity])('rejects invalid measurement %s', valueMm => {
    expect(transitionMeasurementRequest(review(), { kind: 'review_field', actorUid: 'owner', field: 'waist_v1', valueMm, manuallyVerified: true }, 1500, policy))
      .toEqual({ ok: false, error: 'invalid_estimate' });
  });
});

import * as functions from 'firebase-functions/v1';
import { setMarketerStatusHandler } from '../../referral/setMarketerStatus';

// Minimal fake Firestore: doc refs + read-before-write transactions.
function makeDb(initial: Record<string, any> = {}) {
  const store = new Map<string, any>(Object.entries(initial));
  const db: any = {
    doc: (path: string) => ({ path }),
    runTransaction: async (fn: any) => {
      let wrote = false;
      const tx = {
        get: async (ref: any) => {
          if (wrote) throw new Error('read after write');
          return { exists: store.has(ref.path), data: () => store.get(ref.path) };
        },
        set: (ref: any, data: any, opts?: { merge?: boolean }) => {
          wrote = true;
          const prev = store.get(ref.path) ?? {};
          store.set(ref.path, opts?.merge ? { ...prev, ...data } : data);
        },
      };
      return fn(tx);
    },
  };
  return { store, db };
}

const deps = (db: any) => ({ db, now: () => new Date('2026-07-13T10:00:00Z') });
const ctx = (isAdmin?: boolean): functions.https.CallableContext =>
  ({ auth: { uid: 'a', token: isAdmin ? { admin: true } : {} } } as unknown as functions.https.CallableContext);

describe('setMarketerStatusHandler', () => {
  it('rejects a non-admin caller', async () => {
    await expect(
      setMarketerStatusHandler({ marketerId: 'm1', status: 'disabled' }, ctx(false), deps(makeDb().db)),
    ).rejects.toMatchObject({ code: 'permission-denied', message: 'admin_only' });
  });

  it('rejects an invalid marketerId (path-injection guard)', async () => {
    for (const bad of ['', 'a/b', '../x', 'has space', 42, undefined]) {
      await expect(
        setMarketerStatusHandler({ marketerId: bad, status: 'disabled' }, ctx(true), deps(makeDb().db)),
      ).rejects.toMatchObject({ code: 'invalid-argument', message: 'invalid_marketer_id' });
    }
  });

  it('rejects an invalid status', async () => {
    for (const bad of [undefined, null, 'paused', 'ACTIVE', 1, true]) {
      await expect(
        setMarketerStatusHandler({ marketerId: 'm1', status: bad }, ctx(true), deps(makeDb().db)),
      ).rejects.toMatchObject({ code: 'invalid-argument', message: 'invalid_status' });
    }
  });

  it('404s an unknown marketer', async () => {
    await expect(
      setMarketerStatusHandler({ marketerId: 'mkt_missing', status: 'disabled' }, ctx(true), deps(makeDb().db)),
    ).rejects.toMatchObject({ code: 'not-found', message: 'marketer_not_found' });
  });

  it('disables an active marketer (merge write: other fields survive)', async () => {
    const { store, db } = makeDb({
      'marketers/m1': { name: 'Ada', status: 'active', confirmedAmount: 500 },
    });
    const res = await setMarketerStatusHandler({ marketerId: 'm1', status: 'disabled' }, ctx(true), deps(db));
    expect(res).toEqual({ marketerId: 'm1', status: 'disabled' });
    expect(store.get('marketers/m1')).toMatchObject({ name: 'Ada', status: 'disabled', confirmedAmount: 500 });
    expect(store.get('marketers/m1').updatedAt).toBeDefined();
  });

  it('re-activates a disabled marketer', async () => {
    const { store, db } = makeDb({ 'marketers/m1': { status: 'disabled' } });
    const res = await setMarketerStatusHandler({ marketerId: 'm1', status: 'active' }, ctx(true), deps(db));
    expect(res).toEqual({ marketerId: 'm1', status: 'active' });
    expect(store.get('marketers/m1').status).toBe('active');
  });

  it('is idempotent — setting the current status again succeeds', async () => {
    const { store, db } = makeDb({ 'marketers/m1': { status: 'disabled' } });
    await setMarketerStatusHandler({ marketerId: 'm1', status: 'disabled' }, ctx(true), deps(db));
    const res = await setMarketerStatusHandler({ marketerId: 'm1', status: 'disabled' }, ctx(true), deps(db));
    expect(res).toEqual({ marketerId: 'm1', status: 'disabled' });
    expect(store.get('marketers/m1').status).toBe('disabled');
  });
});

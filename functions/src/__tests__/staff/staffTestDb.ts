import * as functions from 'firebase-functions/v1';
import { buildLaunchGrantFields } from '../../freemium/launchGrant';

// Minimal in-memory Firestore for the staff handler tests: doc get/set/create/
// update/delete keyed by full path, and shallow collection.get() over the store.
export function makeStaffDb(initial: Record<string, unknown> = {}) {
  const store = new Map<string, Record<string, unknown>>(
    Object.entries(initial) as [string, Record<string, unknown>][],
  );
  const docRef = (path: string) => ({
    path,
    id: path.split('/').pop() as string,
    get: async () => ({
      exists: store.has(path),
      id: path.split('/').pop(),
      data: () => store.get(path),
    }),
    set: async (data: Record<string, unknown>, opts?: { merge?: boolean }) => {
      const prev = store.get(path) ?? {};
      store.set(path, opts?.merge ? { ...prev, ...data } : data);
    },
    create: async (data: Record<string, unknown>) => {
      if (store.has(path)) {
        const e = new Error('ALREADY_EXISTS') as Error & { code?: number };
        e.code = 6;
        throw e;
      }
      store.set(path, data);
    },
    update: async (data: Record<string, unknown>) => {
      if (!store.has(path)) {
        const e = new Error('NOT_FOUND') as Error & { code?: number };
        e.code = 5;
        throw e;
      }
      store.set(path, { ...store.get(path), ...data });
    },
    delete: async () => {
      store.delete(path);
    },
  });
  const collectionRef = (path: string) => ({
    get: async () => {
      const prefix = `${path}/`;
      const docs = [...store.entries()]
        .filter(([k]) => k.startsWith(prefix) && !k.slice(prefix.length).includes('/'))
        .map(([k, v]) => ({ id: k.slice(prefix.length), data: () => v }));
      return { docs, size: docs.length, empty: docs.length === 0 };
    },
  });
  // Fake transaction: no real isolation (tests are single-threaded); buffers writes
  // and only applies them if the callback completes without throwing, discarding
  // them if it throws. Verifies the handler's read-before-write structure and logic,
  // and the atomicity invariant that all-or-nothing writes happen together.
  const runTransaction = async <T>(fn: (tx: {
    get: (ref: { get: () => Promise<unknown> }) => Promise<unknown>;
    set: (ref: { set: (d: Record<string, unknown>, o?: { merge?: boolean }) => Promise<void> }, d: Record<string, unknown>, o?: { merge?: boolean }) => void;
    update: (ref: { update: (d: Record<string, unknown>) => Promise<void> }, d: Record<string, unknown>) => void;
  }) => Promise<T>): Promise<T> => {
    const writes: Array<() => Promise<void>> = [];
    const tx = {
      get: (ref: { get: () => Promise<unknown> }) => ref.get(),
      set: (ref: { set: (d: Record<string, unknown>, o?: { merge?: boolean }) => Promise<void> }, d: Record<string, unknown>, o?: { merge?: boolean }) => {
        writes.push(() => ref.set(d, o));
      },
      update: (ref: { update: (d: Record<string, unknown>) => Promise<void> }, d: Record<string, unknown>) => {
        writes.push(() => ref.update(d));
      },
    };
    const result = await fn(tx);
    // Only apply writes if the transaction callback succeeded.
    for (const write of writes) {
      await write();
    }
    return result;
  };
  const db = { doc: docRef, collection: collectionRef, runTransaction } as unknown as import('firebase-admin').firestore.Firestore;
  return { store, db };
}

// Captures setCustomUserClaims calls (uid -> last claims object, null = cleared).
export function makeClaimsRecorder() {
  const claims = new Map<string, Record<string, unknown> | null>();
  const setClaims = async (uid: string, c: Record<string, unknown> | null) => {
    claims.set(uid, c);
  };
  return { claims, setClaims };
}

// Launch-grant hooks for the staff handler tests, shared by the revoke and cancel
// suites (they had identical copies). `writeGrant` mirrors production exactly — real
// buildLaunchGrantFields through the fake db — so tests assert on real field content,
// not a stub. `overrides` swaps in a disabled flag or a throwing write.
export function makeLaunchGrantDeps(
  db: import('firebase-admin').firestore.Firestore,
  overrides: Partial<LaunchGrantHooks> = {},
): LaunchGrantHooks {
  return {
    isGrantEnabled: async () => true,
    writeGrant: async (uid: string, now: Date) => {
      await db.doc(`users/${uid}`).set(buildLaunchGrantFields(now), { merge: true });
    },
    ...overrides,
  };
}

export interface LaunchGrantHooks {
  isGrantEnabled: () => Promise<boolean>;
  writeGrant: (uid: string, now: Date) => Promise<void>;
}

export const authedCtx = (
  uid?: string,
  token: Record<string, unknown> = {},
): functions.https.CallableContext =>
  ({ auth: uid ? { uid, token: { ...token, email: token.email ?? `${uid}@ex.co` } } : undefined } as unknown as functions.https.CallableContext);

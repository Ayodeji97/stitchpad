import * as functions from 'firebase-functions/v1';

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
  const db = { doc: docRef, collection: collectionRef } as unknown as import('firebase-admin').firestore.Firestore;
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

export const authedCtx = (
  uid?: string,
  token: Record<string, unknown> = {},
): functions.https.CallableContext =>
  ({ auth: uid ? { uid, token: { ...token, email: token.email ?? `${uid}@ex.co` } } : undefined } as unknown as functions.https.CallableContext);

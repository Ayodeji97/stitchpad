// In-memory Firestore double: doc get/set/update, batch, and runTransaction on one store.
// update() rejects a missing doc, like Firestore.
export function fakeDb(initial: Record<string, any> = {}) {
  const store = new Map<string, any>(Object.entries(initial));
  const apply = (path: string, data: any, options?: { merge?: boolean }) => {
    const prev = store.get(path) ?? {};
    store.set(path, options?.merge ? { ...prev, ...data } : data);
  };
  const update = (path: string, data: any) => {
    if (!store.has(path)) throw new Error(`NOT_FOUND: ${path}`);
    store.set(path, { ...store.get(path), ...data });
  };
  const doc = (path: string) => ({
    path,
    get: async () => ({ exists: store.has(path), data: () => store.get(path) }),
    set: async (data: any, options?: { merge?: boolean }) => apply(path, data, options),
    update: async (data: any) => update(path, data),
  });
  const tx = {
    get: async (ref: any) => ({ exists: store.has(ref.path), data: () => store.get(ref.path) }),
    set: (ref: any, data: any, options?: { merge?: boolean }) => apply(ref.path, data, options),
    update: (ref: any, data: any) => update(ref.path, data),
    delete: (ref: any) => { store.delete(ref.path); },
  };
  const batch = () => {
    const ops: Array<() => void> = [];
    return {
      set: (ref: any, data: any, options?: { merge?: boolean }) => { ops.push(() => apply(ref.path, data, options)); },
      update: (ref: any, data: any) => { ops.push(() => update(ref.path, data)); },
      commit: async () => { ops.forEach((op) => op()); },
    };
  };
  // Transactions run one at a time, like Firestore's conflict-retry outcome.
  let queue: Promise<unknown> = Promise.resolve();
  const runTransaction = (fn: any) => {
    const run = queue.then(() => fn(tx));
    queue = run.catch(() => undefined);
    return run;
  };
  const db = { doc, batch, runTransaction };
  return { store, db: db as never };
}

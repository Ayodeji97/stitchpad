import { handleUserCreated, UserCreatedDeps } from '../../freemium/onUserCreated';

function makeDeps(overrides: Partial<UserCreatedDeps> = {}): {
  deps: UserCreatedDeps;
  writes: string[];
} {
  const writes: string[] = [];
  const deps: UserCreatedDeps = {
    isGrantEnabled: async () => true,
    writeGrant: async (uid) => {
      writes.push(uid);
    },
    now: () => new Date('2026-07-06T00:00:00Z'),
    ...overrides,
  };
  return { deps, writes };
}

describe('handleUserCreated', () => {
  it('grants a brand-new free user when the flag is on', async () => {
    const { deps, writes } = makeDeps();
    const outcome = await handleUserCreated(deps, 'uid-1', {});
    expect(outcome).toBe('granted');
    expect(writes).toEqual(['uid-1']);
  });

  it('skips (no write) when the flag is off', async () => {
    const { deps, writes } = makeDeps({ isGrantEnabled: async () => false });
    const outcome = await handleUserCreated(deps, 'uid-1', {});
    expect(outcome).toBe('skipped-disabled');
    expect(writes).toEqual([]);
  });

  it('skips (no write) an active paid subscriber', async () => {
    const { deps, writes } = makeDeps();
    const outcome = await handleUserCreated(deps, 'uid-1', {
      subscriptionTier: 'pro',
      subscriptionStatus: 'active',
    });
    expect(outcome).toBe('skipped-managed');
    expect(writes).toEqual([]);
  });
});

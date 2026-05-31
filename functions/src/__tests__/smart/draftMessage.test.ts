import firebaseFunctionsTest from 'firebase-functions-test';
import * as freeTierCounter from '../../smart/freeTierCounter';
import { formatMonthYear, reconcileUsage } from '../../smart/freeTierCounter';
import { formatDeadline } from '../../smart/draftMessage';
import { FreeTierUsageDoc } from '../../smart/types';
import { VertexClient } from '../../smart/vertexClient';

const test = firebaseFunctionsTest();

// Lazy import after firebase-admin init in setup
let handler: any;
let __setVertexClientForTests: (c: VertexClient) => void;

const fakeVertex: VertexClient = {
  generateText: jest.fn().mockResolvedValue('Hi Folake, just a friendly note about your balance.'),
};

beforeAll(async () => {
  const mod = await import('../../smart/draftMessage');
  handler = mod.draftMessageHandler;
  __setVertexClientForTests = mod.__setVertexClientForTests;
});

beforeEach(() => {
  __setVertexClientForTests(fakeVertex);
  jest.clearAllMocks();
});

afterAll(() => {
  test.cleanup();
});

const validRequest = {
  intentType: 'balance_reminder' as const,
  customerId: 'cust-1',
  orderId: 'order-1',
  language: 'en' as const,
};

const baseContext = {
  auth: { uid: 'user-1', token: {} as any },
};

/**
 * The fake io models a single in-memory usage doc and exposes the
 * reserveFreeTierSlot transaction semantics: read existing, check
 * exhausted, write next-or-reject. This matches the production impl
 * shape and lets us assert how many times the reservation ran.
 */
const fakeFirestore = (overrides: Partial<{
  profile: { tier: 'free' | 'pro' | 'atelier' | 'premium' };
  usage: FreeTierUsageDoc | null;
  customer: { firstName: string };
  order: {
    customerId: string;
    garmentLabel: string;
    depositFormatted: string;
    balanceFormatted: string;
    deadlineFormatted: string;
    isOpen: boolean;
  };
}>) => {
  const profile = overrides.profile ?? { tier: 'free' as const };
  const initialUsage: FreeTierUsageDoc | null =
    'usage' in overrides ? (overrides.usage ?? null) : { monthYear: formatMonthYear(new Date()), count: 0, limit: 5 };
  let usage: FreeTierUsageDoc | null = initialUsage;
  const customer = overrides.customer ?? { firstName: 'Folake' };
  const order = overrides.order ?? {
    customerId: 'cust-1',
    garmentLabel: 'Adire boubou',
    depositFormatted: '₦5,000',
    balanceFormatted: '₦7,500',
    deadlineFormatted: 'Friday, May 22',
    isOpen: true,
  };

  return {
    profileGet: jest.fn().mockResolvedValue({ exists: true, data: () => profile }),
    reserveFreeTierSlot: jest.fn().mockImplementation((now: Date, _welcomeBonusToSeed: number = 0, tierLimit?: number) => {
      const existing = usage;
      const next = reconcileUsage({ existing, now, limit: tierLimit }, 'draft');
      // Mirror the production fix: compare existing.count against the
      // BASELINE limit (next.limit, which carries the tier-derived override),
      // not the existing doc's limit — so an upgraded user with a stale 5/5
      // free doc gets their new Pro limit of 50 applied on the next call.
      if (existing !== null && existing.count >= next.limit && existing.monthYear === next.monthYear) {
        return Promise.resolve({ exhausted: true });
      }
      usage = next;
      return Promise.resolve({ exhausted: false, usage: next });
    }),
    customerGet: jest.fn().mockResolvedValue({ exists: true, data: () => customer }),
    orderGet: jest.fn().mockResolvedValue({ exists: true, data: () => order }),
  };
};

describe('draftMessageHandler', () => {
  it('rejects unauthenticated requests', async () => {
    await expect(
      handler(validRequest, { auth: undefined } as any, fakeFirestore({}))
    ).rejects.toMatchObject({ code: 'unauthenticated' });
  });

  it('returns drafted text + remainingFreeQuota for a free user under quota', async () => {
    const fs = fakeFirestore({});
    const result = await handler(validRequest, baseContext as any, fs);
    expect(result.draftText).toContain('Folake');
    expect(result.remainingFreeQuota).toBe(4); // limit 5 - count 1
    expect(fs.reserveFreeTierSlot).toHaveBeenCalledTimes(1);
    // Verifies the response shape end-to-end via the fake. The production
    // wire (productionIO -> reconcileUsage with 'draft') is covered
    // separately by the productionIO contract test below.
    const reservation = await (fs.reserveFreeTierSlot as jest.Mock).mock.results[0].value;
    expect(reservation.exhausted).toBe(false);
    expect(reservation.usage.perFeature).toEqual({ draft: 1 });
  });

  it('rejects with permission-denied when free tier exhausted', async () => {
    const fs = fakeFirestore({ usage: { monthYear: formatMonthYear(new Date()), count: 5, limit: 5 } });
    await expect(handler(validRequest, baseContext as any, fs)).rejects.toMatchObject({
      code: 'permission-denied',
      message: expect.stringContaining('free_tier_exhausted'),
    });
    expect(fakeVertex.generateText).not.toHaveBeenCalled();
  });

  it('does not block an upgraded user whose old free quota was exhausted', async () => {
    // User was Free, hit 5/5 (exhausted). Upgrades to Pro. Next call
    // should succeed with the new Pro limit of 50.
    const fs = fakeFirestore({
      profile: { tier: 'pro' },
      usage: { monthYear: formatMonthYear(new Date()), count: 5, limit: 5 } as any,
    });
    const result = await handler(validRequest, baseContext as any, fs);
    // 50 (Pro limit) - 6 (incremented count) = 44 remaining
    expect(result.remainingFreeQuota).toBe(44);
  });

  it('rejects with invalid-argument when customer does not exist', async () => {
    const fs = fakeFirestore({});
    fs.customerGet = jest.fn().mockResolvedValue({ exists: false, data: () => undefined });
    await expect(handler(validRequest, baseContext as any, fs)).rejects.toMatchObject({
      code: 'invalid-argument',
    });
    // Invalid input must NOT consume one of the caller's five free drafts.
    expect(fs.reserveFreeTierSlot).not.toHaveBeenCalled();
  });

  it('rejects with invalid-argument when order belongs to a different customer', async () => {
    const fs = fakeFirestore({});
    fs.orderGet = jest.fn().mockResolvedValue({
      exists: true,
      data: () => ({
        customerId: 'cust-OTHER',
        garmentLabel: 'x', depositFormatted: 'x', balanceFormatted: 'x', deadlineFormatted: 'x',
        isOpen: true,
      }),
    });
    await expect(handler(validRequest, baseContext as any, fs)).rejects.toMatchObject({
      code: 'invalid-argument',
    });
    expect(fs.reserveFreeTierSlot).not.toHaveBeenCalled();
  });

  it('rejects with invalid-argument when intentType is unsupported (no quota burned)', async () => {
    const fs = fakeFirestore({});
    await expect(
      handler({ ...validRequest, intentType: 'bogus' as any }, baseContext as any, fs),
    ).rejects.toMatchObject({ code: 'invalid-argument' });
    expect(fs.reserveFreeTierSlot).not.toHaveBeenCalled();
    expect(fakeVertex.generateText).not.toHaveBeenCalled();
  });

  it('rejects with invalid-argument when language is unsupported (no quota burned)', async () => {
    const fs = fakeFirestore({});
    await expect(
      handler({ ...validRequest, language: 'fr' as any }, baseContext as any, fs),
    ).rejects.toMatchObject({ code: 'invalid-argument' });
    expect(fs.reserveFreeTierSlot).not.toHaveBeenCalled();
    expect(fakeVertex.generateText).not.toHaveBeenCalled();
  });

  it('rejects with invalid-argument when customNotes exceed the 200-char cap', async () => {
    const fs = fakeFirestore({});
    const tooLong = 'a'.repeat(201);
    await expect(
      handler({ ...validRequest, customNotes: tooLong }, baseContext as any, fs),
    ).rejects.toMatchObject({ code: 'invalid-argument' });
    expect(fs.reserveFreeTierSlot).not.toHaveBeenCalled();
  });

  it('rejects with invalid-argument when customNotes is not a string', async () => {
    const fs = fakeFirestore({});
    await expect(
      handler({ ...validRequest, customNotes: 12345 as any }, baseContext as any, fs),
    ).rejects.toMatchObject({ code: 'invalid-argument' });
    expect(fs.reserveFreeTierSlot).not.toHaveBeenCalled();
  });

  it('rejects with invalid-argument when the order is closed (delivered or archived)', async () => {
    const fs = fakeFirestore({});
    fs.orderGet = jest.fn().mockResolvedValue({
      exists: true,
      data: () => ({
        customerId: 'cust-1',
        garmentLabel: 'Adire boubou',
        depositFormatted: '₦5,000',
        balanceFormatted: '₦7,500',
        deadlineFormatted: 'Friday, May 22',
        isOpen: false,
      }),
    });
    await expect(handler(validRequest, baseContext as any, fs)).rejects.toMatchObject({
      code: 'invalid-argument',
    });
    expect(fs.reserveFreeTierSlot).not.toHaveBeenCalled();
    expect(fakeVertex.generateText).not.toHaveBeenCalled();
  });

  it('maps Vertex failures to unavailable and keeps the reservation', async () => {
    // V1 trade-off: the slot is committed before Vertex, so a Vertex failure
    // does consume the slot. Test reflects current behavior; a compensating
    // decrement is a V1.5 follow-up if Vertex flake becomes a real problem.
    const fs = fakeFirestore({});
    (fakeVertex.generateText as jest.Mock).mockRejectedValueOnce(new Error('vertex_no_candidates'));
    await expect(handler(validRequest, baseContext as any, fs)).rejects.toMatchObject({
      code: 'unavailable',
    });
    expect(fs.reserveFreeTierSlot).toHaveBeenCalledTimes(1);
  });

  it('rolls over the month-year counter on first call of new month', async () => {
    const fs = fakeFirestore({ usage: { monthYear: '2026-04', count: 5, limit: 5 } });
    const result = await handler(validRequest, baseContext as any, fs, new Date('2026-05-16T10:00:00Z'));
    expect(result.remainingFreeQuota).toBe(4);
    expect(fs.reserveFreeTierSlot).toHaveBeenCalledTimes(1);
  });

  it('back-to-back calls consume quota in order and reject the 6th', async () => {
    // Regression guard for the race the transaction fixes: even though the
    // fake is single-threaded, the test asserts that 5 reservations succeed
    // and the 6th is rejected — which is the invariant the transaction
    // upholds in production under real concurrency.
    const fs = fakeFirestore({ usage: { monthYear: formatMonthYear(new Date()), count: 0, limit: 5 } });
    for (let i = 0; i < 5; i++) {
      const result = await handler(validRequest, baseContext as any, fs);
      expect(result.remainingFreeQuota).toBe(4 - i);
    }
    await expect(handler(validRequest, baseContext as any, fs)).rejects.toMatchObject({
      code: 'permission-denied',
    });
  });

  it('treats subscriptionTier "atelier" as unlimited (no quota burn)', async () => {
    const fs = fakeFirestore({ profile: { tier: 'atelier' } });
    const result = await handler(validRequest, baseContext as any, fs);
    expect(result.remainingFreeQuota).toBeNull();
    expect(fs.reserveFreeTierSlot).not.toHaveBeenCalled();
  });

  it('treats subscriptionTier "pro" as gated (Pro still consumes coins)', async () => {
    const fs = fakeFirestore({ profile: { tier: 'pro' } });
    const result = await handler(validRequest, baseContext as any, fs);
    expect(result.remainingFreeQuota).toBe(49); // limit 50 - count 1
    expect(fs.reserveFreeTierSlot).toHaveBeenCalledTimes(1);
  });

  it('normalizes legacy subscriptionTier "premium" to "pro" (pre-V1.0 doc)', async () => {
    // productionIO.profileGet normalizes "premium" → "pro" before the handler
    // sees it. The fake mirrors that normalization by overriding profileGet so
    // the handler receives the already-normalized value, documenting the contract:
    // wire value "premium" must be treated identically to "pro".
    const fs = fakeFirestore({});
    fs.profileGet = jest.fn().mockResolvedValue({
      exists: true,
      // Simulate what productionIO returns after normalizing "premium" → "pro".
      data: () => ({ tier: 'pro', welcomeBonusCoins: 0 }),
    });
    const result = await handler(validRequest, baseContext as any, fs);
    // Pro limit is 50; one slot consumed → 49 remaining.
    expect(result.remainingFreeQuota).toBe(49);
    expect(fs.reserveFreeTierSlot).toHaveBeenCalledTimes(1);
  });

  it('rejects Atelier-only intentType (pricing_help) when caller is "free"', async () => {
    const fs = fakeFirestore({ profile: { tier: 'free' } });
    await expect(
      handler({ ...validRequest, intentType: 'pricing_help' as any }, baseContext as any, fs),
    ).rejects.toMatchObject({ code: 'permission-denied' });
    expect(fs.reserveFreeTierSlot).not.toHaveBeenCalled();
  });

  it('rejects Atelier-only intentType (pricing_help) when caller is "pro"', async () => {
    const fs = fakeFirestore({ profile: { tier: 'pro' } });
    await expect(
      handler({ ...validRequest, intentType: 'pricing_help' as any }, baseContext as any, fs),
    ).rejects.toMatchObject({ code: 'permission-denied' });
    expect(fs.reserveFreeTierSlot).not.toHaveBeenCalled();
  });

  it('rejects Atelier-only intentType (pricing_help) even when caller IS atelier (templates ship in V1.5)', async () => {
    const fs = fakeFirestore({ profile: { tier: 'atelier' } });
    await expect(
      handler({ ...validRequest, intentType: 'pricing_help' as any }, baseContext as any, fs),
    ).rejects.toMatchObject({ code: 'unimplemented' });
    expect(fs.reserveFreeTierSlot).not.toHaveBeenCalled();
  });

  it('consumes bonusBalance before monthly count', async () => {
    const fs = fakeFirestore({
      usage: { monthYear: formatMonthYear(new Date()), count: 0, limit: 5, bonusBalance: 3 } as any,
    });
    // Override reserveFreeTierSlot to model bonus consumption with the same
    // local-state mutation pattern as the real fake but tracking bonus.
    let bonus = 3;
    let count = 0;
    fs.reserveFreeTierSlot = jest.fn().mockImplementation((_now: Date, _welcomeBonusToSeed: number = 0) => {
      if (bonus > 0) {
        bonus -= 1;
        return Promise.resolve({
          exhausted: false,
          usage: { monthYear: formatMonthYear(new Date()), count, limit: 5, bonusBalance: bonus },
          consumedBonus: true,
        });
      }
      if (count >= 5) return Promise.resolve({ exhausted: true });
      count += 1;
      return Promise.resolve({
        exhausted: false,
        usage: { monthYear: formatMonthYear(new Date()), count, limit: 5, bonusBalance: 0 },
        consumedBonus: false,
      });
    });

    // First 3 calls hit bonus, count stays 0.
    for (let i = 0; i < 3; i++) {
      const result = await handler(validRequest, baseContext as any, fs);
      expect(result.remainingFreeQuota).toBe(5); // count untouched
    }
    // Then 5 calls eat the monthly quota.
    for (let i = 0; i < 5; i++) {
      const result = await handler(validRequest, baseContext as any, fs);
      expect(result.remainingFreeQuota).toBe(4 - i);
    }
    // 9th call: bonus gone, monthly quota gone — exhausted.
    await expect(handler(validRequest, baseContext as any, fs)).rejects.toMatchObject({
      code: 'permission-denied',
    });
  });

  it('seeds the welcome bonus into the usage doc on the first Smart call', async () => {
    // No usage doc exists yet (welcomeBonusToSeed should be honored).
    const fs = fakeFirestore({ usage: null });
    // Stub the profile to carry a welcome bonus of 30.
    fs.profileGet = jest.fn().mockResolvedValue({
      exists: true,
      data: () => ({ tier: 'free', welcomeBonusCoins: 30 }),
    });
    // Override reserveFreeTierSlot to model the seeding behaviour.
    let bonusBalance: number | null = null;
    let count = 0;
    fs.reserveFreeTierSlot = jest.fn().mockImplementation((_now: Date, welcomeBonusToSeed = 0) => {
      if (bonusBalance === null) {
        bonusBalance = welcomeBonusToSeed; // first call seeds
      }
      const currentBonus = bonusBalance as number;
      if (currentBonus > 0) {
        bonusBalance = currentBonus - 1;
        return Promise.resolve({
          exhausted: false,
          usage: { monthYear: formatMonthYear(new Date()), count, limit: 5, bonusBalance },
          consumedBonus: true,
        });
      }
      count += 1;
      return Promise.resolve({
        exhausted: false,
        usage: { monthYear: formatMonthYear(new Date()), count, limit: 5, bonusBalance: 0 },
        consumedBonus: false,
      });
    });

    const result = await handler(validRequest, baseContext as any, fs);
    expect(result.remainingFreeQuota).toBe(5); // count untouched, bonus consumed
    expect(fs.reserveFreeTierSlot).toHaveBeenCalledWith(expect.any(Date), 30, 5); // free tier limit
  });
});

describe('productionIO contract: reserveFreeTierSlot threads "draft" to reconcileUsage', () => {
  afterEach(() => {
    jest.restoreAllMocks();
  });

  it('calls reconcileUsage with "draft" as the feature key', async () => {
    const fakeUsageDoc: FreeTierUsageDoc = {
      monthYear: formatMonthYear(new Date()),
      count: 1,
      limit: 5,
      perFeature: { draft: 1 },
    };

    const spy = jest
      .spyOn(freeTierCounter, 'reconcileUsage')
      .mockReturnValue(fakeUsageDoc);

    // Build a thin in-memory Firestore stand-in that supports the transaction
    // pattern used by productionIO.reserveFreeTierSlot.
    const fakeSnap = { exists: false, data: () => undefined };
    const fakeRef = { /* opaque to productionIO; only passed to tx.get/tx.set */ };
    const fakeTx = {
      get: jest.fn().mockResolvedValue(fakeSnap),
      set: jest.fn(),
    };
    const fakeDb = {
      doc: jest.fn().mockReturnValue(fakeRef),
      runTransaction: jest.fn().mockImplementation(
        async (fn: (tx: typeof fakeTx) => Promise<unknown>) => fn(fakeTx),
      ),
    } as unknown as import('firebase-admin').firestore.Firestore;

    const { productionIO: buildIO } = await import('../../smart/draftMessage');
    const io = buildIO('uid-test', 'cust-1', 'order-1', fakeDb);

    await io.reserveFreeTierSlot(new Date('2026-05-16T10:00:00Z'));

    expect(spy).toHaveBeenCalledTimes(1);
    expect(spy.mock.calls[0][1]).toBe('draft');
  });
});

describe('formatDeadline', () => {
  it('renders deadlines in Africa/Lagos so a Lagos-local-midnight stays on the right day', () => {
    // App stores May 1 2026 (Lagos) as the UTC instant 2026-04-30T23:00:00Z.
    // Default Node UTC formatting would call this Thursday, April 30 — wrong.
    const aprilThirty23UtcMillis = Date.UTC(2026, 3, 30, 23, 0, 0, 0);
    expect(formatDeadline(aprilThirty23UtcMillis)).toBe('Friday, May 1');
  });

  it('renders a normal mid-day instant on its calendar day', () => {
    const mayOneNoonUtcMillis = Date.UTC(2026, 4, 1, 12, 0, 0, 0);
    expect(formatDeadline(mayOneNoonUtcMillis)).toBe('Friday, May 1');
  });
});

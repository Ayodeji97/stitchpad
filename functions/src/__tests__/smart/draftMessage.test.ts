import firebaseFunctionsTest from 'firebase-functions-test';
import { reconcileUsage, isExhausted } from '../../smart/freeTierCounter';
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
  profile: { tier: 'free' | 'premium' };
  usage: { monthYear: string; count: number; limit: number } | null;
  customer: { firstName: string };
  order: {
    customerId: string;
    garmentLabel: string;
    depositFormatted: string;
    balanceFormatted: string;
    deadlineFormatted: string;
  };
}>) => {
  const profile = overrides.profile ?? { tier: 'free' as const };
  const initialUsage: { monthYear: string; count: number; limit: number } | null =
    'usage' in overrides ? (overrides.usage ?? null) : { monthYear: '2026-05', count: 0, limit: 5 };
  let usage: { monthYear: string; count: number; limit: number } | null = initialUsage;
  const customer = overrides.customer ?? { firstName: 'Folake' };
  const order = overrides.order ?? {
    customerId: 'cust-1',
    garmentLabel: 'Adire boubou',
    depositFormatted: '₦5,000',
    balanceFormatted: '₦7,500',
    deadlineFormatted: 'Friday, May 22',
  };

  return {
    profileGet: jest.fn().mockResolvedValue({ exists: true, data: () => profile }),
    reserveFreeTierSlot: jest.fn().mockImplementation((now: Date) => {
      const existing = usage;
      const next = reconcileUsage({ existing, now });
      if (existing !== null && isExhausted(existing) && existing.monthYear === next.monthYear) {
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
  });

  it('rejects with permission-denied when free tier exhausted', async () => {
    const fs = fakeFirestore({ usage: { monthYear: '2026-05', count: 5, limit: 5 } });
    await expect(handler(validRequest, baseContext as any, fs)).rejects.toMatchObject({
      code: 'permission-denied',
      message: expect.stringContaining('free_tier_exhausted'),
    });
    expect(fakeVertex.generateText).not.toHaveBeenCalled();
  });

  it('skips reservation for premium users and returns null remaining quota', async () => {
    const fs = fakeFirestore({ profile: { tier: 'premium' } });
    const result = await handler(validRequest, baseContext as any, fs);
    expect(result.remainingFreeQuota).toBeNull();
    expect(fs.reserveFreeTierSlot).not.toHaveBeenCalled();
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
      }),
    });
    await expect(handler(validRequest, baseContext as any, fs)).rejects.toMatchObject({
      code: 'invalid-argument',
    });
    expect(fs.reserveFreeTierSlot).not.toHaveBeenCalled();
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
    const fs = fakeFirestore({ usage: { monthYear: '2026-05', count: 0, limit: 5 } });
    for (let i = 0; i < 5; i++) {
      const result = await handler(validRequest, baseContext as any, fs);
      expect(result.remainingFreeQuota).toBe(4 - i);
    }
    await expect(handler(validRequest, baseContext as any, fs)).rejects.toMatchObject({
      code: 'permission-denied',
    });
  });
});

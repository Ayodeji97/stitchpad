import firebaseFunctionsTest from 'firebase-functions-test';
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

const fakeFirestore = (overrides: Partial<{
  profile: { tier: 'free' | 'premium' };
  usage: { monthYear: string; count: number; limit: number } | null;
  customer: { firstName: string };
  order: { customerId: string; garmentLabel: string; depositFormatted: string; balanceFormatted: string; deadlineFormatted: string };
}>) => {
  const profile = overrides.profile ?? { tier: 'free' as const };
  const usage = 'usage' in overrides ? overrides.usage : { monthYear: '2026-05', count: 0, limit: 5 };
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
    usageGet: jest.fn().mockResolvedValue(
      usage === null ? { exists: false, data: () => undefined } : { exists: true, data: () => usage }
    ),
    usageSet: jest.fn().mockResolvedValue(undefined),
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
    expect(fs.usageSet).toHaveBeenCalledTimes(1);
  });

  it('rejects with permission-denied when free tier exhausted', async () => {
    const fs = fakeFirestore({ usage: { monthYear: '2026-05', count: 5, limit: 5 } });
    await expect(handler(validRequest, baseContext as any, fs)).rejects.toMatchObject({
      code: 'permission-denied',
      message: expect.stringContaining('free_tier_exhausted'),
    });
    expect(fs.usageSet).not.toHaveBeenCalled();
    expect(fakeVertex.generateText).not.toHaveBeenCalled();
  });

  it('skips counter for premium users and returns null remaining quota', async () => {
    const fs = fakeFirestore({ profile: { tier: 'premium' } });
    const result = await handler(validRequest, baseContext as any, fs);
    expect(result.remainingFreeQuota).toBeNull();
    expect(fs.usageSet).not.toHaveBeenCalled();
  });

  it('rejects with invalid-argument when customer does not exist', async () => {
    const fs = fakeFirestore({});
    fs.customerGet = jest.fn().mockResolvedValue({ exists: false, data: () => undefined });
    await expect(handler(validRequest, baseContext as any, fs)).rejects.toMatchObject({
      code: 'invalid-argument',
    });
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
  });

  it('maps Vertex failures to unavailable', async () => {
    const fs = fakeFirestore({});
    (fakeVertex.generateText as jest.Mock).mockRejectedValueOnce(new Error('vertex_no_candidates'));
    await expect(handler(validRequest, baseContext as any, fs)).rejects.toMatchObject({
      code: 'unavailable',
    });
    expect(fs.usageSet).not.toHaveBeenCalled(); // counter NOT incremented on Vertex failure
  });

  it('rolls over the month-year counter on first call of new month', async () => {
    const fs = fakeFirestore({ usage: { monthYear: '2026-04', count: 5, limit: 5 } });
    const result = await handler(validRequest, baseContext as any, fs, new Date('2026-05-16T10:00:00Z'));
    expect(result.remainingFreeQuota).toBe(4);
    expect(fs.usageSet).toHaveBeenCalledWith(
      expect.objectContaining({ monthYear: '2026-05', count: 1 })
    );
  });
});

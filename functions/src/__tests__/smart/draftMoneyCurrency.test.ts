import type { firestore } from 'firebase-admin';
import type { CallableContext } from 'firebase-functions/v1/https';
import {
  __setVertexClientForTests,
  draftMessageHandler,
  productionIO,
} from '../../smart/draftMessage';

const request = { intentType: 'balance_reminder', language: 'en', customerId: 'customer', orderId: 'order' } as const;
const context = { auth: { uid: 'owner', token: {} } } as unknown as CallableContext;
const generateText = jest.fn().mockResolvedValue('Your order balance is ready to review.');
const prefix = 'users/owner/orders/order';

function database(extra: Record<string, firestore.DocumentData | undefined> = {}, failurePath?: string) {
  const documents: Record<string, firestore.DocumentData | undefined> = {
    'users/owner': { subscriptionTier: 'atelier', country: 'GH' },
    'users/owner/customers/customer': { name: 'Ama Mensah' },
    [prefix]: { customerId: 'customer', status: 'IN_PROGRESS', items: [{ description: 'Kaba and slit' }] },
    ...extra,
  };
  const reads: string[] = [];
  const db = {
    doc: (path: string) => ({
      get: async () => {
        reads.push(path);
        if (path === failurePath) throw new Error('money read unavailable');
        const value = documents[path];
        return { exists: value !== undefined, data: () => value };
      },
    }),
  } as unknown as firestore.Firestore;
  return { db, reads };
}

beforeEach(() => {
  generateText.mockClear();
  __setVertexClientForTests({ generateText });
});

describe('Smart money is sourced from the authenticated order and its stamped mirror', () => {
  it('joins stamped Ghana money instead of drafting a zero balance from the base order', async () => {
    const { db, reads } = database({
      [`${prefix}/private/money`]: {
        ownerId: 'owner', orderId: 'order', currency: 'GHS', totalPrice: 100.75,
        discount: 10, payments: [{ amount: 25.25 }],
      },
    });
    await draftMessageHandler(request, context, productionIO('owner', 'customer', 'order', db));
    expect(reads).toContain(`${prefix}/private/money`);
    const prompt = generateText.mock.calls[0][0];
    expect(prompt.userPrompt).toContain('Workshop country: GH');
    expect(prompt.userPrompt).toContain('Order currency: GHS');
    expect(prompt.userPrompt).toContain('Deposit paid: GH₵25.25');
    expect(prompt.userPrompt).toContain('Balance due: GH₵65.50');
    expect(prompt.systemPrompt).not.toContain('₦');
    expect(prompt.systemPrompt).not.toContain('for a Nigerian tailor');
  });

  it('does not relabel a legacy NGN order because the owner now has a Ghana profile', async () => {
    const { db } = database({
      [prefix]: { customerId: 'customer', totalPrice: 1000, depositPaid: 250, status: 'IN_PROGRESS' },
    });
    await draftMessageHandler(request, context, productionIO('owner', 'customer', 'order', db));
    const prompt = generateText.mock.calls[0][0].userPrompt;
    expect(prompt).toContain('Workshop country: GH');
    expect(prompt).toContain('Order currency: NGN');
    expect(prompt).toContain('Balance due: ₦750');
    expect(prompt).not.toContain('GH₵');
  });

  it('ignores unstamped partial mirrors and preserves real legacy base money', async () => {
    const { db } = database({
      [prefix]: { customerId: 'customer', totalPrice: 1000, depositPaid: 250, status: 'IN_PROGRESS' },
      [`${prefix}/private/money`]: { payments: [{ amount: 10 }], currency: 'GHS' },
    });
    const result = (await productionIO('owner', 'customer', 'order', db).orderGet()).data();
    expect(result).toMatchObject({ currency: 'NGN', depositFormatted: '₦250', balanceFormatted: '₦750' });
  });

  it('retains the legacy deposit fallback when a base-only payment array has no positive total', async () => {
    const { db } = database({
      [prefix]: { customerId: 'customer', totalPrice: 1000, depositPaid: 250, payments: [{ amount: 0 }], status: 'IN_PROGRESS' },
    });
    const result = (await productionIO('owner', 'customer', 'order', db).orderGet()).data();
    expect(result).toMatchObject({ depositFormatted: '₦250', balanceFormatted: '₦750' });
  });

  it('treats cleared mirror payments as authoritative rather than resurrecting stale base deposits', async () => {
    const { db } = database({
      [prefix]: { customerId: 'customer', totalPrice: 1000, depositPaid: 900, status: 'IN_PROGRESS' },
      [`${prefix}/private/money`]: { ownerId: 'owner', totalPrice: 50.5, currency: 'GHS', payments: [] },
    });
    const result = (await productionIO('owner', 'customer', 'order', db).orderGet()).data();
    expect(result).toMatchObject({ depositFormatted: 'GH₵0', balanceFormatted: 'GH₵50.50' });
  });

  it('honors a zero-price stamped mirror instead of resurrecting a stale base amount', async () => {
    const { db } = database({
      [prefix]: { customerId: 'customer', totalPrice: 1000, status: 'IN_PROGRESS' },
      [`${prefix}/private/money`]: { ownerId: 'owner', totalPrice: 0, currency: 'GHS' },
    });
    expect((await productionIO('owner', 'customer', 'order', db).orderGet()).data()?.balanceFormatted).toBe('GH₵0');
  });

  it('renders XOF without decimals while preserving the actual currency in prompt context', async () => {
    const { db } = database({
      [`${prefix}/private/money`]: { ownerId: 'owner', totalPrice: 5000.5, currency: 'XOF' },
    });
    await draftMessageHandler(request, context, productionIO('owner', 'customer', 'order', db));
    expect(generateText.mock.calls[0][0].userPrompt).toContain('Order currency: XOF');
    expect(generateText.mock.calls[0][0].userPrompt).toContain('Balance due: F CFA 5,001');
  });

  it('fails before reserving quota when the money write has not arrived yet', async () => {
    const { db } = database({ 'users/owner': { subscriptionTier: 'pro', country: 'GH' } });
    const io = productionIO('owner', 'customer', 'order', db);
    io.reserveFreeTierSlot = jest.fn();
    await expect(draftMessageHandler(request, context, io)).rejects.toMatchObject({
      code: 'failed-precondition', message: 'money_not_available',
    });
    expect(io.reserveFreeTierSlot).not.toHaveBeenCalled();
    expect(generateText).not.toHaveBeenCalled();
  });

  it('does not substitute stale base money when the private read fails', async () => {
    const { db } = database({
      [prefix]: { customerId: 'customer', totalPrice: 1000, status: 'IN_PROGRESS' },
    }, `${prefix}/private/money`);
    await expect(productionIO('owner', 'customer', 'order', db).orderGet()).rejects.toThrow('money read unavailable');
  });

  it('never rewrites the caller path to a workshop owner to expose staff-only money', async () => {
    const { db, reads } = database();
    const result = await productionIO('staff', 'customer', 'order', db).orderGet();
    expect(result.exists).toBe(false);
    expect(reads).toEqual(['users/staff/orders/order']);
  });

  it('preserves the existing unsupported-French guard without reading data or consuming quota', async () => {
    const { db, reads } = database();
    const io = productionIO('owner', 'customer', 'order', db);
    await expect(draftMessageHandler({ ...request, language: 'fr' as 'en' }, context, io)).rejects.toMatchObject({
      code: 'invalid-argument',
    });
    expect(reads).toEqual([]);
    expect(generateText).not.toHaveBeenCalled();
  });
});

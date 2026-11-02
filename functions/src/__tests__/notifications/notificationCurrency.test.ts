import { formatNotificationMoney, notificationCurrency } from '../../notifications/moneyDisplay';
import { mapOrderScanDoc } from '../../notifications/orderScan';
import { moneyFromDoc, withMoney } from '../../notifications/orderMoney';
import { digestDetector } from '../../notifications/digestDetector';
import { notificationDocsFromModel } from '../../notifications/notificationDocs';
import { buildDigestEmail } from '../../notifications/digestEmailTemplate';
import { pushSummary } from '../../notifications/pushSummary';
import { collectibleTransition, collectPushCopy } from '../../notifications/orderCollectNotify';

const NOW = Date.parse('2026-11-02T07:00:00Z');
const OPTIONS = { ctaUrl: 'https://app.test', unsubscribeUrl: 'https://app.test/unsubscribe' };

function scanned(id: string) {
  return mapOrderScanDoc(id, { status: 'READY', customerName: id, items: [{ garmentType: 'Shirt' }] });
}

describe('notification currency rendering', () => {
  it.each([
    ['NGN', '₦1,200.50'], ['GHS', 'GH₵1,200.50'], ['KES', 'KSh 1,200.50'],
    ['XOF', 'F CFA 1,201'], ['XAF', 'FCFA 1,201'], ['GNF', 'FG 1,201'], ['CDF', 'FC 1,200.50'],
  ])('formats major units using %s without relabeling or changing stored amounts', (currency, expected) => {
    expect(formatNotificationMoney(1200.5, currency)).toBe(expected);
  });

  it.each([undefined, null, '', 'unknown', 234, {}])('falls back to NGN for legacy or malformed currency %p', (code) => {
    expect(notificationCurrency(code)).toBe('NGN');
    expect(formatNotificationMoney(1200, code)).toBe('₦1,200');
  });

  it('normalizes supported wire codes and renders whole amounts without .00', () => {
    expect(notificationCurrency(' ghs ')).toBe('GHS');
    expect(formatNotificationMoney(1200, ' ghs ')).toBe('GH₵1,200');
  });

  it('preserves stamped mirror currency through detector, inbox spec, email and push', () => {
    const mirror = moneyFromDoc({ currency: 'GHS', totalPrice: 100.75, payments: [{ amount: 25.25 }] });
    const orders = withMoney([scanned('Ghana')], new Map([['Ghana', mirror]]));
    const model = digestDetector(orders, NOW);
    expect(model.outstanding[0]).toMatchObject({ currency: 'GHS', amount: 75.5 });
    expect(notificationDocsFromModel(model)[0].data).toMatchObject({ currency: 'GHS', amount: 75.5 });
    const email = buildDigestEmail(model, 'Tailor', OPTIONS);
    expect(email.text).toContain('GH₵75.50');
    expect(email.html).toContain('GH₵75.50');
    expect(pushSummary(model).body).toBe('Ghana owes GH₵75.50');
  });

  it('keeps old NGN and new GHS balances in separate email sections without a combined amount', () => {
    const orders = withMoney([scanned('OldNigeria'), scanned('NewGhana')], new Map([
      ['OldNigeria', moneyFromDoc({ totalPrice: 1000 })],
      ['NewGhana', moneyFromDoc({ currency: 'GHS', totalPrice: 50.5 })],
    ]));
    const model = digestDetector(orders, NOW);
    expect(model.outstanding.map((item) => item.currency)).toEqual(['GHS', 'NGN']);
    const email = buildDigestEmail(model, 'Now lives in Ghana', OPTIONS);
    expect(email.text).toContain('To collect · GHS (1):');
    expect(email.text).toContain('To collect · NGN (1):');
    expect(email.text).toContain('GH₵50.50');
    expect(email.text).toContain('₦1,000');
    expect(email.text).not.toContain('1,050');
  });

  it('uses the authoritative mirror currency rather than stale base currency', () => {
    const base = mapOrderScanDoc('id', { status: 'READY', currency: 'KES', totalPrice: 300 });
    const [joined] = withMoney([base], new Map([['id', moneyFromDoc({ currency: 'GHS', totalPrice: 20 })]]));
    expect(joined.currency).toBe('GHS');
    expect(withMoney([base], new Map())[0].currency).toBe('KES');
  });

  it('keeps instant collectible notifications aligned with the digest precision and currency', () => {
    const notification = collectibleTransition({ status: 'IN_PROGRESS' }, {
      ...scanned('Ghana'), ...moneyFromDoc({ currency: 'GHS', totalPrice: 75.5 }),
    });
    expect(notification).toMatchObject({ currency: 'GHS', amount: 75.5 });
    expect(collectPushCopy(notification!).body).toContain('GH₵75.50 to collect');
  });

  it('does not round a small positive balance to zero before a money payload is written', () => {
    const orders = withMoney([scanned('Small')], new Map([['Small', moneyFromDoc({ currency: 'GHS', totalPrice: 0.25 })]]));
    const model = digestDetector(orders, NOW);
    expect(notificationDocsFromModel(model)[0].data.amount).toBe(0.25);
    expect(pushSummary(model).body).toContain('GH₵0.25');
  });
});

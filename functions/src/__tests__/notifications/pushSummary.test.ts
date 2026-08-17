import { pushSummary } from '../../notifications/pushSummary';
import { DigestModel } from '../../notifications/types';

const item = (
  customerName: string,
  garmentSummary: string,
  extra: Partial<{ deadline: number; amount: number; isOverdue: boolean }> = {},
) => ({ orderId: customerName, customerName, garmentSummary, ...extra });

const model = (over: Partial<DigestModel> = {}): DigestModel =>
  ({ overdue: [], dueSoon: [], outstanding: [], ...over });

describe('pushSummary', () => {
  // The title used to be the literal "StitchPad", which both platforms already show as
  // the app name — so it read "StitchPad · StitchPad · Folake's Asoebi is overdue".
  // It now carries the counts, which is the line that earns the open.
  it('puts the counts in the title, omitting empty buckets', () => {
    expect(pushSummary(model({ overdue: [item('Folake', 'Asoebi')] })).title)
      .toBe('1 overdue');
    expect(pushSummary(model({
      overdue: [item('Folake', 'Asoebi'), item('Bola', 'Agbada')],
      dueSoon: [item('Aina', 'Buba')],
      outstanding: [item('Ngozi', 'Shirt', { amount: 18000 })],
    })).title).toBe('2 overdue · 1 due soon · 1 to collect');
    expect(pushSummary(model({ outstanding: [item('Ngozi', 'Shirt', { amount: 18000 })] })).title)
      .toBe('1 to collect');
  });

  it('leads with the single overdue item, no tail', () => {
    expect(pushSummary(model({ overdue: [item('Folake', 'Asoebi')] })).body)
      .toBe('Folake\'s Asoebi is overdue');
  });

  // The body is the single most urgent item and nothing else — the totals live in the
  // title now, so the old "+N more need attention" tail would only repeat them.
  it('prioritises overdue over due-soon and outstanding for the lead', () => {
    const m = model({
      overdue: [item('Folake', 'Asoebi')],
      dueSoon: [item('Aina', 'Buba')],
      outstanding: [item('Ngozi', 'Shirt', { amount: 18000 })],
    });
    expect(pushSummary(m).body).toBe('Folake\'s Asoebi is overdue');
  });

  it('falls back to due-soon when no overdue', () => {
    expect(pushSummary(model({ dueSoon: [item('Aina', 'Buba')] })).body)
      .toBe('Aina\'s Buba is due soon');
  });

  it('falls back to outstanding (owes, formatted naira) when only outstanding', () => {
    expect(pushSummary(model({ outstanding: [item('Ngozi', 'Shirt', { amount: 18000 })] })).body)
      .toBe('Ngozi owes ₦18,000');
  });

  it('within outstanding, ranks an overdue-to-collect item above a non-overdue one for the lead', () => {
    const m = model({
      outstanding: [
        item('Ngozi', 'Shirt', { amount: 30000 }), // fresh, biggest amount, listed first
        item('Chidi', 'Kaftan', { amount: 5000, isOverdue: true }),
      ],
    });
    expect(pushSummary(m).body).toBe('Chidi owes ₦5,000');
  });
});

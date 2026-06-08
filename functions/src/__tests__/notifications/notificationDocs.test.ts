// functions/src/__tests__/notifications/notificationDocs.test.ts
import { notificationDocsFromModel } from '../../notifications/notificationDocs';
import { DigestModel } from '../../notifications/types';

function model(p: Partial<DigestModel> = {}): DigestModel {
  return { dueSoon: [], overdue: [], outstanding: [], ...p };
}

describe('notificationDocsFromModel', () => {
  it('produces a deterministic-id doc per item with structured fields', () => {
    const docs = notificationDocsFromModel(model({
      overdue: [{ orderId: 'o1', customerName: 'Ada', garmentSummary: 'Agbada', deadline: 123 }],
      outstanding: [{ orderId: 'o2', customerName: 'Bola', garmentSummary: 'Buba', amount: 6000 }],
    }));
    expect(docs).toHaveLength(2);
    const overdue = docs.find((d) => d.id === 'o1__OVERDUE')!;
    expect(overdue.data).toEqual({
      orderId: 'o1', type: 'OVERDUE', customerName: 'Ada', garmentSummary: 'Agbada', amount: null, deadline: 123,
    });
    const collect = docs.find((d) => d.id === 'o2__TO_COLLECT')!;
    expect(collect.data.amount).toBe(6000);
    expect(collect.data.deadline).toBeNull();
  });

  it('maps due-soon to DUE_SOON and includes deadline', () => {
    const docs = notificationDocsFromModel(model({
      dueSoon: [{ orderId: 'o3', customerName: 'C', garmentSummary: 'G', deadline: 999 }],
    }));
    expect(docs[0].id).toBe('o3__DUE_SOON');
    expect(docs[0].data.type).toBe('DUE_SOON');
    expect(docs[0].data.deadline).toBe(999);
  });

  it('returns empty for an empty model', () => {
    expect(notificationDocsFromModel(model())).toEqual([]);
  });
});

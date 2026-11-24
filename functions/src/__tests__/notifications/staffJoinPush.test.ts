import { joinRequestPushCopy, JoinPushIO, runJoinRequestPush } from '../../notifications/staffJoinPush';
import type { PushPayload } from '../../notifications/fcm';

const REQ = { ownerUid: 'alice', staffUid: 'chidi', staffName: 'Chidi', staffEmail: 'chidi@ex.co' };

function fakeIO(owner: Record<string, unknown> | null, tokens = ['t1']) {
  const sent: PushPayload[] = [];
  const io: JoinPushIO = {
    loadOwner: async () => owner,
    loadTokens: async () => tokens,
    send: async (_t, p) => { sent.push(p); return { successCount: 1, invalidTokens: [] }; },
    deleteTokens: async () => undefined,
  };
  return { io, sent };
}

describe('join-request push', () => {
  it('copy is localized and falls back to email, then a generic name', () => {
    expect(joinRequestPushCopy(REQ, 'en')).toEqual({
      title: 'New join request', body: 'Chidi asked to join your workshop. Tap to review.',
    });
    expect(joinRequestPushCopy({ ...REQ, staffName: '' }, 'fr').body)
      .toBe('chidi@ex.co demande à rejoindre votre atelier. Touchez pour répondre.');
    expect(joinRequestPushCopy({ ...REQ, staffName: '', staffEmail: '' }, 'en').body).toMatch(/^Someone /);
  });

  it('sends to the owner with the team target and a per-requester tag', async () => {
    const { io, sent } = fakeIO({ locale: 'fr' });
    expect(await runJoinRequestPush(REQ, io)).toBe('sent');
    expect(sent[0]).toMatchObject({
      title: 'Nouvelle demande d’adhésion',
      data: { target: 'team' },
      androidTag: 'stitchpad_join_chidi',
    });
  });

  it('respects the owner opt-out and a missing owner doc', async () => {
    expect(await runJoinRequestPush(REQ, fakeIO({ activityPushEnabled: false }).io)).toBe('push_disabled');
    expect(await runJoinRequestPush(REQ, fakeIO({ dailyPushEnabled: false }).io)).toBe('push_disabled');
    expect(await runJoinRequestPush(REQ, fakeIO(null).io)).toBe('push_disabled');
    expect(await runJoinRequestPush(REQ, fakeIO({}, []).io)).toBe('no_tokens');
  });
});

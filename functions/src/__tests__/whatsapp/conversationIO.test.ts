import { mapConversationDoc } from '../../whatsapp/conversationIO';

describe('mapConversationDoc', () => {
  it('defaults a missing/empty doc to a BOT, terms-pending conversation', () => {
    expect(mapConversationDoc(undefined)).toMatchObject({ state: 'BOT', termsAccepted: false });
    expect(mapConversationDoc({})).toMatchObject({ state: 'BOT', termsAccepted: false });
  });

  it('round-trips the account-linking fields (so consent state survives a read)', () => {
    const d = mapConversationDoc({
      state: 'BOT',
      termsAccepted: true,
      language: 'en',
      linkedUid: 'uid-1',
      linkingConsent: true,
      awaitingLinkConsent: false,
      pendingAccountIntent: 'tier',
    });
    expect(d.linkedUid).toBe('uid-1');
    expect(d.linkingConsent).toBe(true);
    expect(d.awaitingLinkConsent).toBe(false);
    expect(d.pendingAccountIntent).toBe('tier');
  });
});

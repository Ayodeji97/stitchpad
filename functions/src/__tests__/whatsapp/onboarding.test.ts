import { handleOnboarding } from '../../whatsapp/onboarding';
import { ConversationDoc } from '../../whatsapp/types';

const fresh: ConversationDoc = { state: 'BOT', termsAccepted: false };

describe('handleOnboarding', () => {
  it('sends the terms + privacy message on first contact and does not proceed', () => {
    const r = handleOnboarding(fresh, 'hello');
    expect(r.proceedToAnswer).toBe(false);
    expect(r.reply).toMatch(/YES/);
    expect(r.reply?.toLowerCase()).toMatch(/privacy|terms/);
    expect(r.updates?.termsAccepted).toBeUndefined();
  });

  it('accepts the terms on YES and then asks for a language', () => {
    const r = handleOnboarding(fresh, 'YES');
    expect(r.updates?.termsAccepted).toBe(true);
    expect(r.reply?.toLowerCase()).toMatch(/english/);
    expect(r.reply?.toLowerCase()).toMatch(/pidgin/);
    expect(r.proceedToAnswer).toBe(false);
  });

  it('treats YES case-insensitively and trimmed', () => {
    expect(handleOnboarding(fresh, '  yes ').updates?.termsAccepted).toBe(true);
  });

  it('re-sends the terms when the user has not accepted', () => {
    const r = handleOnboarding(fresh, 'what is this');
    expect(r.updates?.termsAccepted).toBeUndefined();
    expect(r.reply).toMatch(/YES/);
  });

  const accepted: ConversationDoc = { state: 'BOT', termsAccepted: true };

  it('sets English from "1"', () => {
    expect(handleOnboarding(accepted, '1').updates?.language).toBe('en');
  });

  it('sets Pidgin from "2" or the word', () => {
    expect(handleOnboarding(accepted, '2').updates?.language).toBe('pcm');
    expect(handleOnboarding(accepted, 'Pidgin').updates?.language).toBe('pcm');
  });

  it('re-prompts on an unrecognized language reply', () => {
    const r = handleOnboarding(accepted, 'spanish please');
    expect(r.updates?.language).toBeUndefined();
    expect(r.reply?.toLowerCase()).toMatch(/english/);
    expect(r.proceedToAnswer).toBe(false);
  });

  it('proceeds to answering once terms + language are set', () => {
    const ready: ConversationDoc = { state: 'BOT', termsAccepted: true, language: 'en' };
    const r = handleOnboarding(ready, 'how do I add a customer?');
    expect(r.proceedToAnswer).toBe(true);
    expect(r.reply).toBeUndefined();
  });
});

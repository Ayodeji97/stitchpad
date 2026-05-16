import { buildSystemPrompt, buildUserPrompt } from '../../smart/promptBuilder';
import { DraftContext } from '../../smart/types';

describe('buildSystemPrompt', () => {
  it('returns a stable system prompt with the tailor instructions', () => {
    const prompt = buildSystemPrompt();
    expect(prompt).toContain('writing assistant for a Nigerian tailor');
    expect(prompt).toContain('Address the customer by their first name');
    expect(prompt).toContain('2-4 sentences');
    expect(prompt).toContain('Output ONLY the message body');
  });
});

describe('buildUserPrompt', () => {
  const ctx: DraftContext = {
    customerFirstName: 'Folake',
    garmentLabel: 'Adire boubou (peach)',
    depositFormatted: '₦5,000',
    balanceFormatted: '₦7,500',
    deadlineFormatted: 'Friday, May 22',
  };

  it('embeds customer + order context for balance reminder in English', () => {
    const prompt = buildUserPrompt({
      intentType: 'balance_reminder',
      language: 'en',
      context: ctx,
    });
    expect(prompt).toContain('polite reminder about an outstanding balance');
    expect(prompt).toContain('English');
    expect(prompt).toContain('Folake');
    expect(prompt).toContain('Adire boubou (peach)');
    expect(prompt).toContain('₦7,500');
    expect(prompt).toContain('Friday, May 22');
  });

  it('switches intent label for pickup_ready', () => {
    const prompt = buildUserPrompt({
      intentType: 'pickup_ready',
      language: 'en',
      context: ctx,
    });
    expect(prompt).toContain('notification that their order is ready for pickup');
  });

  it('switches intent label for follow_up', () => {
    const prompt = buildUserPrompt({
      intentType: 'follow_up',
      language: 'en',
      context: ctx,
    });
    expect(prompt).toContain('casual check-in');
  });

  it('uses custom_note label and includes the notes', () => {
    const prompt = buildUserPrompt({
      intentType: 'custom_note',
      language: 'en',
      context: ctx,
      customNotes: 'Apologise for the delay due to power outage',
    });
    expect(prompt).toContain('custom message');
    expect(prompt).toContain('Apologise for the delay due to power outage');
  });

  it('switches language label to Pidgin for pcm', () => {
    const prompt = buildUserPrompt({
      intentType: 'balance_reminder',
      language: 'pcm',
      context: ctx,
    });
    expect(prompt).toContain('Pidgin');
    expect(prompt).not.toMatch(/in English/);
  });

  it('omits the custom notes section when no notes provided', () => {
    const prompt = buildUserPrompt({
      intentType: 'balance_reminder',
      language: 'en',
      context: ctx,
    });
    expect(prompt).not.toMatch(/Notes:/);
  });
});

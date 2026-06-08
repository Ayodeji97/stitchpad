import { buildSupportSystemPrompt, buildSupportUserPrompt } from '../../whatsapp/ai/promptBuilder';
import { KbArticle } from '../../whatsapp/ai/knowledgeBase';

const articles: KbArticle[] = [
  { id: 'login', category: 'account', question: 'I cannot log in', answerEn: 'Tap Forgot Password on the login screen.', answerPcm: 'Press Forgot Password for the login page.', keywords: ['login'] },
];

describe('buildSupportSystemPrompt', () => {
  it('scopes the bot to StitchPad support and forbids guessing outside the knowledge', () => {
    const p = buildSupportSystemPrompt('en');
    expect(p).toMatch(/StitchPad/);
    expect(p.toLowerCase()).toMatch(/only|knowledge/);
    expect(p.toLowerCase()).toMatch(/connect|human|team/); // escalate when unknown
  });

  it('instructs an off-topic refusal (Meta compliance)', () => {
    expect(buildSupportSystemPrompt('en').toLowerCase()).toMatch(/not about stitchpad|off-topic|decline|politely/);
  });

  it('requires the parseable confidence/escalate tail', () => {
    const p = buildSupportSystemPrompt('en');
    expect(p).toMatch(/CONFIDENCE:/);
    expect(p).toMatch(/ESCALATE:/);
  });

  it('tells the model to answer in Pidgin when language is pcm', () => {
    expect(buildSupportSystemPrompt('pcm').toLowerCase()).toMatch(/pidgin/);
  });
});

describe('buildSupportUserPrompt', () => {
  it('includes the user question and the English knowledge answers', () => {
    const p = buildSupportUserPrompt({ question: 'I forgot my password', articles, language: 'en' });
    expect(p).toMatch(/I forgot my password/);
    expect(p).toMatch(/Tap Forgot Password/);
  });

  it('uses the Pidgin answer when language is pcm and it exists', () => {
    const p = buildSupportUserPrompt({ question: 'I no fit login', articles, language: 'pcm' });
    expect(p).toMatch(/Press Forgot Password/);
  });

  it('signals when no knowledge matched so the model escalates instead of guessing', () => {
    const p = buildSupportUserPrompt({ question: 'random', articles: [], language: 'en' });
    expect(p.toLowerCase()).toMatch(/no (matching )?knowledge|nothing/);
  });
});

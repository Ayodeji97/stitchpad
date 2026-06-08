import { selectRelevant, KbArticle } from '../../whatsapp/ai/knowledgeBase';

const articles: KbArticle[] = [
  { id: 'login', category: 'account', question: 'I cannot log in', answerEn: '…', keywords: ['login', 'log', 'password', 'access', 'signin'] },
  { id: 'crash', category: 'app', question: 'The app keeps crashing', answerEn: '…', keywords: ['crash', 'app', 'freeze', 'close'] },
  { id: 'billing', category: 'billing', question: 'How much is Tailor Pro', answerEn: '…', keywords: ['price', 'pro', 'subscription', 'pay', 'cost'] },
];

describe('selectRelevant', () => {
  it('returns articles whose keywords overlap the question', () => {
    const result = selectRelevant('I forgot my password and cannot access my account', articles);
    expect(result.map((a) => a.id)).toContain('login');
  });

  it('ranks the most-overlapping article first', () => {
    const result = selectRelevant('the app keeps freezing and crashing when I close it', articles);
    expect(result[0].id).toBe('crash');
  });

  it('is case-insensitive', () => {
    const result = selectRelevant('PASSWORD reset', articles);
    expect(result.map((a) => a.id)).toContain('login');
  });

  it('returns an empty array when nothing matches (lets the bot escalate)', () => {
    expect(selectRelevant('what is the weather today', articles)).toEqual([]);
  });

  it('caps the number of returned articles at the limit', () => {
    const many: KbArticle[] = Array.from({ length: 12 }, (_, i) => ({
      id: `a${i}`, category: 'c', question: 'q', answerEn: '…', keywords: ['order'],
    }));
    expect(selectRelevant('order order', many, 5)).toHaveLength(5);
  });
});

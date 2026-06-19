import { answerSupportQuestion } from '../../whatsapp/ai/answerer';
import { KbArticle } from '../../whatsapp/ai/knowledgeBase';
import { VertexClient } from '../../smart/vertexClient';

const kb: KbArticle[] = [
  { id: 'login', category: 'account', question: 'I cannot log in', answerEn: 'Tap Forgot Password.', keywords: ['login', 'password'] },
];

/** Fake client returning a canned completion and capturing the call args. */
function fakeClient(reply: string) {
  const calls: { systemPrompt: string; userPrompt: string; maxOutputTokens?: number }[] = [];
  const client: VertexClient = {
    async generateText(args) { calls.push(args); return reply; },
  };
  return { client, calls };
}

function throwingClient(): VertexClient {
  return { async generateText() { throw new Error('genai down'); } };
}

describe('answerSupportQuestion', () => {
  it('returns the answer with the CONFIDENCE/ESCALATE tail stripped off', async () => {
    const { client } = fakeClient('Tap Forgot Password on the login screen.\nCONFIDENCE: high\nESCALATE: no');
    const result = await answerSupportQuestion({ question: 'forgot password', language: 'en', knowledge: kb, client });
    expect(result.answer).toBe('Tap Forgot Password on the login screen.');
    expect(result.confidence).toBe('high');
    expect(result.escalate).toBe(false);
  });

  it('parses an explicit escalate=yes', async () => {
    const { client } = fakeClient('I am not sure about that.\nCONFIDENCE: low\nESCALATE: yes');
    const result = await answerSupportQuestion({ question: 'weird edge case', language: 'en', knowledge: kb, client });
    expect(result.escalate).toBe(true);
    expect(result.confidence).toBe('low');
  });

  it('escalates a low-confidence answer even when the model says ESCALATE: no', async () => {
    const { client } = fakeClient('Maybe try resetting it.\nCONFIDENCE: low\nESCALATE: no');
    const result = await answerSupportQuestion({ question: 'forgot password', language: 'en', knowledge: kb, client });
    expect(result.escalate).toBe(true);
  });

  it('escalates when the model returns an empty answer body (only tail markers)', async () => {
    const { client } = fakeClient('CONFIDENCE: high\nESCALATE: no');
    const result = await answerSupportQuestion({ question: 'forgot password', language: 'en', knowledge: kb, client });
    expect(result.escalate).toBe(true);
  });

  it('defaults to escalate when the model omits the tail (untrusted output)', async () => {
    const { client } = fakeClient('Some freeform answer with no markers.');
    const result = await answerSupportQuestion({ question: 'q', language: 'en', knowledge: kb, client });
    expect(result.escalate).toBe(true);
  });

  it('requests more output tokens than the 200 default for grounded answers', async () => {
    const { client, calls } = fakeClient('ok\nCONFIDENCE: high\nESCALATE: no');
    await answerSupportQuestion({ question: 'forgot password', language: 'en', knowledge: kb, client });
    expect(calls[0].maxOutputTokens).toBeGreaterThan(200);
  });

  it('escalates without calling the model when no knowledge matches (no ungrounded answers)', async () => {
    let called = false;
    const client: VertexClient = { async generateText() { called = true; return 'anything\nCONFIDENCE: high\nESCALATE: no'; } };
    const result = await answerSupportQuestion({ question: 'what is the weather today', language: 'en', knowledge: kb, client });
    expect(called).toBe(false);
    expect(result.escalate).toBe(true);
  });

  it('escalates without throwing when the model call fails', async () => {
    const result = await answerSupportQuestion({ question: 'q', language: 'en', knowledge: kb, client: throwingClient() });
    expect(result.escalate).toBe(true);
    expect(result.confidence).toBe('low');
  });
});

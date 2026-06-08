import { handleInboundPayload, MessageHandlerDeps } from '../../whatsapp/messageHandler';
import { ConversationIO } from '../../whatsapp/conversationIO';
import { DedupIO } from '../../whatsapp/dedup';
import { WhatsAppClient } from '../../whatsapp/cloudApiClient';
import { KbIO, KbArticle } from '../../whatsapp/ai/knowledgeBase';
import { VertexClient } from '../../smart/vertexClient';
import { ConversationDoc } from '../../whatsapp/types';

const KB: KbArticle[] = [
  { id: 'login', category: 'account', question: 'I cannot log in', answerEn: 'Tap Forgot Password.', keywords: ['login', 'password'] },
];

function textPayload(from: string, id: string, body: string): unknown {
  return { entry: [{ changes: [{ field: 'messages', value: { messages: [{ from, id, type: 'text', text: { body } }] } }] }] };
}

function fakeClient() {
  const sent: { to: string; body: string }[] = [];
  const client: WhatsAppClient = { async sendText(to, body) { sent.push({ to, body }); }, async markRead() { /* noop */ } };
  return { client, sent };
}

function fakeDedup(): DedupIO & { seen: Set<string> } {
  const seen = new Set<string>();
  return {
    seen,
    async markProcessed(waId, messageId) { const k = `${waId}/${messageId}`; if (seen.has(k)) return false; seen.add(k); return true; },
    async release(waId, messageId) { seen.delete(`${waId}/${messageId}`); },
  };
}

function fakeConversations(initial: Record<string, ConversationDoc> = {}) {
  const store = new Map<string, ConversationDoc>(Object.entries(initial));
  const io: ConversationIO = {
    async get(waId) { return store.get(waId) ?? { state: 'BOT', termsAccepted: false }; },
    async update(waId, updates) { store.set(waId, { state: 'BOT', termsAccepted: false, ...store.get(waId), ...updates }); },
  };
  return { io, store };
}

function fakeKnowledge(articles: KbArticle[] = KB): KbIO {
  return { async loadKnowledge() { return articles; } };
}

function fakeVertex(reply: string): VertexClient {
  return { async generateText() { return reply; } };
}

function deps(over: Partial<MessageHandlerDeps>): MessageHandlerDeps {
  return {
    client: fakeClient().client,
    dedup: fakeDedup(),
    conversations: fakeConversations().io,
    knowledge: fakeKnowledge(),
    vertex: fakeVertex('ok\nCONFIDENCE: high\nESCALATE: no'),
    ...over,
  };
}

describe('handleInboundPayload (orchestration)', () => {
  it('sends the terms gate on first contact and never calls the model', async () => {
    const { client, sent } = fakeClient();
    let vertexCalled = false;
    const vertex: VertexClient = { async generateText() { vertexCalled = true; return ''; } };
    await handleInboundPayload(textPayload('a', '1', 'hello'), deps({ client, vertex }));

    expect(sent).toHaveLength(1);
    expect(sent[0].body).toMatch(/YES/);
    expect(vertexCalled).toBe(false);
  });

  it('accepts terms on YES, persists it, and asks for language', async () => {
    const { client, sent } = fakeClient();
    const conv = fakeConversations();
    await handleInboundPayload(textPayload('a', '1', 'YES'), deps({ client, conversations: conv.io }));

    expect(conv.store.get('a')?.termsAccepted).toBe(true);
    expect(sent[0].body.toLowerCase()).toMatch(/english/);
  });

  it('answers a question from the knowledge base once onboarded', async () => {
    const { client, sent } = fakeClient();
    const conv = fakeConversations({ a: { state: 'BOT', termsAccepted: true, language: 'en' } });
    const vertex = fakeVertex('Tap Forgot Password on the login screen.\nCONFIDENCE: high\nESCALATE: no');
    await handleInboundPayload(textPayload('a', '1', 'I forgot my password'), deps({ client, conversations: conv.io, vertex }));

    expect(sent).toHaveLength(1);
    expect(sent[0].body).toBe('Tap Forgot Password on the login screen.');
  });

  it('sends a fallback handoff message when the model fails', async () => {
    const { client, sent } = fakeClient();
    const conv = fakeConversations({ a: { state: 'BOT', termsAccepted: true, language: 'en' } });
    const vertex: VertexClient = { async generateText() { throw new Error('genai down'); } };
    await handleInboundPayload(textPayload('a', '1', 'something obscure'), deps({ client, conversations: conv.io, vertex }));

    expect(sent).toHaveLength(1);
    expect(sent[0].body.toLowerCase()).toMatch(/team|connect|human/);
  });

  it('stays silent when a human owns the conversation (non-BOT state)', async () => {
    const { client, sent } = fakeClient();
    let vertexCalled = false;
    const vertex: VertexClient = { async generateText() { vertexCalled = true; return ''; } };
    const conv = fakeConversations({ a: { state: 'AWAITING_HUMAN', termsAccepted: true, language: 'en' } });
    await handleInboundPayload(textPayload('a', '1', 'are you there?'), deps({ client, conversations: conv.io, vertex }));

    expect(sent).toEqual([]);
    expect(vertexCalled).toBe(false);
  });

  it('sends the handoff fallback, not the model text, when the answer is flagged to escalate', async () => {
    const { client, sent } = fakeClient();
    const conv = fakeConversations({ a: { state: 'BOT', termsAccepted: true, language: 'en' } });
    // Freeform output with no CONFIDENCE/ESCALATE tail -> answerer escalates,
    // but still has answer text. That untrusted text must NOT reach the user.
    const vertex = fakeVertex('Here is some unverified freeform answer.');
    await handleInboundPayload(textPayload('a', '1', 'obscure question'), deps({ client, conversations: conv.io, vertex }));

    expect(sent).toHaveLength(1);
    expect(sent[0].body).not.toMatch(/unverified freeform/);
    expect(sent[0].body.toLowerCase()).toMatch(/team|connect|human/);
  });

  it('does not reprocess a duplicate delivery', async () => {
    const { client, sent } = fakeClient();
    const dedup = fakeDedup();
    const conv = fakeConversations();
    const p = textPayload('a', '1', 'hello');
    await handleInboundPayload(p, deps({ client, dedup, conversations: conv.io }));
    await handleInboundPayload(p, deps({ client, dedup, conversations: conv.io }));
    expect(sent).toHaveLength(1);
  });

  it('releases the dedup marker and rethrows when a send fails (so Meta retries)', async () => {
    const dedup = fakeDedup();
    const client: WhatsAppClient = { async sendText() { throw new Error('graph down'); }, async markRead() { /* noop */ } };
    await expect(handleInboundPayload(textPayload('a', '1', 'hello'), deps({ client, dedup }))).rejects.toThrow('graph down');
    expect(dedup.seen.has('a/1')).toBe(false);
  });

  it('ignores empty/non-text messages', async () => {
    const { client, sent } = fakeClient();
    const payload = { entry: [{ changes: [{ field: 'messages', value: { messages: [{ from: 'a', id: '9', type: 'image', image: {} }] } }] }] };
    await handleInboundPayload(payload, deps({ client }));
    expect(sent).toEqual([]);
  });

  it('caps an over-long outgoing answer at the WhatsApp limit', async () => {
    const { client, sent } = fakeClient();
    const conv = fakeConversations({ a: { state: 'BOT', termsAccepted: true, language: 'en' } });
    const vertex = fakeVertex('x'.repeat(5000) + '\nCONFIDENCE: high\nESCALATE: no');
    await handleInboundPayload(textPayload('a', '1', 'login help'), deps({ client, conversations: conv.io, vertex }));
    expect(sent[0].body.length).toBeLessThanOrEqual(4096);
  });
});

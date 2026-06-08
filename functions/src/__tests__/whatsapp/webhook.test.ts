import { verifyChallenge, handleInboundPayload, WebhookIO } from '../../whatsapp/webhook';
import { WhatsAppClient } from '../../whatsapp/cloudApiClient';

function textPayload(from: string, id: string, body: string): unknown {
  return {
    object: 'whatsapp_business_account',
    entry: [{ changes: [{ field: 'messages', value: { messages: [{ from, id, type: 'text', text: { body } }] } }] }],
  };
}

function fakeClient() {
  const sent: { to: string; body: string }[] = [];
  const read: string[] = [];
  const client: WhatsAppClient = {
    async sendText(to, body) { sent.push({ to, body }); },
    async markRead(messageId) { read.push(messageId); },
  };
  return { client, sent, read };
}

/** Fake dedup store: first time a (waId,messageId) is seen returns true. */
function fakeIO(): WebhookIO & { seen: Set<string> } {
  const seen = new Set<string>();
  return {
    seen,
    async markProcessed(waId, messageId) {
      const key = `${waId}/${messageId}`;
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    },
  };
}

describe('verifyChallenge', () => {
  it('returns the challenge when mode is subscribe and the token matches', () => {
    expect(verifyChallenge({ mode: 'subscribe', token: 'secret', challenge: '12345' }, 'secret')).toBe('12345');
  });

  it('returns null when the token does not match', () => {
    expect(verifyChallenge({ mode: 'subscribe', token: 'wrong', challenge: '12345' }, 'secret')).toBeNull();
  });

  it('returns null when the mode is not subscribe', () => {
    expect(verifyChallenge({ mode: 'unsubscribe', token: 'secret', challenge: '12345' }, 'secret')).toBeNull();
  });
});

describe('handleInboundPayload', () => {
  it('echoes a new text message and records it as processed', async () => {
    const { client, sent } = fakeClient();
    const io = fakeIO();
    await handleInboundPayload(textPayload('234801', 'wamid.1', 'Hello'), io, client);

    expect(sent).toEqual([{ to: '234801', body: 'You said: Hello' }]);
    expect(io.seen.has('234801/wamid.1')).toBe(true);
  });

  it('does not echo a duplicate delivery of the same message id (Meta retry)', async () => {
    const { client, sent } = fakeClient();
    const io = fakeIO();
    const payload = textPayload('234801', 'wamid.1', 'Hello');

    await handleInboundPayload(payload, io, client);
    await handleInboundPayload(payload, io, client); // retry

    expect(sent).toHaveLength(1);
  });

  it('handles multiple distinct messages in one payload', async () => {
    const { client, sent } = fakeClient();
    const io = fakeIO();
    const payload = {
      entry: [{ changes: [{ field: 'messages', value: { messages: [
        { from: 'a', id: '1', type: 'text', text: { body: 'one' } },
        { from: 'b', id: '2', type: 'text', text: { body: 'two' } },
      ] } }] }],
    };
    await handleInboundPayload(payload, io, client);
    expect(sent).toEqual([
      { to: 'a', body: 'You said: one' },
      { to: 'b', body: 'You said: two' },
    ]);
  });

  it('records but does not echo a non-text message', async () => {
    const { client, sent } = fakeClient();
    const io = fakeIO();
    const payload = { entry: [{ changes: [{ field: 'messages', value: { messages: [{ from: 'a', id: '9', type: 'image', image: {} }] } }] }] };
    await handleInboundPayload(payload, io, client);

    expect(sent).toEqual([]);
    expect(io.seen.has('a/9')).toBe(true);
  });

  it('ignores a status-only callback without sending anything', async () => {
    const { client, sent } = fakeClient();
    const io = fakeIO();
    await handleInboundPayload({ entry: [{ changes: [{ value: { statuses: [{ id: 'x' }] } }] }] }, io, client);
    expect(sent).toEqual([]);
  });
});

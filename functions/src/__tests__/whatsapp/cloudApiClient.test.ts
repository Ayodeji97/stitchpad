import { createWhatsAppClient, GRAPH_VERSION } from '../../whatsapp/cloudApiClient';

describe('createWhatsAppClient', () => {
  const realFetch = global.fetch;
  afterEach(() => { global.fetch = realFetch; });

  it('sendText POSTs a text message to the phone-number-id endpoint with a bearer token', async () => {
    const calls: any[] = [];
    global.fetch = (async (url: any, init: any) => {
      calls.push({ url, init });
      return { ok: true, status: 200, text: async () => '' } as any;
    }) as any;

    const client = createWhatsAppClient('TOKEN_123', 'PNID_456');
    await client.sendText('2348012345678', 'Hi there');

    expect(calls).toHaveLength(1);
    expect(calls[0].url).toBe(`https://graph.facebook.com/${GRAPH_VERSION}/PNID_456/messages`);
    expect(calls[0].init.headers.Authorization).toBe('Bearer TOKEN_123');
    expect(calls[0].init.headers['Content-Type']).toBe('application/json');
    const body = JSON.parse(calls[0].init.body);
    expect(body).toMatchObject({
      messaging_product: 'whatsapp',
      to: '2348012345678',
      type: 'text',
      text: { body: 'Hi there' },
    });
  });

  it('markRead POSTs a read status for the given message id', async () => {
    let captured: any;
    global.fetch = (async (_url: any, init: any) => { captured = JSON.parse(init.body); return { ok: true, status: 200, text: async () => '' } as any; }) as any;

    const client = createWhatsAppClient('T', 'PNID');
    await client.markRead('wamid.XYZ');

    expect(captured).toMatchObject({
      messaging_product: 'whatsapp',
      status: 'read',
      message_id: 'wamid.XYZ',
    });
  });

  it('throws with status detail when the Graph API responds non-ok', async () => {
    global.fetch = (async () => ({ ok: false, status: 400, text: async () => 'bad request' } as any)) as any;
    const client = createWhatsAppClient('T', 'PNID');
    await expect(client.sendText('234', 'x')).rejects.toThrow('WhatsApp send failed 400: bad request');
  });
});

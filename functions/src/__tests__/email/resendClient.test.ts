import { sendResendEmail } from '../../email/resendClient';

describe('sendResendEmail', () => {
  const realFetch = global.fetch;
  afterEach(() => { global.fetch = realFetch; });

  it('POSTs to Resend with auth header and from/reply-to', async () => {
    const calls: any[] = [];
    global.fetch = (async (url: any, init: any) => {
      calls.push({ url, init });
      return { ok: true, status: 200, text: async () => '' } as any;
    }) as any;

    await sendResendEmail('key_123', { to: 'a@b.com', subject: 'Hi', html: '<p>x</p>', text: 'x' });

    expect(calls).toHaveLength(1);
    expect(calls[0].url).toBe('https://api.resend.com/emails');
    expect(calls[0].init.headers.Authorization).toBe('Bearer key_123');
    const body = JSON.parse(calls[0].init.body);
    expect(body.from).toContain('noreply@send.getstitchpad.com');
    expect(body.reply_to).toBe('support@getstitchpad.com');
    expect(body.to).toEqual(['a@b.com']);
    expect(body.subject).toBe('Hi');
    expect(body.text).toBe('x');
  });

  it('omits the text field from the payload when no text is provided', async () => {
    let captured: any;
    global.fetch = (async (_url: any, init: any) => { captured = JSON.parse(init.body); return { ok: true, status: 200, text: async () => '' } as any; }) as any;
    await sendResendEmail('k', { to: 'a@b.com', subject: 's', html: '<p>h</p>' });
    expect(captured.text).toBeUndefined();
  });

  it('throws with status detail when Resend responds non-ok', async () => {
    global.fetch = (async () => ({ ok: false, status: 422, text: async () => 'bad' } as any)) as any;
    await expect(sendResendEmail('k', { to: 'a@b.com', subject: 's', html: 'h', text: 't' }))
      .rejects.toThrow('Resend responded 422: bad');
  });
});

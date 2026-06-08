import {
  detectExplicitEscalation,
  isFounder,
  parseFounderCommand,
  buildTicketEmail,
  productionEscalationIO,
} from '../../whatsapp/escalation';

describe('detectExplicitEscalation', () => {
  it('flags explicit requests to reach a human (English)', () => {
    expect(detectExplicitEscalation('can I talk to a human please')).toBe('human_requested');
    expect(detectExplicitEscalation('I want to speak to an agent')).toBe('human_requested');
    expect(detectExplicitEscalation('connect me to customer service')).toBe('human_requested');
  });

  it('flags human requests phrased in Pidgin', () => {
    expect(detectExplicitEscalation('abeg make I talk to person')).toBe('human_requested');
  });

  it('flags sensitive account/billing actions', () => {
    expect(detectExplicitEscalation('I want a refund')).toBe('sensitive_action');
    expect(detectExplicitEscalation('please delete my account')).toBe('sensitive_action');
    expect(detectExplicitEscalation('I need to change my number')).toBe('sensitive_action');
    expect(detectExplicitEscalation('cancel my subscription')).toBe('sensitive_action');
  });

  it('returns null for an ordinary support question', () => {
    expect(detectExplicitEscalation('how do I add a customer?')).toBeNull();
  });
});

describe('isFounder', () => {
  it('matches an allow-listed number regardless of + / spacing format', () => {
    expect(isFounder('2348012345678', ['+234 801 234 5678'])).toBe(true);
    expect(isFounder('2348012345678', ['2349999999999'])).toBe(false);
  });

  it('is false for an empty allowlist', () => {
    expect(isFounder('2348012345678', [])).toBe(false);
  });
});

describe('parseFounderCommand', () => {
  it('parses #reply <target> <message>', () => {
    expect(parseFounderCommand('#reply 2348012345678 Your order is ready')).toEqual({
      kind: 'reply', target: '2348012345678', body: 'Your order is ready',
    });
  });

  it('keeps the full multi-word body including extra spaces', () => {
    expect(parseFounderCommand('#reply 234801  hello   there')).toMatchObject({ kind: 'reply', body: 'hello   there' });
  });

  it('parses #resolve <target>', () => {
    expect(parseFounderCommand('#resolve 2348012345678')).toEqual({ kind: 'resolve', target: '2348012345678' });
  });

  it('returns null for non-commands or malformed commands', () => {
    expect(parseFounderCommand('hello there')).toBeNull();
    expect(parseFounderCommand('#reply 2348012345678')).toBeNull(); // no body
    expect(parseFounderCommand('#resolve')).toBeNull(); // no target
  });
});

describe('buildTicketEmail', () => {
  it('includes the user number, reason and message, and the reply instruction', () => {
    const mail = buildTicketEmail({ waId: '2348012345678', reason: 'human_requested', message: 'I need help urgently' });
    expect(mail.subject).toMatch(/2348012345678/);
    expect(mail.html).toMatch(/human_requested/);
    expect(mail.html).toMatch(/I need help urgently/);
    expect(mail.text).toMatch(/#reply 2348012345678/);
  });

  it('escapes HTML in the user message to avoid injection in the email body', () => {
    const mail = buildTicketEmail({ waId: '234', reason: 'sensitive_action', message: '<script>alert(1)</script>' });
    expect(mail.html).not.toMatch(/<script>/);
    expect(mail.html).toMatch(/&lt;script&gt;/);
  });
});

describe('productionEscalationIO', () => {
  const realFetch = global.fetch;
  afterEach(() => { global.fetch = realFetch; });

  it('emails the ticket to the support address via Resend', async () => {
    let body: { to?: string[]; subject?: string } = {};
    global.fetch = (async (_url: unknown, init: { body: string }) => {
      body = JSON.parse(init.body);
      return { ok: true, status: 200, text: async () => '' };
    }) as unknown as typeof fetch;

    const io = productionEscalationIO('key_x', 'support@getstitchpad.com');
    await io.sendTicket({ waId: '2348012345678', reason: 'human_requested', message: 'help me' });

    expect(body.to).toEqual(['support@getstitchpad.com']);
    expect(body.subject).toMatch(/2348012345678/);
  });
});

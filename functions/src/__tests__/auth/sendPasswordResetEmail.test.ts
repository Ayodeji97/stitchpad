import firebaseFunctionsTest from 'firebase-functions-test';
import {
  sendPasswordResetEmailHandler,
  PasswordResetEmailIO,
} from '../../auth/sendPasswordResetEmail';
import { buildPasswordResetEmail } from '../../auth/passwordResetEmailTemplate';

const test = firebaseFunctionsTest();

afterAll(() => {
  test.cleanup();
});

function makeIO(overrides: Partial<{
  user: { displayName?: string } | null;
  link: string;
  sendEmail: jest.Mock;
  reserveSend: jest.Mock;
}> = {}): {
  io: PasswordResetEmailIO;
  getUserByEmail: jest.Mock;
  generateLink: jest.Mock;
  sendEmail: jest.Mock;
  reserveSend: jest.Mock;
  releaseSend: jest.Mock;
} {
  const getUserByEmail = jest
    .fn()
    .mockResolvedValue('user' in overrides ? overrides.user : { displayName: 'Tunde Johnson' });
  const generateLink = jest.fn().mockResolvedValue(overrides.link ?? 'https://reset.example/link');
  const sendEmail = overrides.sendEmail ?? jest.fn().mockResolvedValue(undefined);
  const reserveSend = overrides.reserveSend ?? jest.fn().mockResolvedValue(true);
  const releaseSend = jest.fn().mockResolvedValue(undefined);
  const io: PasswordResetEmailIO = {
    getUserByEmail,
    generateLink,
    sendEmail,
    reserveSend,
    releaseSend,
  };
  return { io, getUserByEmail, generateLink, sendEmail, reserveSend, releaseSend };
}

describe('sendPasswordResetEmailHandler', () => {
  it('rejects a missing or malformed email', async () => {
    const { io } = makeIO();
    await expect(sendPasswordResetEmailHandler({}, io)).rejects.toMatchObject({
      code: 'invalid-argument',
    });
    await expect(sendPasswordResetEmailHandler({ email: 'not-an-email' }, io)).rejects.toMatchObject({
      code: 'invalid-argument',
    });
  });

  it('generates a link and sends the email on the happy path', async () => {
    const { io, generateLink, sendEmail } = makeIO({ link: 'https://reset.example/abc' });
    const result = await sendPasswordResetEmailHandler({ email: 'Tunde@Example.com ' }, io);
    expect(result).toEqual({ sent: true });
    // Email is normalized (trimmed + lowercased) before use.
    expect(generateLink).toHaveBeenCalledWith('tunde@example.com');
    expect(sendEmail).toHaveBeenCalledWith({
      to: 'tunde@example.com',
      displayName: 'Tunde Johnson',
      resetLink: 'https://reset.example/abc',
    });
  });

  it('succeeds silently without sending when the email is not registered', async () => {
    const { io, generateLink, sendEmail, releaseSend } = makeIO({ user: null });
    const result = await sendPasswordResetEmailHandler({ email: 'ghost@example.com' }, io);
    // No error and no "user not found" signal — enumeration guard.
    expect(result).toEqual({ sent: false });
    expect(generateLink).not.toHaveBeenCalled();
    expect(sendEmail).not.toHaveBeenCalled();
    // Reservation is held (not released) so probes are throttled identically.
    expect(releaseSend).not.toHaveBeenCalled();
  });

  it('throttles when a send was reserved too recently', async () => {
    const { io, sendEmail } = makeIO({ reserveSend: jest.fn().mockResolvedValue(false) });
    await expect(
      sendPasswordResetEmailHandler({ email: 'tunde@example.com' }, io),
    ).rejects.toMatchObject({ code: 'resource-exhausted' });
    expect(sendEmail).not.toHaveBeenCalled();
  });

  it('maps a send failure to unavailable and releases the reservation', async () => {
    const { io, releaseSend } = makeIO({
      sendEmail: jest.fn().mockRejectedValue(new Error('boom')),
    });
    await expect(
      sendPasswordResetEmailHandler({ email: 'tunde@example.com' }, io),
    ).rejects.toMatchObject({ code: 'unavailable' });
    expect(releaseSend).toHaveBeenCalledTimes(1);
  });

  it('does not release the reservation on a successful send', async () => {
    const { io, releaseSend } = makeIO();
    await sendPasswordResetEmailHandler({ email: 'tunde@example.com' }, io);
    expect(releaseSend).not.toHaveBeenCalled();
  });
});

describe('buildPasswordResetEmail', () => {
  it('embeds the reset link in the button and the personalized greeting', () => {
    const { html, text } = buildPasswordResetEmail({
      displayName: 'Tunde',
      resetLink: 'https://reset.example/abc',
    });
    expect(html).toContain('href="https://reset.example/abc"');
    expect(html).toContain('Reset password');
    expect(html).toContain('Hi Tunde,');
    // Copy-paste fallback for when the button doesn't work.
    expect(html).toContain('Button not working?');
    // Multipart text alternative carries the raw link too.
    expect(text).toContain('https://reset.example/abc');
    expect(text).toContain('Hi Tunde,');
  });

  it('embeds the brand logo, headline, and indigo CTA', () => {
    const { html } = buildPasswordResetEmail({ resetLink: 'https://x/y' });
    expect(html).toContain('stitchpad-email-logo.png');
    expect(html).toContain('alt="StitchPad"');
    expect(html).toContain('Reset your password');
    expect(html).toContain('#1E2B5C'); // indigo CTA fill
  });

  it('falls back to a generic greeting and escapes HTML in the name', () => {
    const withoutName = buildPasswordResetEmail({ resetLink: 'https://x/y' });
    expect(withoutName.html).toContain('Hi there,');

    const malicious = buildPasswordResetEmail({
      displayName: '<script>x</script>',
      resetLink: 'https://x/y',
    });
    expect(malicious.html).not.toContain('<script>x</script>');
    expect(malicious.html).toContain('&lt;script&gt;');
  });
});

import firebaseFunctionsTest from 'firebase-functions-test';
import {
  sendVerificationEmailHandler,
  VerificationEmailIO,
} from '../../auth/sendVerificationEmail';
import { buildVerificationEmailHtml } from '../../auth/verificationEmailTemplate';

const test = firebaseFunctionsTest();

afterAll(() => {
  test.cleanup();
});

function makeIO(overrides: Partial<{
  email?: string;
  displayName?: string;
  emailVerified: boolean;
  link: string;
  sendEmail: jest.Mock;
}> = {}): { io: VerificationEmailIO; sendEmail: jest.Mock; generateLink: jest.Mock } {
  const sendEmail = overrides.sendEmail ?? jest.fn().mockResolvedValue(undefined);
  const generateLink = jest.fn().mockResolvedValue(overrides.link ?? 'https://verify.example/link');
  const io: VerificationEmailIO = {
    getUser: jest.fn().mockResolvedValue({
      email: 'email' in overrides ? overrides.email : 'tunde@example.com',
      displayName: overrides.displayName ?? 'Tunde Johnson',
      emailVerified: overrides.emailVerified ?? false,
    }),
    generateLink,
    sendEmail,
  };
  return { io, sendEmail, generateLink };
}

const authedContext = { auth: { uid: 'uid-1', token: {} } } as never;
const anonContext = { auth: undefined } as never;

describe('sendVerificationEmailHandler', () => {
  it('rejects unauthenticated callers', async () => {
    const { io } = makeIO();
    await expect(sendVerificationEmailHandler(anonContext, io)).rejects.toMatchObject({
      code: 'unauthenticated',
    });
  });

  it('rejects when the account has no email', async () => {
    const { io } = makeIO({ email: undefined });
    await expect(sendVerificationEmailHandler(authedContext, io)).rejects.toMatchObject({
      code: 'failed-precondition',
    });
  });

  it('skips sending when already verified', async () => {
    const { io, sendEmail } = makeIO({ emailVerified: true });
    const result = await sendVerificationEmailHandler(authedContext, io);
    expect(result).toEqual({ sent: false, alreadyVerified: true });
    expect(sendEmail).not.toHaveBeenCalled();
  });

  it('generates a link and sends the email on the happy path', async () => {
    const { io, sendEmail, generateLink } = makeIO({ link: 'https://verify.example/abc' });
    const result = await sendVerificationEmailHandler(authedContext, io);
    expect(result).toEqual({ sent: true, alreadyVerified: false });
    expect(generateLink).toHaveBeenCalledWith('tunde@example.com');
    expect(sendEmail).toHaveBeenCalledWith({
      to: 'tunde@example.com',
      displayName: 'Tunde Johnson',
      verifyLink: 'https://verify.example/abc',
    });
  });

  it('maps a send failure to unavailable', async () => {
    const { io } = makeIO({ sendEmail: jest.fn().mockRejectedValue(new Error('boom')) });
    await expect(sendVerificationEmailHandler(authedContext, io)).rejects.toMatchObject({
      code: 'unavailable',
    });
  });
});

describe('buildVerificationEmailHtml', () => {
  it('embeds the verify link in a button and a fallback link', () => {
    const html = buildVerificationEmailHtml({
      displayName: 'Tunde',
      verifyLink: 'https://verify.example/abc',
    });
    expect(html).toContain('href="https://verify.example/abc"');
    expect(html).toContain('Verify Email');
    expect(html).toContain('Hello Tunde');
  });

  it('falls back to a generic greeting and escapes HTML in the name', () => {
    const withoutName = buildVerificationEmailHtml({ verifyLink: 'https://x/y' });
    expect(withoutName).toContain('Hello there');

    const malicious = buildVerificationEmailHtml({
      displayName: '<script>x</script>',
      verifyLink: 'https://x/y',
    });
    expect(malicious).not.toContain('<script>x</script>');
    expect(malicious).toContain('&lt;script&gt;');
  });
});

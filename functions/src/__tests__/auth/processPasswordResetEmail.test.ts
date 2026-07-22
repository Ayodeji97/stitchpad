import {
  processPasswordResetEmailHandler,
  PasswordResetWorkerIO,
} from '../../auth/processPasswordResetEmail';

function makeIO(overrides: Partial<{
  user: { displayName?: string } | null;
  link: string;
  sendEmail: jest.Mock;
}> = {}): {
  io: PasswordResetWorkerIO;
  getUserByEmail: jest.Mock;
  generateLink: jest.Mock;
  sendEmail: jest.Mock;
} {
  const getUserByEmail = jest
    .fn()
    .mockResolvedValue('user' in overrides ? overrides.user : { displayName: 'Tunde Johnson' });
  const generateLink = jest.fn().mockResolvedValue(overrides.link ?? 'https://reset.example/link');
  const sendEmail = overrides.sendEmail ?? jest.fn().mockResolvedValue(undefined);
  const io: PasswordResetWorkerIO = { getUserByEmail, generateLink, sendEmail };
  return { io, getUserByEmail, generateLink, sendEmail };
}

describe('processPasswordResetEmailHandler (worker)', () => {
  it('generates a link and sends the email for a registered account', async () => {
    const { io, generateLink, sendEmail } = makeIO({ link: 'https://reset.example/abc' });
    await processPasswordResetEmailHandler({ email: 'tunde@example.com' }, io);
    expect(generateLink).toHaveBeenCalledWith('tunde@example.com');
    expect(sendEmail).toHaveBeenCalledWith({
      to: 'tunde@example.com',
      displayName: 'Tunde Johnson',
      resetLink: 'https://reset.example/abc',
    });
  });

  it('sends the link on our own domain, not the blocklisted firebaseapp.com host', async () => {
    const { io, sendEmail } = makeIO({
      link:
        'https://stitchpad-30607.firebaseapp.com/__/auth/action' +
        '?mode=resetPassword&oobCode=ABC123&apiKey=AIzaSyKEY&lang=en',
    });

    await processPasswordResetEmailHandler({ email: 'tunde@example.com' }, io);

    expect(sendEmail).toHaveBeenCalledWith(
      expect.objectContaining({
        resetLink:
          'https://auth.getstitchpad.com/__/auth/action' +
          '?mode=resetPassword&oobCode=ABC123&apiKey=AIzaSyKEY&lang=en',
      }),
    );
  });

  it('normalizes the email from the task payload', async () => {
    const { io, generateLink } = makeIO();
    await processPasswordResetEmailHandler({ email: '  Tunde@Example.com ' }, io);
    expect(generateLink).toHaveBeenCalledWith('tunde@example.com');
  });

  it('sends nothing when the account does not exist', async () => {
    const { io, generateLink, sendEmail } = makeIO({ user: null });
    await processPasswordResetEmailHandler({ email: 'ghost@example.com' }, io);
    expect(generateLink).not.toHaveBeenCalled();
    expect(sendEmail).not.toHaveBeenCalled();
  });

  it('drops a malformed task payload without throwing or sending', async () => {
    const { io, getUserByEmail, sendEmail } = makeIO();
    await expect(
      processPasswordResetEmailHandler({ email: 'not-an-email' }, io),
    ).resolves.toBeUndefined();
    expect(getUserByEmail).not.toHaveBeenCalled();
    expect(sendEmail).not.toHaveBeenCalled();
  });

  it('propagates a send failure so Cloud Tasks retries', async () => {
    const { io } = makeIO({ sendEmail: jest.fn().mockRejectedValue(new Error('resend 500')) });
    await expect(
      processPasswordResetEmailHandler({ email: 'tunde@example.com' }, io),
    ).rejects.toThrow('resend 500');
  });
});

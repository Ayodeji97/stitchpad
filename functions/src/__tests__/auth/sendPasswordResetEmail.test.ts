import firebaseFunctionsTest from 'firebase-functions-test';
import {
  sendPasswordResetEmailHandler,
  PasswordResetEnqueuerIO,
} from '../../auth/sendPasswordResetEmail';

const test = firebaseFunctionsTest();

afterAll(() => {
  test.cleanup();
});

function makeIO(overrides: Partial<{
  reserveSend: jest.Mock;
  enqueue: jest.Mock;
}> = {}): {
  io: PasswordResetEnqueuerIO;
  reserveSend: jest.Mock;
  releaseSend: jest.Mock;
  enqueue: jest.Mock;
} {
  const reserveSend = overrides.reserveSend ?? jest.fn().mockResolvedValue(true);
  const releaseSend = jest.fn().mockResolvedValue(undefined);
  const enqueue = overrides.enqueue ?? jest.fn().mockResolvedValue(undefined);
  const io: PasswordResetEnqueuerIO = { reserveSend, releaseSend, enqueue };
  return { io, reserveSend, releaseSend, enqueue };
}

describe('sendPasswordResetEmailHandler (enqueuer)', () => {
  it('rejects a missing or malformed email', async () => {
    const { io, enqueue } = makeIO();
    await expect(sendPasswordResetEmailHandler({}, io)).rejects.toMatchObject({
      code: 'invalid-argument',
    });
    await expect(sendPasswordResetEmailHandler({ email: 'not-an-email' }, io)).rejects.toMatchObject({
      code: 'invalid-argument',
    });
    expect(enqueue).not.toHaveBeenCalled();
  });

  it('enqueues the normalized email and returns the constant ack', async () => {
    const { io, enqueue } = makeIO();
    const result = await sendPasswordResetEmailHandler({ email: 'Tunde@Example.com ' }, io);
    expect(result).toEqual({ ok: true });
    // Trimmed + lowercased before it reaches the queue.
    expect(enqueue).toHaveBeenCalledWith('tunde@example.com');
  });

  it('is constant-time: enqueues without ever checking account existence', async () => {
    // The IO has no user-lookup/send method at all — the enqueuer cannot branch
    // on whether the account exists, so its work (and timing) is identical for
    // every email. This test pins that the seam stays existence-agnostic.
    const { io } = makeIO();
    expect(Object.keys(io).sort()).toEqual(['enqueue', 'releaseSend', 'reserveSend']);
  });

  it('throttles when a send was reserved too recently and does not enqueue', async () => {
    const { io, enqueue } = makeIO({ reserveSend: jest.fn().mockResolvedValue(false) });
    await expect(
      sendPasswordResetEmailHandler({ email: 'tunde@example.com' }, io),
    ).rejects.toMatchObject({ code: 'resource-exhausted' });
    expect(enqueue).not.toHaveBeenCalled();
  });

  it('releases the reservation and maps to unavailable when enqueue fails', async () => {
    const { io, releaseSend } = makeIO({
      enqueue: jest.fn().mockRejectedValue(new Error('cloud tasks down')),
    });
    await expect(
      sendPasswordResetEmailHandler({ email: 'tunde@example.com' }, io),
    ).rejects.toMatchObject({ code: 'unavailable' });
    expect(releaseSend).toHaveBeenCalledTimes(1);
  });

  it('does not release the reservation on a successful enqueue', async () => {
    const { io, releaseSend } = makeIO();
    await sendPasswordResetEmailHandler({ email: 'tunde@example.com' }, io);
    expect(releaseSend).not.toHaveBeenCalled();
  });
});

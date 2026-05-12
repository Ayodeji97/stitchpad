jest.mock('../cleanup/firestore', () => ({
  deleteUserFirestoreData: jest.fn().mockResolvedValue(undefined),
  ALLOWED_SUBCOLLECTIONS: ['customers', 'orders', 'measurements', 'styles', 'goals'],
}));
jest.mock('../cleanup/storage', () => ({
  deleteUserStorageData: jest.fn().mockResolvedValue(undefined),
}));

import firebaseFunctionsTest from 'firebase-functions-test';
import { deleteUserFirestoreData } from '../cleanup/firestore';
import { deleteUserStorageData } from '../cleanup/storage';

const testEnv = firebaseFunctionsTest();

describe('onAuthUserDeleted', () => {
  afterAll(() => testEnv.cleanup());

  it('invokes both cleanup branches with the deleted user uid', async () => {
    const { onAuthUserDeleted } = await import('../index');
    const wrapped = testEnv.wrap(onAuthUserDeleted);
    const fakeUser = testEnv.auth.makeUserRecord({ uid: 'fake-uid-xyz' });

    await wrapped(fakeUser);

    expect(deleteUserFirestoreData).toHaveBeenCalledWith(
      'fake-uid-xyz',
      expect.anything(),
    );
    expect(deleteUserStorageData).toHaveBeenCalledWith(
      'fake-uid-xyz',
      expect.anything(),
    );
  });

  it('still resolves when one cleanup branch rejects (Promise.allSettled semantics)', async () => {
    (deleteUserFirestoreData as jest.Mock).mockRejectedValueOnce(new Error('boom'));
    const { onAuthUserDeleted } = await import('../index');
    const wrapped = testEnv.wrap(onAuthUserDeleted);
    const fakeUser = testEnv.auth.makeUserRecord({ uid: 'fake-uid-xyz' });

    await expect(wrapped(fakeUser)).resolves.toBeUndefined();
  });
});

jest.mock('../cleanup/firestore', () => ({
  // Pull through the real ALLOWED_SUBCOLLECTIONS so this mock can't drift
  // from production when the allow-list changes.
  ...jest.requireActual('../cleanup/firestore'),
  deleteUserFirestoreData: jest.fn().mockResolvedValue(undefined),
}));
jest.mock('../cleanup/storage', () => ({
  deleteUserStorageData: jest.fn().mockResolvedValue(undefined),
}));
jest.mock('../referral/clawback', () => ({
  // Pull through the real constants (REJECT_ACCOUNT_DELETED etc.) so this mock
  // can't drift, and only stub the async branch that would otherwise make a
  // real Firestore call and hang the test.
  ...jest.requireActual('../referral/clawback'),
  clawbackReferralOnDelete: jest.fn().mockResolvedValue('none'),
}));

import firebaseFunctionsTest from 'firebase-functions-test';
import { deleteUserFirestoreData } from '../cleanup/firestore';
import { deleteUserStorageData } from '../cleanup/storage';
import { clawbackReferralOnDelete } from '../referral/clawback';

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
    expect(clawbackReferralOnDelete).toHaveBeenCalledWith(
      'fake-uid-xyz',
      expect.anything(),
    );
  });

  it('still resolves AND runs the other branch when one cleanup rejects (Promise.allSettled semantics)', async () => {
    (deleteUserFirestoreData as jest.Mock).mockClear();
    (deleteUserStorageData as jest.Mock).mockClear();
    (clawbackReferralOnDelete as jest.Mock).mockClear();
    (deleteUserFirestoreData as jest.Mock).mockRejectedValueOnce(new Error('boom'));
    const { onAuthUserDeleted } = await import('../index');
    const wrapped = testEnv.wrap(onAuthUserDeleted);
    const fakeUser = testEnv.auth.makeUserRecord({ uid: 'fake-uid-xyz' });

    await expect(wrapped(fakeUser)).resolves.toBeUndefined();

    // Critical: this asserts the Promise.allSettled isolation guarantee. If
    // someone swaps allSettled for Promise.all, storage cleanup would never
    // run after the firestore rejection and this assertion would fail.
    expect(deleteUserStorageData).toHaveBeenCalledWith(
      'fake-uid-xyz',
      expect.anything(),
    );
  });
});

import * as functions from 'firebase-functions/v1';
import { deleteUserStorageData } from '../cleanup/storage';

describe('deleteUserStorageData', () => {
  const uid = 'test-uid-abc';

  it('calls bucket.deleteFiles with users/<uid>/ prefix', async () => {
    const deleteFiles = jest.fn().mockResolvedValue(undefined);
    const bucket = { deleteFiles } as never;

    await deleteUserStorageData(uid, bucket);

    expect(deleteFiles).toHaveBeenCalledTimes(1);
    expect(deleteFiles).toHaveBeenCalledWith({ prefix: `users/${uid}/` });
  });

  it('logs and swallows errors instead of throwing', async () => {
    const error = new Error('boom');
    const deleteFiles = jest.fn().mockRejectedValue(error);
    const bucket = { deleteFiles } as never;
    const loggerSpy = jest
      .spyOn(functions.logger, 'error')
      .mockImplementation(() => undefined);

    await expect(deleteUserStorageData(uid, bucket)).resolves.toBeUndefined();
    expect(loggerSpy).toHaveBeenCalledWith(
      'storage cleanup failed',
      expect.objectContaining({
        uid,
        error: expect.objectContaining({
          name: error.name,
          message: error.message,
        }),
      }),
    );
  });
});

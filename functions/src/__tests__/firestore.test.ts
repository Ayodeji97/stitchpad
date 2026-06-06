import * as functions from 'firebase-functions/v1';
import { deleteUserFirestoreData, ALLOWED_SUBCOLLECTIONS } from '../cleanup/firestore';

describe('deleteUserFirestoreData', () => {
  const uid = 'test-uid-abc';

  function makeDbMock(
    options: {
      recursiveDelete?: jest.Mock;
      docDelete?: jest.Mock;
      listCollections?: jest.Mock;
    } = {},
  ) {
    const docDelete = options.docDelete ?? jest.fn().mockResolvedValue(undefined);
    const listCollections =
      options.listCollections ?? jest.fn().mockResolvedValue([]);
    const collectionRef = (id: string) => ({ id, _kind: 'collectionRef', _name: id });
    const userDocRef = {
      delete: docDelete,
      listCollections,
      collection: jest.fn((id: string) => collectionRef(id)),
    };
    const db = {
      collection: jest.fn(() => ({ doc: jest.fn(() => userDocRef) })),
      recursiveDelete: options.recursiveDelete ?? jest.fn().mockResolvedValue(undefined),
    };
    return { db, userDocRef, docDelete };
  }

  it('calls recursiveDelete for every allow-listed subcollection', async () => {
    const recursiveDelete = jest.fn().mockResolvedValue(undefined);
    const { db } = makeDbMock({ recursiveDelete });

    await deleteUserFirestoreData(uid, db as never);

    expect(recursiveDelete).toHaveBeenCalledTimes(ALLOWED_SUBCOLLECTIONS.length);
    for (const sub of ALLOWED_SUBCOLLECTIONS) {
      expect(recursiveDelete).toHaveBeenCalledWith(
        expect.objectContaining({ _name: sub }),
      );
    }
  });

  it('deletes the user doc after subcollections are cleaned', async () => {
    const callOrder: string[] = [];
    const recursiveDelete = jest.fn(async (ref: { _name: string }) => {
      callOrder.push(`rec:${ref._name}`);
    });
    const docDelete = jest.fn(async () => {
      callOrder.push('doc:delete');
    });
    const { db } = makeDbMock({ recursiveDelete, docDelete });

    await deleteUserFirestoreData(uid, db as never);

    // Asserts both ORDER (doc last) and that subcollections were actually
    // iterated — guards against a future refactor skipping the for-loop.
    expect(callOrder).toHaveLength(ALLOWED_SUBCOLLECTIONS.length + 1);
    expect(callOrder[callOrder.length - 1]).toBe('doc:delete');
  });

  it('continues when one subcollection delete fails', async () => {
    const recursiveDelete = jest.fn(async (ref: { _name: string }) => {
      if (ref._name === 'orders') throw new Error('boom');
    });
    const docDelete = jest.fn().mockResolvedValue(undefined);
    const { db } = makeDbMock({ recursiveDelete, docDelete });
    const loggerSpy = jest
      .spyOn(functions.logger, 'error')
      .mockImplementation(() => undefined);

    await expect(deleteUserFirestoreData(uid, db as never)).resolves.toBeUndefined();

    expect(recursiveDelete).toHaveBeenCalledTimes(ALLOWED_SUBCOLLECTIONS.length);
    expect(docDelete).toHaveBeenCalled();
    expect(loggerSpy).toHaveBeenCalledWith(
      'firestore subcollection cleanup failed',
      expect.objectContaining({ uid, subcollection: 'orders' }),
    );
  });

  it('logs and swallows when user doc delete fails', async () => {
    const docDelete = jest.fn().mockRejectedValue(new Error('boom'));
    const { db } = makeDbMock({ docDelete });
    const loggerSpy = jest
      .spyOn(functions.logger, 'error')
      .mockImplementation(() => undefined);

    await expect(deleteUserFirestoreData(uid, db as never)).resolves.toBeUndefined();
    expect(loggerSpy).toHaveBeenCalledWith(
      'firestore user doc cleanup failed',
      expect.objectContaining({ uid }),
    );
  });

  it('warns on unexpected subcollection names but does NOT auto-delete them', async () => {
    const recursiveDelete = jest.fn().mockResolvedValue(undefined);
    const listCollections = jest
      .fn()
      .mockResolvedValue([
        { id: 'customers' },
        { id: 'invoices' },
        { id: 'notifications' },
        { id: 'orders' },
      ]);
    const { db } = makeDbMock({ recursiveDelete, listCollections });
    const warnSpy = jest
      .spyOn(functions.logger, 'warn')
      .mockImplementation(() => undefined);

    await deleteUserFirestoreData(uid, db as never);

    // Allow-listed names not warned about (including notifications, which holds
    // PII and must be swept on account deletion)
    for (const sub of ['customers', 'notifications', 'orders']) {
      expect(warnSpy).not.toHaveBeenCalledWith(
        expect.anything(),
        expect.objectContaining({ unexpectedSubcollection: sub }),
      );
    }
    // Unexpected name is warned about
    expect(warnSpy).toHaveBeenCalledWith(
      'unexpected subcollection found under users/{uid}; not cleaned up',
      expect.objectContaining({ uid, unexpectedSubcollection: 'invoices' }),
    );
    // recursiveDelete is NOT called on the unexpected subcollection
    expect(recursiveDelete).not.toHaveBeenCalledWith(
      expect.objectContaining({ _name: 'invoices' }),
    );
  });

  it('logs an error when listCollections itself fails, but still attempts cleanup', async () => {
    const listCollections = jest.fn().mockRejectedValue(new Error('list-boom'));
    const recursiveDelete = jest.fn().mockResolvedValue(undefined);
    const { db } = makeDbMock({ listCollections, recursiveDelete });
    const errorSpy = jest
      .spyOn(functions.logger, 'error')
      .mockImplementation(() => undefined);

    await deleteUserFirestoreData(uid, db as never);

    expect(errorSpy).toHaveBeenCalledWith(
      'subcollection drift check failed',
      expect.objectContaining({ uid }),
    );
    expect(recursiveDelete).toHaveBeenCalledTimes(ALLOWED_SUBCOLLECTIONS.length);
  });
});

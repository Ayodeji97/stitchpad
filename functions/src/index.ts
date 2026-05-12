import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';
import { deleteUserFirestoreData } from './cleanup/firestore';
import { deleteUserStorageData } from './cleanup/storage';

if (admin.apps.length === 0) {
  admin.initializeApp();
}

export const onAuthUserDeleted = functions
  .region('europe-west1')
  .auth.user()
  .onDelete(async (user) => {
    const uid = user.uid;
    functions.logger.info('cleanup starting', { uid });
    await Promise.allSettled([
      deleteUserFirestoreData(uid, admin.firestore()),
      deleteUserStorageData(uid, admin.storage().bucket()),
    ]);
    functions.logger.info('cleanup completed', { uid });
  });

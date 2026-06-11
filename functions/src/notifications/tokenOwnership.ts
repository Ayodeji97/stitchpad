/** A notificationTokens doc reduced to its owner uid and a deletable ref. */
export interface TokenDocOwner<T> {
  uid: string;
  ref: T;
}

/**
 * Refs to delete to enforce single-ownership of an FCM token: every doc whose owner
 * uid differs from `ownerUid` (the user the token was just registered under). The
 * just-created doc and any same-user dupes (uid === ownerUid) are kept.
 */
export function dupeTokenRefsToPrune<T>(ownerUid: string, docs: TokenDocOwner<T>[]): T[] {
  return docs.filter((d) => d.uid !== ownerUid).map((d) => d.ref);
}

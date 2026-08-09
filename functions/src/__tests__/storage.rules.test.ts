import { readFileSync } from 'fs';
import { resolve } from 'path';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
  RulesTestEnvironment,
} from '@firebase/rules-unit-testing';
import { ref, uploadString, getBytes } from 'firebase/storage';
import { doc, setDoc } from 'firebase/firestore';

/**
 * Security-rules tests for storage.rules. Run with `npm run test:rules`
 * (wraps this in `firebase emulators:exec --only firestore,storage`).
 *
 * The staff branch of storage.rules calls firestore.get() to check the
 * membership doc's status, so the firestore emulator must run alongside the
 * storage emulator for these tests — mirrors the firestore.rules harness in
 * firestore.rules.test.ts.
 */

const STORAGE_RULES = readFileSync(resolve(__dirname, '../../../storage.rules'), 'utf8');
const FIRESTORE_RULES = readFileSync(resolve(__dirname, '../../../firestore.rules'), 'utf8');

let testEnv: RulesTestEnvironment;
const OWNER = 'owner-uid';
const STAFF = 'staff-uid';

beforeAll(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: 'demo-stitchpad',
    firestore: { rules: FIRESTORE_RULES, host: '127.0.0.1', port: 8080 },
    storage: { rules: STORAGE_RULES, host: '127.0.0.1', port: 9199 },
  });
});

afterAll(async () => {
  await testEnv.cleanup();
});

beforeEach(async () => {
  await testEnv.clearStorage();
  await testEnv.clearFirestore();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), `users/${OWNER}/memberships/${STAFF}`), { status: 'active' });
  });
});

const staffStorage = () =>
  testEnv.authenticatedContext(STAFF, { role: 'staff', workshopUid: OWNER }).storage();

it('staff may write under the owner order-media subtree', async () => {
  await assertSucceeds(uploadString(ref(staffStorage(), `users/${OWNER}/orders/o1/fabrics/i1-abc.jpg`), 'x'));
});

it('staff may read anywhere in the workshop tree', async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await uploadString(ref(ctx.storage(), `users/${OWNER}/logo.png`), 'x');
  });
  await assertSucceeds(getBytes(ref(staffStorage(), `users/${OWNER}/logo.png`)));
});

it('staff may NOT write outside orders (brand logo)', async () => {
  await assertFails(uploadString(ref(staffStorage(), `users/${OWNER}/logo.png`), 'x'));
});

it('revoked staff may not write order media', async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) =>
    setDoc(doc(ctx.firestore(), `users/${OWNER}/memberships/${STAFF}`), { status: 'revoked' }));
  await assertFails(uploadString(ref(staffStorage(), `users/${OWNER}/orders/o1/fabrics/i1.jpg`), 'x'));
});

it('a foreign user may neither read nor write the tree', async () => {
  const foreign = testEnv.authenticatedContext('other-uid').storage();
  await assertFails(uploadString(ref(foreign, `users/${OWNER}/orders/o1/fabrics/i1.jpg`), 'x'));
  await assertFails(getBytes(ref(foreign, `users/${OWNER}/logo.png`)));
});

it('owner keeps full read/write', async () => {
  const owner = testEnv.authenticatedContext(OWNER).storage();
  await assertSucceeds(uploadString(ref(owner, `users/${OWNER}/logo.png`), 'x'));
});

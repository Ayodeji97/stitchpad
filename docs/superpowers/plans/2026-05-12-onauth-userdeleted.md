# `onAuthUserDeleted` Cloud Function — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Server-side `onAuthUserDeleted` Cloud Function (Gen 1, TypeScript, region `europe-west1`) that recursively cleans up Firestore `users/{uid}/*` and Cloud Storage `users/{uid}/*` whenever a Firebase Auth user is deleted. Best-effort with per-branch error logging.

**Architecture:** Single function in a new `functions/` codebase at repo root. Two cleanup modules (`cleanup/firestore.ts`, `cleanup/storage.ts`) called in parallel via `Promise.allSettled`. Firestore uses Admin SDK `recursiveDelete` over an explicit allow-list of subcollections plus a drift-warning that logs unexpected subcollection names. Jest unit tests with mocked `firebase-admin`, run in CI in a new `functions-tests` job. Manual `firebase deploy` after merge.

**Tech Stack:** Firebase Functions Gen 1, firebase-admin, TypeScript 5.6, Jest 29, firebase-functions-test, ESLint, Node 20.

---

### Task 1: Scaffold the `functions/` project

**Files:**
- Create: `functions/package.json`
- Create: `functions/tsconfig.json`
- Create: `functions/tsconfig.dev.json`
- Create: `functions/.eslintrc.js`
- Create: `functions/.gitignore`
- Create: `functions/jest.config.js`

- [ ] **Step 1: Create `functions/package.json`**

```json
{
  "name": "functions",
  "description": "Cloud Functions for StitchPad",
  "private": true,
  "engines": { "node": "20" },
  "main": "lib/index.js",
  "scripts": {
    "build": "tsc",
    "lint": "eslint --ext .js,.ts src/",
    "test": "jest",
    "deploy": "npm run build && firebase deploy --only functions:onAuthUserDeleted"
  },
  "dependencies": {
    "firebase-admin": "^12.7.0",
    "firebase-functions": "^6.0.1"
  },
  "devDependencies": {
    "@types/jest": "^29.5.13",
    "@types/node": "^20.16.0",
    "@typescript-eslint/eslint-plugin": "^8.8.0",
    "@typescript-eslint/parser": "^8.8.0",
    "eslint": "^8.57.0",
    "firebase-functions-test": "^3.3.0",
    "jest": "^29.7.0",
    "ts-jest": "^29.2.5",
    "typescript": "^5.6.0"
  }
}
```

- [ ] **Step 2: Create `functions/tsconfig.json`**

```json
{
  "compilerOptions": {
    "module": "commonjs",
    "target": "es2022",
    "lib": ["es2022"],
    "outDir": "lib",
    "rootDir": "src",
    "strict": true,
    "noImplicitReturns": true,
    "noUnusedLocals": true,
    "sourceMap": true,
    "esModuleInterop": true,
    "skipLibCheck": true,
    "forceConsistentCasingInFileNames": true
  },
  "include": ["src/**/*"],
  "exclude": ["src/__tests__/**/*", "lib/**/*", "node_modules/**/*"]
}
```

- [ ] **Step 3: Create `functions/tsconfig.dev.json`**

```json
{
  "extends": "./tsconfig.json",
  "compilerOptions": {
    "noUnusedLocals": false
  },
  "include": ["src/**/*"],
  "exclude": ["lib/**/*", "node_modules/**/*"]
}
```

- [ ] **Step 4: Create `functions/.eslintrc.js`**

```javascript
module.exports = {
  root: true,
  env: { node: true, es2022: true, jest: true },
  parser: '@typescript-eslint/parser',
  parserOptions: {
    ecmaVersion: 2022,
    sourceType: 'module',
    project: ['./tsconfig.dev.json'],
  },
  plugins: ['@typescript-eslint'],
  extends: ['eslint:recommended', 'plugin:@typescript-eslint/recommended'],
  ignorePatterns: ['/lib/**/*', '/node_modules/**/*'],
  rules: {
    quotes: ['error', 'single'],
    semi: ['error', 'always'],
    '@typescript-eslint/no-explicit-any': 'warn',
  },
};
```

- [ ] **Step 5: Create `functions/.gitignore`**

```
node_modules/
lib/
*.log
.firebase/
```

- [ ] **Step 6: Create `functions/jest.config.js`**

```javascript
/** @type {import('jest').Config} */
module.exports = {
  preset: 'ts-jest',
  testEnvironment: 'node',
  roots: ['<rootDir>/src'],
  testMatch: ['**/__tests__/**/*.test.ts'],
  collectCoverageFrom: ['src/**/*.ts', '!src/__tests__/**/*'],
  clearMocks: true,
};
```

- [ ] **Step 7: Install dependencies and verify tooling**

Run: `cd functions && npm install && npm run build 2>&1 | head -5`
Expected: no errors. `lib/` directory is created (will be empty since no src yet).

Run: `cd functions && npx tsc --version && npx jest --version`
Expected: prints `Version 5.6.x` and `29.7.x`.

- [ ] **Step 8: Commit**

```bash
git add functions/package.json functions/package-lock.json functions/tsconfig.json functions/tsconfig.dev.json functions/.eslintrc.js functions/.gitignore functions/jest.config.js
git commit -m "chore(functions): scaffold Cloud Functions TypeScript project"
```

---

### Task 2: Add root Firebase config files

**Files:**
- Create: `firebase.json`
- Create: `.firebaserc`

- [ ] **Step 1: Create `firebase.json`**

```json
{
  "functions": [
    {
      "source": "functions",
      "codebase": "default",
      "ignore": [
        "node_modules",
        ".git",
        "firebase-debug.log",
        "firebase-debug.*.log",
        "*.local"
      ],
      "predeploy": [
        "npm --prefix \"$RESOURCE_DIR\" run lint",
        "npm --prefix \"$RESOURCE_DIR\" run build"
      ]
    }
  ]
}
```

- [ ] **Step 2: Create `.firebaserc`**

```json
{
  "projects": {
    "default": "stitchpad-30607"
  }
}
```

- [ ] **Step 3: Verify Firebase CLI sees the config**

Run: `firebase --project stitchpad-30607 functions:list 2>&1 | head -5`
Expected: prints existing functions (may be empty list). No "configuration not found" error.

If `firebase` CLI is not installed, instruct: `npm install -g firebase-tools` and re-run.

- [ ] **Step 4: Commit**

```bash
git add firebase.json .firebaserc
git commit -m "chore(functions): wire firebase.json + .firebaserc for stitchpad-30607"
```

---

### Task 3: Storage cleanup module (TDD)

**Files:**
- Create: `functions/src/cleanup/storage.ts`
- Create: `functions/src/__tests__/storage.test.ts`

- [ ] **Step 1: Write the failing test**

`functions/src/__tests__/storage.test.ts`:

```typescript
import { deleteUserStorageData } from '../cleanup/storage';

describe('deleteUserStorageData', () => {
  const uid = 'test-uid-abc';

  it('calls bucket.deleteFiles with users/<uid>/ prefix', async () => {
    const deleteFiles = jest.fn().mockResolvedValue(undefined);
    const bucket = { deleteFiles } as never;

    await deleteUserStorageData(uid, bucket);

    expect(deleteFiles).toHaveBeenCalledWith({ prefix: `users/${uid}/` });
  });

  it('logs and swallows errors instead of throwing', async () => {
    const error = new Error('boom');
    const deleteFiles = jest.fn().mockRejectedValue(error);
    const bucket = { deleteFiles } as never;
    const loggerSpy = jest
      .spyOn(require('firebase-functions/v1').logger, 'error')
      .mockImplementation(() => undefined);

    await expect(deleteUserStorageData(uid, bucket)).resolves.toBeUndefined();
    expect(loggerSpy).toHaveBeenCalledWith(
      'storage cleanup failed',
      expect.objectContaining({ uid }),
    );

    loggerSpy.mockRestore();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd functions && npm test -- --testPathPattern=storage`
Expected: FAIL with "Cannot find module '../cleanup/storage'".

- [ ] **Step 3: Write the implementation**

`functions/src/cleanup/storage.ts`:

```typescript
import * as functions from 'firebase-functions/v1';
import type { Bucket } from '@google-cloud/storage';

/**
 * Delete every Cloud Storage object under users/<uid>/.
 *
 * Best-effort: a failure here is logged but never thrown, so the surrounding
 * Promise.allSettled in index.ts can still report success and the function
 * never auto-retries (see spec for the rationale).
 */
export async function deleteUserStorageData(
  uid: string,
  bucket: Bucket,
): Promise<void> {
  try {
    await bucket.deleteFiles({ prefix: `users/${uid}/` });
  } catch (error) {
    functions.logger.error('storage cleanup failed', {
      uid,
      error: serialiseError(error),
    });
  }
}

function serialiseError(error: unknown): unknown {
  return error instanceof Error
    ? { name: error.name, message: error.message, stack: error.stack }
    : error;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd functions && npm test -- --testPathPattern=storage`
Expected: PASS, 2 tests.

- [ ] **Step 5: Commit**

```bash
git add functions/src/cleanup/storage.ts functions/src/__tests__/storage.test.ts
git commit -m "feat(functions): storage cleanup module with best-effort error logging"
```

---

### Task 4: Firestore cleanup module — allow-list deletes (TDD)

**Files:**
- Create: `functions/src/cleanup/firestore.ts`
- Create: `functions/src/__tests__/firestore.test.ts`

- [ ] **Step 1: Write the failing test (allow-list deletes only — drift warning is Task 5)**

`functions/src/__tests__/firestore.test.ts`:

```typescript
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

    expect(callOrder[callOrder.length - 1]).toBe('doc:delete');
  });

  it('continues when one subcollection delete fails', async () => {
    const recursiveDelete = jest.fn(async (ref: { _name: string }) => {
      if (ref._name === 'orders') throw new Error('boom');
    });
    const docDelete = jest.fn().mockResolvedValue(undefined);
    const { db } = makeDbMock({ recursiveDelete, docDelete });
    const loggerSpy = jest
      .spyOn(require('firebase-functions/v1').logger, 'error')
      .mockImplementation(() => undefined);

    await expect(deleteUserFirestoreData(uid, db as never)).resolves.toBeUndefined();

    expect(recursiveDelete).toHaveBeenCalledTimes(ALLOWED_SUBCOLLECTIONS.length);
    expect(docDelete).toHaveBeenCalled();
    expect(loggerSpy).toHaveBeenCalledWith(
      'firestore subcollection cleanup failed',
      expect.objectContaining({ uid, subcollection: 'orders' }),
    );

    loggerSpy.mockRestore();
  });

  it('logs and swallows when user doc delete fails', async () => {
    const docDelete = jest.fn().mockRejectedValue(new Error('boom'));
    const { db } = makeDbMock({ docDelete });
    const loggerSpy = jest
      .spyOn(require('firebase-functions/v1').logger, 'error')
      .mockImplementation(() => undefined);

    await expect(deleteUserFirestoreData(uid, db as never)).resolves.toBeUndefined();
    expect(loggerSpy).toHaveBeenCalledWith(
      'firestore user doc cleanup failed',
      expect.objectContaining({ uid }),
    );

    loggerSpy.mockRestore();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd functions && npm test -- --testPathPattern=firestore`
Expected: FAIL with "Cannot find module '../cleanup/firestore'".

- [ ] **Step 3: Write the implementation (allow-list only — drift warning added in Task 5)**

`functions/src/cleanup/firestore.ts`:

```typescript
import * as functions from 'firebase-functions/v1';
import type { Firestore } from 'firebase-admin/firestore';

/**
 * Subcollections under users/<uid>/ that this function deletes.
 * Explicit allow-list (not runtime discovery) so accidental data writes
 * under users/<uid>/<unexpected>/ don't silently get nuked. New subcollections
 * must be added here AND logged via the drift-warning code below.
 */
export const ALLOWED_SUBCOLLECTIONS = [
  'customers',
  'orders',
  'measurements',
  'styles',
  'goals',
] as const;

export async function deleteUserFirestoreData(
  uid: string,
  db: Firestore,
): Promise<void> {
  const userDocRef = db.collection('users').doc(uid);

  for (const sub of ALLOWED_SUBCOLLECTIONS) {
    try {
      await db.recursiveDelete(userDocRef.collection(sub));
    } catch (error) {
      functions.logger.error('firestore subcollection cleanup failed', {
        uid,
        subcollection: sub,
        error: serialiseError(error),
      });
    }
  }

  try {
    await userDocRef.delete();
  } catch (error) {
    functions.logger.error('firestore user doc cleanup failed', {
      uid,
      error: serialiseError(error),
    });
  }
}

function serialiseError(error: unknown): unknown {
  return error instanceof Error
    ? { name: error.name, message: error.message, stack: error.stack }
    : error;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd functions && npm test -- --testPathPattern=firestore`
Expected: PASS, 4 tests.

- [ ] **Step 5: Commit**

```bash
git add functions/src/cleanup/firestore.ts functions/src/__tests__/firestore.test.ts
git commit -m "feat(functions): firestore cleanup with allow-list + per-step error logging"
```

---

### Task 5: Firestore drift-warning for unexpected subcollections (TDD)

**Files:**
- Modify: `functions/src/cleanup/firestore.ts`
- Modify: `functions/src/__tests__/firestore.test.ts`

- [ ] **Step 1: Add the failing tests**

Append to `functions/src/__tests__/firestore.test.ts` inside the same `describe('deleteUserFirestoreData', ...)` block:

```typescript
  it('warns on unexpected subcollection names but does NOT auto-delete them', async () => {
    const recursiveDelete = jest.fn().mockResolvedValue(undefined);
    const listCollections = jest
      .fn()
      .mockResolvedValue([{ id: 'customers' }, { id: 'invoices' }, { id: 'orders' }]);
    const { db } = makeDbMock({ recursiveDelete, listCollections });
    const warnSpy = jest
      .spyOn(require('firebase-functions/v1').logger, 'warn')
      .mockImplementation(() => undefined);

    await deleteUserFirestoreData(uid, db as never);

    // Allow-listed names not warned about
    for (const sub of ['customers', 'orders']) {
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

    warnSpy.mockRestore();
  });

  it('logs an error when listCollections itself fails, but still attempts cleanup', async () => {
    const listCollections = jest.fn().mockRejectedValue(new Error('list-boom'));
    const recursiveDelete = jest.fn().mockResolvedValue(undefined);
    const { db } = makeDbMock({ listCollections, recursiveDelete });
    const errorSpy = jest
      .spyOn(require('firebase-functions/v1').logger, 'error')
      .mockImplementation(() => undefined);

    await deleteUserFirestoreData(uid, db as never);

    expect(errorSpy).toHaveBeenCalledWith(
      'subcollection drift check failed',
      expect.objectContaining({ uid }),
    );
    expect(recursiveDelete).toHaveBeenCalledTimes(ALLOWED_SUBCOLLECTIONS.length);

    errorSpy.mockRestore();
  });
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd functions && npm test -- --testPathPattern=firestore`
Expected: FAIL on the new cases (warn was never called).

- [ ] **Step 3: Update the implementation**

Edit `functions/src/cleanup/firestore.ts` — replace the function body with:

```typescript
export async function deleteUserFirestoreData(
  uid: string,
  db: Firestore,
): Promise<void> {
  const userDocRef = db.collection('users').doc(uid);

  // Drift warning: surface subcollections not in the allow-list, but do NOT
  // auto-delete them. An accidental write under users/<uid>/<unexpected>/
  // should never silently nuke data; the operator must consciously update
  // the allow-list (or clean up the bad write).
  try {
    const found = await userDocRef.listCollections();
    for (const sub of found) {
      if (
        !ALLOWED_SUBCOLLECTIONS.includes(
          sub.id as typeof ALLOWED_SUBCOLLECTIONS[number],
        )
      ) {
        functions.logger.warn(
          'unexpected subcollection found under users/{uid}; not cleaned up',
          {
            uid,
            unexpectedSubcollection: sub.id,
            hint: 'update onAuthUserDeleted allow-list',
          },
        );
      }
    }
  } catch (error) {
    functions.logger.error('subcollection drift check failed', {
      uid,
      error: serialiseError(error),
    });
  }

  for (const sub of ALLOWED_SUBCOLLECTIONS) {
    try {
      await db.recursiveDelete(userDocRef.collection(sub));
    } catch (error) {
      functions.logger.error('firestore subcollection cleanup failed', {
        uid,
        subcollection: sub,
        error: serialiseError(error),
      });
    }
  }

  try {
    await userDocRef.delete();
  } catch (error) {
    functions.logger.error('firestore user doc cleanup failed', {
      uid,
      error: serialiseError(error),
    });
  }
}
```

- [ ] **Step 4: Run tests to verify all pass**

Run: `cd functions && npm test -- --testPathPattern=firestore`
Expected: PASS, 6 tests total.

- [ ] **Step 5: Commit**

```bash
git add functions/src/cleanup/firestore.ts functions/src/__tests__/firestore.test.ts
git commit -m "feat(functions): drift-warning for unexpected users/{uid}/ subcollections"
```

---

### Task 6: Index trigger entry (TDD)

**Files:**
- Create: `functions/src/index.ts`
- Create: `functions/src/__tests__/index.test.ts`

- [ ] **Step 1: Write the failing test**

`functions/src/__tests__/index.test.ts`:

```typescript
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd functions && npm test -- --testPathPattern=index`
Expected: FAIL with "Cannot find module '../index'".

- [ ] **Step 3: Write the implementation**

`functions/src/index.ts`:

```typescript
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd functions && npm test -- --testPathPattern=index`
Expected: PASS, 2 tests.

- [ ] **Step 5: Run the full test suite, lint, and build to make sure nothing else broke**

Run: `cd functions && npm test && npm run lint && npm run build`
Expected: all green. 8 tests pass overall. `lib/index.js` exists.

- [ ] **Step 6: Commit**

```bash
git add functions/src/index.ts functions/src/__tests__/index.test.ts
git commit -m "feat(functions): onAuthUserDeleted trigger entry — parallel cleanup branches"
```

---

### Task 7: CI integration — add functions-tests job

**Files:**
- Modify: `.github/workflows/ci.yml`

- [ ] **Step 1: Read existing CI workflow**

Open `.github/workflows/ci.yml` and confirm it follows the standard `jobs:` pattern with `secrets-scan`, `detekt`, `build-android`, `build-ios`, `Unit Tests` jobs. Note the node-version or actions/setup-node usage if present.

- [ ] **Step 2: Add the `functions-tests` job**

Append the following job under `jobs:` (use the same indentation as existing jobs; do not modify any other job):

```yaml
  functions-tests:
    name: functions-tests
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: functions
    steps:
      - uses: actions/checkout@v6
      - uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: 'npm'
          cache-dependency-path: functions/package-lock.json
      - run: npm ci
      - run: npm run lint
      - run: npm run build
      - run: npm test
```

- [ ] **Step 3: Verify the workflow is valid YAML and references real actions**

Run: `cd /Users/danzucker/Desktop/Project/StitchPad/.claude/worktrees/cleanup-function && python3 -c "import yaml; yaml.safe_load(open('.github/workflows/ci.yml'))"`
Expected: no output (means YAML parsed without error).

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "ci(functions): run functions/ tests, lint, and build in GitHub Actions"
```

---

### Task 8: Deployment runbook

**Files:**
- Create: `docs/auth/firebase-functions.md`

- [ ] **Step 1: Create the runbook**

```markdown
# Firebase Functions — deployment + operations runbook

This project has one Cloud Function as of 2026-05-12: `onAuthUserDeleted` (Gen 1, region `europe-west1`). Cleans up Firestore + Cloud Storage data after Firebase Auth account deletion.

## Prerequisites

- Node 20+ locally (`node --version`)
- Firebase CLI installed (`npm install -g firebase-tools`)
- Logged into the Firebase project: `firebase login` (one-time)

## Deploy

From repo root:

\```bash
cd functions
npm ci
npm run lint && npm run build && npm test
cd ..
firebase deploy --only functions:onAuthUserDeleted
\```

Deploy takes ~60-90 seconds. On success you'll see:

\```
✔  functions[onAuthUserDeleted(europe-west1)] Successful update operation.
Function URL: <not applicable — auth trigger>
\```

## Verify deployment

\```bash
firebase functions:list
\```

Should show `onAuthUserDeleted` with trigger type `providers/firebase.auth/eventTypes/user.delete` and region `europe-west1`.

In Firebase Console: Functions tab → `onAuthUserDeleted` → "Logs" subtab. Empty initially.

## Smoke test the deployed function

1. Firebase Console → Authentication → Users → "Add user", e.g., `smoke-test@example.com`
2. Note the new UID
3. Firestore Console: manually create `users/{uid}` doc with `{ businessName: "Smoke Test" }`, plus a fake doc in `users/{uid}/customers/test`
4. Cloud Storage Console: upload any file to `users/{uid}/orders/x/fabrics/test.jpg`
5. Authentication → Users → delete the smoke-test user
6. Wait 10-30 seconds, refresh Logs tab → should see two `INFO` entries (`cleanup starting`, `cleanup completed`) with the uid
7. Verify in Firestore Console: `users/{uid}` and subcollections gone
8. Verify in Storage Console: `users/{uid}/` prefix empty

## View logs

\```bash
firebase functions:log --only onAuthUserDeleted
\```

Or in Cloud Logging: filter by `resource.labels.function_name = "onAuthUserDeleted"`.

## Rollback

There's no automatic rollback. To revert:

\```bash
git revert <commit>
cd functions && npm ci && npm run build
cd .. && firebase deploy --only functions:onAuthUserDeleted
\```

If you need the function to stop running NOW (faster than a redeploy):

\```bash
firebase functions:delete onAuthUserDeleted --region europe-west1
\```

Then redeploy when fixed.

## Adding a new subcollection under `users/{uid}/`

If you add a new feature that introduces a subcollection (e.g., `invoices`), **you must update `functions/src/cleanup/firestore.ts`** — add the new name to `ALLOWED_SUBCOLLECTIONS`. If you forget, the function logs a `WARN` ("unexpected subcollection found") the first time a real user with that subcollection is deleted; the data is left orphaned until you fix the allow-list and redeploy.
\```
```

(Replace the escaped backticks `\``` with real triple backticks when writing the file — the escapes above are only because the plan itself is a code-fenced markdown block.)

- [ ] **Step 2: Commit**

```bash
git add docs/auth/firebase-functions.md
git commit -m "docs(auth): deployment + operations runbook for firebase-functions"
```

---

### Task 9: Final local verification before pushing

- [ ] **Step 1: Run the full local CI gauntlet**

Run: `cd functions && npm ci && npm run lint && npm run build && npm test && cd ..`
Expected: all green. 8 unit tests pass, `lib/index.js` exists, lint clean.

- [ ] **Step 2: Verify the existing Kotlin gauntlet still passes (nothing changed but defensively check)**

Run: `./gradlew :composeApp:testDebugUnitTest :composeApp:compileKotlinIosSimulatorArm64 detekt`
Expected: all green.

- [ ] **Step 3: Push the branch and open the PR**

```bash
git push -u origin feature/auth-cleanup-function
gh pr create --title "feat(auth): onAuthUserDeleted Cloud Function for orphan cleanup" --body "$(cat <<'EOF'
## Summary

First Cloud Function in the project. Server-side cleanup of orphan Firestore + Cloud Storage data when a Firebase Auth user is deleted. Triggered by `auth.user().onDelete` (Gen 1 — the only generation that supports this trigger).

- Recursive delete of `users/{uid}/{customers,orders,measurements,styles,goals}` via Admin SDK `recursiveDelete`
- Recursive delete of Cloud Storage prefix `users/{uid}/`
- Drift-warning: any subcollection under `users/{uid}/` that is NOT in the allow-list is logged (WARN) but NOT auto-deleted
- Best-effort per branch — failures logged structurally to Cloud Logging, function never throws (no auto-retry)
- Jest unit tests with mocked firebase-admin; new `functions-tests` CI job

Design spec: `docs/superpowers/specs/2026-05-12-onauth-userdeleted-design.md`
Plan: `docs/superpowers/plans/2026-05-12-onauth-userdeleted.md`

## What this replaces

Phase 3 (PR #37) shipped client-side `userRepository.deleteUserDoc(uid)` as a best-effort cleanup. That only deletes the user doc (not subcollections) and depends on the device staying connected. PR #35 (Settings redesign) removes the client-side call; this function takes over. Once both PRs are merged + this function is deployed, the deletion path is server-driven and complete.

## Deferred to follow-up PRs

- CI auto-deploy of functions (V1 is manual `firebase deploy`)
- Firebase Local Emulator Suite integration tests (Jest unit tests + manual smoke only for V1)
- Retroactive cleanup of orphan data from accounts deleted before this function existed (ad-hoc admin script if/when needed)
- Account-data export (GDPR Article 20)

## Test plan

- [x] Unit tests: `cd functions && npm test` — 8 tests pass locally
- [x] Lint + build: `npm run lint && npm run build`
- [x] New CI job `functions-tests` runs the above on PR
- [ ] Manual smoke after merge: follow `docs/auth/firebase-functions.md` "Smoke test the deployed function" checklist:
  1. Create Auth user via Console
  2. Seed `users/{uid}` Firestore doc + `users/{uid}/customers/test` doc + `users/{uid}/orders/x/fabrics/test.jpg` Storage object
  3. Delete the Auth user
  4. Wait ~30s, verify Cloud Logging shows `cleanup starting` + `cleanup completed`
  5. Verify Firestore Console: `users/{uid}` and subcollections gone
  6. Verify Storage Console: `users/{uid}/` prefix empty

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 4: Verify CI passes on the PR**

Wait for the new `functions-tests` job to land green alongside the existing 5 Kotlin checks.

- [ ] **Step 5: Manual smoke after merge (NOT part of this PR)**

After merging the PR, follow the runbook's "Deploy" + "Smoke test" sections. Verify the function actually runs end-to-end against real Firebase. If the smoke fails, the fix is a follow-up PR (this PR ships the code; the function isn't live until you `firebase deploy`).

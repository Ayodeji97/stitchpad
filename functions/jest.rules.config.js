/**
 * Jest config for Firestore + Storage security-rules tests
 * (firestore.rules.test.ts, storage.rules.test.ts).
 *
 * These run against the Firestore + Storage emulators (started by
 * `npm run test:rules`, which wraps this config in `firebase emulators:exec
 * --only firestore,storage`). Kept separate from the default unit-test config
 * because they need the emulators and a longer timeout.
 *
 * maxWorkers: 1 — every *.rules.test.ts file's testEnv targets the same
 * "demo-stitchpad" emulator project, and each beforeEach calls
 * clearFirestore()/clearStorage(). Jest's default parallel workers would run
 * the two files in separate processes against that one shared project,
 * racing each other's clears/writes (observed as flaky failures in unrelated
 * tests). Forcing a single worker makes test files run sequentially instead.
 *
 * @type {import('jest').Config}
 */
module.exports = {
  preset: 'ts-jest',
  testEnvironment: 'node',
  roots: ['<rootDir>/src'],
  testMatch: ['**/__tests__/**/*.rules.test.ts'],
  testTimeout: 20000,
  clearMocks: true,
  restoreMocks: true,
  maxWorkers: 1,
};

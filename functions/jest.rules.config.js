/**
 * Jest config for Firestore security-rules tests.
 *
 * These run against the Firestore emulator (started by `npm run test:rules`,
 * which wraps this config in `firebase emulators:exec`). Kept separate from the
 * default unit-test config because they need the emulator and a longer timeout.
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
};

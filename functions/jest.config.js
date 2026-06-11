/** @type {import('jest').Config} */
module.exports = {
  preset: 'ts-jest',
  testEnvironment: 'node',
  roots: ['<rootDir>/src'],
  testMatch: ['**/__tests__/**/*.test.ts'],
  // Firestore rules tests need a running emulator; they run via the separate
  // `npm run test:rules` script (jest.rules.config.js), not the default suite.
  testPathIgnorePatterns: ['/node_modules/', '\\.rules\\.test\\.ts$'],
  collectCoverageFrom: ['src/**/*.ts', '!src/__tests__/**/*'],
  clearMocks: true,
  restoreMocks: true,
};

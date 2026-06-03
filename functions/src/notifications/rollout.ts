/**
 * Staged rollout gate. While STAGING is true the digest only sends to the
 * allowlisted test accounts — a single detector bug must not email every
 * tester a wrong digest. Flip STAGING to false (one line) to open to all users
 * once verified against real mornings. See the design spec "Rollout" section.
 */
const STAGING = true;

// Test-account emails (lower-cased). Replace/extend with the real tester emails.
export const DIGEST_ALLOWLIST: string[] = [
  'fola.tailor@getstitchpad.com',
  'gabby.tailor@getstitchpad.com',
];

export function isDigestAllowed(_uid: string, email: string): boolean {
  if (!STAGING) return true;
  return DIGEST_ALLOWLIST.includes(email.trim().toLowerCase());
}

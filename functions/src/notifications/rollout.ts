/**
 * Staged rollout gate. While STAGING is true the digest only sends to the
 * allowlisted test accounts — a single detector bug must not email every
 * tester a wrong digest. Flip STAGING to false (one line) to open to all users
 * once verified against real mornings. See the design spec "Rollout" section.
 */
const STAGING = true;

// Test-account emails (lower-cased). Daniel's Gmail +aliases deliver to his
// inbox while being distinct Firebase Auth addresses — so digests are visible
// during staging. Replace/extend with the real tester emails before the
// STAGING=false flip to broad rollout.
export const DIGEST_ALLOWLIST: string[] = [
  'danielayodeji97+fola@gmail.com',
  'danielayodeji97+gabby@gmail.com',
];

export function isDigestAllowed(_uid: string, email: string): boolean {
  if (!STAGING) return true;
  return DIGEST_ALLOWLIST.includes(email.trim().toLowerCase());
}

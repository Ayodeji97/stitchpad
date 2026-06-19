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
  // Internal (Daniel + aliases)
  'danielayodeji97@gmail.com',
  'danielayodeji97+fola@gmail.com',
  'danielayodeji97+gabby@gmail.com',
  'fola@gmail.com',
  // Tailor testers
  'clementsola98@gmail.com',
  'bamigbadeoluwatobi@gmail.com',
  'farombiopeyemi@gmail.com',
  'oluwayemisifaith646@gmail.com',
  'ariyoabigailf@gmail.com',
  'augustinahtoyin5957@gmail.com',
  'vickiejummy@gmail.com',
  'vejummy@gmail.com',
  'vejimmy@gmail.com',
  'veejumy@gmail.com',
  'olaideruthadeleleke@gmail.com',
  'penelopeademi@gmail.com',
  'owodunnideborah@gmail.com',
  'bettyagent9@gmail.com',
  'jerrysanmi@gmail.com',
  'oladehinayo@gmail.com',
  'preciousfaith183@gmail.com',
  'adeolawa278@gmail.com',
  'dorcassylvester2015@gmail.com',
  'rebeccaadebayo54@gmail.com',
  'jeromekajo2002@gmail.com',
  'moempire1131@gmail.com',
  'moronkeoshanimi1@gmail.com',
  'onyinyechinwamere@gmail.com',
  'raphaeleunice42@gmail.com',
  'estherdolapo2017@gmail.com',
  'timzyfashionempire@gmail.com',
  'talk2dummi@gmail.com',
  'liliaceous4luv@gmail.com',
  'samuelonigiobi47@gmail.com',
  'prestigewhinnydesigns@gmail.com',
];

/** Raw allowlist membership — independent of STAGING. Used to gate the debug callable. */
export function isDigestTester(email: string): boolean {
  return DIGEST_ALLOWLIST.includes(email.trim().toLowerCase());
}

export function isDigestAllowed(_uid: string, email: string): boolean {
  if (!STAGING) return true;
  return isDigestTester(email);
}

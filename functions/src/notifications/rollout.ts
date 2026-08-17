/**
 * Staged rollout gate for the DAILY DIGEST (email + push) and the
 * order-collect push. Opened to all users on 2026-08-17 after the digest ran
 * against real Lagos mornings for the allowlist below since the 2026-08-10
 * smoke PASS.
 *
 * Typed as `boolean` rather than letting TS narrow to the literal `false`, so
 * both branches stay reachable and flipping this back is a genuine one-liner.
 *
 * NOTE: this gates `sendEmail` too (see runDailyDigest.ts) — not just push.
 */
const STAGING: boolean = false;

/**
 * Independent gate for the ENGAGEMENT PUSH (see engagementPush.ts). Deliberately
 * separate from STAGING: the digest is proven and open to everyone, while the
 * brand-new twice-weekly nudge stays on the allowlist until it has been watched
 * through a couple of real Tuesdays. Two features, two blast radii, two flips.
 */
const ENGAGEMENT_STAGING: boolean = true;

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

/**
 * Engagement-push gate. Same allowlist, its own staging flag — so opening the
 * digest to everyone does not also open the nudge.
 */
export function isEngagementAllowed(_uid: string, email: string): boolean {
  if (!ENGAGEMENT_STAGING) return true;
  return isDigestTester(email);
}

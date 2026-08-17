/**
 * `{{variable}}` substitution for engagement-push copy.
 *
 * Personal copy outperforms generic copy, but it is also the easiest way to send
 * something embarrassing — a literal `{{bussinessName}}` on a lock screen, or a
 * confident number that happens to be wrong. Two rules follow:
 *
 *   1. Unknown variables are caught at PARSE time, not send time. A campaign whose
 *      copy references a variable we cannot fill is dropped by parseEngagementConfig
 *      with a logged reason, exactly like a missing title — so a typo silences one
 *      campaign in the console instead of reaching real users.
 *   2. Every known variable always resolves to something printable. `points` is 0
 *      for a tailor who never minted a referral link; `businessName` falls back
 *      through displayName to a neutral word. Rendering never leaves a hole.
 */

/** Variables campaign copy may reference. Keep the runbook's field table in step. */
export const TEMPLATE_VARIABLES = [
  'businessName',
  'points',
  'customerCount',
  'orderCount',
] as const;

export type TemplateVariable = (typeof TEMPLATE_VARIABLES)[number];

export interface TemplateVars {
  /** businessName || displayName || 'Tailor' — never blank. */
  businessName: string;
  /** Founding Tailors points this month. 0 when the tailor has no referral link. */
  points: number;
  customerCount: number;
  orderCount: number;
}

/** Matches `{{name}}` with optional inner whitespace. */
const PLACEHOLDER = /\{\{\s*([A-Za-z0-9_]+)\s*\}\}/g;

/**
 * Every distinct placeholder name used in `text`, in order of first appearance.
 * Exported for the config parser's validation.
 */
export function templateVariablesIn(text: string): string[] {
  const found: string[] = [];
  for (const m of text.matchAll(PLACEHOLDER)) {
    if (!found.includes(m[1])) found.push(m[1]);
  }
  return found;
}

/** Placeholder names in `text` that we cannot fill. Empty means the copy is safe. */
export function unknownTemplateVariables(text: string): string[] {
  return templateVariablesIn(text)
    .filter((name) => !(TEMPLATE_VARIABLES as readonly string[]).includes(name));
}

/**
 * Fills every `{{variable}}` in `text`.
 *
 * Unknown names are left untouched rather than blanked — but they cannot reach here,
 * because the parser drops such campaigns first. Leaving them visible in the unit
 * tests makes a regression in that guarantee obvious rather than silent.
 */
export function renderTemplate(text: string, vars: TemplateVars): string {
  return text.replace(PLACEHOLDER, (whole, name: string) => {
    switch (name) {
      case 'businessName': return vars.businessName;
      case 'points': return String(vars.points);
      case 'customerCount': return String(vars.customerCount);
      case 'orderCount': return String(vars.orderCount);
      default: return whole;
    }
  });
}

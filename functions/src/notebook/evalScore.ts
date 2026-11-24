import type { ExtractedPerson } from './types';

export interface EvalScore {
  namesCorrect: number;
  namesTotal: number;
  valuesCorrect: number;
  valuesTotal: number;
  /** Extracted values on matched people that are wrong (differ from truth or have no truth counterpart). */
  wrongValues: number;
  /** Of wrongValues, how many the tailor would be shown as needing a look (value flagged or person needsReview). */
  wrongFlagged: number;
  /** Truth values absent from the matched extracted person. Never flagged. */
  missingValues: number;
  /** Truth people with no matched extracted person. */
  unmatchedPeople: number;
  /** Truth values belonging to unmatched people. Never flagged. */
  unmatchedPeopleValues: number;
  /** Extracted people with no truth match. Informational, not part of the bars. */
  inventedPeople: number;
}

/** Every error the tailor could end up with: wrong + missing + lost-person values. */
export function errorsTotal(s: EvalScore): number {
  return s.wrongValues + s.missingValues + s.unmatchedPeopleValues;
}

export function normName(s: string): string {
  return s
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/\s+/g, ' ')
    .trim();
}

function normValue(s: string): string {
  return s.replace(/\s+/g, ' ').trim();
}

function valueKey(v: { fieldKey: string | null; label: string }): string {
  return v.fieldKey ?? `label:${normName(v.label)}`;
}

export function score(truth: ExtractedPerson[], got: ExtractedPerson[]): EvalScore {
  const result: EvalScore = {
    namesCorrect: 0,
    namesTotal: truth.length,
    valuesCorrect: 0,
    valuesTotal: 0,
    wrongValues: 0,
    wrongFlagged: 0,
    missingValues: 0,
    unmatchedPeople: 0,
    unmatchedPeopleValues: 0,
    inventedPeople: 0,
  };
  const usedPeople = new Set<number>();
  for (const t of truth) {
    result.valuesTotal += t.values.length;
    const gi = got.findIndex((g, i) => !usedPeople.has(i) && normName(g.name) === normName(t.name));
    if (gi < 0) {
      result.unmatchedPeople += 1;
      result.unmatchedPeopleValues += t.values.length;
      continue;
    }
    usedPeople.add(gi);
    const g = got[gi];
    result.namesCorrect += 1;

    const usedGot = new Set<number>();
    const pendingTruth: number[] = [];
    // Pass 1: exact (key + value) one-to-one.
    t.values.forEach((tv, ti) => {
      const m = g.values.findIndex(
        (gv, i) => !usedGot.has(i) && valueKey(gv) === valueKey(tv) && normValue(gv.value) === normValue(tv.value),
      );
      if (m >= 0) {
        usedGot.add(m);
        result.valuesCorrect += 1;
      } else {
        pendingTruth.push(ti);
      }
    });
    // Pass 2: same key, different value -> a wrong extracted value.
    const missing: number[] = [];
    for (const ti of pendingTruth) {
      const m = g.values.findIndex((gv, i) => !usedGot.has(i) && valueKey(gv) === valueKey(t.values[ti]));
      if (m >= 0) {
        usedGot.add(m);
        result.wrongValues += 1;
        if (g.values[m].flagged || g.needsReview) result.wrongFlagged += 1;
      } else {
        missing.push(ti);
      }
    }
    result.missingValues += missing.length;
    // Extra extracted values with no truth counterpart are wrong too.
    g.values.forEach((gv, i) => {
      if (usedGot.has(i)) return;
      result.wrongValues += 1;
      if (gv.flagged || g.needsReview) result.wrongFlagged += 1;
    });
  }
  result.inventedPeople = got.length - usedPeople.size;
  return result;
}

/** Score for a page whose extraction failed: everything in the truth is a lost, unflagged error. */
export function scoreFailedPage(truth: ExtractedPerson[]): EvalScore {
  return score(truth, []);
}

export function addScores(a: EvalScore, b: EvalScore): EvalScore {
  const out = { ...a };
  for (const k of Object.keys(b) as (keyof EvalScore)[]) out[k] = a[k] + b[k];
  return out;
}

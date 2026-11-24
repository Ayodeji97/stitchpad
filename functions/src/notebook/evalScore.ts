import type { ExtractedPerson } from './types';

export interface EvalScore {
  namesCorrect: number;
  namesTotal: number;
  valuesCorrect: number;
  valuesTotal: number;
  /** Extracted values (on matched people) that disagree with the truth. */
  wrongValues: number;
  /** Of wrongValues, how many the model flagged for review. */
  wrongFlagged: number;
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
  const gotByName = new Map<string, ExtractedPerson>();
  for (const p of got) {
    const k = normName(p.name);
    if (!gotByName.has(k)) gotByName.set(k, p);
  }
  const result: EvalScore = {
    namesCorrect: 0,
    namesTotal: truth.length,
    valuesCorrect: 0,
    valuesTotal: 0,
    wrongValues: 0,
    wrongFlagged: 0,
  };
  for (const t of truth) {
    result.valuesTotal += t.values.length;
    const g = gotByName.get(normName(t.name));
    if (!g) continue;
    result.namesCorrect += 1;
    const truthByKey = new Map(t.values.map((v) => [valueKey(v), normValue(v.value)]));
    const gotKeys = new Set<string>();
    for (const gv of g.values) {
      const key = valueKey(gv);
      const expected = truthByKey.get(key);
      const ok = expected !== undefined && expected === normValue(gv.value) && !gotKeys.has(key);
      gotKeys.add(key);
      if (ok) {
        result.valuesCorrect += 1;
      } else {
        result.wrongValues += 1;
        if (gv.flagged) result.wrongFlagged += 1;
      }
    }
  }
  return result;
}

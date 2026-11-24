import { Box, ExtractedPerson, ExtractedValue, Gender, Unit } from './types';

export class ExtractionParseError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'ExtractionParseError';
  }
}

const RANGES: Record<Unit, [number, number]> = {
  inches: [3, 90],
  cm: [8, 230],
  unknown: [3, 230],
};
const FRACTIONS: Record<string, number> = { '½': 0.5, '¼': 0.25, '¾': 0.75 };

export function isPlausible(value: string, unit: Unit): boolean {
  let s = value.trim();
  if (!s) return false;
  s = s
    .replace(/(\d)\s*([½¼¾])/g, (_, n: string, f: string) => String(Number(n) + FRACTIONS[f]))
    .replace(/(\d+)\s+1\/2(?!\d)/g, (_, n: string) => String(Number(n) + 0.5));
  const parts = s.split(/[/,\s]+/).filter((p) => p.length > 0);
  if (parts.length < 1 || parts.length > 4) return false;
  const [min, max] = RANGES[unit];
  return parts.every((p) => {
    if (!/^\d+(\.\d+)?$/.test(p)) return false;
    const n = Number(p);
    return n >= min && n <= max;
  });
}

const fail = (msg: string): never => {
  throw new ExtractionParseError(msg);
};

const isObj = (v: unknown): v is Record<string, unknown> =>
  typeof v === 'object' && v !== null && !Array.isArray(v);

const str = (v: unknown, field: string): string =>
  typeof v === 'string' ? v.trim() : fail(`${field} must be a string`);

const strOrNull = (v: unknown, field: string): string | null => {
  if (v === null || v === undefined) return null;
  const s = str(v, field);
  return s === '' ? null : s;
};

function parseBox(v: unknown): Box | null {
  if (v === null || v === undefined) return null;
  if (!isObj(v)) return fail('box must be an object');
  const { x, y, w, h } = v;
  if ([x, y, w, h].some((n) => typeof n !== 'number' || !Number.isFinite(n))) {
    return fail('box fields must be numbers');
  }
  return { x: x as number, y: y as number, w: w as number, h: h as number };
}

function parseValue(v: unknown, unit: Unit): ExtractedValue {
  if (!isObj(v)) return fail('value must be an object');
  const value = str(v.value, 'value');
  return {
    label: str(v.label, 'label'),
    value,
    fieldKey: strOrNull(v.fieldKey, 'fieldKey'),
    flagged: v.flagged === true || !isPlausible(value, unit),
  };
}

function parsePerson(p: unknown): ExtractedPerson {
  if (!isObj(p)) return fail('person must be an object');
  const name = str(p.name, 'name');
  const gender = (p.gender ?? 'unknown') as Gender;
  const unit = (p.unit ?? 'unknown') as Unit;
  if (!['female', 'male', 'unknown'].includes(gender)) return fail('bad gender');
  if (!['inches', 'cm', 'unknown'].includes(unit)) return fail('bad unit');
  if (p.values !== undefined && !Array.isArray(p.values)) return fail('values must be an array');
  const values = ((p.values as unknown[] | undefined) ?? []).map((v) => parseValue(v, unit));
  return {
    name,
    phone: strOrNull(p.phone, 'phone'),
    gender,
    unit,
    dateWritten: strOrNull(p.dateWritten, 'dateWritten'),
    values,
    box: parseBox(p.box),
    needsReview:
      gender === 'unknown' || unit === 'unknown' || values.some((v) => v.flagged) || name.length < 2,
  };
}

/** Never include the raw text in errors: it holds customer names and measurements. */
export function parseExtraction(raw: string): ExtractedPerson[] {
  let json: unknown;
  try {
    json = JSON.parse(raw);
  } catch {
    throw new ExtractionParseError('response is not valid JSON');
  }
  if (!Array.isArray(json)) throw new ExtractionParseError('response is not an array');
  return json
    .map(parsePerson)
    .filter((p) => p.name !== '' || p.values.length > 0);
}

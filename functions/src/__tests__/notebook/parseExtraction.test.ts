import * as fs from 'fs';
import * as path from 'path';
import { ExtractionParseError, isPlausible, parseExtraction } from '../../notebook/parseExtraction';
import { FIELD_CATALOG } from '../../notebook/fieldCatalog';

const person = (over: Record<string, unknown> = {}) => ({
  name: 'Ada',
  phone: '08012345678',
  gender: 'female',
  unit: 'inches',
  dateWritten: '2026-03-02',
  values: [{ label: 'Wst', value: '30', fieldKey: 'waist', flagged: false }],
  box: { x: 0.1, y: 0.2, w: 0.5, h: 0.3 },
  ...over,
});

describe('parseExtraction', () => {
  it('parse_validArray', () => {
    const raw = JSON.stringify([
      person(),
      person({ name: ' Bola ', phone: null, gender: 'male', box: null, dateWritten: null }),
    ]);
    const out = parseExtraction(raw);
    expect(out).toHaveLength(2);
    expect(out[0]).toEqual({
      name: 'Ada', phone: '08012345678', gender: 'female', unit: 'inches',
      dateWritten: '2026-03-02',
      values: [{ label: 'Wst', value: '30', fieldKey: 'waist', flagged: false }],
      box: { x: 0.1, y: 0.2, w: 0.5, h: 0.3 }, needsReview: false,
    });
    expect(out[1].name).toBe('Bola');
    expect(out[1].phone).toBeNull();
    expect(out[1].box).toBeNull();
  });

  it('parse_rejectsMalformed', () => {
    for (const raw of ['not json', '{}', '[{"name":5}]']) {
      expect(() => parseExtraction(raw)).toThrow(ExtractionParseError);
    }
  });

  it('parse_emptyArray_returnsEmpty', () => {
    expect(parseExtraction('[]')).toEqual([]);
  });

  it('parse_dropsBlankPeople', () => {
    const raw = JSON.stringify([person({ name: '  ', values: [] }), person({ name: 'Keep' })]);
    const out = parseExtraction(raw);
    expect(out.map((p) => p.name)).toEqual(['Keep']);
  });

  it('needsReview_unknownGender', () => {
    const [p] = parseExtraction(JSON.stringify([person({ gender: 'unknown' })]));
    expect(p.needsReview).toBe(true);
  });

  it('needsReview_flaggedValue', () => {
    const modelFlag = person({ values: [{ label: 'W', value: '30', fieldKey: 'waist', flagged: true }] });
    const implausible = person({ values: [{ label: 'W', value: '400', fieldKey: 'waist', flagged: false }] });
    const [a, b] = parseExtraction(JSON.stringify([modelFlag, implausible]));
    expect(a.values[0].flagged).toBe(true);
    expect(a.needsReview).toBe(true);
    expect(b.values[0].flagged).toBe(true);
    expect(b.needsReview).toBe(true);
  });

  it('needsReview_falseWhenClean', () => {
    const [p] = parseExtraction(JSON.stringify([person()]));
    expect(p.needsReview).toBe(false);
  });
});

describe('isPlausible', () => {
  it('plausibility_acceptsFractionsAndSegments', () => {
    for (const v of ['14½', '14 1/2', '40/38', '16.5', '40, 45, 56']) {
      expect(isPlausible(v, 'inches')).toBe(true);
    }
  });

  it('plausibility_rejects', () => {
    expect(isPlausible('400', 'inches')).toBe(false);
    expect(isPlausible('abc', 'inches')).toBe(false);
    expect(isPlausible('', 'inches')).toBe(false);
  });

  it('applies per-unit ranges', () => {
    expect(isPlausible('100', 'cm')).toBe(true);
    expect(isPlausible('5', 'cm')).toBe(false);
    expect(isPlausible('100', 'unknown')).toBe(true);
    expect(isPlausible('2', 'unknown')).toBe(false);
    expect(isPlausible('1 2 3 4 5', 'inches')).toBe(false);
  });
});

describe('FIELD_CATALOG', () => {
  it('catalog_keysExistInBodyProfileTemplate', () => {
    const file = path.resolve(
      __dirname,
      '../../../../composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/BodyProfileTemplate.kt',
    );
    const text = fs.readFileSync(file, 'utf8');
    for (const f of FIELD_CATALOG) {
      expect(text).toContain(`MeasurementField("${f.key}"`);
    }
  });
});

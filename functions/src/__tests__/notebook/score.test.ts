import { score } from '../../notebook/evalScore';
import type { ExtractedPerson, ExtractedValue } from '../../notebook/types';

function v(fieldKey: string, value: string, flagged = false): ExtractedValue {
  return { label: fieldKey, value, fieldKey, flagged };
}
function person(name: string, values: ExtractedValue[], needsReview = false): ExtractedPerson {
  return { name, phone: null, gender: 'unknown', unit: 'inches', dateWritten: null, values, box: null, needsReview };
}

describe('notebook eval score', () => {
  it('score_perfect', () => {
    const truth = [person('Ada Obi', [v('chest', '36'), v('waist', '30')])];
    const got = [person('ada  obi', [v('chest', ' 36'), v('waist', '30')])];
    expect(score(truth, got)).toEqual({
      namesCorrect: 1, namesTotal: 1, valuesCorrect: 2, valuesTotal: 2, wrongValues: 0, wrongFlagged: 0,
    });
  });

  it('score_wrongValueFlagged', () => {
    const truth = [person('Ọlá Béé', [v('chest', '36'), v('waist', '30')])];
    const got = [person('Ola Bee', [v('chest', '38', true), v('waist', '30')])];
    expect(score(truth, got)).toEqual({
      namesCorrect: 1, namesTotal: 1, valuesCorrect: 1, valuesTotal: 2, wrongValues: 1, wrongFlagged: 1,
    });
  });

  it('score_wrongValueUnflagged', () => {
    const truth = [person('Ada', [v('chest', '36')])];
    const got = [person('Ada', [v('chest', '38')])];
    const s = score(truth, got);
    expect(s.wrongValues).toBe(1);
    expect(s.wrongFlagged).toBe(0);
  });

  it('score_missingPerson', () => {
    const truth = [person('Ada', [v('chest', '36')]), person('Bola', [v('waist', '30'), v('hip', '40')])];
    const got = [person('Ada', [v('chest', '36')])];
    expect(score(truth, got)).toEqual({
      namesCorrect: 1, namesTotal: 2, valuesCorrect: 1, valuesTotal: 3, wrongValues: 0, wrongFlagged: 0,
    });
  });
});

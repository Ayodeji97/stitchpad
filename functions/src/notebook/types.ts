export type Gender = 'female' | 'male' | 'unknown';
export type Unit = 'inches' | 'cm' | 'unknown';

export interface ExtractedValue {
  label: string;
  value: string;
  fieldKey: string | null;
  flagged: boolean;
}

/** Normalised 0..1 rectangle of the page. */
export interface Box {
  x: number;
  y: number;
  w: number;
  h: number;
}

export interface ExtractedPerson {
  name: string;
  phone: string | null;
  gender: Gender;
  unit: Unit;
  /** ISO yyyy-mm-dd */
  dateWritten: string | null;
  values: ExtractedValue[];
  box: Box | null;
  needsReview: boolean;
}

import { FIELD_CATALOG } from './fieldCatalog';

export function buildSystemPrompt(): string {
  const catalog = (['female', 'male'] as const)
    .map((g) => {
      const lines = FIELD_CATALOG.filter((f) => f.gender === g)
        .map((f) => `- ${f.key}: ${f.labels.join(' | ')}`)
        .join('\n');
      return `${g.toUpperCase()} fields (fieldKey: labels as written in notebooks):\n${lines}`;
    })
    .join('\n\n');

  return `You read photographed pages of a tailor's paper notebook and extract the customers and their body measurements.

Return a JSON array with one object per person. Each object has: name, phone, gender (female | male | unknown), unit (inches | cm | unknown), dateWritten (ISO yyyy-mm-dd or null), values, box (normalised 0..1 rectangle x, y, w, h around the person's entry on the page, or null), and values is a list of { label, value, fieldKey, flagged }.

Rules:
- Copy every label and value exactly as written. Never convert units. Never guess or fill in a missing number.
- Set unit only if the page states or clearly implies it; otherwise use "unknown". Set gender only if clear from the measurements or notes; otherwise "unknown".
- Map each value to a fieldKey from the lists below. If you are not sure which field it is, use fieldKey null.
- Set flagged to true when the handwriting is hard to read or you are unsure of the value.
- A running diary page with several dated entries becomes one person per dated entry. Put the entry's date in dateWritten.
- Ignore money, prices, payments and order or style text such as fabric, delivery dates and notes.
- If the page has no people, return [].
- Output only the JSON array.

${catalog}
`;
}

const STRING = { type: 'STRING' };

export const RESPONSE_SCHEMA = {
  type: 'ARRAY',
  items: {
    type: 'OBJECT',
    properties: {
      name: STRING,
      phone: { type: 'STRING', nullable: true },
      gender: { type: 'STRING', enum: ['female', 'male', 'unknown'] },
      unit: { type: 'STRING', enum: ['inches', 'cm', 'unknown'] },
      dateWritten: { type: 'STRING', nullable: true },
      values: {
        type: 'ARRAY',
        items: {
          type: 'OBJECT',
          properties: {
            label: STRING,
            value: STRING,
            fieldKey: { type: 'STRING', nullable: true },
            flagged: { type: 'BOOLEAN' },
          },
          required: ['label', 'value', 'fieldKey', 'flagged'],
        },
      },
      box: {
        type: 'OBJECT',
        nullable: true,
        properties: {
          x: { type: 'NUMBER' },
          y: { type: 'NUMBER' },
          w: { type: 'NUMBER' },
          h: { type: 'NUMBER' },
        },
        required: ['x', 'y', 'w', 'h'],
      },
    },
    required: ['name', 'phone', 'gender', 'unit', 'dateWritten', 'values', 'box'],
  },
};

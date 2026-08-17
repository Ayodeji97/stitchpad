import {
  renderTemplate,
  templateVariablesIn,
  unknownTemplateVariables,
  TemplateVars,
} from '../../notifications/campaignTemplate';

const vars = (over: Partial<TemplateVars> = {}): TemplateVars => ({
  businessName: 'Apeke Couture',
  points: 12,
  customerCount: 7,
  orderCount: 23,
  ...over,
});

describe('templateVariablesIn', () => {
  it('finds each distinct placeholder once, in order', () => {
    expect(templateVariablesIn('{{businessName}}, you have {{points}} — {{points}}!'))
      .toEqual(['businessName', 'points']);
  });

  it('tolerates inner whitespace', () => {
    expect(templateVariablesIn('{{ businessName }}')).toEqual(['businessName']);
  });

  it('returns nothing for plain copy', () => {
    expect(templateVariablesIn('Know another tailor?')).toEqual([]);
  });
});

describe('unknownTemplateVariables', () => {
  it('accepts every documented variable', () => {
    expect(unknownTemplateVariables(
      '{{businessName}} {{points}} {{customerCount}} {{orderCount}}',
    )).toEqual([]);
  });

  // The whole point of parse-time validation: a typo must be caught in the console,
  // not rendered literally onto somebody's lock screen.
  it('reports a misspelling', () => {
    expect(unknownTemplateVariables('Hi {{bussinessName}}')).toEqual(['bussinessName']);
  });

  it('is case-sensitive', () => {
    expect(unknownTemplateVariables('{{BusinessName}}')).toEqual(['BusinessName']);
  });
});

describe('renderTemplate', () => {
  it('substitutes every known variable', () => {
    expect(renderTemplate('{{businessName}}, you are on {{points}} points', vars()))
      .toBe('Apeke Couture, you are on 12 points');
    expect(renderTemplate('{{customerCount}} customers, {{orderCount}} orders', vars()))
      .toBe('7 customers, 23 orders');
  });

  it('substitutes every occurrence, not just the first', () => {
    expect(renderTemplate('{{points}} and {{points}}', vars({ points: 3 })))
      .toBe('3 and 3');
  });

  it('handles whitespace inside the braces', () => {
    expect(renderTemplate('Hi {{ businessName }}', vars())).toBe('Hi Apeke Couture');
  });

  it('leaves plain copy untouched', () => {
    expect(renderTemplate('Know another tailor?', vars())).toBe('Know another tailor?');
  });

  // Zero is a real, printable answer for a tailor who never minted a referral link —
  // it must render as "0", not as blank or NaN.
  it('renders zero points rather than a hole', () => {
    expect(renderTemplate('You have {{points}} points', vars({ points: 0 })))
      .toBe('You have 0 points');
  });

  it('leaves an unknown placeholder visible rather than blanking it', () => {
    // Unreachable in production — the parser drops such campaigns — but if that
    // guarantee ever breaks, a visible {{typo}} is far easier to spot than an
    // empty gap in the copy.
    expect(renderTemplate('Hi {{whoIsThis}}', vars())).toBe('Hi {{whoIsThis}}');
  });
});

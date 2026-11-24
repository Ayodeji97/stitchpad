import { buildSystemPrompt, RESPONSE_SCHEMA } from '../../notebook/extractionPrompt';
import { FIELD_CATALOG } from '../../notebook/fieldCatalog';

describe('extraction prompt', () => {
  it('prompt_containsEveryCatalogKey', () => {
    const prompt = buildSystemPrompt();
    for (const f of FIELD_CATALOG) {
      expect(prompt).toContain(f.key);
    }
  });

  it('states the key rules and exposes a schema', () => {
    const prompt = buildSystemPrompt();
    expect(prompt).toMatch(/exactly as written/i);
    expect(prompt).toMatch(/never convert/i);
    expect(prompt).toMatch(/\[\]/);
    expect((RESPONSE_SCHEMA as { type: string }).type).toBe('ARRAY');
  });
});

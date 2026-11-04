/** Language preference only: never infer it from country, currency or IP. */
export function serverLanguage(locale: unknown): 'en' | 'fr' {
  return typeof locale === 'string' && /^fr(?:[-_][a-z0-9]+)*$/i.test(locale.trim()) ? 'fr' : 'en';
}

export function escapeEmailHtml(value: string): string {
  return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

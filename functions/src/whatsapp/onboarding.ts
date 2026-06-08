import { BotLanguage, ConversationDoc } from './types';

// TODO(founder): swap these for the canonical Termly URLs before launch.
const TERMS_URL = 'https://getstitchpad.com/terms';
const PRIVACY_URL = 'https://getstitchpad.com/privacy';

const TERMS_MESSAGE = [
  'Welcome to StitchPad support 👋',
  '',
  'Before we start, please reply YES to accept our Terms of Service and Privacy Policy.',
  '',
  `Terms: ${TERMS_URL}`,
  `Privacy: ${PRIVACY_URL}`,
].join('\n');

const LANGUAGE_PROMPT = [
  'Thanks! Which language would you prefer?',
  '',
  '1. English',
  '2. Pidgin',
  '',
  'Reply 1 or 2.',
].join('\n');

const WELCOME: Record<BotLanguage, string> = {
  en: 'Great — how can I help you with StitchPad today? Just type your question.',
  pcm: 'Sweet — wetin you wan make I help you with for StitchPad? Just type your question.',
};

export interface OnboardingResult {
  /** A message to send to the user, if onboarding needs one this turn. */
  reply?: string;
  /** Conversation-doc fields to persist (e.g. termsAccepted, language). */
  updates?: Partial<ConversationDoc>;
  /** True only when onboarding is complete and this message is a real question. */
  proceedToAnswer: boolean;
}

function isYes(text: string): boolean {
  return text.trim().toUpperCase() === 'YES';
}

function parseLanguage(text: string): BotLanguage | null {
  const t = text.trim().toLowerCase();
  if (t === '1' || t === 'english' || t === 'en') return 'en';
  if (t === '2' || t === 'pidgin' || t === 'pcm') return 'pcm';
  return null;
}

/**
 * Runs the first-contact gate: accept terms, then pick a language, then hand
 * off to AI answering. Pure — the caller persists `updates` and sends `reply`.
 */
export function handleOnboarding(conv: ConversationDoc, text: string): OnboardingResult {
  if (!conv.termsAccepted) {
    if (isYes(text)) {
      return { updates: { termsAccepted: true }, reply: LANGUAGE_PROMPT, proceedToAnswer: false };
    }
    return { reply: TERMS_MESSAGE, proceedToAnswer: false };
  }

  if (!conv.language) {
    const language = parseLanguage(text);
    if (language) {
      return { updates: { language }, reply: WELCOME[language], proceedToAnswer: false };
    }
    return { reply: LANGUAGE_PROMPT, proceedToAnswer: false };
  }

  return { proceedToAnswer: true };
}

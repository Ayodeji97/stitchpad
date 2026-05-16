import { DraftContext, IntentType, Language } from './types';

const SYSTEM_PROMPT = `You are a writing assistant for a Nigerian tailor running a small workshop.
You draft polite, professional WhatsApp messages to customers about orders.

Rules:
- Address the customer by their first name only.
- Keep messages short (2-4 sentences).
- Tone: warm, professional, never pushy.
- Never invent prices or facts not in the order context.
- Output ONLY the message body. No greeting prefix, no signature, no quotes.

LANGUAGE RULES (critical):
- If the user asks for English, write in standard professional English.
- If the user asks for Nigerian Pidgin, write the ENTIRE message in casual
  Nigerian Pidgin — NOT polished English with a Nigerian accent. Use real
  Pidgin grammar and markers: "don" (have/has), "dey" (is/are/-ing),
  "go" (will), "make" (let/should), "abeg" (please), "wahala" (problem),
  "small time" (soon), "wetin" (what), "sabi" (know).
- Example Pidgin "ready for pickup": "Amino, your kaftan don ready o.
  Make you come collect am for the shop wen you ready. Balance of ₦1,900,000
  remain. We dey wait you."
- Example Pidgin "balance reminder": "Folake, just to remind you say balance
  of ₦7,500 dey for your boubou. Abeg try settle am before Friday so we fit
  release am to you."
- Do not mix English sentences into a Pidgin message or vice versa.`;

const INTENT_LABELS: Record<IntentType, string> = {
  balance_reminder: 'polite reminder about an outstanding balance',
  pickup_ready: 'notification that their order is ready for pickup',
  follow_up: 'casual check-in to see if they need anything else',
  custom_note: 'custom message based on the notes provided',
};

const LANGUAGE_LABELS: Record<Language, string> = {
  en: 'English',
  pcm: 'Pidgin (Nigerian)',
};

export function buildSystemPrompt(): string {
  return SYSTEM_PROMPT;
}

export interface BuildUserPromptArgs {
  intentType: IntentType;
  language: Language;
  context: DraftContext;
  customNotes?: string;
}

export function buildUserPrompt(args: BuildUserPromptArgs): string {
  const { intentType, language, context, customNotes } = args;
  const intentLabel = INTENT_LABELS[intentType];
  const languageLabel = LANGUAGE_LABELS[language];

  const lines = [
    `Draft a ${intentLabel} message in ${languageLabel} for this customer:`,
    '',
    `Customer: ${context.customerFirstName}`,
    `Order: ${context.garmentLabel}`,
    `Deposit paid: ${context.depositFormatted}`,
    `Balance due: ${context.balanceFormatted}`,
    `Deadline: ${context.deadlineFormatted}`,
  ];

  if (customNotes && customNotes.trim().length > 0) {
    lines.push('', `Notes: ${customNotes.trim()}`);
  }

  lines.push('', 'Your draft:');
  return lines.join('\n');
}

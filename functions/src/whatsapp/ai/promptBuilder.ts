import { BotLanguage } from '../types';
import { KbArticle } from './knowledgeBase';

/**
 * System prompt for the support assistant. It enforces three hard rules:
 *  1. Scope — StitchPad support only (Meta bans general-purpose chatbots since
 *     2026-01-15, so off-topic questions must be declined).
 *  2. Grounding — answer ONLY from the supplied knowledge; never invent app
 *     behavior; when the knowledge doesn't cover it, escalate to a human.
 *  3. A machine-parseable tail (CONFIDENCE / ESCALATE) the answerer reads to
 *     decide whether to hand off.
 */
export function buildSupportSystemPrompt(language: BotLanguage): string {
  const languageRule = language === 'pcm'
    ? `Reply in casual Nigerian Pidgin — real Pidgin grammar ("don", "dey", "go",
"abeg", "wetin", "sabi"), not English with an accent.`
    : 'Reply in clear, simple English.';

  return `You are StitchPad's customer-support assistant. StitchPad is a mobile app
Nigerian tailors use to manage customers, measurements and orders. You help
those tailors use the app and with their account and billing.

RULES:
- Answer ONLY using the KNOWLEDGE provided in the user message. Do not invent
  features, steps, prices or policies that are not in the knowledge.
- If the knowledge does not cover the question, do NOT guess — tell the user you
  will connect them to a human on the team.
- If the question is NOT about StitchPad (general chit-chat, other apps, coding,
  anything off-topic), politely decline and steer them back to StitchPad help.
- Keep replies short and WhatsApp-friendly (1-4 sentences). No greeting prefix,
  no signature.
- ${languageRule}

After your reply, on their own final lines, output exactly:
CONFIDENCE: high   (or low — low when the knowledge only partly covers it)
ESCALATE: no       (or yes — yes when you could not answer from the knowledge,
the user asks for a human, or the request needs an account change)`;
}

export interface BuildSupportUserPromptArgs {
  question: string;
  articles: KbArticle[];
  language: BotLanguage;
}

/** Builds the per-message user prompt: the grounding knowledge + the question. */
export function buildSupportUserPrompt(args: BuildSupportUserPromptArgs): string {
  const { question, articles, language } = args;

  const knowledgeBlock = articles.length === 0
    ? 'No matching knowledge was found for this question.'
    : articles
      .map((a, i) => {
        const answer = language === 'pcm' && a.answerPcm ? a.answerPcm : a.answerEn;
        return `${i + 1}. Q: ${a.question}\n   A: ${answer}`;
      })
      .join('\n');

  return [
    'KNOWLEDGE:',
    knowledgeBlock,
    '',
    `USER QUESTION: ${question.trim()}`,
    '',
    'Your reply:',
  ].join('\n');
}

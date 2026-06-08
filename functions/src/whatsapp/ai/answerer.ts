import * as functions from 'firebase-functions/v1';
import { VertexClient } from '../../smart/vertexClient';
import { BotLanguage } from '../types';
import { KbArticle, selectRelevant } from './knowledgeBase';
import { buildSupportSystemPrompt, buildSupportUserPrompt } from './promptBuilder';

// 200 (the Smart Draft default) truncates grounded multi-step answers; support
// replies stay WhatsApp-short but need a little more headroom.
const SUPPORT_MAX_OUTPUT_TOKENS = 400;

export interface SupportAnswer {
  /** Text to send back (tail markers removed). Empty only on a hard failure. */
  answer: string;
  confidence: 'high' | 'low';
  /** True when the bot could not confidently answer and a human should step in. */
  escalate: boolean;
}

export interface AnswerArgs {
  question: string;
  language: BotLanguage;
  /** The full active knowledge base; relevant articles are selected here. */
  knowledge: KbArticle[];
  client: VertexClient;
}

/**
 * Answers a support question, grounded in the knowledge base. Never throws —
 * any model failure or unparseable output resolves to an escalation so the
 * conversation can be handed to a human rather than dropped.
 */
export async function answerSupportQuestion(args: AnswerArgs): Promise<SupportAnswer> {
  const { question, language, knowledge, client } = args;
  const relevant = selectRelevant(question, knowledge);

  const systemPrompt = buildSupportSystemPrompt(language);
  const userPrompt = buildSupportUserPrompt({ question, articles: relevant, language });

  let raw: string;
  try {
    raw = await client.generateText({ systemPrompt, userPrompt, maxOutputTokens: SUPPORT_MAX_OUTPUT_TOKENS });
  } catch (err) {
    functions.logger.error('support answer generation failed', {
      message: err instanceof Error ? err.message : String(err),
    });
    return { answer: '', confidence: 'low', escalate: true };
  }

  return parseSupportCompletion(raw);
}

const CONFIDENCE_RE = /^\s*CONFIDENCE:\s*(high|low)\s*$/im;
const ESCALATE_RE = /^\s*ESCALATE:\s*(yes|no)\s*$/im;

/**
 * Splits the model's tail markers off the answer body. Missing/garbled markers
 * are treated as "don't trust it" → escalate, low confidence.
 */
export function parseSupportCompletion(raw: string): SupportAnswer {
  const confidenceMatch = raw.match(CONFIDENCE_RE);
  const escalateMatch = raw.match(ESCALATE_RE);

  const answer = raw
    .replace(CONFIDENCE_RE, '')
    .replace(ESCALATE_RE, '')
    .trim();

  const confidence: 'high' | 'low' = confidenceMatch?.[1].toLowerCase() === 'high' ? 'high' : 'low';
  // Default to escalation whenever the marker is absent — an un-formatted
  // completion means the model didn't follow instructions, so don't trust it.
  const escalate = escalateMatch ? escalateMatch[1].toLowerCase() === 'yes' : true;

  return { answer, confidence, escalate };
}

import { parseInboundMessages, BotLanguage, InboundMessage } from './types';
import { WhatsAppClient, WHATSAPP_MAX_TEXT_LENGTH } from './cloudApiClient';
import { DedupIO } from './dedup';
import { ConversationIO } from './conversationIO';
import { KbIO } from './ai/knowledgeBase';
import { VertexClient } from '../smart/vertexClient';
import { handleOnboarding } from './onboarding';
import { answerSupportQuestion } from './ai/answerer';

/** Everything the inbound dispatcher needs, injectable for tests. */
export interface MessageHandlerDeps {
  client: WhatsAppClient;
  dedup: DedupIO;
  conversations: ConversationIO;
  knowledge: KbIO;
  vertex: VertexClient;
}

// Sent when the bot couldn't produce an answer (model failure or escalation
// with no text). Slice 3 turns this into a real human handoff + notification.
const HANDOFF_FALLBACK: Record<BotLanguage, string> = {
  en: 'Let me connect you with someone on our team — they will get back to you shortly.',
  pcm: 'Make I connect you with person for our team — dem go reply you shortly.',
};

function cap(text: string): string {
  return text.slice(0, WHATSAPP_MAX_TEXT_LENGTH);
}

/**
 * Inbound dispatch: dedup each message, then run it through onboarding and (once
 * onboarded) AI answering. On a send failure the dedup marker is released and
 * the error rethrown, so the webhook returns 500 and Meta retries.
 */
export async function handleInboundPayload(payload: unknown, deps: MessageHandlerDeps): Promise<void> {
  for (const msg of parseInboundMessages(payload)) {
    const isNew = await deps.dedup.markProcessed(msg.waId, msg.messageId);
    if (!isNew) continue;
    if (msg.text.trim().length === 0) continue;
    try {
      await handleOneMessage(msg, deps);
    } catch (err) {
      await deps.dedup.release(msg.waId, msg.messageId);
      throw err;
    }
  }
}

async function handleOneMessage(msg: InboundMessage, deps: MessageHandlerDeps): Promise<void> {
  const conv = await deps.conversations.get(msg.waId);

  // 0. A non-BOT state means a human owns this thread (set during escalation in
  // Slice 3). The inbound is already recorded via dedup; stay silent so the bot
  // never talks over an agent or interrupts an active handoff.
  if (conv.state !== 'BOT') {
    return;
  }

  // 1. Onboarding gate: terms, then language. Returns a reply to send and/or
  // doc updates to persist, and only proceeds once fully onboarded.
  const onboarding = handleOnboarding(conv, msg.text);
  if (onboarding.updates) {
    await deps.conversations.update(msg.waId, onboarding.updates);
  }
  if (onboarding.reply) {
    await deps.client.sendText(msg.waId, cap(onboarding.reply));
  }
  if (!onboarding.proceedToAnswer) return;

  // 2. Onboarded → answer from the knowledge base.
  const language: BotLanguage = conv.language ?? 'en';
  const knowledge = await deps.knowledge.loadKnowledge();
  const result = await answerSupportQuestion({
    question: msg.text,
    language,
    knowledge,
    client: deps.vertex,
  });
  const outgoing = result.answer.trim().length > 0 ? result.answer : HANDOFF_FALLBACK[language];
  await deps.client.sendText(msg.waId, cap(outgoing));
  // Slice 3: when result.escalate, flip state to AWAITING_HUMAN, notify a human,
  // and email the transcript to support@.
}

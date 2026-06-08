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

// Sent when the bot couldn't answer (model failure, off-topic, or low
// confidence). Slice 2 has no in-thread handoff yet, so this points to a real
// channel that works today rather than promising a follow-up nobody is wired to
// deliver. Slice 3 replaces this with a live human takeover + notification.
const HANDOFF_FALLBACK: Record<BotLanguage, string> = {
  en: 'Sorry, I could not answer that one. Please email our team at support@getstitchpad.com and we will help you out.',
  pcm: 'Abeg, I no fit answer that one. Make you email our team for support@getstitchpad.com, we go help you.',
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
  // Send the reply BEFORE persisting the state change. If we persisted first and
  // the send then failed, the marker is released and Meta's retry of the same
  // "1"/"2" would hit an already-onboarded conversation and route it to the AI.
  // Sending first means a failed step releases with no state change and replays
  // cleanly (worst case: a duplicate prompt, which is harmless).
  if (onboarding.reply) {
    await deps.client.sendText(msg.waId, cap(onboarding.reply));
  }
  if (onboarding.updates) {
    await deps.conversations.update(msg.waId, onboarding.updates);
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
  // On escalation the answer is untrusted (off-topic, low-confidence, or an
  // un-formatted completion) — send the handoff message, never the model text.
  const outgoing = !result.escalate && result.answer.trim().length > 0
    ? result.answer
    : HANDOFF_FALLBACK[language];
  await deps.client.sendText(msg.waId, cap(outgoing));
  // Slice 3: when result.escalate, also flip state to AWAITING_HUMAN, notify a
  // human, and email the transcript to support@.
}

/**
 * Shared types for the WhatsApp support bot, plus a tolerant parser that
 * extracts inbound messages from a Meta Cloud API webhook payload.
 *
 * The payload is untrusted (it arrives over HTTP), so the parser never assumes
 * a field exists — anything malformed yields an empty result rather than
 * throwing inside the webhook.
 */

/** A single inbound message, normalized down to what the bot acts on. */
export interface InboundMessage {
  /** Sender's WhatsApp id — E.164 digits without a leading '+', as Meta sends. */
  waId: string;
  /** Meta's globally-unique message id (`wamid.…`) — used for idempotency. */
  messageId: string;
  /** Message type: 'text', 'interactive', 'image', etc. */
  type: string;
  /** Best-effort text body. Empty string for non-text messages. */
  text: string;
}

/** Conversation handoff state. BOT answers; the others mean a human owns it. */
export type ConversationState = 'BOT' | 'AWAITING_HUMAN' | 'HUMAN_ACTIVE';

/** Supported reply languages — mirrors the Smart Draft `Language` union. */
export type BotLanguage = 'en' | 'pcm';

/**
 * Persisted per-conversation state (`whatsappConversations/{waId}`). Slice 2
 * needs terms + language; Slice 3 extends this with handoff/window fields.
 */
export interface ConversationDoc {
  state: ConversationState;
  termsAccepted: boolean;
  language?: BotLanguage;
}

function asRecord(value: unknown): Record<string, unknown> | undefined {
  return typeof value === 'object' && value !== null ? (value as Record<string, unknown>) : undefined;
}

function asArray(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

/** Extracts inbound messages from a webhook POST body. Status callbacks → []. */
export function parseInboundMessages(payload: unknown): InboundMessage[] {
  const root = asRecord(payload);
  if (!root) return [];

  const result: InboundMessage[] = [];
  for (const entryRaw of asArray(root.entry)) {
    const entry = asRecord(entryRaw);
    if (!entry) continue;
    for (const changeRaw of asArray(entry.changes)) {
      const change = asRecord(changeRaw);
      const value = asRecord(change?.value);
      if (!value) continue;
      for (const msgRaw of asArray(value.messages)) {
        const msg = asRecord(msgRaw);
        if (!msg) continue;
        const waId = typeof msg.from === 'string' ? msg.from : undefined;
        const messageId = typeof msg.id === 'string' ? msg.id : undefined;
        if (!waId || !messageId) continue;
        const type = typeof msg.type === 'string' ? msg.type : 'unknown';
        const textBody = asRecord(msg.text)?.body;
        result.push({
          waId,
          messageId,
          type,
          text: typeof textBody === 'string' ? textBody : '',
        });
      }
    }
  }
  return result;
}

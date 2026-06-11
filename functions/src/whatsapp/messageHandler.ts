import * as functions from 'firebase-functions/v1';
import { parseInboundMessages, BotLanguage, InboundMessage, ConversationDoc } from './types';
import { WhatsAppClient, WHATSAPP_MAX_TEXT_LENGTH } from './cloudApiClient';
import { DedupIO } from './dedup';
import { ConversationIO } from './conversationIO';
import { KbIO } from './ai/knowledgeBase';
import { VertexClient } from '../smart/vertexClient';
import { handleOnboarding } from './onboarding';
import { answerSupportQuestion } from './ai/answerer';
import {
  EscalationIO,
  EscalationReason,
  detectExplicitEscalation,
  isFounder,
  parseFounderCommand,
  FounderCommand,
} from './escalation';
import { AccountLinkIO, detectAccountIntent } from './accountLinking';

/** Everything the inbound dispatcher needs, injectable for tests. */
export interface MessageHandlerDeps {
  client: WhatsAppClient;
  dedup: DedupIO;
  conversations: ConversationIO;
  knowledge: KbIO;
  vertex: VertexClient;
  escalation: EscalationIO;
  accountLink: AccountLinkIO;
  /** Allow-listed founder/support numbers that may use #reply / #resolve. */
  founderNumbers: string[];
}

const TIER_LABELS: Record<string, string> = {
  free: 'Free',
  pro: 'Tailor Pro',
  premium: 'Tailor Pro',
  atelier: 'Tailor Atelier',
};

const CONSENT_PROMPT: Record<BotLanguage, string> = {
  en: 'I found a StitchPad account registered to this number. Reply YES to let me share your account details here.',
  pcm: 'I see StitchPad account wey register with this number. Reply YES make I fit share your account details here.',
};

const NO_ACCOUNT_FOUND: Record<BotLanguage, string> = {
  en: 'I could not find a StitchPad account registered to this number. Please message from the number on your StitchPad profile.',
  pcm: 'I no fit find StitchPad account wey register with this number. Abeg message from the number wey dey your StitchPad profile.',
};

const ACCOUNT_LINKED: Record<BotLanguage, string> = {
  en: 'Done — your account is linked. What would you like to know? (for example, your plan)',
  pcm: 'Done — your account don link. Wetin you wan know? (like your plan)',
};

function tierMessage(language: BotLanguage, tier: string | null): string {
  const label = tier ? (TIER_LABELS[tier.toLowerCase()] ?? tier) : 'Free';
  return language === 'pcm' ? `You dey for the ${label} plan.` : `You are on the ${label} plan.`;
}

function isAffirmative(text: string): boolean {
  return text.trim().toUpperCase() === 'YES';
}

const ESCALATION_ACK: Record<BotLanguage, string> = {
  en: 'Thanks — I am connecting you with someone on our team. They will reply here shortly.',
  pcm: 'Thank you — I dey connect you with person for our team. Dem go reply you here shortly.',
};

const RESOLVED_NOTICE: Record<BotLanguage, string> = {
  en: 'You are back with our support assistant. How else can I help?',
  pcm: 'You don come back to our support assistant. Wetin else I fit help you?',
};

function cap(text: string): string {
  return text.slice(0, WHATSAPP_MAX_TEXT_LENGTH);
}

/**
 * Inbound dispatch: dedup each message, then route it (founder command, human
 * handoff, onboarding, or AI answer). On a send failure the dedup marker is
 * released and the error rethrown, so the webhook returns 500 and Meta retries.
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
  // 0. Founder admin commands run first — the founder isn't a normal user, so
  // they must bypass onboarding/state. A founder message that isn't a command
  // falls through and is treated as a normal user (lets them test the bot).
  if (isFounder(msg.waId, deps.founderNumbers)) {
    const cmd = parseFounderCommand(msg.text);
    if (cmd) {
      await handleFounderCommand(cmd, deps);
      return;
    }
  }

  const conv = await deps.conversations.get(msg.waId);

  // 1. A non-BOT state means a human owns this thread. Relay the user's message
  // to the founder (best-effort) so they can keep the conversation going, and
  // never let the bot reply over them.
  if (conv.state !== 'BOT') {
    await relayToFounders(msg, deps);
    return;
  }

  // 2. Onboarding gate: terms, then language. Send the reply BEFORE persisting
  // so a failed send releases with no state change and replays cleanly.
  const onboarding = handleOnboarding(conv, msg.text);
  if (onboarding.reply) {
    await deps.client.sendText(msg.waId, cap(onboarding.reply));
  }
  if (onboarding.updates) {
    await deps.conversations.update(msg.waId, onboarding.updates);
  }
  if (!onboarding.proceedToAnswer) return;

  const language: BotLanguage = conv.language ?? 'en';

  // 3. Optional account linking / personalization (consent-gated). Returns true
  // when it fully handled the turn (asked consent, revealed tier, etc.).
  if (await handleAccountTurn(conv, msg, language, deps)) {
    return;
  }

  // 4. Explicit escalation (asks for a human / sensitive account action) skips
  // the model entirely.
  const explicit = detectExplicitEscalation(msg.text);
  if (explicit) {
    await escalate(msg, explicit, language, deps);
    return;
  }

  // 4. Otherwise answer from the knowledge base; escalate if the answer is not
  // trustworthy (off-topic, low confidence, no grounding, or model failure).
  const knowledge = await deps.knowledge.loadKnowledge();
  const result = await answerSupportQuestion({ question: msg.text, language, knowledge, client: deps.vertex });
  if (result.escalate) {
    await escalate(msg, 'low_confidence', language, deps);
    return;
  }
  await deps.client.sendText(msg.waId, cap(result.answer));
}

/**
 * Hands the conversation to a human: ack the user, park the thread, then notify
 * (best-effort). The ack is sent BEFORE the state change so a failed ack
 * replays cleanly; notifications never throw so they can't trigger a retry.
 */
async function escalate(msg: InboundMessage, reason: EscalationReason, language: BotLanguage, deps: MessageHandlerDeps): Promise<void> {
  await deps.client.sendText(msg.waId, cap(ESCALATION_ACK[language]));
  await deps.conversations.update(msg.waId, { state: 'AWAITING_HUMAN' });

  await bestEffort('escalation ticket', () =>
    deps.escalation.sendTicket({ waId: msg.waId, reason, message: msg.text }));
  await relayToFounders(msg, deps);
}

/**
 * Consent-gated account personalization. Returns true if it owned this turn.
 * Sends happen BEFORE the persisted state change so a failed send replays the
 * same step cleanly (consistent with onboarding). No account data is ever sent
 * before the user has explicitly consented.
 */
async function handleAccountTurn(conv: ConversationDoc, msg: InboundMessage, language: BotLanguage, deps: MessageHandlerDeps): Promise<boolean> {
  // A consent prompt is outstanding: a bare YES grants it (and fulfils the
  // remembered intent); anything else cancels and falls through to normal flow.
  if (conv.awaitingLinkConsent) {
    if (isAffirmative(msg.text)) {
      // Re-resolve before honoring consent — the binding may have changed
      // between the prompt and this YES. Only disclose if it still maps to the
      // account the user was shown.
      const confirmedUid = await deps.accountLink.findUidByNumber(msg.waId);
      if (!confirmedUid || confirmedUid !== conv.linkedUid) {
        await deps.conversations.update(msg.waId, { linkingConsent: false, awaitingLinkConsent: false, pendingAccountIntent: null });
        await deps.client.sendText(msg.waId, cap(NO_ACCOUNT_FOUND[language]));
        return true;
      }
      if (conv.pendingAccountIntent === 'tier') {
        await sendTier(confirmedUid, msg.waId, language, deps);
      } else {
        await deps.client.sendText(msg.waId, cap(ACCOUNT_LINKED[language]));
      }
      await deps.conversations.update(msg.waId, { linkingConsent: true, awaitingLinkConsent: false, pendingAccountIntent: null });
      return true;
    }
    await deps.conversations.update(msg.waId, { awaitingLinkConsent: false, pendingAccountIntent: null });
    return false;
  }

  const intent = detectAccountIntent(msg.text);
  if (!intent) return false;

  // ALWAYS re-resolve the current number → uid binding before disclosing
  // anything. A cached link can go stale (the user edits/removes their number,
  // or the SIM is reassigned to someone else), and consent only ever applied to
  // the account that was linked at consent time.
  const uid = await deps.accountLink.findUidByNumber(msg.waId);
  if (!uid) {
    // Revoke any stale consent so a later re-match can't ride on it. We leave
    // linkedUid as-is (harmless — every disclosure re-resolves and compares it).
    if (conv.linkingConsent || conv.awaitingLinkConsent) {
      await deps.conversations.update(msg.waId, { linkingConsent: false, awaitingLinkConsent: false, pendingAccountIntent: null });
    }
    await deps.client.sendText(msg.waId, cap(NO_ACCOUNT_FOUND[language]));
    return true;
  }

  // Consent is valid only for the SAME account the user consented to.
  if (conv.linkingConsent && conv.linkedUid === uid) {
    await sendTier(uid, msg.waId, language, deps);
    return true;
  }

  // New or changed binding → (re)ask consent for the current account, resetting
  // any consent that was granted for a different (now-stale) account.
  await deps.client.sendText(msg.waId, cap(CONSENT_PROMPT[language]));
  await deps.conversations.update(msg.waId, { linkedUid: uid, linkingConsent: false, awaitingLinkConsent: true, pendingAccountIntent: intent });
  return true;
}

async function sendTier(uid: string, waId: string, language: BotLanguage, deps: MessageHandlerDeps): Promise<void> {
  const tier = await deps.accountLink.getTier(uid);
  await deps.client.sendText(waId, cap(tierMessage(language, tier)));
}

async function handleFounderCommand(cmd: FounderCommand, deps: MessageHandlerDeps): Promise<void> {
  if (cmd.kind === 'reply') {
    await deps.client.sendText(cmd.target, cap(cmd.body));
    await deps.conversations.update(cmd.target, { state: 'HUMAN_ACTIVE' });
    return;
  }
  // resolve
  const target = await deps.conversations.get(cmd.target);
  await deps.conversations.update(cmd.target, { state: 'BOT' });
  await bestEffort('resolve notice', () =>
    deps.client.sendText(cmd.target, cap(RESOLVED_NOTICE[target.language ?? 'en'])));
}

/** Forwards a user message to each founder so they can reply via #reply. Best-effort. */
async function relayToFounders(msg: InboundMessage, deps: MessageHandlerDeps): Promise<void> {
  for (const founder of deps.founderNumbers) {
    await bestEffort('founder relay', () =>
      deps.client.sendText(founder, cap(`📩 From ${msg.waId}: ${msg.text}\nReply: #reply ${msg.waId} <message>`)));
  }
}

/** Runs a non-critical side effect, swallowing+logging failures so it can never
 * trigger a dedup release / Meta retry of the whole message. */
async function bestEffort(label: string, fn: () => Promise<void>): Promise<void> {
  try {
    await fn();
  } catch (err) {
    functions.logger.warn(`whatsapp ${label} failed`, {
      error: err instanceof Error ? err.message : String(err),
    });
  }
}

import { sendResendEmail } from '../email/resendClient';

/** Why a conversation was handed to a human. */
export type EscalationReason = 'human_requested' | 'sensitive_action' | 'low_confidence';

/** Side-effect seam for delivering an escalation ticket (production: Resend). */
export interface EscalationIO {
  sendTicket(args: { waId: string; reason: string; message: string }): Promise<void>;
}

/** Production EscalationIO — emails the ticket to `to` (support@) via Resend. */
export function productionEscalationIO(apiKey: string, to: string): EscalationIO {
  return {
    async sendTicket({ waId, reason, message }) {
      const mail = buildTicketEmail({ waId, reason, message });
      await sendResendEmail(apiKey, { to, subject: mail.subject, html: mail.html, text: mail.text });
    },
  };
}

// Explicit "get me a person" phrasing, English + common Pidgin.
const HUMAN_REQUEST_RE = /\b(human|agent|representative|real person|customer (care|service)|speak to (someone|a person|an agent)|talk to (someone|a person|a human|an agent)|talk to person|call me)\b/i;

// Account/billing actions that should never be handled by the bot.
const SENSITIVE_RE = /\b(refund|charge ?back|billing dispute|dispute|delete (my )?account|close (my )?account|cancel (my )?(subscription|plan)|change (my )?(number|phone))\b/i;

/** Detects an explicit reason to escalate from the user's text, or null. */
export function detectExplicitEscalation(text: string): EscalationReason | null {
  if (SENSITIVE_RE.test(text)) return 'sensitive_action';
  if (HUMAN_REQUEST_RE.test(text)) return 'human_requested';
  return null;
}

const digitsOnly = (s: string): string => s.replace(/\D/g, '');

/** True when the sender is one of the allow-listed founder/support numbers. */
export function isFounder(waId: string, allowlist: string[]): boolean {
  const target = digitsOnly(waId);
  return allowlist.some((n) => digitsOnly(n) === target && target.length > 0);
}

/**
 * Parses the comma-separated founder allowlist into digit-only numbers. The
 * Cloud API send target must be unformatted (no '+' or spaces), so we normalize
 * here — `relayToFounders` sends to these directly.
 */
export function parseFounderNumbers(raw: string | undefined): string[] {
  return (raw ?? '').split(',').map((s) => digitsOnly(s)).filter(Boolean);
}

export type FounderCommand =
  | { kind: 'reply'; target: string; body: string }
  | { kind: 'resolve'; target: string };

const REPLY_RE = /^#reply\s+(\S+)\s+([\s\S]+)$/i;
const RESOLVE_RE = /^#resolve\s+(\S+)\s*$/i;

/**
 * Parses a founder admin command, or null if the text isn't a valid command.
 * The target is normalized to digits-only (so `#reply +234…` works and `#resolve`
 * touches the same `234…` conversation doc); a target with no digits is rejected.
 */
export function parseFounderCommand(text: string): FounderCommand | null {
  const trimmed = text.trim();
  const reply = trimmed.match(REPLY_RE);
  if (reply) {
    const target = digitsOnly(reply[1]);
    return target ? { kind: 'reply', target, body: reply[2] } : null;
  }
  const resolve = trimmed.match(RESOLVE_RE);
  if (resolve) {
    const target = digitsOnly(resolve[1]);
    return target ? { kind: 'resolve', target } : null;
  }
  return null;
}

function escapeHtml(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

export interface TicketEmail {
  subject: string;
  html: string;
  text: string;
}

/** Builds the support@ escalation ticket. */
export function buildTicketEmail(args: { waId: string; reason: string; message: string }): TicketEmail {
  const { waId, reason, message } = args;
  const replyHint = `#reply ${waId} <your message>`;
  const subject = `WhatsApp escalation from ${waId} (${reason})`;
  const text = [
    'A WhatsApp support chat needs a human.',
    '',
    `From: ${waId}`,
    `Reason: ${reason}`,
    `Message: ${message}`,
    '',
    'To reply, send this to the bot number from an allow-listed phone:',
    replyHint,
    `To hand back to the bot: #resolve ${waId}`,
  ].join('\n');
  const html = [
    '<p>A WhatsApp support chat needs a human.</p>',
    `<p><b>From:</b> ${escapeHtml(waId)}<br/>`,
    `<b>Reason:</b> ${escapeHtml(reason)}<br/>`,
    `<b>Message:</b> ${escapeHtml(message)}</p>`,
    '<p>To reply, send this to the bot number from an allow-listed phone:<br/>',
    `<code>${escapeHtml(replyHint)}</code></p>`,
  ].join('');
  return { subject, html, text };
}

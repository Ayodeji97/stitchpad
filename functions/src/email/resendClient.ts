const RESEND_ENDPOINT = 'https://api.resend.com/emails';
export const FROM = 'StitchPad <noreply@send.getstitchpad.com>';
export const REPLY_TO = 'support@getstitchpad.com';

/** A Resend send failure carrying the HTTP status, so callers can tell a permanent
 *  4xx (bad address — don't retry) from a transient 5xx/network error (retry). */
export class ResendError extends Error {
  /** HTTP status from Resend, or undefined for a network-level failure (no response). */
  readonly status?: number;
  constructor(message: string, status?: number) {
    super(message);
    this.name = 'ResendError';
    this.status = status;
  }
}

/** POSTs one email through Resend. Throws [ResendError] on a non-ok response (caller logs/handles). */
export async function sendResendEmail(
  apiKey: string,
  params: { to: string; subject: string; html: string; text?: string },
): Promise<void> {
  const response = await fetch(RESEND_ENDPOINT, {
    method: 'POST',
    headers: { Authorization: `Bearer ${apiKey}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({
      from: FROM,
      to: [params.to],
      reply_to: REPLY_TO,
      subject: params.subject,
      html: params.html,
      ...(params.text ? { text: params.text } : {}),
    }),
  });
  if (!response.ok) {
    const detail = await response.text().catch(() => '');
    throw new ResendError(`Resend responded ${response.status}: ${detail}`, response.status);
  }
}

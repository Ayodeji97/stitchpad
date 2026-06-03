const RESEND_ENDPOINT = 'https://api.resend.com/emails';
export const FROM = 'StitchPad <noreply@send.getstitchpad.com>';
export const REPLY_TO = 'support@getstitchpad.com';

/** POSTs one email through Resend. Throws on a non-ok response (caller logs/handles). */
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
    throw new Error(`Resend responded ${response.status}: ${detail}`);
  }
}

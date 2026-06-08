/** Graph API version pinned for the Cloud API send/read endpoints. */
export const GRAPH_VERSION = 'v21.0';

/**
 * Thin interface over the WhatsApp Cloud API send endpoints, so the message
 * handler can be tested with a fake client (no real Graph calls in CI). Mirrors
 * the `VertexClient` test-seam pattern used by Smart Draft.
 */
export interface WhatsAppClient {
  /** Sends a plain text reply. Only valid inside the 24h customer service window. */
  sendText(to: string, body: string): Promise<void>;
  /** Marks an inbound message as read (blue ticks). Best-effort. */
  markRead(messageId: string): Promise<void>;
}

async function postMessages(
  token: string,
  phoneNumberId: string,
  payload: Record<string, unknown>,
): Promise<void> {
  const response = await fetch(
    `https://graph.facebook.com/${GRAPH_VERSION}/${phoneNumberId}/messages`,
    {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    },
  );
  if (!response.ok) {
    const detail = await response.text().catch(() => '');
    throw new Error(`WhatsApp send failed ${response.status}: ${detail}`);
  }
}

/** Production WhatsAppClient backed by the Graph API. */
export function createWhatsAppClient(token: string, phoneNumberId: string): WhatsAppClient {
  return {
    sendText(to, body) {
      return postMessages(token, phoneNumberId, {
        messaging_product: 'whatsapp',
        recipient_type: 'individual',
        to,
        type: 'text',
        text: { preview_url: false, body },
      });
    },
    markRead(messageId) {
      return postMessages(token, phoneNumberId, {
        messaging_product: 'whatsapp',
        status: 'read',
        message_id: messageId,
      });
    },
  };
}

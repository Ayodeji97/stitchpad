import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';
import { parseInboundMessages } from './types';
import { verifyMetaSignature } from './signature';
import { WhatsAppClient, createWhatsAppClient } from './cloudApiClient';

const REGION = 'europe-west1';
const SECRETS = ['WHATSAPP_TOKEN', 'WHATSAPP_VERIFY_TOKEN', 'WHATSAPP_APP_SECRET', 'WHATSAPP_PHONE_NUMBER_ID'];

/**
 * Side-effect seam for the webhook handler. Slice 1 only needs idempotency;
 * later slices extend this with conversation state + transcript reads.
 */
export interface WebhookIO {
  /**
   * Records an inbound message id as processed. Returns true the FIRST time a
   * given (waId, messageId) is seen, false on any repeat — Meta retries
   * deliveries aggressively, so this is what stops a slow response from
   * producing duplicate replies.
   */
  markProcessed(waId: string, messageId: string): Promise<boolean>;
}

/**
 * GET verify-token handshake. Meta calls this once when you register the
 * webhook URL: echo back `hub.challenge` only when the mode is `subscribe`
 * and the token matches the one we configured. Returns null to signal 403.
 */
export function verifyChallenge(
  query: { mode?: string; token?: string; challenge?: string },
  expectedToken: string,
): string | null {
  if (query.mode === 'subscribe' && query.token === expectedToken) {
    return query.challenge ?? '';
  }
  return null;
}

/**
 * Pure inbound dispatch. Slice 1 behavior: dedup, then echo each new text
 * message. Slices 2+ replace the echo with onboarding / AI answering /
 * escalation while keeping this same shape.
 */
export async function handleInboundPayload(
  payload: unknown,
  io: WebhookIO,
  client: WhatsAppClient,
): Promise<void> {
  for (const msg of parseInboundMessages(payload)) {
    const isNew = await io.markProcessed(msg.waId, msg.messageId);
    if (!isNew) continue;
    if (msg.text.trim().length === 0) continue;
    await client.sendText(msg.waId, `You said: ${msg.text}`);
  }
}

/**
 * Firestore-backed dedup. Uses `.create()` on a deterministic id: it throws
 * ALREADY_EXISTS (gRPC code 6) on the second delivery of the same message, so
 * a `true` return means "first time, go process it". The doc lives in the
 * conversation's transcript subcollection (per the bot's data model).
 */
export function productionWebhookIO(db: admin.firestore.Firestore): WebhookIO {
  return {
    async markProcessed(waId, messageId) {
      const ref = db.collection('whatsappConversations').doc(waId).collection('messages').doc(messageId);
      try {
        await ref.create({ direction: 'inbound', receivedAt: Date.now() });
        return true;
      } catch (err) {
        // gRPC ALREADY_EXISTS = 6 — a retry of a message we already handled.
        // admin.firestore.GrpcStatus is a type-only export at runtime, so we
        // compare the numeric code directly (same trick as dailyDigest).
        if ((err as { code?: number }).code === 6) {
          return false;
        }
        throw err;
      }
    },
  };
}

/**
 * Production webhook. GET = verify handshake; POST = signature-verified inbound
 * dispatch. We ACK 200 before doing the async work so Meta never times out and
 * retries — the dedup doc protects us if it retries anyway.
 */
export const whatsappWebhook = functions
  .region(REGION)
  .runWith({ secrets: SECRETS })
  .https.onRequest(async (req, res) => {
    if (req.method === 'GET') {
      const challenge = verifyChallenge(
        {
          mode: req.query['hub.mode'] as string | undefined,
          token: req.query['hub.verify_token'] as string | undefined,
          challenge: req.query['hub.challenge'] as string | undefined,
        },
        process.env.WHATSAPP_VERIFY_TOKEN ?? '',
      );
      if (challenge === null) {
        res.sendStatus(403);
        return;
      }
      res.status(200).send(challenge);
      return;
    }

    if (req.method !== 'POST') {
      res.sendStatus(405);
      return;
    }

    const appSecret = process.env.WHATSAPP_APP_SECRET ?? '';
    const signature = req.get('x-hub-signature-256');
    if (!verifyMetaSignature(appSecret, req.rawBody, signature)) {
      functions.logger.warn('whatsapp webhook rejected: bad signature');
      res.sendStatus(401);
      return;
    }

    // ACK first so Meta doesn't retry on our processing latency.
    res.sendStatus(200);

    try {
      const token = process.env.WHATSAPP_TOKEN ?? '';
      const phoneNumberId = process.env.WHATSAPP_PHONE_NUMBER_ID ?? '';
      const client = createWhatsAppClient(token, phoneNumberId);
      const io = productionWebhookIO(admin.firestore());
      await handleInboundPayload(req.body, io, client);
    } catch (err) {
      functions.logger.error('whatsapp webhook processing failed', {
        error: err instanceof Error ? err.message : String(err),
        stack: err instanceof Error ? err.stack : undefined,
      });
    }
  });

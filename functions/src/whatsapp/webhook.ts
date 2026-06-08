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
  /**
   * Undoes a `markProcessed` when the side effect (the reply) failed, so a
   * Meta retry re-processes the message instead of skipping it as a duplicate.
   * Mirrors the reserve/release pattern in sendVerificationEmail.
   */
  release(waId: string, messageId: string): Promise<void>;
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
    try {
      await client.sendText(msg.waId, `You said: ${msg.text}`);
    } catch (err) {
      // The dedup marker was written before the reply. Release it so the
      // retry re-sends this message; messages already replied to in this
      // payload keep their marker and won't be double-sent. Then rethrow so
      // the webhook returns 500 and Meta retries.
      await io.release(msg.waId, msg.messageId);
      throw err;
    }
  }
}

/**
 * Firestore-backed dedup. Uses `.create()` on a deterministic id: it throws
 * ALREADY_EXISTS (gRPC code 6) on the second delivery of the same message, so
 * a `true` return means "first time, go process it". The doc lives in the
 * conversation's transcript subcollection (per the bot's data model).
 */
export function productionWebhookIO(db: admin.firestore.Firestore): WebhookIO {
  const messageRef = (waId: string, messageId: string) =>
    db.collection('whatsappConversations').doc(waId).collection('messages').doc(messageId);
  return {
    async markProcessed(waId, messageId) {
      try {
        await messageRef(waId, messageId).create({ direction: 'inbound', receivedAt: Date.now() });
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
    async release(waId, messageId) {
      await messageRef(waId, messageId).delete();
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

    // Process BEFORE responding. In Cloud Functions, the instance can be
    // frozen the moment the response is sent, so any await after res.send()
    // may never run — ACK-first would silently drop the dedup write and the
    // reply. The work here is tiny (one Firestore .create() + one Graph send),
    // well within Meta's webhook timeout, and the per-message .create() dedup
    // makes a retry safe if we ever are slow.
    try {
      const token = process.env.WHATSAPP_TOKEN ?? '';
      const phoneNumberId = process.env.WHATSAPP_PHONE_NUMBER_ID ?? '';
      const client = createWhatsAppClient(token, phoneNumberId);
      const io = productionWebhookIO(admin.firestore());
      await handleInboundPayload(req.body, io, client);
      res.sendStatus(200);
    } catch (err) {
      functions.logger.error('whatsapp webhook processing failed', {
        error: err instanceof Error ? err.message : String(err),
        stack: err instanceof Error ? err.stack : undefined,
      });
      // 500 → Meta retries; the dedup doc prevents re-handling whatever
      // already succeeded on the first attempt.
      res.sendStatus(500);
    }
  });

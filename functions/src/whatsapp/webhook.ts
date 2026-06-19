import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';
import { verifyMetaSignature } from './signature';
import { createWhatsAppClient } from './cloudApiClient';
import { productionDedupIO } from './dedup';
import { productionConversationIO } from './conversationIO';
import { productionKbIO } from './ai/knowledgeBase';
import { productionEscalationIO, parseFounderNumbers } from './escalation';
import { productionAccountLinkIO } from './accountLinking';
import { getVertexClient } from '../smart/vertexClient';
import { REPLY_TO } from '../email/resendClient';
import { handleInboundPayload } from './messageHandler';

const REGION = 'europe-west1';
const SECRETS = [
  'WHATSAPP_TOKEN',
  'WHATSAPP_VERIFY_TOKEN',
  'WHATSAPP_APP_SECRET',
  'WHATSAPP_PHONE_NUMBER_ID',
  'WHATSAPP_FOUNDER_NUMBERS',
  'RESEND_API_KEY',
];

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
 * Production webhook. GET = verify handshake; POST = signature-verified inbound
 * dispatch. Every config check FAILS CLOSED: a missing secret rejects the
 * request rather than defaulting to an empty value that could accept spoofed
 * traffic (an empty app secret would otherwise validate an attacker's empty-key
 * HMAC) or complete the verify handshake with an empty token.
 */
export const whatsappWebhook = functions
  .region(REGION)
  .runWith({ secrets: SECRETS })
  .https.onRequest(async (req, res) => {
    if (req.method === 'GET') {
      const verifyToken = process.env.WHATSAPP_VERIFY_TOKEN;
      if (!verifyToken) {
        functions.logger.error('WHATSAPP_VERIFY_TOKEN not configured');
        res.sendStatus(403);
        return;
      }
      const challenge = verifyChallenge(
        {
          mode: req.query['hub.mode'] as string | undefined,
          token: req.query['hub.verify_token'] as string | undefined,
          challenge: req.query['hub.challenge'] as string | undefined,
        },
        verifyToken,
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

    const appSecret = process.env.WHATSAPP_APP_SECRET;
    if (!appSecret) {
      functions.logger.error('WHATSAPP_APP_SECRET not configured');
      res.sendStatus(401);
      return;
    }
    const signature = req.get('x-hub-signature-256');
    if (!verifyMetaSignature(appSecret, req.rawBody, signature)) {
      functions.logger.warn('whatsapp webhook rejected: bad signature');
      res.sendStatus(401);
      return;
    }

    const token = process.env.WHATSAPP_TOKEN;
    const phoneNumberId = process.env.WHATSAPP_PHONE_NUMBER_ID;
    if (!token || !phoneNumberId) {
      functions.logger.error('WhatsApp send credentials not configured');
      res.sendStatus(500);
      return;
    }

    // Process BEFORE responding. In Cloud Functions the instance can be frozen
    // the moment the response is sent, so any await after res.send() may never
    // run — ACK-first would silently drop the write and reply. The work is small
    // and the per-message dedup makes a retry safe if we are ever slow.
    try {
      // Escalation has two notification channels (support@ email + founder
      // WhatsApp relay). We don't fail the whole webhook on a missing Resend key
      // — the bot can still answer and relay — but we log it loudly so a deploy
      // that can't email tickets is visible in Cloud Logging.
      const resendKey = process.env.RESEND_API_KEY;
      if (!resendKey) {
        functions.logger.error('RESEND_API_KEY not configured — escalation tickets will not be emailed');
      }
      const db = admin.firestore();
      await handleInboundPayload(req.body, {
        client: createWhatsAppClient(token, phoneNumberId),
        dedup: productionDedupIO(db),
        conversations: productionConversationIO(db),
        knowledge: productionKbIO(db),
        vertex: getVertexClient(),
        escalation: productionEscalationIO(resendKey ?? '', REPLY_TO),
        accountLink: productionAccountLinkIO(db),
        founderNumbers: parseFounderNumbers(process.env.WHATSAPP_FOUNDER_NUMBERS),
      });
      res.sendStatus(200);
    } catch (err) {
      functions.logger.error('whatsapp webhook processing failed', {
        error: err instanceof Error ? err.message : String(err),
        stack: err instanceof Error ? err.stack : undefined,
      });
      // 500 → Meta retries; released dedup markers let the retry re-process.
      res.sendStatus(500);
    }
  });

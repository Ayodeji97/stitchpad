import * as crypto from 'crypto';

/**
 * Verifies Meta's `X-Hub-Signature-256` header against the raw request body.
 *
 * Meta signs the EXACT bytes it sent (`sha256=` + hex HMAC-SHA256 of the raw
 * payload, keyed with the app secret). The verification MUST run against
 * `req.rawBody` — re-serializing `req.body` to JSON changes the byte order /
 * whitespace and the signature will never match.
 *
 * Returns false (never throws) on a missing or malformed header so the caller
 * can simply reject the request with 401.
 */
export function verifyMetaSignature(
  appSecret: string,
  rawBody: Buffer,
  header: string | undefined,
): boolean {
  if (!header || !header.startsWith('sha256=')) {
    return false;
  }

  const expected = 'sha256=' + crypto
    .createHmac('sha256', appSecret)
    .update(rawBody)
    .digest('hex');

  const headerBuf = Buffer.from(header);
  const expectedBuf = Buffer.from(expected);
  // Length check first — timingSafeEqual throws on unequal-length buffers.
  if (headerBuf.length !== expectedBuf.length) {
    return false;
  }
  return crypto.timingSafeEqual(headerBuf, expectedBuf);
}

import { verifyMetaSignature } from '../../whatsapp/signature';

// Real known vector generated with:
//   crypto.createHmac('sha256', secret).update(Buffer.from(body)).digest('hex')
// Keeping it hard-coded (not recomputed in the test) makes this a genuine
// fixture rather than the implementation checking itself.
const SECRET = 'wh_app_secret_test';
const BODY = Buffer.from('{"object":"whatsapp_business_account","entry":[{"id":"123"}]}');
const VALID_SIG = 'sha256=d0a58f25d86d494a2daf2172f99830e5960b5b2edb7f4f8ae8e2254407eeb286';

describe('verifyMetaSignature', () => {
  it('accepts a correct sha256 signature for the raw body', () => {
    expect(verifyMetaSignature(SECRET, BODY, VALID_SIG)).toBe(true);
  });

  it('rejects when the body is tampered (signature no longer matches)', () => {
    const tampered = Buffer.from('{"object":"whatsapp_business_account","entry":[{"id":"999"}]}');
    expect(verifyMetaSignature(SECRET, tampered, VALID_SIG)).toBe(false);
  });

  it('rejects when the app secret is wrong', () => {
    expect(verifyMetaSignature('wrong_secret', BODY, VALID_SIG)).toBe(false);
  });

  it('returns false when the signature header is missing', () => {
    expect(verifyMetaSignature(SECRET, BODY, undefined)).toBe(false);
  });

  it('returns false for a malformed header without the sha256= prefix', () => {
    expect(verifyMetaSignature(SECRET, BODY, 'd0a58f25')).toBe(false);
  });
});

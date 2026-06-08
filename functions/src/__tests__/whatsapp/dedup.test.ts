import { messageDocId } from '../../whatsapp/dedup';

describe('messageDocId', () => {
  it('is deterministic for the same id (so dedup still works across retries)', () => {
    expect(messageDocId('wamid.ABC')).toBe(messageDocId('wamid.ABC'));
  });

  it('maps distinct ids to distinct doc ids', () => {
    expect(messageDocId('wamid.ABC')).not.toBe(messageDocId('wamid.XYZ'));
  });

  it('produces a Firestore-safe id (hex only) even for ids containing slashes', () => {
    const id = messageDocId('wamid.HBg/Mr+oo==');
    expect(id).toMatch(/^[0-9a-f]+$/);
    expect(id).not.toContain('/');
  });
});

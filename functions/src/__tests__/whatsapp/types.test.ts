import { parseInboundMessages } from '../../whatsapp/types';

// A representative WhatsApp Cloud API inbound text payload.
function textPayload(from: string, id: string, body: string): unknown {
  return {
    object: 'whatsapp_business_account',
    entry: [
      {
        id: 'WABA_ID',
        changes: [
          {
            field: 'messages',
            value: {
              messaging_product: 'whatsapp',
              metadata: { display_phone_number: '15550000000', phone_number_id: 'PNID' },
              contacts: [{ profile: { name: 'Tester' }, wa_id: from }],
              messages: [
                { from, id, timestamp: '1700000000', type: 'text', text: { body } },
              ],
            },
          },
        ],
      },
    ],
  };
}

describe('parseInboundMessages', () => {
  it('extracts a text message with sender, id, type and body', () => {
    const result = parseInboundMessages(textPayload('2348012345678', 'wamid.ABC', 'Hello'));
    expect(result).toEqual([
      { waId: '2348012345678', messageId: 'wamid.ABC', type: 'text', text: 'Hello' },
    ]);
  });

  it('returns an empty array for a status-only callback (no messages)', () => {
    const statusPayload = {
      object: 'whatsapp_business_account',
      entry: [{ id: 'WABA_ID', changes: [{ field: 'messages', value: { statuses: [{ id: 'x', status: 'read' }] } }] }],
    };
    expect(parseInboundMessages(statusPayload)).toEqual([]);
  });

  it('returns an empty array for malformed or empty payloads', () => {
    expect(parseInboundMessages(undefined)).toEqual([]);
    expect(parseInboundMessages({})).toEqual([]);
    expect(parseInboundMessages({ entry: 'nope' })).toEqual([]);
  });

  it('extracts multiple messages across entries and changes', () => {
    const payload = {
      object: 'whatsapp_business_account',
      entry: [
        { changes: [{ field: 'messages', value: { messages: [{ from: 'a', id: '1', type: 'text', text: { body: 'one' } }] } }] },
        { changes: [{ field: 'messages', value: { messages: [{ from: 'b', id: '2', type: 'text', text: { body: 'two' } }] } }] },
      ],
    };
    const result = parseInboundMessages(payload);
    expect(result.map((m) => m.messageId)).toEqual(['1', '2']);
    expect(result.map((m) => m.text)).toEqual(['one', 'two']);
  });

  it('represents a non-text message (e.g. image) with empty text and its type', () => {
    const payload = {
      entry: [{ changes: [{ field: 'messages', value: { messages: [{ from: 'a', id: '9', type: 'image', image: { id: 'media1' } }] } }] }],
    };
    expect(parseInboundMessages(payload)).toEqual([
      { waId: 'a', messageId: '9', type: 'image', text: '' },
    ]);
  });
});

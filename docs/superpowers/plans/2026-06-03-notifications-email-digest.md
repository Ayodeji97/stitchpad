# Notifications Slice 1 — Daily Email Digest Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a daily, suppress-when-empty email digest that emails each tailor (≈07:00 Africa/Lagos) about orders due soon, overdue, and ready/delivered-with-balance — establishing the reusable event-detection backbone for later in-app and push slices.

**Architecture:** A new v1 scheduled Cloud Function scans each tailor's orders via the Admin SDK and sends one digest email through Resend, gated by an opt-out preference flag (default on) and a tester allowlist. All detection logic lives in a pure, Jest-tested `digestDetector`; the email body in a pure `buildDigestEmail`; the run loop in a pure `runDailyDigest(io)` with an injected IO seam — mirroring the existing `sendVerificationEmail` handler/IO pattern. The client adds one opt-out toggle in Settings.

**Tech Stack:** TypeScript Cloud Functions (Node 20, `firebase-functions/v1` ^6.0.1, `europe-west1`), Jest; Kotlin Multiplatform + Compose + Koin + GitLive Firebase SDK; client tests via Turbine on `:composeApp:testDebugUnitTest`.

**Design spec:** `docs/superpowers/specs/2026-06-03-notifications-email-digest-design.md`

**Branch:** `feature/notifications-email-digest` (already checked out)

---

## File structure

**Backend (`functions/`):**
- Create `functions/src/notifications/lagosTime.ts` — pure Lagos-timezone day helpers + constants.
- Create `functions/src/notifications/types.ts` — `OrderScanDoc`, `DigestItem`, `DigestModel`, `DigestRecipient`, `DigestIO`, `DigestRunResult`.
- Create `functions/src/notifications/digestDetector.ts` — pure `digestDetector(orders, now)` + `isDigestEmpty`.
- Create `functions/src/notifications/digestEmailTemplate.ts` — pure `buildDigestEmail(model, tailorName)`.
- Create `functions/src/notifications/rollout.ts` — `isDigestAllowed(uid, email)` tester allowlist.
- Create `functions/src/notifications/runDailyDigest.ts` — pure run loop over `DigestIO`.
- Create `functions/src/notifications/dailyDigest.ts` — `onSchedule` wiring + `productionDigestIO` + `debugSendMyDigest` callable.
- Create `functions/src/email/resendClient.ts` — extracted shared `sendResendEmail(...)`.
- Modify `functions/src/auth/sendVerificationEmail.ts` — call the extracted client.
- Modify `functions/src/index.ts` — export `dailyDigest`, `debugSendMyDigest`.
- Modify `functions/package.json` — add both functions to the deploy `--only` list.
- Tests under `functions/__tests__/notifications/` and `functions/__tests__/email/`.

**Client (`composeApp/`):**
- Modify `core/domain/model/User.kt` — add `dailyDigestEmailEnabled`.
- Modify `core/data/dto/UserDto.kt` — add field (default true).
- Modify `core/data/mapper/UserMapper.kt` — map both directions.
- Modify `core/domain/repository/UserRepository.kt` — add `setDailyDigestEmailEnabled`.
- Modify `core/data/repository/FirebaseUserRepository.kt` — implement it.
- Modify `feature/settings/presentation/home/SettingsState.kt` / `SettingsAction.kt` / `SettingsViewModel.kt` / `SettingsScreen.kt` — toggle wiring + row.
- Add string resources for the row label.
- Test `composeApp/src/commonTest/.../settings/SettingsViewModelTest.kt` (extend or create).

---

## TASK 1 — Lagos time helpers (pure)

**Files:**
- Create: `functions/src/notifications/lagosTime.ts`
- Test: `functions/__tests__/notifications/lagosTime.test.ts`

- [ ] **Step 1: Write the failing test**

```typescript
// functions/__tests__/notifications/lagosTime.test.ts
import { lagosDayIndex, lagosDateKey, LAGOS_OFFSET_MS, DAY_MS } from '../../src/notifications/lagosTime';

describe('lagosTime', () => {
  // 2026-06-03T05:30:00Z = 2026-06-03 06:30 Lagos (UTC+1)
  const morningUtc = Date.parse('2026-06-03T05:30:00Z');
  // 2026-06-03T23:30:00Z = 2026-06-04 00:30 Lagos — crosses the day boundary
  const lateUtc = Date.parse('2026-06-03T23:30:00Z');

  it('LAGOS_OFFSET_MS is +1h, DAY_MS is 24h', () => {
    expect(LAGOS_OFFSET_MS).toBe(3_600_000);
    expect(DAY_MS).toBe(86_400_000);
  });

  it('lagosDayIndex puts a late-evening UTC time on the next Lagos day', () => {
    expect(lagosDayIndex(lateUtc)).toBe(lagosDayIndex(morningUtc) + 1);
  });

  it('lagosDateKey returns the Lagos calendar date, not the UTC date', () => {
    expect(lagosDateKey(morningUtc)).toBe('2026-06-03');
    expect(lagosDateKey(lateUtc)).toBe('2026-06-04');
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd functions && npx jest notifications/lagosTime`
Expected: FAIL — `Cannot find module '../../src/notifications/lagosTime'`.

- [ ] **Step 3: Write minimal implementation**

```typescript
// functions/src/notifications/lagosTime.ts
/**
 * Africa/Lagos is UTC+1 year-round (no DST), so a fixed +1h offset is exact.
 * Shifting the epoch by the offset and reading it as UTC gives the Lagos
 * calendar day without pulling in a timezone library.
 */
export const DAY_MS = 86_400_000;
export const LAGOS_OFFSET_MS = 3_600_000;

/** Whole-day index in Lagos time. Two timestamps on the same Lagos date share an index. */
export function lagosDayIndex(epochMillis: number): number {
  return Math.floor((epochMillis + LAGOS_OFFSET_MS) / DAY_MS);
}

/** Lagos calendar date as 'YYYY-MM-DD' (used as the idempotency key). */
export function lagosDateKey(epochMillis: number): string {
  return new Date(epochMillis + LAGOS_OFFSET_MS).toISOString().slice(0, 10);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd functions && npx jest notifications/lagosTime`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add functions/src/notifications/lagosTime.ts functions/__tests__/notifications/lagosTime.test.ts
git commit -m "feat(notifications): pure Lagos-timezone day helpers"
```

---

## TASK 2 — Shared types

**Files:**
- Create: `functions/src/notifications/types.ts`

No test (type-only module; exercised by Tasks 3–6).

- [ ] **Step 1: Write the module**

```typescript
// functions/src/notifications/types.ts

/** A tailor's order as read from `users/{uid}/orders` (raw Admin SDK shape). */
export interface OrderScanDoc {
  id: string;
  customerName: string;
  status: 'PENDING' | 'IN_PROGRESS' | 'READY' | 'DELIVERED' | string;
  deadline: number | null;     // epoch millis; null = no deadline set
  archivedAt: number | null;   // epoch millis; non-null = archived (excluded)
  totalPrice: number;
  payments: { amount: number }[];
  items: { garmentType?: string; customGarmentName?: string; description?: string }[];
}

export interface DigestItem {
  customerName: string;
  garmentSummary: string;
  deadline?: number; // present for dueSoon / overdue
  amount?: number;   // present for outstanding (naira)
}

export interface DigestModel {
  dueSoon: DigestItem[];      // capped
  overdue: DigestItem[];      // capped
  outstanding: DigestItem[];  // capped
  dueSoonTotal: number;       // pre-cap counts, for "+N more"
  overdueTotal: number;
  outstandingTotal: number;
}

export interface DigestRecipient {
  uid: string;
  email: string;
  name: string;          // businessName || displayName || email prefix
  digestEnabled: boolean; // false only when explicitly opted out
}

export interface DigestIO {
  listRecipients(): Promise<DigestRecipient[]>;
  loadOrders(uid: string): Promise<OrderScanDoc[]>;
  getLastSentDate(uid: string): Promise<string | null>;
  setLastSentDate(uid: string, dateKey: string): Promise<void>;
  sendEmail(p: { to: string; subject: string; html: string; text: string }): Promise<void>;
  isAllowed(uid: string, email: string): boolean;
}

export interface DigestRunResult {
  considered: number;
  sent: number;
  suppressedEmpty: number;
  skippedDisabled: number;
  skippedAlreadySent: number;
  skippedNotAllowed: number;
  failed: number;
}
```

- [ ] **Step 2: Verify it compiles**

Run: `cd functions && npx tsc --noEmit`
Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add functions/src/notifications/types.ts
git commit -m "feat(notifications): shared digest types + IO seam"
```

---

## TASK 3 — Digest detector (pure, the backbone)

**Files:**
- Create: `functions/src/notifications/digestDetector.ts`
- Test: `functions/__tests__/notifications/digestDetector.test.ts`

- [ ] **Step 1: Write the failing test**

```typescript
// functions/__tests__/notifications/digestDetector.test.ts
import { digestDetector, isDigestEmpty } from '../../src/notifications/digestDetector';
import { OrderScanDoc } from '../../src/notifications/types';

const NOW = Date.parse('2026-06-03T06:00:00Z'); // 07:00 Lagos
const DAY = 86_400_000;

function order(p: Partial<OrderScanDoc>): OrderScanDoc {
  return {
    id: 'o', customerName: 'Ada', status: 'IN_PROGRESS', deadline: null,
    archivedAt: null, totalPrice: 0, payments: [], items: [{ garmentType: 'Agbada' }], ...p,
  };
}

describe('digestDetector', () => {
  it('flags an order due tomorrow as dueSoon, not overdue', () => {
    const m = digestDetector([order({ deadline: NOW + DAY })], NOW);
    expect(m.dueSoonTotal).toBe(1);
    expect(m.overdueTotal).toBe(0);
    expect(m.dueSoon[0].customerName).toBe('Ada');
  });

  it('flags an order due today as dueSoon', () => {
    const m = digestDetector([order({ deadline: NOW + 5 * 3600_000 })], NOW);
    expect(m.dueSoonTotal).toBe(1);
  });

  it('flags a past-day deadline as overdue', () => {
    const m = digestDetector([order({ deadline: NOW - DAY })], NOW);
    expect(m.overdueTotal).toBe(1);
    expect(m.dueSoonTotal).toBe(0);
  });

  it('does not flag deadlines beyond tomorrow', () => {
    const m = digestDetector([order({ deadline: NOW + 3 * DAY })], NOW);
    expect(isDigestEmpty(m)).toBe(true);
  });

  it('ignores null-deadline and archived orders for due/overdue', () => {
    const m = digestDetector([
      order({ deadline: null }),
      order({ deadline: NOW - DAY, archivedAt: NOW }),
    ], NOW);
    expect(m.dueSoonTotal + m.overdueTotal).toBe(0);
  });

  it('flags DELIVERED-with-balance and READY-with-balance as outstanding', () => {
    const m = digestDetector([
      order({ status: 'DELIVERED', totalPrice: 10000, payments: [{ amount: 4000 }] }),
      order({ status: 'READY', totalPrice: 5000, payments: [] }),
    ], NOW);
    expect(m.outstandingTotal).toBe(2);
    expect(m.outstanding[0].amount).toBe(6000); // biggest owed first (DELIVERED bal 6000 > READY bal 5000)
  });

  it('excludes in-progress balances and sub-naira residue from outstanding', () => {
    const m = digestDetector([
      order({ status: 'IN_PROGRESS', totalPrice: 9000, payments: [{ amount: 1000 }] }),
      order({ status: 'DELIVERED', totalPrice: 5000, payments: [{ amount: 4999.7 }] }),
    ], NOW);
    expect(m.outstandingTotal).toBe(0);
  });

  it('caps each bucket at 5 but keeps the true total', () => {
    const many = Array.from({ length: 8 }, (_, i) => order({ deadline: NOW - DAY, customerName: `c${i}` }));
    const m = digestDetector(many, NOW);
    expect(m.overdue.length).toBe(5);
    expect(m.overdueTotal).toBe(8);
  });

  it('summarises multiple garments', () => {
    const m = digestDetector([order({
      deadline: NOW + DAY,
      items: [{ garmentType: 'Agbada' }, { garmentType: 'Buba' }],
    })], NOW);
    expect(m.dueSoon[0].garmentSummary).toBe('Agbada +1 more');
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd functions && npx jest notifications/digestDetector`
Expected: FAIL — module not found.

- [ ] **Step 3: Write minimal implementation**

```typescript
// functions/src/notifications/digestDetector.ts
import { lagosDayIndex } from './lagosTime';
import { DigestItem, DigestModel, OrderScanDoc } from './types';

const CAP = 5;
const MIN_BALANCE = 1; // ignore sub-naira rounding residue from totalPrice - payments

function balanceRemaining(o: OrderScanDoc): number {
  const paid = o.payments.reduce((sum, p) => sum + (p.amount || 0), 0);
  return Math.max(0, o.totalPrice - paid);
}

function summariseGarments(items: OrderScanDoc['items']): string {
  if (!items || items.length === 0) return 'Order';
  const f = items[0];
  const name = (f.customGarmentName?.trim() || f.garmentType?.trim() || f.description?.trim() || 'Garment');
  return items.length > 1 ? `${name} +${items.length - 1} more` : name;
}

export function digestDetector(orders: OrderScanDoc[], now: number): DigestModel {
  const today = lagosDayIndex(now);
  const dueSoon: DigestItem[] = [];
  const overdue: DigestItem[] = [];
  const outstanding: DigestItem[] = [];

  for (const o of orders) {
    const open = o.status !== 'DELIVERED' && o.archivedAt == null;

    if (open && o.deadline != null) {
      const day = lagosDayIndex(o.deadline);
      const item: DigestItem = { customerName: o.customerName, garmentSummary: summariseGarments(o.items), deadline: o.deadline };
      if (day < today) overdue.push(item);
      else if (day === today || day === today + 1) dueSoon.push(item);
    }

    // Outstanding draws from READY (open) and DELIVERED (not open); excludes archived.
    if ((o.status === 'READY' || o.status === 'DELIVERED') && o.archivedAt == null) {
      const bal = balanceRemaining(o);
      if (bal >= MIN_BALANCE) {
        outstanding.push({ customerName: o.customerName, garmentSummary: summariseGarments(o.items), amount: Math.round(bal) });
      }
    }
  }

  overdue.sort((a, b) => (a.deadline! - b.deadline!));  // most overdue first
  dueSoon.sort((a, b) => (a.deadline! - b.deadline!));  // soonest first
  outstanding.sort((a, b) => (b.amount! - a.amount!));  // biggest owed first

  return {
    dueSoon: dueSoon.slice(0, CAP),
    overdue: overdue.slice(0, CAP),
    outstanding: outstanding.slice(0, CAP),
    dueSoonTotal: dueSoon.length,
    overdueTotal: overdue.length,
    outstandingTotal: outstanding.length,
  };
}

export function isDigestEmpty(m: DigestModel): boolean {
  return m.dueSoonTotal === 0 && m.overdueTotal === 0 && m.outstandingTotal === 0;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd functions && npx jest notifications/digestDetector`
Expected: PASS (9 tests).

- [ ] **Step 5: Commit**

```bash
git add functions/src/notifications/digestDetector.ts functions/__tests__/notifications/digestDetector.test.ts
git commit -m "feat(notifications): pure digest detector (due/overdue/outstanding buckets)"
```

---

## TASK 4 — Digest email template (pure)

**Files:**
- Create: `functions/src/notifications/digestEmailTemplate.ts`
- Test: `functions/__tests__/notifications/digestEmailTemplate.test.ts`

- [ ] **Step 1: Write the failing test**

```typescript
// functions/__tests__/notifications/digestEmailTemplate.test.ts
import { buildDigestEmail } from '../../src/notifications/digestEmailTemplate';
import { DigestModel } from '../../src/notifications/types';

function model(p: Partial<DigestModel> = {}): DigestModel {
  return { dueSoon: [], overdue: [], outstanding: [], dueSoonTotal: 0, overdueTotal: 0, outstandingTotal: 0, ...p };
}

describe('buildDigestEmail', () => {
  it('builds a subject ordered overdue → due → balance', () => {
    const { subject } = buildDigestEmail(model({
      overdueTotal: 1, dueSoonTotal: 2, outstandingTotal: 3,
    }), 'Ada Couture');
    expect(subject).toBe('StitchPad: 1 overdue, 2 due soon, 3 to collect');
  });

  it('greets by tailor name and renders only non-empty sections', () => {
    const { html, text } = buildDigestEmail(model({
      overdue: [{ customerName: 'Bola', garmentSummary: 'Agbada', deadline: 0 }],
      overdueTotal: 1,
    }), 'Ada Couture');
    expect(html).toContain('Ada Couture');
    expect(html).toContain('Bola');
    expect(html).not.toContain('Due soon'); // empty section omitted
    expect(text).toContain('Bola');
  });

  it('shows a +N more line when a bucket is capped', () => {
    const { html } = buildDigestEmail(model({
      overdue: Array.from({ length: 5 }, (_, i) => ({ customerName: `c${i}`, garmentSummary: 'x', deadline: 0 })),
      overdueTotal: 8,
    }), 'Ada');
    expect(html).toContain('+3 more');
  });

  it('formats outstanding amounts as naira with thousands separators', () => {
    const { html } = buildDigestEmail(model({
      outstanding: [{ customerName: 'Ada', garmentSummary: 'Buba', amount: 15000 }],
      outstandingTotal: 1,
    }), 'Ada');
    expect(html).toContain('₦15,000');
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd functions && npx jest notifications/digestEmailTemplate`
Expected: FAIL — module not found.

- [ ] **Step 3: Write minimal implementation**

```typescript
// functions/src/notifications/digestEmailTemplate.ts
/**
 * Pure HTML+text builder for the daily digest. Inline styles only (email clients
 * strip <style>). Adire Atelier palette mirrors verificationEmailTemplate.ts;
 * duplicated here intentionally so the two email types stay independent.
 */
import { DigestItem, DigestModel } from './types';

const INDIGO = '#2C3E7C';
const WHITE = '#FFFFFF';
const INK = '#252320';
const MUTED = '#57534C';
const FAINT = '#A8A49D';
const BORDER = '#E5E3DF';
const FONT_STACK = '\'Plus Jakarta Sans\',-apple-system,BlinkMacSystemFont,\'Segoe UI\',Roboto,Helvetica,Arial,sans-serif';
const SERIF_STACK = 'Georgia,\'Times New Roman\',serif';
const LOGO_URL = 'https://firebasestorage.googleapis.com/v0/b/stitchpad-30607.firebasestorage.app/o/stitchpad-email-logo.png?alt=media&token=d05c88f4-d9c4-4085-a0a8-a136e0c9d8b3'; // gitleaks:allow

function escapeHtml(v: string): string {
  return v.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

function naira(amount: number): string {
  return `₦${Math.round(amount).toLocaleString('en-NG')}`;
}

export function buildDigestEmail(model: DigestModel, tailorName: string): { subject: string; html: string; text: string } {
  const name = tailorName?.trim() ? tailorName.trim() : 'there';

  const subjectParts: string[] = [];
  if (model.overdueTotal > 0) subjectParts.push(`${model.overdueTotal} overdue`);
  if (model.dueSoonTotal > 0) subjectParts.push(`${model.dueSoonTotal} due soon`);
  if (model.outstandingTotal > 0) subjectParts.push(`${model.outstandingTotal} to collect`);
  const subject = `StitchPad: ${subjectParts.join(', ')}`;

  const sections: { title: string; items: DigestItem[]; total: number; line: (i: DigestItem) => string }[] = [
    { title: 'Overdue', items: model.overdue, total: model.overdueTotal, line: (i) => `${i.customerName} · ${i.garmentSummary}` },
    { title: 'Due soon', items: model.dueSoon, total: model.dueSoonTotal, line: (i) => `${i.customerName} · ${i.garmentSummary}` },
    { title: 'To collect', items: model.outstanding, total: model.outstandingTotal, line: (i) => `${i.customerName} · ${i.garmentSummary} — ${naira(i.amount || 0)}` },
  ];

  const htmlSections = sections.filter((s) => s.total > 0).map((s) => {
    const rows = s.items.map((i) => `<p style="margin:0 0 6px;font-size:14px;line-height:1.5;color:${MUTED};">${escapeHtml(s.line(i))}</p>`).join('');
    const more = s.total > s.items.length ? `<p style="margin:6px 0 0;font-size:13px;color:${FAINT};">+${s.total - s.items.length} more</p>` : '';
    return `<div style="margin:0 0 24px;"><h2 style="margin:0 0 10px;font-size:13px;font-weight:800;letter-spacing:0.6px;text-transform:uppercase;color:${INDIGO};">${escapeHtml(s.title)} (${s.total})</h2>${rows}${more}</div>`;
  }).join('');

  const html = `<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8" /><meta name="viewport" content="width=device-width, initial-scale=1.0" /><meta name="color-scheme" content="light only" /></head>
<body style="margin:0;padding:0;background-color:${WHITE};font-family:${FONT_STACK};">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background-color:${WHITE};padding:44px 16px;"><tr><td align="center">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:480px;background-color:${WHITE};border:1px solid ${BORDER};border-radius:14px;"><tr><td style="padding:36px 44px 40px;">
<table role="presentation" cellpadding="0" cellspacing="0" style="margin:0 0 30px;"><tr>
<td style="vertical-align:middle;padding-right:10px;"><img src="${escapeHtml(LOGO_URL)}" width="34" height="34" alt="StitchPad" style="display:block;border:0;width:34px;height:34px;" /></td>
<td style="vertical-align:middle;"><span style="font-size:18px;font-weight:800;color:${INDIGO};letter-spacing:-0.2px;">StitchPad</span></td>
</tr></table>
<h1 style="margin:0 0 18px;font-family:${SERIF_STACK};font-size:26px;font-weight:700;color:${INDIGO};line-height:1.2;">Good morning, ${escapeHtml(name)}</h1>
<p style="margin:0 0 28px;font-size:15px;line-height:1.6;color:${INK};">Here's what needs your attention today.</p>
${htmlSections}
<p style="margin:30px 0 0;font-size:12px;line-height:1.6;color:${FAINT};">You're getting this because daily summaries are on. Turn them off in Settings → Notifications.</p>
</td></tr></table></td></tr></table></body></html>`;

  const textSections = sections.filter((s) => s.total > 0).map((s) => {
    const rows = s.items.map((i) => `  - ${s.line(i)}`).join('\n');
    const more = s.total > s.items.length ? `\n  +${s.total - s.items.length} more` : '';
    return `${s.title} (${s.total}):\n${rows}${more}`;
  }).join('\n\n');
  const text = `Good morning, ${name}\nHere's what needs your attention today.\n\n${textSections}\n\nTurn off daily summaries in Settings → Notifications.`;

  return { subject, html, text };
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd functions && npx jest notifications/digestEmailTemplate`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add functions/src/notifications/digestEmailTemplate.ts functions/__tests__/notifications/digestEmailTemplate.test.ts
git commit -m "feat(notifications): pure digest email template (html + text)"
```

---

## TASK 5 — Extract shared Resend client

**Files:**
- Create: `functions/src/email/resendClient.ts`
- Modify: `functions/src/auth/sendVerificationEmail.ts` (replace inline `sendViaResend` body)
- Test: `functions/__tests__/email/resendClient.test.ts`

- [ ] **Step 1: Write the failing test**

```typescript
// functions/__tests__/email/resendClient.test.ts
import { sendResendEmail } from '../../src/email/resendClient';

describe('sendResendEmail', () => {
  const realFetch = global.fetch;
  afterEach(() => { global.fetch = realFetch; });

  it('POSTs to Resend with auth header and from/reply-to', async () => {
    const calls: any[] = [];
    global.fetch = (async (url: any, init: any) => {
      calls.push({ url, init });
      return { ok: true, status: 200, text: async () => '' } as any;
    }) as any;

    await sendResendEmail('key_123', { to: 'a@b.com', subject: 'Hi', html: '<p>x</p>', text: 'x' });

    expect(calls).toHaveLength(1);
    expect(calls[0].url).toBe('https://api.resend.com/emails');
    expect(calls[0].init.headers.Authorization).toBe('Bearer key_123');
    const body = JSON.parse(calls[0].init.body);
    expect(body.from).toContain('noreply@send.getstitchpad.com');
    expect(body.reply_to).toBe('support@getstitchpad.com');
    expect(body.to).toEqual(['a@b.com']);
    expect(body.subject).toBe('Hi');
  });

  it('throws with status detail when Resend responds non-ok', async () => {
    global.fetch = (async () => ({ ok: false, status: 422, text: async () => 'bad' } as any)) as any;
    await expect(sendResendEmail('k', { to: 'a@b.com', subject: 's', html: 'h', text: 't' }))
      .rejects.toThrow('Resend responded 422: bad');
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd functions && npx jest email/resendClient`
Expected: FAIL — module not found.

- [ ] **Step 3: Write the client**

```typescript
// functions/src/email/resendClient.ts
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
```

- [ ] **Step 4: Refactor the verification sender to use it**

In `functions/src/auth/sendVerificationEmail.ts`, replace the `sendViaResend` function body so it delegates (keep the `SUBJECT`/template wiring; drop the duplicated `FROM`/`REPLY_TO`/`RESEND_ENDPOINT` constants and the inline fetch):

```typescript
import { sendResendEmail } from '../email/resendClient';
// ...remove: const FROM, REPLY_TO, RESEND_ENDPOINT (now in resendClient)

async function sendViaResend(
  apiKey: string,
  params: { to: string; displayName?: string; verifyLink: string },
): Promise<void> {
  const html = buildVerificationEmailHtml({ displayName: params.displayName, verifyLink: params.verifyLink });
  await sendResendEmail(apiKey, { to: params.to, subject: SUBJECT, html });
}
```

- [ ] **Step 5: Run all functions tests to verify no regression**

Run: `cd functions && npx jest`
Expected: PASS — new `email/resendClient` tests plus the existing verification/template/cleanup suites all green.

- [ ] **Step 6: Commit**

```bash
git add functions/src/email/resendClient.ts functions/src/auth/sendVerificationEmail.ts functions/__tests__/email/resendClient.test.ts
git commit -m "refactor(functions): extract shared sendResendEmail client"
```

---

## TASK 6 — Rollout allowlist (pure)

**Files:**
- Create: `functions/src/notifications/rollout.ts`
- Test: `functions/__tests__/notifications/rollout.test.ts`

- [ ] **Step 1: Write the failing test**

```typescript
// functions/__tests__/notifications/rollout.test.ts
import { isDigestAllowed, DIGEST_ALLOWLIST } from '../../src/notifications/rollout';

describe('isDigestAllowed', () => {
  it('allows allowlisted emails (case-insensitive) during staging', () => {
    const email = DIGEST_ALLOWLIST[0];
    expect(isDigestAllowed('uid', email.toUpperCase())).toBe(true);
  });
  it('blocks non-allowlisted recipients during staging', () => {
    expect(isDigestAllowed('uid', 'stranger@example.com')).toBe(false);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd functions && npx jest notifications/rollout`
Expected: FAIL — module not found.

- [ ] **Step 3: Write the implementation**

```typescript
// functions/src/notifications/rollout.ts
/**
 * Staged rollout gate. While STAGING is true the digest only sends to the
 * allowlisted test accounts — a single detector bug must not email every
 * tester a wrong digest. Flip STAGING to false (one line) to open to all users
 * once verified against real mornings. See the design spec "Rollout" section.
 */
const STAGING = true;

// Test-account emails (lower-cased). Replace/extend with the real tester emails.
export const DIGEST_ALLOWLIST: string[] = [
  'fola.tailor@getstitchpad.com',
  'gabby.tailor@getstitchpad.com',
];

export function isDigestAllowed(_uid: string, email: string): boolean {
  if (!STAGING) return true;
  return DIGEST_ALLOWLIST.includes(email.trim().toLowerCase());
}
```

> NOTE: confirm the two allowlist emails against `reference_test_environment` (Fola/Gabby test accounts) before flipping rollout to production.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd functions && npx jest notifications/rollout`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add functions/src/notifications/rollout.ts functions/__tests__/notifications/rollout.test.ts
git commit -m "feat(notifications): staged rollout allowlist for digest sends"
```

---

## TASK 7 — Run loop (pure, over the IO seam)

**Files:**
- Create: `functions/src/notifications/runDailyDigest.ts`
- Test: `functions/__tests__/notifications/runDailyDigest.test.ts`

- [ ] **Step 1: Write the failing test**

```typescript
// functions/__tests__/notifications/runDailyDigest.test.ts
import { runDailyDigest } from '../../src/notifications/runDailyDigest';
import { DigestIO, DigestRecipient, OrderScanDoc } from '../../src/notifications/types';

const NOW = Date.parse('2026-06-03T06:00:00Z');
const DAY = 86_400_000;

function fakeIO(over: Partial<DigestIO> & { recipients: DigestRecipient[]; ordersByUid: Record<string, OrderScanDoc[]> }): {
  io: DigestIO; sent: { to: string; subject: string }[]; stamps: Record<string, string>;
} {
  const sent: { to: string; subject: string }[] = [];
  const stamps: Record<string, string> = {};
  const io: DigestIO = {
    listRecipients: async () => over.recipients,
    loadOrders: async (uid) => over.ordersByUid[uid] || [],
    getLastSentDate: async (uid) => stamps[uid] ?? null,
    setLastSentDate: async (uid, d) => { stamps[uid] = d; },
    sendEmail: async (p) => { sent.push({ to: p.to, subject: p.subject }); },
    isAllowed: over.isAllowed ?? (() => true),
  };
  return { io, sent, stamps };
}

const recip = (p: Partial<DigestRecipient> = {}): DigestRecipient => ({ uid: 'u1', email: 'u1@x.com', name: 'Ada', digestEnabled: true, ...p });
const order = (p: Partial<OrderScanDoc>): OrderScanDoc => ({ id: 'o', customerName: 'C', status: 'IN_PROGRESS', deadline: null, archivedAt: null, totalPrice: 0, payments: [], items: [], ...p });

describe('runDailyDigest', () => {
  it('sends one email to a tailor with actionable orders and stamps the date', async () => {
    const { io, sent, stamps } = fakeIO({ recipients: [recip()], ordersByUid: { u1: [order({ deadline: NOW - DAY })] } });
    const r = await runDailyDigest(io, NOW);
    expect(sent).toHaveLength(1);
    expect(r.sent).toBe(1);
    expect(stamps.u1).toBe('2026-06-03');
  });

  it('suppresses when there is nothing actionable', async () => {
    const { io, sent } = fakeIO({ recipients: [recip()], ordersByUid: { u1: [] } });
    const r = await runDailyDigest(io, NOW);
    expect(sent).toHaveLength(0);
    expect(r.suppressedEmpty).toBe(1);
  });

  it('skips opted-out tailors', async () => {
    const { io, sent } = fakeIO({ recipients: [recip({ digestEnabled: false })], ordersByUid: { u1: [order({ deadline: NOW - DAY })] } });
    const r = await runDailyDigest(io, NOW);
    expect(sent).toHaveLength(0);
    expect(r.skippedDisabled).toBe(1);
  });

  it('skips when already sent today', async () => {
    const { io, sent, stamps } = fakeIO({ recipients: [recip()], ordersByUid: { u1: [order({ deadline: NOW - DAY })] } });
    stamps.u1 = '2026-06-03';
    const r = await runDailyDigest(io, NOW);
    expect(sent).toHaveLength(0);
    expect(r.skippedAlreadySent).toBe(1);
  });

  it('skips non-allowlisted recipients', async () => {
    const { io, sent } = fakeIO({ recipients: [recip()], ordersByUid: { u1: [order({ deadline: NOW - DAY })] }, isAllowed: () => false });
    const r = await runDailyDigest(io, NOW);
    expect(sent).toHaveLength(0);
    expect(r.skippedNotAllowed).toBe(1);
  });

  it('isolates a failing recipient so others still send', async () => {
    const { io, sent } = fakeIO({
      recipients: [recip({ uid: 'u1', email: 'u1@x.com' }), recip({ uid: 'u2', email: 'u2@x.com' })],
      ordersByUid: { u1: [order({ deadline: NOW - DAY })], u2: [order({ deadline: NOW - DAY })] },
    });
    io.loadOrders = async (uid) => { if (uid === 'u1') throw new Error('boom'); return [order({ deadline: NOW - DAY })]; };
    const r = await runDailyDigest(io, NOW);
    expect(r.failed).toBe(1);
    expect(sent.map((s) => s.to)).toEqual(['u2@x.com']);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd functions && npx jest notifications/runDailyDigest`
Expected: FAIL — module not found.

- [ ] **Step 3: Write the implementation**

```typescript
// functions/src/notifications/runDailyDigest.ts
import * as functions from 'firebase-functions/v1';
import { digestDetector, isDigestEmpty } from './digestDetector';
import { buildDigestEmail } from './digestEmailTemplate';
import { lagosDateKey } from './lagosTime';
import { DigestIO, DigestRunResult } from './types';

/** Pure run loop. Production wraps this with productionDigestIO; tests inject fakes. */
export async function runDailyDigest(io: DigestIO, now: number): Promise<DigestRunResult> {
  const recipients = await io.listRecipients();
  const todayKey = lagosDateKey(now);
  const result: DigestRunResult = {
    considered: recipients.length, sent: 0, suppressedEmpty: 0,
    skippedDisabled: 0, skippedAlreadySent: 0, skippedNotAllowed: 0, failed: 0,
  };

  for (const r of recipients) {
    try {
      if (!r.digestEnabled) { result.skippedDisabled++; continue; }
      if (!io.isAllowed(r.uid, r.email)) { result.skippedNotAllowed++; continue; }
      if ((await io.getLastSentDate(r.uid)) === todayKey) { result.skippedAlreadySent++; continue; }

      const model = digestDetector(await io.loadOrders(r.uid), now);
      if (isDigestEmpty(model)) { result.suppressedEmpty++; continue; }

      const { subject, html, text } = buildDigestEmail(model, r.name);
      await io.sendEmail({ to: r.email, subject, html, text });
      await io.setLastSentDate(r.uid, todayKey);
      result.sent++;
    } catch (err) {
      result.failed++;
      functions.logger.error('daily digest: recipient failed', {
        uid: r.uid, error: err instanceof Error ? err.message : String(err),
      });
    }
  }

  functions.logger.info('daily digest run complete', { ...result });
  return result;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd functions && npx jest notifications/runDailyDigest`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add functions/src/notifications/runDailyDigest.ts functions/__tests__/notifications/runDailyDigest.test.ts
git commit -m "feat(notifications): pure daily-digest run loop with per-recipient isolation"
```

---

## TASK 8 — Production wiring: scheduled function + IO + debug callable

**Files:**
- Create: `functions/src/notifications/dailyDigest.ts`
- Modify: `functions/src/index.ts`
- Modify: `functions/package.json`

No new unit test (pure logic is covered by Tasks 1–7; this is I/O wiring verified by `tsc`, lint, and the manual smoke test in Task 14).

- [ ] **Step 1: Write the production IO + functions**

```typescript
// functions/src/notifications/dailyDigest.ts
import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';
import { runDailyDigest } from './runDailyDigest';
import { isDigestAllowed } from './rollout';
import { sendResendEmail } from '../email/resendClient';
import { buildDigestEmail } from './digestEmailTemplate';
import { digestDetector, isDigestEmpty } from './digestDetector';
import { lagosDateKey } from './lagosTime';
import { DigestIO, DigestRecipient, OrderScanDoc } from './types';

const REGION = 'europe-west1';
const SCHEDULE = '0 7 * * *';
const TIMEZONE = 'Africa/Lagos';

function digestStateRef(uid: string) {
  return admin.firestore().collection('users').doc(uid).collection('private').doc('digestState');
}

function mapOrder(id: string, d: FirebaseFirestore.DocumentData): OrderScanDoc {
  return {
    id,
    customerName: d.customerName ?? '',
    status: d.status ?? 'PENDING',
    deadline: typeof d.deadline === 'number' ? d.deadline : null,
    archivedAt: typeof d.archivedAt === 'number' ? d.archivedAt : null,
    totalPrice: typeof d.totalPrice === 'number' ? d.totalPrice : 0,
    payments: Array.isArray(d.payments) ? d.payments.map((p: any) => ({ amount: Number(p?.amount) || 0 })) : [],
    items: Array.isArray(d.items) ? d.items.map((i: any) => ({
      garmentType: i?.garmentType, customGarmentName: i?.customGarmentName, description: i?.description,
    })) : [],
  };
}

function productionDigestIO(apiKey: string): DigestIO {
  const db = admin.firestore();
  return {
    async listRecipients(): Promise<DigestRecipient[]> {
      const usersSnap = await db.collection('users').get();
      const recipients: DigestRecipient[] = [];
      for (const doc of usersSnap.docs) {
        const data = doc.data();
        let email: string | undefined;
        try {
          const authUser = await admin.auth().getUser(doc.id);
          if (!authUser.email || !authUser.emailVerified) continue;
          email = authUser.email;
        } catch {
          continue; // doc with no matching/verified auth user — skip
        }
        const name = (data.businessName?.trim() || data.displayName?.trim() || email.split('@')[0]);
        recipients.push({ uid: doc.id, email, name, digestEnabled: data.dailyDigestEmailEnabled !== false });
      }
      return recipients;
    },
    async loadOrders(uid) {
      const snap = await db.collection('users').doc(uid).collection('orders').get();
      return snap.docs.map((d) => mapOrder(d.id, d.data()));
    },
    async getLastSentDate(uid) {
      const snap = await digestStateRef(uid).get();
      return (snap.exists && snap.data()?.lastSentDate) || null;
    },
    async setLastSentDate(uid, dateKey) {
      await digestStateRef(uid).set({ lastSentDate: dateKey }, { merge: true });
    },
    sendEmail(p) {
      return sendResendEmail(apiKey, p);
    },
    isAllowed: isDigestAllowed,
  };
}

export const dailyDigest = functions
  .region(REGION)
  .runWith({ secrets: ['RESEND_API_KEY'] })
  .pubsub.schedule(SCHEDULE)
  .timeZone(TIMEZONE)
  .onRun(async () => {
    const apiKey = process.env.RESEND_API_KEY;
    if (!apiKey) {
      functions.logger.error('RESEND_API_KEY secret is not configured');
      return;
    }
    await runDailyDigest(productionDigestIO(apiKey), Date.now());
  });

/**
 * Debug/QA trigger: runs the digest for the CALLER only, ignoring the
 * already-sent stamp and the rollout allowlist, so a tester can verify content
 * on demand. Still respects suppress-when-empty and the opt-out flag.
 */
export const debugSendMyDigest = functions
  .region(REGION)
  .runWith({ secrets: ['RESEND_API_KEY'] })
  .https.onCall(async (_data, context) => {
    const uid = context.auth?.uid;
    if (!uid) throw new functions.https.HttpsError('unauthenticated', 'Sign in required.');
    const apiKey = process.env.RESEND_API_KEY;
    if (!apiKey) throw new functions.https.HttpsError('failed-precondition', 'email_not_configured');

    const db = admin.firestore();
    const userDoc = await db.collection('users').doc(uid).get();
    if (userDoc.data()?.dailyDigestEmailEnabled === false) {
      return { sent: false, reason: 'disabled' };
    }
    const authUser = await admin.auth().getUser(uid);
    if (!authUser.email) throw new functions.https.HttpsError('failed-precondition', 'no_email_on_account');

    const ordersSnap = await db.collection('users').doc(uid).collection('orders').get();
    const model = digestDetector(ordersSnap.docs.map((d) => mapOrder(d.id, d.data())), Date.now());
    if (isDigestEmpty(model)) return { sent: false, reason: 'empty' };

    const data = userDoc.data() || {};
    const name = (data.businessName?.trim() || data.displayName?.trim() || authUser.email.split('@')[0]);
    const { subject, html, text } = buildDigestEmail(model, name);
    await sendResendEmail(apiKey, { to: authUser.email, subject, html, text });
    await digestStateRef(uid).set({ lastSentDate: lagosDateKey(Date.now()) }, { merge: true });
    return { sent: true };
  });
```

- [ ] **Step 2: Export from index.ts**

Add to the end of `functions/src/index.ts` (alongside the existing `export { ... }` lines):

```typescript
export { dailyDigest, debugSendMyDigest } from './notifications/dailyDigest';
```

- [ ] **Step 3: Add both functions to the deploy allowlist**

In `functions/package.json`, update the `deploy` script's `--only` list (append the two new functions):

```json
"deploy": "npm run build && firebase deploy --only functions:onAuthUserDeleted,functions:smartDraftMessage,functions:reconcileCustomerSlots,functions:sendVerificationEmail,functions:dailyDigest,functions:debugSendMyDigest"
```

- [ ] **Step 4: Typecheck + lint + full test run**

Run: `cd functions && npx tsc --noEmit && npm run lint && npx jest`
Expected: no type errors, no lint errors, all suites PASS.

- [ ] **Step 5: Commit**

```bash
git add functions/src/notifications/dailyDigest.ts functions/src/index.ts functions/package.json
git commit -m "feat(notifications): scheduled dailyDigest function + production IO + debug trigger"
```

---

## TASK 9 — Client: preference field on the user model

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/User.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/dto/UserDto.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/mapper/UserMapper.kt`

- [ ] **Step 1: Add the domain field**

In `User.kt`, add to the `User` data class (after `whatsappConfirmed`):

```kotlin
    /**
     * Whether the tailor receives the daily operational email digest (orders
     * due soon / overdue / outstanding balance). Opt-out: true by default, so a
     * legacy doc with the field absent still gets the digest. Flipped from
     * Settings → Notifications.
     */
    val dailyDigestEmailEnabled: Boolean = true,
```

- [ ] **Step 2: Add the DTO field**

In `UserDto.kt`, add (after `whatsappConfirmed`):

```kotlin
    @SerialName("dailyDigestEmailEnabled")
    val dailyDigestEmailEnabled: Boolean = true,
```

- [ ] **Step 3: Map both directions**

In `UserMapper.kt`, add `dailyDigestEmailEnabled = dailyDigestEmailEnabled,` to BOTH `UserDto.toUser()` and `User.toUserDto()` (the property name is identical on both sides).

- [ ] **Step 4: Compile (Android) to verify**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/User.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/dto/UserDto.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/mapper/UserMapper.kt
git commit -m "feat(notifications): add dailyDigestEmailEnabled to user model/dto/mapper"
```

---

## TASK 10 — Client: repository write path

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/repository/UserRepository.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/repository/FirebaseUserRepository.kt`

- [ ] **Step 1: Add the interface method**

In `UserRepository.kt`, add inside the interface:

```kotlin
    /**
     * Sets the daily digest email opt-out flag on `users/{userId}`. Fire-and-forget
     * (offline outbox) — the snapshot listener reflects the change locally at once.
     */
    suspend fun setDailyDigestEmailEnabled(
        userId: String,
        enabled: Boolean,
    ): EmptyResult<DataError.Network>
```

- [ ] **Step 2: Implement it**

In `FirebaseUserRepository.kt`, add (mirrors the fire-and-forget pattern in `updateBrandLogo`):

```kotlin
    @Suppress("INLINE_FROM_HIGHER_PLATFORM")
    override suspend fun setDailyDigestEmailEnabled(
        userId: String,
        enabled: Boolean,
    ): EmptyResult<DataError.Network> {
        val data = mapOf<String, Any>(
            "dailyDigestEmailEnabled" to enabled,
            "updatedAt" to FieldValue.serverTimestamp,
        )
        val accepted = offlineWrites.enqueue("setDailyDigestEmailEnabled userId=$userId") {
            firestore.collection(USERS).document(userId).set(data, merge = true)
        }
        return if (accepted) Result.Success(Unit) else Result.Error(DataError.Network.UNKNOWN)
    }
```

- [ ] **Step 3: Compile to verify**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/repository/UserRepository.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/repository/FirebaseUserRepository.kt
git commit -m "feat(notifications): repository setter for daily digest opt-out"
```

---

## TASK 11 — Client: Settings MVI wiring (test-first)

**Files:**
- Modify: `feature/settings/presentation/home/SettingsState.kt`
- Modify: `feature/settings/presentation/home/SettingsAction.kt`
- Modify: `feature/settings/presentation/home/SettingsViewModel.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/settings/SettingsDigestToggleTest.kt`

- [ ] **Step 1: Add the State field**

In `SettingsState.kt`, add to `SettingsState` (after `themePreference`):

```kotlin
    val dailyDigestEmailEnabled: Boolean = true,
```

- [ ] **Step 2: Add the Action**

In `SettingsAction.kt`, add to the sealed interface:

```kotlin
    data class OnDailyDigestToggle(val enabled: Boolean) : SettingsAction
```

- [ ] **Step 3: Wire the ViewModel**

In `SettingsViewModel.kt`:

(a) Add the optimistic override to `LocalUiState`:

```kotlin
    val dailyDigestEnabledOverride: Boolean? = null,
```

(b) Handle the action in `onAction`'s `when` (add a branch):

```kotlin
            is SettingsAction.OnDailyDigestToggle -> setDailyDigest(action.enabled)
```

(c) Add the handler method:

```kotlin
    private fun setDailyDigest(enabled: Boolean) {
        // Optimistic: reflect immediately, then persist. The snapshot listener
        // confirms; on failure we revert the override and tell the user.
        uiState.update { it.copy(dailyDigestEnabledOverride = enabled) }
        viewModelScope.launch {
            when (val result = userRepository.setDailyDigestEmailEnabled(authUserId(), enabled)) {
                is Result.Success -> Unit
                is Result.Error -> {
                    uiState.update { it.copy(dailyDigestEnabledOverride = !enabled) }
                    AppLogger.e(tag = TAG) { "setDailyDigest failed error=${result.error}" }
                    emit(SettingsEvent.ShowSnackbar(result.error.toUiText()))
                }
            }
        }
    }
```

(d) The handler needs the current user id. The `settingsStateFlow()` already resolves `authUser` from `authRepository.getCurrentUser()`. Add a small helper so the action handler (outside the flow) can read it:

```kotlin
    private suspend fun authUserId(): String =
        authRepository.getCurrentUser()?.id ?: ""
```

(e) Surface the value in `buildState(...)` — add to the returned `SettingsState(...)`:

```kotlin
            dailyDigestEmailEnabled = ui.dailyDigestEnabledOverride
                ?: firestoreUser?.dailyDigestEmailEnabled ?: true,
```

> NOTE on imports: `Result`, `AppLogger`, `viewModelScope`, `launch`, and `toUiText` are already imported in this file. `DataError.toUiText()` covers `DataError.Network` (used by the repo setter).

- [ ] **Step 4: Write the failing ViewModel test**

```kotlin
// composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/settings/SettingsDigestToggleTest.kt
package com.danzucker.stitchpad.feature.settings

import app.cash.turbine.test
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.feature.settings.presentation.home.SettingsAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SettingsDigestToggleTest {

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun toggleOff_optimisticallyDisables_andPersists() = runTest {
        val (vm, repo) = buildSettingsVmForDigest(initialEnabled = true)
        vm.state.test {
            awaitItem() // initial
            vm.onAction(SettingsAction.OnDailyDigestToggle(false))
            assertFalse(awaitItem().dailyDigestEmailEnabled)
            assertEquals(false, repo.lastDigestEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun toggleOff_revertsOnRepositoryError() = runTest {
        val (vm, _) = buildSettingsVmForDigest(initialEnabled = true, setterResult = Result.Error(DataError.Network.UNKNOWN))
        vm.state.test {
            awaitItem()
            vm.onAction(SettingsAction.OnDailyDigestToggle(false))
            // optimistic false, then reverted to true
            assertFalse(awaitItem().dailyDigestEmailEnabled)
            assertEquals(true, awaitItem().dailyDigestEmailEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

> NOTE: `buildSettingsVmForDigest(...)` is a test helper that constructs `SettingsViewModel` with fakes. A fake-heavy VM with 8 constructor deps already exists in this module's settings tests — reuse the existing fakes/builders if present; otherwise add a minimal helper in the same test source set that returns `(SettingsViewModel, FakeUserRepository)` where `FakeUserRepository.setDailyDigestEmailEnabled` records `lastDigestEnabled` and returns `setterResult`, and `observeUser` emits a `User` with `dailyDigestEmailEnabled = initialEnabled`. Model it on the existing fake repositories under `commonTest` (see `reference_test_toolchain`: kotlin.test + Turbine, no assertk).

- [ ] **Step 5: Run the test (red, then green after Step 3 is in place)**

Run: `./gradlew :composeApp:testDebugUnitTest --tests '*SettingsDigestToggleTest*'`
Expected: PASS (2 tests). If the helper doesn't yet exist, create it first per the Step 4 note.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/home/SettingsState.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/home/SettingsAction.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/home/SettingsViewModel.kt composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/settings/SettingsDigestToggleTest.kt
git commit -m "feat(notifications): settings MVI wiring for daily digest opt-out"
```

---

## TASK 12 — Client: Settings toggle row UI + string

**Files:**
- Modify: `feature/settings/presentation/home/SettingsScreen.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml` (and any other locale files present)

- [ ] **Step 1: Add the string resource**

In `composeApp/src/commonMain/composeResources/values/strings.xml`, add:

```xml
<string name="settings_row_daily_digest">Daily summary email</string>
```

> Use `&apos;` (never `\'`) for any apostrophe in strings — see `feedback_strings_no_backslash_escape`. This label has none.

- [ ] **Step 2: Add the toggle row to the Settings screen**

In `SettingsScreen.kt`, inside the **Preferences** section card — `SettingsSectionCard(label = stringResource(Res.string.settings_section_preferences))`, the one holding the measurement-units / appearance rows — add after the appearance `SettingsRow`:

```kotlin
                SettingsRowDivider()
                SettingsRow(
                    label = stringResource(Res.string.settings_row_daily_digest),
                    onClick = { onAction(SettingsAction.OnDailyDigestToggle(!state.dailyDigestEmailEnabled)) },
                    trailing = {
                        Switch(
                            checked = state.dailyDigestEmailEnabled,
                            onCheckedChange = { onAction(SettingsAction.OnDailyDigestToggle(it)) },
                        )
                    },
                )
```

Add the imports at the top of the file if not already present:

```kotlin
import androidx.compose.material3.Switch
import stitchpad.composeapp.generated.resources.settings_row_daily_digest
```

> Both the row tap and the switch toggle dispatch the same action, matching how the existing rows route through `onAction`. The `SettingsRow(label, onClick, trailing)` signature is the one used by the Invite/Email rows above.

- [ ] **Step 3: Verify the preview + compile**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid -q`
Expected: BUILD SUCCESSFUL. (The screen already has a `@Preview`; the new row renders within it.)

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/home/SettingsScreen.kt composeApp/src/commonMain/composeResources/values/strings.xml
git commit -m "feat(notifications): daily summary email toggle row in Settings"
```

---

## TASK 13 — Full verification sweep

**Files:** none (verification only).

- [ ] **Step 1: Functions — lint + typecheck + tests**

Run: `cd functions && npm run lint && npx tsc --noEmit && npx jest`
Expected: all green. (`functions-tests` CI job gates on lint, per the integration guide.)

- [ ] **Step 2: Client — unit tests**

Run: `./gradlew :composeApp:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, including `SettingsDigestToggleTest`.

- [ ] **Step 3: Detekt**

Run: `./gradlew detekt`
Expected: no new violations. If the format skill is available, run it first on changed Kotlin files.

- [ ] **Step 4: iOS compile (required — KMP JVM-only/Native pitfalls)**

Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64 -q`
Expected: BUILD SUCCESSFUL. (Per `feedback_kmp_jvm_only_apis` — no JVM-only APIs were introduced, but the compile must pass before "done".)

- [ ] **Step 5: Commit any format/lint fixups**

```bash
git add -A
git commit -m "chore(notifications): formatting + lint fixups" || echo "nothing to commit"
```

---

## TASK 14 — Manual smoke test + PR

**Files:** none (manual QA + PR creation). Daniel is QA (`feedback_qa_smoke_tests`).

- [ ] **Step 1: Deploy functions to the project**

Run: `cd functions && npm run deploy`
Then confirm both functions registered (deploy-omission gotcha):
Run: `firebase functions:list`
Expected: `dailyDigest` and `debugSendMyDigest` appear in `europe-west1`.

- [ ] **Step 2: Seed a test account (Fola) for all three buckets**

Per `reference_test_environment`, seed in `users/{folaUid}/orders`:
- one order: `status: IN_PROGRESS`, `deadline` = tomorrow (Lagos), `archivedAt: null` → **due soon**
- one order: `status: IN_PROGRESS`, `deadline` = 3 days ago → **overdue**
- one order: `status: DELIVERED`, `totalPrice: 10000`, `payments: [{amount: 4000}]` → **outstanding ₦6,000**

- [ ] **Step 3: Trigger the digest for the caller and verify content**

Call `debugSendMyDigest` while signed in as Fola (via the app debug trigger if wired, or the Functions shell / a temporary callable invocation).
Expected: one email arrives titled like *"StitchPad: 1 overdue, 1 due soon, 1 to collect"*, with three sections and `₦6,000` under "To collect".

- [ ] **Step 4: Verify suppress-when-empty**

Clear/deliver-and-pay the seeded orders so nothing is actionable, reset the stamp (delete `users/{folaUid}/private/digestState`), and call `debugSendMyDigest` again.
Expected: response `{ sent: false, reason: 'empty' }`, no email.

- [ ] **Step 5: Verify the opt-out toggle**

In the app → Settings → toggle "Daily summary email" off. Confirm `users/{folaUid}.dailyDigestEmailEnabled == false` in Firestore and the Snackbar appears. Re-seed an actionable order, call `debugSendMyDigest`.
Expected: response `{ sent: false, reason: 'disabled' }`, no email. Toggle back on; verify it sends again.

- [ ] **Step 6: Confirm rollout staging is still ON**

Verify `functions/src/notifications/rollout.ts` has `STAGING = true` and the allowlist holds the real tester emails. The scheduled `dailyDigest` must NOT email non-allowlisted users until you deliberately flip it.

- [ ] **Step 7: Push and open the PR (review rotation)**

```bash
git push -u origin feature/notifications-email-digest
gh pr create --title "feat(notifications): daily email digest (slice 1 of 3)" --body "$(cat <<'EOF'
## What
Slice 1 of the notifications feature: a daily, suppress-when-empty **email digest** sent ≈07:00 Africa/Lagos via a new scheduled Cloud Function. Buckets: orders **due soon**, **overdue**, and **ready/delivered with outstanding balance**. Opt-out toggle in Settings (default on). Establishes the pure `digestDetector` backbone for the later in-app and push slices.

Design spec: `docs/superpowers/specs/2026-06-03-notifications-email-digest-design.md`

## Rollout (important)
Scheduled sends are gated to a **tester allowlist** (`rollout.ts`, `STAGING = true`). Flip to all users only after verifying real mornings against test accounts. `debugSendMyDigest` bypasses the gate/stamp for the caller only.

## Smoke test
Seeded Fola with due-soon + overdue + delivered-with-balance orders; `debugSendMyDigest` produced one email with all three sections and the correct naira total. Verified suppress-when-empty and the opt-out toggle (disabled → no send). iOS compile + detekt + functions lint/tests + client unit tests all green.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 8: Run the review rotation**

Per `feedback_review_rotation`: Cursor Bugbot runs automatically; run `codex review` via the pre-push hook (already configured with `-c model=gpt-5.5`). Address findings — watch for client/server constant drift (the default-on flag), timezone/day-boundary errors, and plural grammar in the digest copy (`feedback_cursor_review_patterns`).

---

## Post-merge follow-ups (not this PR)

- Flip `rollout.ts` `STAGING = false` after verified mornings.
- Wire a Settings → Debug menu button to `debugSendMyDigest` + a "reset digest stamp" action (deferred with the debug-menu work, `project_debug_menu`).
- PR2 (in-app inbox) reuses `digestDetector`; PR3 (push) reuses the scheduled-function pattern.
- Consider a real tokenised `List-Unsubscribe` endpoint if deliverability needs it.

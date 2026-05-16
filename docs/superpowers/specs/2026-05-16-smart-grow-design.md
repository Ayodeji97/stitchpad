# Smart Suggestions — Grow Customers (V1 Design Spec)

**Date:** 2026-05-16
**Status:** Approved (brainstorming complete; ready for implementation plan)
**Companion spec:** [[2026-05-16-ai-assistant-design]] — the "Keep customers" half (Draft Message, already in flight). This document is **additive** and inherits its locked infrastructure decisions.
**Memory references:** [[project-ai-assistant]], [[project-rebrand-styleos]], [[project-rebrand-terminology]], [[project-premium-tier-candidates]], [[project-freemium-plan-card]], [[project-pm-intern]], [[project-landing-page]], [[reference-webp-assets]], [[feedback-notification-patterns]], [[feedback-qa-smoke-tests]], [[feedback-pr-workflow]], [[feedback-apple-sign-in-required]], [[reference-test-environment]]

## Goal

Extend Smart Suggestions from a **retention** surface (Draft Message — Keep customers) to a **customer-acquisition** surface (Grow customers). Help a Nigerian tailor grow their customer base by removing the content-creation and word-of-mouth friction at the moment they finish an outfit.

Tailor stays in control of their own channels (WhatsApp Status, IG, in-person referrals); StitchPad provides the AI assistance, the rendered share assets, and the public shareable surface.

V1-Grow adds **four new features** under Smart Suggestions, sequenced as independent slices:

| # | Feature | Slice |
|---|---|---|
| 1 | Generate a Post (caption + hashtags from a completed order) | postcaption |
| 2 | Make a Share Card (branded image: photo + business name + accent color, square + 9:16) | sharecard |
| 3 | Ask for a Referral + Mini-Portfolio (text drafts + public `stitchpad.app/t/<handle>` page) | referral |
| 4 | This Week's Plan (Monday-morning AI weekly content coach) | contentplan |

## Why

Per [[project-ai-assistant]]: AI assistance is a V1 launch feature, "thoroughly built for a fashion business owner — not a generic chatbot." The companion Draft Message feature solves *communicating with customers you already have*. Customer acquisition — "how do I get more customers?" — is the **biggest unaddressed pain point** for Nigerian tailors and is not on the Keep side at all.

Differentiation against [[project-rebrand-styleos]]: StyleOS ships workshop management; it does **not** ship a customer-acquisition layer. This is the highest-leverage place to pull ahead before launch.

## Decisions locked (in brainstorming, 2026-05-16)

| Decision | Choice |
|---|---|
| **Direction** | Outbound marketing engine (tailor's own channels), NOT in-app marketplace or browsable directory |
| **Feature boundary** | 4 distinct features in `feature/`, each with its own ViewModel/screen/route; share infrastructure, not feature code |
| **Shared infra** | Promote AI client + quota store + language enum to `core/smart-infra/` (Slice 0 refactor) |
| **Quota model** | Single shared monthly bucket across ALL Smart Suggestions features (Draft + 4 Grow). 5/month free tier — inherited from companion spec. Sharecard rendering and system-generated weekly plan are **0-cost** |
| **Navigation home** | New **Smart Hub** screen with "Keep" + "Grow" halves; surfaced both from the existing Dashboard Smart Suggestions section card ("See all") and as inline actions on Order/Customer detail |
| **Postcaption inputs** | Order-bound + optional 1-line freeform note |
| **Sharecard formats** | Square (1:1, IG feed) + 9:16 (Story/Status), rendered client-side in Compose. No AI image generation |
| **Referral scope** | **Full** — text drafts + public mini-portfolio page |
| **Mini-portfolio hosting** | Dynamic route added to existing `stitchpad-web` Astro site (per [[project-landing-page]]) — `stitchpad.app/t/<handle>` |
| **Mini-portfolio rule** | **Direct-link only**, not a browsable index. Never sprout a consumer-side discovery surface |
| **Contentplan cadence** | Weekly, Monday 06:00 WAT, push notification + hub card. System-initiated generation is free; tailor-initiated regenerate costs 1 slot |
| **Languages** | English + Pidgin (`pcm`) — inherited from companion spec. Yoruba/Hausa/Igbo deferred until tester signal warrants the prompt-tuning work |
| **LLM** | Gemini 2.0 Flash via Firebase Vertex AI via Cloud Function in `europe-west1` — inherited from companion spec |
| **Premium check** | `users/{uid}/profile.tier` — inherited from companion spec |

## Reconciliation with the companion spec

The companion spec describes a **Smart Suggestions section card** on the Dashboard with 3 fixed tiles (Draft Message + 2 grayed "Coming soon"). V1-Grow needs **5 entry points** total. Resolution:

- **Dashboard section card stays** as the always-on discoverability surface. Its tile count is the open implementation-level question (likely 3 most-used + "See all" link).
- **Smart Hub screen** is added. Routed to from the section card's "See all" link, from an inline "Generate post" on Order detail, and from "Ask for a referral" on Customer detail.
- The hub renders the full list, organized as **Keep customers** (Draft Message) and **Grow customers** (Generate a Post, Make a Share Card, Ask for a Referral, This Week's Plan).
- Existing "Coming soon" tiles in the Dashboard card (Help Me Price This, Customer Reply Helper) remain as scoped in the companion spec — they're additional Keep-side intents, not Grow-side. Their priority vs the new Grow tiles is a sequencing question the implementation plan should surface; this spec assumes Grow ships first because acquisition is the larger unaddressed pain point.

## Architecture

```
composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/
  core/smart-infra/                        ← NEW (Slice 0 refactor — promoted from feature/smart)
    domain/
      ai/SmartAiClient.kt                  ← interface, callable-function backed
      quota/SmartQuotaStore.kt             ← shared monthly counter, tagged by SmartFeatureKey
      quota/SmartFeatureKey.kt             ← enum: DRAFT, POSTCAPTION,
                                              REFERRAL_MSG, REFERRAL_BIO, CONTENTPLAN_REGEN
      language/DraftLanguage.kt            ← moved from feature/smart
    data/
      ai/CloudFunctionSmartAiClient.kt
      quota/FirestoreSmartQuotaStore.kt    ← single doc at users/{uid}/usage/smart_drafts
                                              extended with featureKey tagging on increment

  feature/
    smart/                                 ← existing — refactored to depend on core/smart-infra
    smarthub/                              ← NEW — pure navigation screen (Keep/Grow tiles)
    postcaption/                           ← NEW — Slice 1
    sharecard/                             ← NEW — Slice 2
    referral/                              ← NEW — Slice 3 (app side)
    contentplan/                           ← NEW — Slice 4

functions/src/smart/
  draftMessage.ts                          ← existing
  postCaption.ts                           ← NEW (Slice 1)
  referralMessage.ts                       ← NEW (Slice 3)
  generateBio.ts                           ← NEW (Slice 3 — mini-portfolio bio)
  weeklyContentPlan.ts                     ← NEW (Slice 4 — scheduled)
  publicProfile/
    claimHandle.ts                         ← NEW (Slice 3 — transactional)
    reportProfile.ts                       ← NEW (Slice 3 — UGC compliance)

  shared/
    vertexClient.ts                        ← existing
    quotaCounter.ts                        ← REFACTORED from freeTierCounter.ts;
                                              accepts featureKey for analytics tagging
    promptBuilder.ts                       ← extended with new prompt templates

stitchpad-web/                             ← separate repo, iterated in Antigravity
  src/pages/t/[handle].astro               ← NEW — public mini-portfolio page
  src/lib/firestore.ts                     ← Firestore client for public_profiles read
  src/components/ProfileGallery.astro
  src/components/ReportProfileButton.astro
```

Follows existing MVVM + Clean rules (Root/Screen split, MVI State/Action/Event, Result<T,E>, Koin DI, DTO/mapper separation, descriptive impl names).

## Slice 0 — Refactor: promote shared infra to `core/smart-infra`

**Why first.** Four new features will depend on the AI client + quota store + language enum. Promoting them to `core/smart-infra` before consumers land prevents `feature/smart/` from bloating into a misleadingly-named utility package.

**Scope.**
- Move `SmartAiClient`, `SmartUsageStore`, `DraftLanguage` from `feature/smart/domain` to `core/smart-infra/domain`.
- Move `CloudFunctionSmartAiClient`, `FirestoreSmartUsageStore` (or equivalent — see `feature/smart/data/` for actual names) to `core/smart-infra/data`.
- Extend the quota store to tag each increment with a `SmartFeatureKey`. Storage shape stays the same (single counter doc); a new sibling subcollection or array captures per-feature counts for analytics. Concrete shape decided in implementation.
- Update existing `feature/smart/` to import from `core/smart-infra`. No behavior change.
- Update existing Koin module wiring.
- Re-run existing unit tests (no logic change expected); add a contract test that `SmartFeatureKey.DRAFT` increments are counted.

**Constraint.** This slice **touches code already merged** for the companion spec. Land Slice 0 as its own PR, separate from any Grow feature, so the diff is reviewable as a pure refactor.

**Estimate.** 1 PR, ~2 dev days.

## Slice 1 — `feature/postcaption`

**Trigger.** Inline "Generate post" button on Order detail screen (for completed orders only), or from the Smart Hub.

**Inputs.** Selected completed order (style, customer first name, primary photo, deposit/balance, deadline); optional 1-line freeform note from tailor; language picker (default = tailor's last-used language).

**Output.** Caption text (60–180 chars) + 5–8 relevant hashtags. Tailor can edit, regenerate (1 quota slot per regeneration, same pattern as Draft Message), or copy/share via native share sheet.

**Cultural grounding.** Prompt seeded with Nigerian fashion context — aso ebi, agbada, Ankara, owambe, asoke vocab; aware of upcoming season cues (Detty December, Eid, Easter, Valentine's, wedding peaks). Tailor's `businessName` + `location.area` interpolated.

**Quota.** 1 slot per accepted generation, tagged `SmartFeatureKey.POSTCAPTION`.

**Cloud Function.** New `postCaption.ts`. Same pattern as `draftMessage.ts`: auth → tier check → fetch order/customer server-side from UID-scoped collections → prompt → Vertex AI → increment counter (tagged) → return.

**Files (estimate).** ~12 — domain models, prompt builder additions, ViewModel, state, screen, root, hashtag chip component, preview, DI module, navigation route, unit tests, mappers.

**Estimate.** 1 PR, ~2 dev days.

## Slice 2 — `feature/sharecard`

**Trigger.** Inline "Make a share card" next to a generated caption (handoff from Slice 1) or standalone from the Smart Hub.

**Inputs.**
- An order photo (or any photo from `users/{uid}/orders/*/photos`).
- Tailor's brand assets from `users/{uid}/brand` (see below).
- Format choice: Square 1:1 (IG feed) or 9:16 (Story/Status).
- Optional caption overlay (handed off from Slice 1 if reached via inline action).

**Rendering.** **Client-side Compose** rendering to `ImageBitmap` → PNG file → native share sheet. No AI call for the image. Two templates per format (4 total): photo-top vs full-bleed.

**Brand asset capture (one-time).** First time the tailor opens Sharecard, a setup screen captures:
- Business name (already captured in workshop onboarding; pre-filled — see [[project-brand-onboarding]])
- Accent color (defaults to post-rebrand primary)
- Optional logo image upload (PNG with transparency preferred)

Stored at `users/{uid}/brand`:

```
{
  businessName: string,
  accentColorHex: string,
  logoUrl: string | null,
  updatedAt: timestamp
}
```

**Quota.** **0 slots.** Pure local rendering — no AI call. Encourages use and helps habit-form around the tailor's brand on every shared post.

**Files (estimate).** ~18 — image renderer composable, 4 templates, brand setup screen + ViewModel + repo, format picker, share intent, ViewModels, screens.

**Estimate.** 2 PRs (brand setup + render pipeline as one; templates + share UX as the other), ~3–4 dev days total.

## Slice 3 — `feature/referral` + mini-portfolio on `stitchpad-web`

Two coordinated pieces, both required for this slice to be useful.

### 3a. App side — `feature/referral`

#### Referral message drafting

Tailor picks a delivered-to customer → "Ask for a referral" → AI drafts a warm, personalized referral-request message. Pipeline pattern matches Draft Message: pick customer → pick language → optional notes → Generate.

**Cloud Function.** New `referralMessage.ts`. Auth → tier check → fetch customer + last completed order server-side → prompt (referral-ask specific) → Vertex → increment counter (tagged `SmartFeatureKey.REFERRAL_MSG`).

**Send/copy UX.** Identical to Draft Message — Send via WhatsApp deeplink (disabled with helper text if customer has no `whatsappNumber`) + Copy text fallback.

#### Mini-portfolio manager

A new section inside `feature/referral/presentation/portfolio/` for creating and managing the public profile.

##### Handle picker (one-time)

- Tailor claims `stitchpad.app/t/<handle>`.
- **Format:** 3–20 chars, lowercase `a-z`, digits, single hyphens (no leading/trailing hyphen).
- **Uniqueness:** Transactional claim via Cloud Function `claimHandle.ts` — atomic write to `handles/{handle}`. Race-safe.
- **Reserved words:** A static list inside the Cloud Function (`admin`, `support`, `app`, `api`, `www`, `t`, `help`, `pricing`, `legal`, plus profanity filter via a small server-side list).
- **One handle per user.** Renaming is allowed via an explicit "Change my handle" action (Cloud Function transfers the reservation, soft-redirects from the old URL for 30 days).

##### Profile editor

- **Tagline** (≤ 80 chars) — AI-assist button writes a tagline from the tailor's portfolio + location + specialty tags. Costs 1 slot, tagged `SmartFeatureKey.REFERRAL_BIO`.
- **Location** — city + area (Nigeria-only in V1; country field stored but fixed to `NG`).
- **Specialty tags** — multi-select chips from a curated list (Bridal, Aso ebi, Agbada, Ready-to-wear, Kids, Corporate, Streetwear, Alterations). Free-form tags deferred.
- **WhatsApp number** — pre-filled from existing `users/{uid}.whatsappNumber`.
- **Instagram handle** — optional, validated as `@handle` format.

##### Photo curation

- Tailor picks up to **12 photos** from existing `users/{uid}/orders/*/photos`.
- **Per-photo `publicOnProfile` toggle, default OFF.** Stored on the photo doc, not the profile.
- **Mandatory banner on selector:** "These photos will be visible on your public profile. Make sure you have your customer's permission to share."
- **Order on the public page** = tailor-controlled drag-reorder. Most-recent-first as default.

##### Privacy controls

- **Public/Paused toggle.** When Paused, the page returns a "this profile is paused" state (not 404 — preserves the URL's value).
- **Default:** Public once handle is claimed AND ≥ 1 photo has `publicOnProfile = true`. If no photos opted-in, profile is automatically Paused with a hint to add photos.

##### Share my page

- Native share sheet (text + URL).
- Copy link.
- **QR code** — generated client-side via `qrose` (KMP-compatible). For in-person sharing.

### 3b. Web side — `stitchpad-web/src/pages/t/[handle].astro`

**Hosting.** Existing Astro landing-page site at `~/Desktop/Project/stitchpad-web/`. Daniel iterates this in Antigravity (per [[project-landing-page]]); implementation plan must note the cross-repo handoff.

**Rendering.** SSR. Reads `public_profiles/{handle}` from Firestore (public read rule). Falls back to a "page not found" template for unclaimed handles, and a "paused" template when `paused == true`.

**Page contents.**
- Header: cover with business name, tagline, location (city, area).
- Specialty tag pills.
- Photo gallery (responsive grid, lightbox on click).
- Primary CTA button: **Chat on WhatsApp** — `https://wa.me/<e164>?text=<prefilled-message>` (deeplink, prefilled with "Hi, I saw your work on StitchPad. ...").
- Secondary CTA: Instagram link (if set).
- Footer: small "Made with StitchPad" attribution (links to landing page) + "Report this profile" link.

**Open Graph tags.** Critical for WhatsApp/IG link previews. Title = `<businessName> on StitchPad`. Description = tagline. Image = first photo from the curated gallery (resized to OG-ratio at SSR time).

**Report flow.** "Report this profile" → small form (reason dropdown + optional details) → Cloud Function `reportProfile.ts` → writes to `reports/{reportId}`. **Required for App Store/Play Store UGC compliance.**

**Index hardening.** Add `noindex` meta tag in V1 to keep the page out of Google search results. This is deliberate — preserves the "direct-link only" rule and avoids surprise discoverability. Revisit in V1.5 alongside SEO strategy.

### Data model

```
public_profiles/{handle}                      ← public read, server-only write
  uid: string,
  handle: string,
  businessName: string,
  tagline: string,
  location: { city: string, area: string, country: "NG" },
  whatsappE164: string,
  instagramHandle: string | null,
  specialtyTags: string[],
  photoUrls: string[],                        ← only photos with publicOnProfile=true,
                                                in tailor-chosen order
  paused: boolean,
  createdAt: timestamp,
  updatedAt: timestamp

handles/{handle}                              ← auth read; server-only write
  uid: string,
  claimedAt: timestamp

users/{uid}/orders/{orderId}/photos/{photoId}
  publicOnProfile: boolean                    ← NEW field, default false; existing schema otherwise

users/{uid}/brand                             ← see Slice 2

reports/{reportId}                            ← UGC reporting
  handle: string,
  reason: string,                             ← enum: "abuse" | "impersonation" | "spam" | "other"
  details: string | null,
  reporterUid: string | null,                 ← null for anonymous web reports
  createdAt: timestamp
```

**Firestore rules sketch.**
- `public_profiles/*`: read = public; write = only via Cloud Function (server SDK).
- `handles/*`: read = auth; write = only via Cloud Function.
- `users/{uid}/orders/*/photos/*`: existing rules; `publicOnProfile` flag mirrored to `public_profiles/{handle}.photoUrls` via a Cloud Function trigger (or recomputed on profile save — implementation decides).
- `reports/*`: write = public (rate-limited via callable function); read = admin only.

### Rebrand coupling

Mini-portfolio visuals (cover treatment, gallery, CTA buttons) must match the post-rebrand brand per [[project-rebrand-styleos]]. **Slice 3b is gated on the rebrand landing** or its template is throwaway. Slice 3a (app side — referral message drafting + handle/photo management UI) can ship before rebrand, since its UI lives inside the app's own (rebranded) design system; the public page is what's blocked.

### Files (estimate)

- **App side:** ~25 — handle reservation flow + repo, profile editor, photo curator, privacy toggle, share intent, QR composable, referral message ViewModel/screen/root, public-profile ViewModel/screen/root.
- **Functions:** ~5 — `claimHandle.ts`, `referralMessage.ts`, `generateBio.ts`, `reportProfile.ts`, photo-mirror trigger.
- **Web:** ~6 — `[handle].astro`, Firestore client, OG tag handler, profile gallery component, report form, paused-state template.

### Estimate

- 3a app side: 2 PRs (referral message as one; mini-portfolio manager as the other), ~4 dev days.
- 3b web side: 1 PR in `stitchpad-web` (Antigravity workflow), ~2 dev days.
- Plus cross-repo coordination overhead.

## Slice 4 — `feature/contentplan` + scheduled Cloud Function

**Generation.** Server-side Cloud Function `weeklyContentPlan.ts`, scheduled weekly via Cloud Scheduler (Monday 06:00 WAT). For each active user (last login < 14 days) it generates **3 suggestions** based on:
- Recent completed orders without a posted caption (signal: no `postcaption.generatedAt` set).
- Idle customers (no contact in 30+ days — threshold tunable in the Cloud Function config without a code change).
- Seasonal cues from a Nigerian calendar JSON (Ramadan, Eid, Detty December peak, Valentine's, wedding peak months, Mother's Day, Independence Day).

**Each suggestion** is one of three kinds: `POST_FROM_ORDER` (routes to postcaption pre-filled), `MAKE_SHARECARD` (routes to sharecard pre-filled), `ASK_REFERRAL` (routes to referral pre-filled with a specific customer).

**Delivery.**
- **Push notification** Monday 06:30 WAT: "Your 3 ideas for the week are ready 💡" (one notification; tapping opens Smart Hub → Plan card).
- **Persistent card in Smart Hub** "This week's plan" with 3 expandable rows.
- **Inbox-style staleness:** the previous week's plan is replaced, not archived. V1 has no plan history.

**Push infra (new for the project).** FCM (Android) + APNs (iOS) device token registration, server-side send via Firebase Admin SDK, notification permission UX (iOS modal on first send attempt; Android 13+ runtime permission). This is **its own ~1 PR of setup work** independent of Slice 4, and Slice 4 blocks on it.

**Quota.**
- System-initiated weekly generation: **free** (does not increment the tailor's counter).
- "Regenerate this week's plan" button: 1 slot, tagged `SmartFeatureKey.CONTENTPLAN_REGEN`.

**Files (estimate).**
- App: ~15 — plan models, ViewModel, hub card composable, suggestion row composable, navigation pre-fill logic per kind, push-token registration, permission UX, DI.
- Functions: ~5 — `weeklyContentPlan.ts`, Cloud Scheduler config, push send helper, seasonal calendar JSON, unit tests.

**Estimate.** Push infra: 1 PR, ~2 dev days. Slice 4 itself: 2 PRs, ~3 dev days.

## Quota model

**Storage.** Existing single Firestore doc at `users/{uid}/usage/smart_drafts` (the name is preserved for back-compat with the companion spec's already-deployed code, but it now covers all Smart features). Schema extended:

```
{
  monthYear: "2026-05",
  count: 3,
  limit: 5,
  perFeature: {                                ← NEW
    "draft": 2,
    "postcaption": 1,
    "referral_msg": 0,
    "referral_bio": 0,
    "contentplan_regen": 0
  }
}
```

**Semantics.**
- Reserve-before-call pattern (existing — preserved). Validate inputs first, then atomic-increment `count` AND `perFeature[featureKey]`, then call Vertex. If Vertex fails, decrement both. (See existing recent commits — input validation before reservation is already in place.)
- Free tier: 5/month total across all features. Sharecard rendering and system-generated weekly plans are 0-cost and do not touch this counter.
- Premium tier: unbounded; counter is read-only (for analytics) but never blocks.
- Monthly reset: idempotent server-side check — if `monthYear != today.YYYY-MM` then reset `count = 0`, clear `perFeature`, update `monthYear`.

**Per-feature freemium gating (future).** `perFeature` exists so Daniel can later cap individual features (e.g. "max 2 mini-portfolio bio regenerations per month even on premium") without a schema migration. Not used in V1.

## Privacy, consent, and compliance

| Concern | V1 mitigation |
|---|---|
| **Customer photo on public page without consent** | Per-photo `publicOnProfile` toggle, default OFF. Mandatory banner on photo selector. Curator tip suggesting tailor ask the customer first |
| **Profile abuse / impersonation** | "Report this profile" flow on the public web page (UGC compliance for app stores) → `reports/` collection |
| **Handle squatting** | Reserved-word + profanity list. One handle per user. No abuse-mitigation tooling beyond that in V1 — revisit if it happens |
| **AI-drafted referral message sent without review** | UX requires tailor to tap "Send via WhatsApp" — the message opens in WhatsApp, tailor still presses Send manually. AI never sends autonomously |
| **Push notifications without consent** | iOS uses standard permission modal on first send. Android 13+ uses runtime permission. Pre-permission rationale screen in app explaining what we'll send (Mondays only, plan updates) |
| **Public profile indexable by search engines** | `noindex` meta tag in V1 — direct-link only |
| **Apple Sign-In already required for SSO** per [[feedback-apple-sign-in-required]] | No new auth work |
| **Account deletion** | Existing `onAuthUserDeleted` Cloud Function ([[2026-05-12-onauth-userdeleted-design]]) must be extended to also delete `public_profiles/{handle}`, release `handles/{handle}`, and orphan any `reports/` referencing the handle. Add to Slice 3 implementation plan |

## Error handling + UX

Inherits patterns from companion spec. Notable additions for Grow features:

| Error | Where | Response |
|---|---|---|
| **Handle already taken** | Server `claimHandle` transaction | Inline form error: "That handle is taken. Try a different one." |
| **Handle reserved word** | Server `claimHandle` rejection | Inline form error: "That handle isn't available." |
| **Cross-repo web preview not yet deployed** | Tailor taps "Share my page" before the page is live | Snackbar: "Your page will be ready in a few minutes." Implementation plan resolves the deploy-handoff |
| **Photo upload failed for mini-portfolio** | Mirror trigger from `publicOnProfile=true` | Snackbar + retry. Profile stays in current state until mirror succeeds |
| **Push permission denied** | iOS/Android runtime permission | Slice 4's hub card states "Enable notifications to get your weekly plan reminder" with a deep-link to system settings. Plan still appears in-app |
| **Weekly plan generation skipped** (no recent activity) | Server `weeklyContentPlan` | Hub card shows "Add a few orders and customers to unlock weekly suggestions" |
| **Caption regeneration burns quota even if user discards** | Existing pattern — reservation is on call, not on acceptance | Acceptable. UI shows "Generated 1 of 5" warning before second regenerate |

## Testing

Per [[android-testing]] skill conventions.

### Per-slice unit tests

Each new feature follows the existing `feature/smart` test pattern:
- ViewModel: state transitions, action handlers, error mapping to events, quota gating
- Repository: DTO mapping, error code mapping, Cloud Function call shape
- Mappers: DTO ↔ domain round-trips

### Cross-cutting unit tests

- `core/smart-infra/SmartQuotaStore`: monthly rollover, atomic increment, `perFeature` tagging, premium tier bypass, decrement-on-failure path.
- Photo `publicOnProfile` flag: toggling it updates the mirrored `public_profiles.photoUrls` array (or queues the mirror — depending on chosen mirror strategy).

### Cloud Functions tests

Extending the existing `functions-tests` CI job:
- `postCaption.ts`: auth, tier check, cross-UID rejection, Vertex mocked, counter increment with correct featureKey tag.
- `claimHandle.ts`: transactional race (two simultaneous claims → one wins), reserved-word rejection, format validation.
- `referralMessage.ts`: same shape as draftMessage.
- `generateBio.ts`: same shape.
- `weeklyContentPlan.ts`: skips inactive users, generates 3 suggestions, push payload shape correct.
- `reportProfile.ts`: rate-limit enforcement; writes to `reports/` with correct shape.

### Compose UI tests

- Smart Hub screen renders 5 tiles correctly (1 Keep + 4 Grow), with quota chip in header.
- Photo curator: toggling `publicOnProfile` on a photo updates the preview count; mandatory consent banner is present.
- Handle picker: format validation feedback per keystroke.
- Mini-portfolio Paused state: hub shows correct status pill.
- Share Card: render fidelity test via screenshot comparison for both formats × both templates × light/dark.

### Web tests (`stitchpad-web`)

- `[handle].astro` SSR: renders for a valid profile; renders paused template when `paused=true`; renders 404 for unclaimed handle; OG tags present with correct content; `noindex` meta tag present.

### Manual smoke

Drafted per slice in the implementation plan, run by Daniel before merge per [[feedback-qa-smoke-tests]]. Critical end-to-end smoke for Slice 3:
- Claim handle on app → wait for deploy → load `stitchpad.app/t/<handle>` in browser → share to WhatsApp → confirm OG preview renders.

### Local dev / debug

- Extend the planned Debug menu (per [[project-debug-menu]]) with **Tier 2 — Smart**:
  - "Use canned response" toggle for each Smart feature (avoid burning Vertex tokens).
  - "Force trigger weekly plan now" — invokes the Cloud Function manually for the current user.
  - "Reset Smart quota this month" — for testing free-tier exhaustion paths.
- Per [[feedback-debug-menu-per-feature]]: every new Slice's PR must evaluate and propose debug-menu entries.

## Sequencing

| Order | Item | Depends on | Est. PRs | Est. dev days |
|---|---|---|---|---|
| **0** | Refactor: promote shared infra to `core/smart-infra` (with `SmartFeatureKey` tagging) | — | 1 | 2 |
| **1** | Slice 1: `feature/postcaption` | Slice 0 | 1 | 2 |
| **2** | Slice 2: `feature/sharecard` (brand setup + render pipeline + templates) | Slice 0; brand setup extends workshop onboarding | 2 | 3–4 |
| **3a** | Slice 3a: `feature/referral` (referral message + handle/profile/photo curator) | Slice 0, Slice 1 (reuses caption pipeline patterns) | 2 | 4 |
| **3b** | Slice 3b: `stitchpad-web` mini-portfolio page | Rebrand landed, 3a data model deployed | 1 (Antigravity) | 2 |
| **P** | Push infrastructure (FCM + APNs + permission UX) | — (independent) | 1 | 2 |
| **4** | Slice 4: `feature/contentplan` + scheduled Cloud Function | Slices 1, 2, 3a; Push infra | 2 | 3 |
| **R** | Smart Hub screen + Dashboard section card "See all" link | Each slice as it lands | folded above | — |

**Critical path:** Slice 0 → Slice 1 (proof point) → Slice 2/3a in parallel → Push infra → Slice 4.
**Web fork:** Slice 3b waits for rebrand AND 3a data model. Iterated separately in Antigravity.

**Total V1-Grow scope:** ~10 PRs in the app repo + 1 PR in the web repo + 1 Cloud Function scheduled deploy. Realistic if rebrand and push infra are sequenced correctly.

## Risks & open questions

1. **Behavioral assumption (high impact).** Tailors will actually use AI-generated captions for IG/WhatsApp. Slice 1 is designed to test this cheaply with the PM intern's 5–10 tester cohort (per [[project-pm-intern]]). **Decision rule:** if <30% of testers use postcaption weekly after a 2-week window, pause Slices 2–4 and re-scope.
2. **Mini-portfolio rebrand dependency.** Slice 3b can't ship until rebrand lands. If rebrand slips, Slice 3a still ships (app-side referral + handle management), but the public page is dark — confusing UX. **Mitigation:** Slice 3a's "Share my page" CTA hides or shows a "Coming soon" state until 3b is live.
3. **Customer photo rights.** Even with per-photo opt-in, a tailor publishing a recognizable customer photo without verbal consent is real social risk. The mandatory banner is the V1 mitigation; revisit if a tester reports an issue.
4. **Push infra is a significant chunk.** ~2 dev days of pure plumbing (FCM project setup, APNs cert, KMP token registration via GitLive, server-side send). Surface this in the V1 Notion plan as its own line item.
5. **Cost ceiling.** With shared 5/month free quota, the monthly Vertex cost per active free user is bounded. Paid tier is unbounded — Daniel decides the per-user cap (or absence thereof) at freemium-lock time per [[project-freemium-plan-card]].
6. **Cross-repo deploy coordination.** Slice 3 requires app + Functions + Astro web all deployed in the right order (data model first, then Function, then app, then web). Implementation plan must spell out the order to avoid breaking the live web page.
7. **Reconciliation with companion spec's tile-count.** Companion spec said "1 enabled + 2 grayed" Dashboard tiles. With Grow added, that count grows. **Open:** does the Dashboard card show all 5, or 3 + "See all"? Implementation plan resolves once we see the section card in design.
8. **No history of weekly plans.** V1 only retains the current week. If tailors say "I want to see last week's ideas," that's a V1.5 ask. Acceptable for V1.
9. **`stitchpad-web` is iterated in Antigravity, not Claude Code** per [[project-landing-page]]. The implementation plan must explicitly note the workflow boundary — Claude Code writes the app + Functions; Daniel does Astro in Antigravity.

## Out of scope (V1.5+)

- Browsable directory of tailors / consumer-side discovery (explicitly non-goal — preserves the "direct-link only" rule).
- Yoruba / Hausa / Igbo language support — added when tester signal warrants the prompt-tuning work.
- AI image generation for share cards (current V1 is templated rendering only).
- Public reviews/ratings on the mini-portfolio.
- Free-form composer ("draft me a post about anything") for caption.
- "Plan history" — see past weekly plans.
- Per-feature freemium gating (the `perFeature` schema is in place; gating UX is V1.5+).
- Marketing/ads platform integrations (Meta Ads, Jiji).
- Booking widget on the public page (currently CTAs deeplink to WhatsApp).
- Video share-cards (Reels/Status video) — separate feature track, deferred indefinitely.

## Glossary

- **Smart Suggestions** — user-facing brand name for the AI surface (never "AI assistant" in UI copy, per [[project-rebrand-terminology]]).
- **Keep customers** — retention features (Draft Message, future Help Me Price, Customer Reply Helper).
- **Grow customers** — acquisition features (Postcaption, Sharecard, Referral, Content Plan).
- **Smart Hub** — the dedicated screen listing all Smart features, organized as Keep/Grow halves.
- **Slice** — an independently shippable PR-or-pair within this spec.

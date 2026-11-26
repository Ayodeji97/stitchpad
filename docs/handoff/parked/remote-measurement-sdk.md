# Remote measurement SDK ("StitchPad Measure"): PAUSED

Status: **paused by Daniel on 2026-11-26. Do not build yet.** Brainstormed with Claude 2026-11-22 to 2026-11-26.
When resuming, start at "Agreed next step" below; do not re-ask the decisions already recorded here.

## The idea (Daniel)
Our own remote body-measurement product: a person takes front and side photos on a phone and gets accurate
body measurements automatically. Sold as a product (SDK/API licensed to other businesses), with StitchPad as
the first customer.

## Decisions so far
- **Who it is for (Daniel):** a product we sell to businesses, not only a StitchPad feature.
- **Our edge (Daniel):** both (1) built for African bodies: accurate on dark skin, African body shapes, real home
  conditions, native-wear fields; and (2) affordable for Africa: per-scan pricing, local currency, no monthly minimum.
- **v1 accuracy bar (Daniel):** about +/-2 cm, loose garments first (agbada, kaftan, senator, boubou, size
  recommendation). Tailor-grade +/-1 cm for fitted styles is later.
- **Architecture (Daniel):** phone guides the capture, our server measures. Hosted capture link + API first,
  native SDKs later.
- **Who snaps (Daniel):** both modes; a helper holding the phone is the default, solo mode (propped phone,
  timer, voice prompts) is secondary.
- **Existing data (Daniel):** the Nov-5 tailor study kit was never run; we have zero paired photo + tape data.

## Market concern (why it is paused)
Daniel asked whether people would pay. Claude's honest read: a standalone measurement SDK has a weak paying
market today.
- Buyers are few and cash-poor: small African fashion apps and platforms, price-sensitive like our tailors.
  Fitted (Nigeria) went quiet; Industrie Africa closed its shop in Apr 2026.
- Even well-funded players struggle: Presize sold to Meta and shut down; TrueToForm raised about $40K;
  3DLOOK needs $499+/month minimums.
- Nigerian tailors measure in person for free; the pain belongs to remote customers.
- Nobody pays for an SDK without published accuracy results, and we have no data yet.

Stronger paths: diaspora orders via StitchPad Connect (earns through the one-time new-client fee), and an
in-app "send your client a measurement link" feature that supports subscriptions. Licensing African body data
is a later option.

## Agreed next step when resumed: 2 weeks of demand validation, before any code
- Talk to 8 to 10 likely business buyers (African fashion and tailoring apps, diaspora made-to-measure brands,
  uniform and workwear suppliers, Nigerian fashion e-commerce). Ask about past behaviour (how they size or
  measure now, what returns and remakes cost, what they pay), and end with a concrete ask: a signed letter of
  intent or a paid pilot at a stated price per scan.
- Survey 20+ StitchPad tailors (EN + FR): how many clients abroad or out of town, how they get those
  measurements now, orders lost or remade, would they use or pay for a measurement link.
- Artifacts to prepare: target list, buyer interview script, LOI template, tailor survey, scorecard.
  Proposed home: a new sibling folder `StitchPad-IT/stitchpad-measure/` (not yet created).

**Go / no-go (Claude's proposal, Daniel may change):**
- GO on the SDK: at least 3 signed LOIs or 1 paid pilot, at $0.50/scan or more.
- GO on the in-app feature instead: at least 30% of surveyed tailors (n >= 20) have regular remote clients and
  would use or pay for a measurement link.
- NO-GO: neither; keep parked and revisit with Connect.

## Build-ready design (only if validation says GO)

**Goals:** the two edges above; +/-2 cm on core fields; first customers are African fashion apps, tailoring
platforms and diaspora brands.

**Non-goals (v1):** fitted-garment +/-1 cm; style choices no scan can give (agbada/gown length preference,
cuff and ankle opening); head or cap size; 3D avatar or try-on; reselling 3DLOOK/Bodygram.

**Anti-goals:** silently worse on dark skin (report accuracy per skin-tone cohort); confident numbers from a
bad capture (refuse and ask for a retake instead); body photos leaked, kept indefinitely, or used for training
without explicit consent; depending on SMPL/SMPL-X (non-commercial without a paid licence).

**Approaches considered:** A, phone + our server (chosen: we keep learning, works on cheap phones, but we hold
body photos); B, fully on-device (private and cheap, but no learning loop and slow on low-end phones);
C, white-label a vendor (dropped: Daniel wants our own).

**Design:**
- Surfaces in order: hosted capture link (WhatsApp, web, any app), REST API (create scan session, webhook or
  poll), native Android/iOS SDKs later.
- Capture: front + side photos, user enters height for scale; live checks for full body in frame, pose,
  distance, lighting, fitted clothing. Start from the Nov-5 prototype's capture, detector and outline modules
  (`output/measurement-study/StitchPad-Tailor-Study-Kit.zip`, untracked, on Daniel's machine).
- Measurement: v0 is silhouette + keypoints + height giving geometric estimates, with per-field corrections
  learned from tape data; later our own trained model as paired data grows.
- Quality gate: per-field confidence; out-of-range scans are rejected with a retake reason.
- Data flywheel (the real moat): StitchPad tailors tape-measure people daily, so consented, rewarded paired
  data, collected under a blinded two-measurer protocol based on the Nov-5 kit, with a held-out set never
  touched during development.
- Privacy: consent per scan, photos auto-deleted after processing by default, separate opt-in for training.
  NDPA-compliant consent, privacy notice, a short impact assessment and a deletion process must exist before
  the first session.
- Fields v1: chest/bust, waist, hip, shoulder width, neck, arm length, sleeve length, bicep (round sleeve),
  wrist, thigh, knee, inseam, trouser length, mapped to `MeasurementField` keys
  (`composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/MeasurementField.kt`).

**Claude's calls (Daniel to check):**
- First build plan covers Phase 0 up to the first held-out accuracy report; billing, API keys and local
  currency are a separate later plan.
- Pass: core fields (chest/bust, waist, hip, shoulder width, sleeve length, inseam) have mean absolute error
  <= 2 cm each and 90th-percentile error <= 4 cm.
- Cohorts: Monk Skin Tone scale grouped into 3 bands, >= 30 people per band in the held-out set; fail if any
  band is more than 0.5 cm worse than the best.
- Retakes: at most 20% of captures; accuracy reported with and without the gate.
- Clothing: fitted clothing for capture; tape readings over thin clothing at agreed landmarks.

**Left to the builder:** server stack and hosting, commercially licensed CV models, API details, repo layout,
evaluation tooling (can adapt the study kit's evaluator).

## Background sources (untracked, on Daniel's machine)
- `research_notes/Native wear marketplace for tailors/measurement_and_vetting.md`: vendor and accuracy research
  (phone-photo methods about +/-1 to 2 cm average, sometimes 3 to 5 cm; no vendor reports error by skin tone).
- `reports/Native wear marketplace for tailors.md`, `reports/Luxury connect validation.md`.
- `output/measurement-study/`: the Nov-5 study kit (never run).

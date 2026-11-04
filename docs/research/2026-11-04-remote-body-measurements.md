# Remote body measurements for StitchPad

Research date: 4 November 2026. Status: proposed product and validation plan.
Daniel wants remote measurement to become a core part of connecting customers with
fashion designers. Daniel approved both existing-customer and new-customer journeys from the start,
prioritised accuracy over one-photo capture, and prefers an inexpensive engine
StitchPad owns if feasible. Provider selection, pilot scope, tolerances and a numeric
cost ceiling remain decisions.
The owned-engine investigation is in [the companion engineering plan](2026-11-04-owned-measurement-engine.md).
No photographs have been collected, no provider account purchased, and no accuracy
claim has been established for StitchPad.

The recommended first experiment is guided front-and-side capture, evaluated against
trained tape measurements, followed by tailor review. A single arbitrary photograph
cannot be treated as sufficient evidence of accurate circumference or absolute scale.
A working camera demo would prove capture works, not that a garment will fit.

## What the evidence supports

| Evidence | Finding | Implication for StitchPad |
| --- | --- | --- |
| Graybeal et al., British Journal of Nutrition, 2023 [1] | 115 participants; repeatability and agreement were evaluated separately. Waist/hip precision error was 0.5–1.9 cm. | Repeated similar results do not establish tailoring accuracy. |
| Same study, individual agreement analysis [2] | Reported waist limits of agreement ranged from ±6.6 to ±12.9 cm across sex/device groups. | Population averages and correlation can conceal unacceptable individual errors; these results do not describe every modern provider. |
| Choudhary et al., npj Digital Medicine, 2023 [3] | In the men's mesh-derived benchmark, direct prediction with three views had waist MAE 16.38 mm versus 23.25 mm for one view; evaluation conditions and targets matter. | More views may help, but even a research improvement is not proof of a sub-centimetre consumer tailoring system. Benchmark results are not a current commercial API. |
| ISO 20685-1 [4] | Defines evaluation protocols for dimensions from 3D surface scans. | Use a disciplined protocol; do not advertise certification or compliance without obtaining and applying the complete relevant standards. |
| Google Pose Landmarker [5] | Produces estimated skeletal landmarks and optional segmentation. | Useful for framing/pose checks; not a validated chest, waist, hip or garment-length measuring engine. |
| Apple Vision [6] | Height may be a 1.8 m reference value without appropriate depth capture. | Never turn reference-height or pose coordinates into claimed measured dimensions. LiDAR cannot be the default for an Android-heavy audience. |

The engineering conclusion is to validate each supported measurement, device/capture
path and intended customer group. There is no defensible promise of exact results
from every photograph. Low-quality or unsupported captures need an explicit failure
or manual path rather than plausible-looking replacement numbers.

## Product journey

Two entry points can eventually share one measurement-request system:

1. **Existing relationship:** tailor selects a customer and sends a scoped request
   through the existing share/WhatsApp pattern.
2. **Customer acquisition:** a customer opens a designer's public profile or enquiry
   link, chooses to contact that designer, and opts into a measurement request.

Both entry points are required from the first product release. They share the same
capture and review system. For discovery, design an opt-in designer profile/search
surface with deliberately published fields, a new-customer enquiry and explicit
connection acceptance; never expose private workshop/customer records. Exact discovery
fields and geographical filters need product review. Booking, marketplace payments,
ratings and dispute resolution are not implied by these two entry points. This is a
separate core-feature programme from the Africa expansion release.

Suggested flow:

`Request → customer confirmation and consent → device check → guided capture →
quality check → estimation → measurement review → customer sharing approval →
tailor review → new saved measurement version → use on an order`

- State clearly which designer receives the result, whether photographs leave the
  device, the processing provider, retention and deletion choices.
- Explain clothing, camera position, lighting and space before asking for camera
  permission. Provide a hands-free timer and an assisted mode where supported.
- Capture fresh images within the guided process; do not begin with arbitrary gallery
  uploads. Select two photos versus video only after comparative tests and Daniel's input.
- Request a reliable height/scale input when required by the chosen method. Explain
  how to measure it; an inaccurate self-reported height can compromise every dimension.
- Tell customers which measurements were obtained and which require manual input.
  Do not label unsupported values as zero or quietly derive them from demographics.
- Customer review concerns consent and obvious mistakes; it does not establish
  accuracy. The tailor explicitly accepts, edits, requests a rescan, or asks for a
  manual check before garment production.
- An estimate never overwrites an existing measurement record automatically.
- Manual measurement remains usable when capture is unsupported, declined or unreliable.

## Capture approaches

| Approach | Strength | Limitation | Role |
| --- | --- | --- | --- |
| One gallery photo | Low effort | Unknown scale, hidden depth, clothing and lens distortion | Exclude from initial accuracy promise |
| Guided front and side photos | Lower bandwidth; supported by candidate providers | Requires good clothing/pose/scale and two captures | First method to evaluate |
| Guided multi-view video | Additional body views and quality checks | Motion, space, bandwidth and processing burden | Compare if photos miss required tolerances |
| Depth-assisted scan | Additional geometric evidence | Device support and self-capture constraints | Optional research arm, not universal requirement |
| Manual-assisted remote measurement | Available when scanning fails | Customer placement/tension errors | Guided fallback with tailor verification |

## Provider and build options

All vendor accuracy statements below are vendor claims until our pilot verifies them.
Public pages do not replace contractual API access, data-processing terms or a benchmark.

| Candidate | Verified public capability | Important gap |
| --- | --- | --- |
| 3DLOOK Mobile Tailor [7] | Guided front/side browser capture and measurement-link workflow. Existing-photo upload is restricted by its capture approach. | Obtain measurement definitions, validation by field/body group, integration terms and actual API plan quote. |
| Bodygram Platform [8] | API/scanner with front/side photos plus supplied stats; five trial scans advertised. Stats-only estimation is a distinct mode. | No stats-only fallback accepted as measured tailoring data. Obtain price, contract and field-level validation. |
| Size Stream [9] | Advertises guided iOS/Android capture, measurement output and local image processing with non-image results sent onward. | Verify compatible low-end devices, SDK/white-label rights, exact processing boundary, web capture availability and quote. |
| Mezer [10] | African-fashion-oriented customer measurement records, camera estimates, review and sharing. | Public developer integration, licensing, current accuracy by field and service terms require verification. Treat as a possible partner or workflow benchmark, not a selected engine. |
| Own model | Control of capture and model evolution | Requires licensed assets, representative training/validation data, ML staffing and ongoing field validation. | 

Preferred direction: evaluate a self-hosted engine against the tape-reference cohort
first, with a commercial service as an optional benchmark only within an agreed budget.
Do not make two paid subscriptions a prerequisite. Keep our customer/request/review
system independent of the engine. No candidate is approved until fit, device and cost
tests support it.

Bodygram's current scanner requirements list Android 13+ and iOS 16+ [11]. StitchPad
supports Android API 24, so choosing this scanner alone would leave some supported
customers without capture. Its offline PWA is experimental and captures images;
that is not evidence that measurement inference works offline [12].

Research code needs a commercial-use audit. SHAPY's published license restricts
commercial use and training; SMPL commercial use needs licensing [13,14]. A GitHub
repository with downloadable weights is not automatically usable in StitchPad.

## Cost model

3DLOOK's public pricing lists Mobile Tailor examples of $499/month for up to 100 scans
and $999/month for up to 500; API/SDK access is listed in enterprise offerings [15].
At full utilisation those example allocations are $4.99 and about $2.00 per scan,
respectively, before other costs. These are arithmetic examples, not API price quotes.
A low-cost tailor subscription must not silently subsidise unlimited scans.

Evaluate cost per **accepted usable measurement set**:

`(platform minimum + billable attempts + retries + processing/storage + support)
 / tailor-accepted usable sets`

Track capture abandonment, technical failure, retries, missing essential fields,
and tailor rejection in that denominator. Ask whether a failed attempt is billable,
whether credits expire, and whether model upgrades change pricing or outputs.
Candidate commercial models are scan credits, a customer-paid measurement request,
or a higher plan with a capped allowance. Daniel chooses after pilot economics.

## Measurement definitions and garment fit

Current code uses `Measurement.fields: Map<String, String>` deliberately because
manual tailor entries may include fractions or segmented lengths. `GarmentType`
also includes ambiguous labels such as `Length`, `Gown Length`, and `Arm Length`.
Do not replace that manual representation or assume one provider field matches by name.

Create a versioned definition registry with canonical numeric units in millimetres,
anatomical landmarks, posture, measurement path, side and definition revision.
Keep body dimensions, wearing ease and desired finished-garment lengths distinct.
Conversions to inches happen only at display/import boundaries (25.4 mm per inch).
Keep original numeric values and provenance; do not round repeatedly.

| Existing field | Mapping work before automatic import |
| --- | --- |
| Chest/Bust | Define anatomical level and clothing conditions; do not substitute one for the other. |
| Waist | Natural waist and navel/belly waist are different definitions. |
| Hip | Confirm the fullest-girth level. |
| Shoulder Width | Straight breadth and an across-back surface path differ. |
| Arm Length | Shoulder-to-wrist and neck-to-wrist sleeve length differ. |
| Inseam | Define start/end landmarks and posture. |
| Dress/Gown/Jacket/Trouser Length | Usually requires garment intent or additional landmarks; do not infer desired hem from body height. |
| Underbust/Bodice/Corset fields | Validate independently; leave manual when not proven. |

Bodygram's definition reference illustrates these distinctions [16]. Map only
fields with matching definitions that pass our validation. Tailors still determine
ease, design choices, fabric behaviour and garment finishing.

## Accuracy validation

An initial standard-library [offline evaluator](../../tools/measurement_validation/README.md)
checks a paired attempt ledger against caller-supplied tolerances. Its ten synthetic
regression tests pass. That validates bookkeeping only; no real scan accuracy or
release gate is established by these tests.

The pilot must answer: “Will this customer's garment fit using this measurement
set?” It must also measure when the app correctly declines to estimate.

1. Tailors agree definitions and acceptable errors **per measurement and garment**
   before provider evaluation. Possible 5–10 mm engineering targets are discussion
   examples, not approved tolerances or demonstrated achievable accuracy.
2. Recruit consenting participants representing the initial customer scope, body-size
   extremes, skin tones, clothing conditions and actual low/mid/high-end phones.
   Do not collect sensitive attributes without a defined purpose and consent.
3. Obtain repeated reference measurements from trained measurers using an agreed
   tape protocol. Resolve disagreements independently; neither reference measurer
   sees the model output before recording their result.
4. Collect repeated guided scans in supervised conditions, then repeat the test at
   home without an expert positioning the customer.
5. Run feasible candidates against the same participants. Keep all data from a
   participant within one development/calibration/evaluation partition.
6. Start with a small feasibility cohort (e.g. 20–30 adults) to find capture and
   mapping defects. Determine the release cohort size from the desired confidence
   in failure rates and subgroup coverage; a small pilot cannot establish broad accuracy.
7. Record signed bias, MAE, median, P90/P95 absolute error, limits of agreement,
   test-retest differences and the proportion within each agreed tolerance.
8. Report participant-level uncertainty and **whole-essential-set** acceptance.
   Six good fields plus one bad critical field is a failed measurement set.
9. Include every initiated attempt: success, failure, abandonment, retry and missing
   fields. Separate conditional accuracy on completed scans from end-to-end usability.
10. Validate uncertainty estimates against held-out data. Pose visibility or a vendor's
    quality score is not an accuracy probability. Do not invent confidence intervals.
11. Make selected sample garments and assess alteration/remake rate with tailors.
    Good measurement statistics alone are not evidence of fit outcomes.
12. Freeze capture SDK, model, definitions and calibration versions for evaluation.
    Revalidate material changes; results for one version do not bless every update.

### Proposed release gate

Release only supported measurement definitions and capture/device cohorts that meet
pre-agreed field and whole-set tolerances with adequate held-out evidence. If any
essential field is missing, unsupported, outside the validated domain or uncertain,
route to rescan/manual verification. Excluded populations are explicitly unsupported,
not quietly served by demographic averages. Calibration is fit only on development
data, never tuned to the final evaluation set.

No public “accurate enough for cutting” claim until garment fit and alteration results
support it. No aggregate “97% accurate” badge in place of field-level evidence.

## Architecture proposal

Use KMP domain contracts and platform capture adapters; keep provider credentials and
raw result parsing on the server. Do not call a generic text/image chatbot to invent
precise measurements from a photo. A general model may help explain instructions,
but it is not the measurement authority.

Suggested domain concepts:

- `MeasurementRequest`: workshop/customer references, approved purpose, requested
  definition IDs, expiry and customer authorisation.
- `CaptureSession`: request-bound upload scope, capture protocol, consent revision,
  attempt number, device compatibility and server-owned state.
- `MeasurementEstimate`: field ID, numeric mm, provider/model/definition version,
  capture conditions, quality outcome, and uncertainty only when validated.
- `MeasurementReview`: accepted/edited/rejected/needs-manual per field, original
  estimate retained, reviewer and immutable revision.
- `ApprovedMeasurementSet`: explicit tailor acceptance; imports as a **new** existing
  Measurement record without changing old records or linked orders automatically.

Server-owned state machine:

`created → consented → capturing → uploaded → processing → ready_for_review → approved`

Terminal/alternative states: `declined`, `expired`, `cancelled`, `failed`, `needs_rescan`,
`needs_manual_measurement`. Transition guards must reject late/replayed provider
responses for a revoked or superseded capture. Idempotency keys prevent double
billing and repeated imports.

Only the server constructs vendor requests and normalises responses. Validate
webhook signatures when available; otherwise retrieve results through authenticated
server-to-server calls. A customer browser declaring success cannot write an
approved record. Pending estimates never become a source for cutting automatically.

Read-only subscription policy also applies: do not let public scan links or Admin
SDK imports bypass the workshop's business-write lock. Customer cancellation, privacy
requests and deletion remain available after expiry. Define whether a request already
paid for may finish processing while import waits for restored workshop access.

## Privacy and access design

Proposed pilot: consenting adults; minimal inputs; customer chooses the receiving
tailor. These scope choices await Daniel. No face identification, health/body-fat
claims, or sharing across unrelated workshops.

- Separate full-body capture storage from staff-readable order-media trees.
  Existing Storage rules permit active staff to read the owner's asset tree, so
  putting these photos under ordinary order/customer media would be inappropriate.
- Scoped, expiring invitations must not expose a customer book or guessable IDs.
  Bearer links can be forwarded; choose additional customer verification before
  displaying or changing existing personal data. No secrets in analytics/referrers.
- Use short-lived upload capabilities, strict type/size checks, encryption and
  service-only processing access. Do not log photos, landmarks, tokens or body values.
- Retain the minimum raw media for processing, with a documented retry timeout and
  automatic deletion. Request provider deletion and record completion. Review backup
  retention and any model-training use contractually.
- Make photo sharing and model-training consent separate from operational measurement
  sharing. Refusing either must not prevent manual measurement entry.
- Measurements and 3D meshes also reveal body information; image deletion alone is
  not full data minimisation. Customer deletion/revocation must reach provider copies.
- Verify region/subprocessors and cross-border arrangements. Firestore's EU region
  does not automatically keep third-party scans in Europe.

3DLOOK's FitXpress privacy FAQ describes immediate deletion or customer-specific
retention up to 30 days, while measurements/models may remain [17]. That is product-
and contract-specific, not a guarantee for every Mobile Tailor plan. Bodygram's
platform agreement describes processed body data, but its linked platform privacy
page returned 404 during this review; obtain the current agreement and DPA before
any participant upload [18]. These are procurement conditions, not reasons to stop
research. Applicable country/privacy requirements need review for the chosen flow;
NDPC provides the Nigerian statutory starting point [19].

## French and accessibility

Current repository has only English `values/strings.xml`; country metadata already
has French defaults. Persisted `locale` is not itself a UI translation system.
The approved expansion spec uses device language; Daniel's new request may instead
warrant a visible English/French preference. That policy is an open decision.

The measurement journey must cover both English and French: consent, spoken/text
capture directions, permissions, retry guidance, measurement definitions, units,
review and notifications. Validate tailoring terms with French-speaking tailors.
The customer's language can differ from the tailor's, and country must not be used
as an irreversible language choice. Never translate stable measurement IDs or
silently change units with language.

Provide text equivalents for voice guidance, large text, clear stance diagrams and
an assisted/manual route for people unable to stand in the capture pose. Do not
force an inaccessible scan to produce guessed numbers.

## Delivery milestones

| Milestone | Concrete output | Exit requirement |
| --- | --- | --- |
| 1. Definition and economics | Prioritised garments/fields, tolerances, pilot cohort and scan budget | Daniel/tailor decisions recorded |
| 2. Provider feasibility | Two-provider shortlist, exact device support, contract/privacy/price answers | A commercially usable, testable candidate |
| 3. Capture prototype | EN/FR customer request/capture/retry and tailor review, synthetic fixtures | UX review; no accuracy claims |
| 4. Controlled pilot | Blinded tape references, repeated scans and complete attempt ledger | Agreed field/set accuracy and usable cost evidence |
| 5. Production integration | KMP MVI screens, typed Result errors, server adapter, rules and deletion | Security/privacy/tenant tests and failure recovery |
| 6. Fit validation | Limited garments with measured alteration/remake outcomes | Evidence supports the promised use |
| 7. Limited release | Selected cohorts/devices with monitoring, rollback and manual fallback | Post-release error and cost review |

These milestones have acceptance criteria rather than guessed delivery dates. Trial
and language PRs remain independently reviewable; photo-measurement code must not
activate alongside an incomplete trial rollout.

## Questions awaiting Daniel

- Which garments, essential fields and fit tolerances come first?
- Can tailors/customers participate in paired tape/scan and garment-fit tests?
- Maximum pilot/monthly/per-usable-scan budget; own engine preferred, inexpensive provider alternatives may be explored.
- Adults-only pilot, customer-controlled sharing and deletion policy acceptable?
- Follow device language or introduce an explicit English/French preference?

Further decisions after feasibility: who pays for scans, scan validity/remeasurement
policy, request identity verification, treatment of already-paid requests when workshop
access expires, unsupported customers, and alteration/remake responsibility. Silence
or a preselected UI option is not approval.

## Sources

[1] [Graybeal et al. abstract](https://pubmed.ncbi.nlm.nih.gov/36632007/).
[2] [Graybeal et al. full paper](https://www.cambridge.org/core/services/aop-cambridge-core/content/view/49AFA085C23914A64D2B89CBBD8C8BD8/S0007114523000090a.pdf/evaluation-of-automated-anthropometrics-produced-by-smartphone-based-machine-learning-a-comparison-with-traditional-anthropometric-assessments.pdf).
[3] [Choudhary et al. full paper, Tables 1–3](https://cdn.amazon.science/c5/5c/77c3c4554a70a4add986eea18a88/development-and-validation-of-an-accurate-smartphone-application-for-measuring-waist-to-hip-circumference-ratio.pdf).
[4] [ISO 20685-1 evaluation protocol](https://www.iso.org/cms/%20render/live/en/sites/isoorg/contents/data/standard/06/32/63260.html).
[5] [Google Pose Landmarker](https://developers.google.com/edge/mediapipe/solutions/vision/pose_landmarker).
[6] [Apple 3D pose and height](https://developer.apple.com/documentation/vision/identifying-3d-human-body-poses-in-images).
[7] [3DLOOK Mobile Tailor](https://3dlook.ai/mobile-tailor/).
[8] [Bodygram Platform](https://docs.bodygram.com/platform).
[9] [Size Stream mobile scanning](https://www.sizestream.com/technology/mobile-scanning/).
[10] [Mezer](https://mezerme.com/).
[11] [Bodygram device requirements](https://docs.bodygram.com/platform/device-requirements).
[12] [Bodygram experimental offline capture](https://docs.bodygram.com/platform/pwa-scanner).
[13] [SHAPY licence](https://shapy.is.tue.mpg.de/license.html).
[14] [SMPL commercial-use FAQ](https://smpl.is.tue.mpg.de/faq.html).
[15] [3DLOOK pricing](https://3dlook.ai/pricing/).
[16] [Bodygram measurement definitions](https://docs.bodygram.com/platform/measurement-definitions).
[17] [3DLOOK FitXpress privacy FAQ](https://3dlook.ai/content-hub/fitxpress-data-privacy-security-regulatory-faq/).
[18] [Bodygram Platform Agreement](https://www.bodygram.com/en/policies/terms-bodygram-platform-agreement).
[19] [NDPC Act publication](https://www.ndpc.gov.ng/ndp-act-2023/).

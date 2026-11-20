# An affordable measurement engine owned by StitchPad

Daniel's direction, 4 November 2026: support both customer journeys, prioritise
accuracy over one-photo capture, and explore an inexpensive engine we own. No
budget, measurement tolerance, training-data consent or launch accuracy has been approved.

## Recommended experiment

Prototype a self-hosted pipeline with guided multi-view capture, a reliable scale
input, quality checks, a commercially usable body representation, and explicit
measurement definitions. Compare outputs with independently recorded tape measures.
Use third-party APIs only as optional, budget-limited benchmarks. Do not start by
training a large foundation model from scratch or by buying an unlimited API plan.

Owning our capture, geometry-fitting and evaluation code is feasible as an engineering
project. Whether it achieves the fit accuracy we need, on the target phones and
budget, is an experimental question. A reconstructed mesh can look right and still
have the wrong circumference. Neither an Apache licence nor a good pose benchmark
establishes tailoring accuracy.

## Candidate components and licence boundaries

| Component | Public evidence | Proposed use |
| --- | --- | --- |
| MediaPipe Pose Landmarker | Google's model returns estimated pose landmarks and optional segmentation [1]. | On-device capture guidance, framing and pose checking; not final circumference authority. |
| Anny native body model | NAVER publishes Apache-2.0 code and describes CC0 MakeHuman-derived assets; optional SMPL-X assets are separately restricted [2,3]. | Evaluate native topology for body fitting and measurement geometry. Avoid restricted optional assets. |
| Multi-HMR plus Anny checkpoint | Despite Anny's permissive body model, the reconstruction repository and released Anny checkpoint carry non-commercial terms [4,5]. | Not a production dependency without appropriate rights. Do not copy/distil its weights into a commercial engine assuming Anny's licence covers them. |
| SAM 3D Body plus MHR | Meta publishes reconstruction checkpoints under its custom SAM licence and MHR code under Apache-2.0 [6,7,8]. | Candidate reconstruction baseline subject to exact code/weight/asset/dependency terms and real cost testing. No verified tailoring accuracy. |
| SMPL/SHAPY/ECON | SMPL commercial use needs licensing; SHAPY and ECON publish non-commercial research restrictions [9,10,11]. | Do not build commercial shipping around a casually downloaded research pipeline. |
| SMPL-Anthropometry | The measurement utility repository is MIT, but requires SMPL/SMPL-X model assets [12]. | Study measurement algorithms only within applicable terms; utility licence does not license the underlying body models. |

Pin every selected repository revision, checkpoint hash, model topology, third-party
asset and licence text. Review dependencies and training-data restrictions separately.
Do not install broad example extras that fetch restricted assets implicitly.

## Proposed owned pipeline

1. **Prepare and consent.** Customer accepts a request from an existing tailor or a
   newly selected designer. Explain capture and sharing in English/French. Check
   supported device, camera, motion sensors, lighting and ability to stand safely.
2. **Establish scale.** Collect measured height and a repeatable camera arrangement;
   test whether a known-size reference improves error. Reject inconsistent or
   unavailable scale. Phone pose metadata is not proof of body dimensions.
3. **Capture guided front and side views.** Arms/feet follow the chosen protocol;
   keep distance and camera height controlled. Compare an additional back view or
   short video if essential fields fail. Loose clothes and occlusion trigger retry
   or manual guidance; do not hallucinate the hidden surface as measured truth.
4. **Quality gate.** Check full-body framing, blur, pose, lighting, one subject,
   capture consistency and scale. Log a reason code, not body imagery. Offline capture
   is distinct from offline inference and must be tested independently.
5. **Reconstruct/fit.** Evaluate either a licensed pretrained reconstruction baseline
   or our own optimisation against multiple silhouettes and landmarks using a
   permitted body representation. Fit camera and shape consistently across views.
   Regularisation prevents impossible shapes but must not flatten real body diversity.
6. **Measure.** Return the fitted surface to the agreed canonical pose. Extract
   definition-specific contours and surface distances, using anatomical regions to
   disambiguate limbs/torso. Keep dimensions in mm. A slice or convex hull of an
   arbitrary mesh is not automatically a correctly placed tape circumference.
7. **Check uncertainty and domain.** Test repeat captures and residuals as diagnostic
   signals. Calibrate any error intervals on held-out real participants. Reject or
   request manual checks where a required field is not validated. Do not present
   pose confidence as probability of correct fit.
8. **Review and version.** Customer controls sharing; tailor reviews estimates and
   enters garment ease/length choices separately. Save a new measurement version
   only after explicit approval, retaining estimate and edit provenance.

Anny-based silhouette optimisation is our proposed experiment, not an existing
validated photo-to-measurement feature. Its shape prior, camera estimation and scale
can still introduce errors. Synthetic bodies help test software but cannot replace
real, consented tape-reference and garment-fit validation.

## Data strategy

- Build a consented reference cohort covering initial garments, body dimensions,
  target phone tiers, capture conditions and relevant customer groups.
- Keep consent for operating the service separate from consent for model training.
  Do not reuse existing customer records or photographs for training by default.
- Collect explicit measurement definitions and trained repeated tape readings. A
  pile of photos with unverified labels is not an accuracy dataset.
- Keep participants separated between development, calibration and final evaluation.
  Repeated photos and scans from the same person must not cross partitions.
- Use only commercially permitted synthetic assets and real data with appropriate
  rights. Never derive a commercial model from a non-commercial teacher or dataset
  without the required permission.
- Start with geometric fitting and simple learned corrections, if justified by real
  error evidence. Correct only consistent definition-specific bias on development
  data; do not tune away test errors or substitute demographic averages.
- Measure failures for body-size extremes and different clothing/skin-tone/device
  conditions. Unsupported groups need honest manual alternatives.

## Cheap does not mean zero cost

Compare these operational modes using the same complete-attempt ledger:

| Mode | Advantage | Cost or unknown |
| --- | --- | --- |
| Device-only | No per-scan server GPU bill and reduced photo transfer | Model size, phone speed, battery, compatibility and accuracy unproven |
| Local capture plus self-hosted inference | Consistent engine and server-owned versions | Compute, cold starts, retries, bandwidth, deletion and support |
| Vendor API | Faster external benchmark | Minimums, billable retries, lock-in and compatible-device limits |
| Trained-from-scratch engine | Maximum training control | Dataset acquisition/annotation, specialist engineering, training and validation |

First benchmark CPU feasibility and one suitable GPU environment if needed; do not
provision paid hardware without a budget. Record actual inference seconds, peak
memory, model load time, upload bytes and end-to-end latency. Estimate request and
accepted-result costs from measurements, including idle minimums. No invented
“pennies per scan” claim before those numbers exist.

Cost comparison:

`fixed monthly platform and compute + measured variable attempts + retries + support`

Divide by **accepted essential measurement sets**, not generated meshes. A low-price
engine with frequent rescans or garment remakes may be the expensive option.

## First implementation sequence

1. Agree definitions, garment scope, field tolerances and a numeric spending ceiling.
2. Create engine-neutral request/estimate/review contracts for both customer journeys.
3. Implement the quality and validation harness using synthetic fixtures, with no
   production results or customer-photo uploads.
4. Choose a commercially usable baseline after checking its full asset/weight terms.
5. Prototype multi-view geometric fitting and measurement extraction offline.
6. Test supervised repeated scans against blinded tape references.
7. Compare at-home captures and actual garments; drop unsupported fields or adjust
   capture requirements based on held-out evidence.
8. Add a limited, explicit pilot in the app only after the accuracy and cost gates.

Public discovery, invite identity checks, consent, EN/FR guidance and tailor review
must work for both entry points from initial release. Engine research and production
journeys can progress independently without claiming the measuring engine is ready.

## Sources

[1] [Google Pose Landmarker](https://developers.google.com/edge/mediapipe/solutions/vision/pose_landmarker).
[2] [Anny official README and asset notices](https://raw.githubusercontent.com/naver/anny/main/README.md).
[3] [Anny code licence](https://raw.githubusercontent.com/naver/anny/main/LICENSE).
[4] [Multi-HMR licence](https://raw.githubusercontent.com/naver/multi-hmr/master/LICENSE.txt).
[5] [Anny reconstruction-checkpoint licence](https://download.europe.naverlabs.com/ComputerVision/MultiHMR/Checkpoint_License_Anny.txt).
[6] [SAM 3D Body](https://github.com/facebookresearch/sam-3d-body).
[7] [SAM checkpoint licence](https://huggingface.co/facebook/sam-3d-body-dinov3/blob/main/LICENSE).
[8] [MHR official repository and licence](https://github.com/facebookresearch/MHR).
[9] [SMPL commercial-use FAQ](https://smpl.is.tue.mpg.de/faq.html).
[10] [SHAPY licence](https://shapy.is.tue.mpg.de/license.html).
[11] [ECON official README](https://github.com/YuliangXiu/ECON/blob/master/README.md).
[12] [SMPL-Anthropometry original repository](https://github.com/DavidBoja/SMPL-Anthropometry).

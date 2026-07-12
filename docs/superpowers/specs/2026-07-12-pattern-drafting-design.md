# Pattern Drafting — Design Spec

**Date:** 2026-07-12
**Status:** Approved direction; visual design sign-off is an explicit checkpoint before app code (see Development Path).

## Why

Tailors draft a pattern block (bodice, sleeve, skirt…) on paper from a customer's measurements before cutting fabric. The math is repetitive, error-prone, and gatekept by fashion-school training. StitchPad already holds the customer's measurements; auto-drafting the block from them — drawn to scale, with every construction step's numbers pre-computed — turns the app from a record-keeper into a craft tool. This is a marquee Atelier-tier feature.

## Scope

### V1 (this project)
- **Auto-drafted blocks** computed from a saved measurement: rendered pattern geometry + step-by-step construction instructions with the customer's numbers substituted (e.g. "Mark B at bust/4 + 1in = 10.5in from A").
- **Blocks:** female bodice, sleeve (depends on bodice armhole geometry), skirt.
- **Outputs (phased in slices):** on-screen interactive draft (zoom/pan, labeled points, step↔drawing highlight sync); share-as-image (WhatsApp); steps-only printable PDF sheet; tiled true-scale multi-page A4 PDF with alignment marks + 5 cm calibration square.
- **Entry points:** (a) "Draft pattern" action on measurement detail; (b) standalone Patterns entry (dashboard tile → customer → measurement). Both converge on the same routes. No 5th bottom tab.
- **Gating:** `patternDraftingEnabled` flag on the `config/app` Firestore doc (fail-closed, mirrors `communityEnabled`) AND Atelier tier via `EntitlementsCalculator` (everyone has Atelier through 2026 via the launch-free grant, but the rule is explicit).

### Fast-follow (same engine, data-only additions)
Trouser block, kaftan/senator, agbada. Then dress/torso block.

### V2 (not now, not precluded)
Freehand drafting canvas — the auto-drafted block becomes an editable starting document. Style adaptations (dart manipulation, collars, facings) later still.

## Formula source of truth

Hybrid: an established flat-pattern-cutting system (Aldrich-style) as the base, adapted to StitchPad's `BodyProfileTemplate` field set, with a **tunable ease layer** so Nigerian-fit corrections from tester feedback are config/data changes, not code changes.

Ease parameters resolve in four layers (lowest → highest precedence):
1. Compiled default in the block definition.
2. Remote override: `patternEaseOverrides: Map<String, Double>` (cm, dotted keys like `bodice.bust_ease`) on `config/app` — console edit, no release.
3. Per-tailor defaults: `users/{uid}/patternPrefs/{blockId}`.
4. Per-draft tweaks: `users/{uid}/customers/{cid}/measurements/{mid}/patternDrafts/{blockId}`.

Geometry is never persisted — always recomputed from measurement snapshot + block definition + resolved params.

## Architecture

### Drafting engine (`feature/patterndrafting/domain/engine/`, pure commonMain Kotlin)
- Canonical unit **cm**; inches converted at the boundary in, display-only conversion out. Literals declare their unit (`cm(2.0)` / `inches(0.75)`).
- **Formulas = sealed expression tree** (`Const`, `MeasureRef`, `ParamRef`, `OutputRef`, `Add/Sub/Mul/Div/Max/Neg`) authored via a DSL (`measure("bust_circumference") / 4 + param("bodice.bust_ease")`). Evaluation yields the value **and** a token breakdown (`FormulaToken` list) — one definition drives both geometry and localized human-readable steps. The engine holds zero user-facing strings.
- **Geometry ops** mirror textbook language: `PlacePoint` (Cartesian / Offset "square down/across" / AlongLine / Midpoint), `DrawLine`, `DrawCurve` (explicit cubic Bezier controls — deterministic, print-exact, SVG-portable), `ExportOutput` (named scalars for cross-block deps; arc length via fixed-tolerance Bezier subdivision).
- **BlockDefinition** = id, gender, requiredMeasurements, declared `EaseParam`s, dependsOn, ordered `DraftStep`s (each: string-resource template key + ops). Blocks live in `domain/blocks/` as DSL-built data. Missing measurements → `Result.Error(DraftError.MissingMeasurements)` → UI links to the measurement form.
- Evaluator resolves block dependency order (sleeve ← bodice), evaluates into `PatternDraft` (points, styled paths, resolved steps with tokens, outputs, bounds).

### Persistence & config
- New DTO `PatternParamsDto(params: Map<String, Double>, updatedAt: Long)` — typed serializable (KMP-safe like `MeasurementDto.fields`).
- `AppConfig`/`AppConfigDto`/mapper: add `patternDraftingEnabled: Boolean = false` + `patternEaseOverrides: Map<String, Double> = emptyMap()`. Add a decode-tolerance unit test (malformed console edit must not nuke the whole config decode).
- `firestore.rules`: owner-scoped `patternDrafts/{blockId}` under measurements; `patternPrefs/{blockId}` under users. Measurement delete must also delete its `patternDrafts/*` (Firestore has no cascade).
- `EntitlementsCalculator.canUsePatternDrafting = tier == ATELIER`; deep links check via `awaitHydrated()` (pre-hydration default is FREE).

### Presentation (`feature/patterndrafting/presentation/`)
- Routes (`@Serializable`, registered in the inner NavHost in `MainScreen.kt`): `PatternsHomeRoute`, `PatternBlockPickerRoute(customerId, measurementId)`, `PatternDraftRoute(customerId, measurementId, blockId)`. `ScreenName` entries for analytics.
- Screens (MVI State/Action/Event, Root/Screen split, @Previews): block picker (gender-filtered cards, ready/missing-fields states), draft screen (PatternCanvas + step list + tweak sheet + share sheet).
- `PatternCanvas`: Compose Canvas, world-space cm → screen px transform, pinch zoom/pan (transform state is Compose-internal remember state), Sparkline.kt precedent. Selected step highlights its points (Sienna accent).
- `PatternStepFormatter` (presentation) renders tokens → localized text; all number formatting here (never `String.format` in commonMain — iOS link breakage).

### Export (`core/sharing/`)
`PatternSharer` interface (`shareAsImage`, `shareStepsPdf`, `shareTiledPdf`) + `PatternShareData` (pre-formatted strings + geometry flattened to polylines in cm at 0.02 cm tolerance) so platform impls are dumb draw loops. `PatternTiler` (pure commonMain, unit-tested): A4 tiling windows, overlap, alignment crosses, calibration square. Android: `PdfDocument` + FileProvider (clone `AndroidMeasurementSharer`); iOS: `UIGraphicsPDFRenderer`.

## Development path (web-first validation loop)

**PR 0 — visual design + formula validation (checkpoint: Daniel signs off on visuals before any app code):**
`preview/pattern-drafting/` — `drafting-math.mjs` (JS twin of the engine: same cm canon, Bezier constructions, arc-length constants) + interactive HTML/SVG page: type measurements → live draft + steps. Screens also pushed to Figma for fashion-designer feedback. All formula iteration happens here.

**Golden tests lock the port:** `scripts/generate-pattern-goldens.mjs` evaluates 4–6 measurement profiles (petite→plus, inches & cm) through the JS engine and generates a Kotlin fixture file; `PatternGoldenTest` (commonTest) asserts the Kotlin engine reproduces every coordinate (1e-6 cm; 1e-4 for arc-length-derived values). Formula changes are prototype-first, regenerate, re-port.

**PR slices 1–9:** flag+tier plumbing → engine primitives → block definitions+goldens → params/persistence+rules → screens+both entry points (flag-gated MVP) → tweak sheet → image+steps-PDF share → tiled true-scale PDF (physical-ruler calibration QA) → polish (analytics, dashboard tile, i18n pass). Each PR: CI + iOS-compile gate + manual QA smoke steps.

## Error handling
- `DraftError` (feature domain): `MissingMeasurements(keys)`, `UnsupportedGender`, `InvalidDefinition` (programmer error, logged). `Result<T,E>` throughout; `toUiText()` mapping in presentation.
- Missing non-essential template fields the draft needs (e.g. `nape_to_waist`): required-for-drafting prompt with deep link to the form; fallback formulas (`Max`/derived) case-by-case.
- Sharer failures → `Event.ShareFailed(UiText)` → snackbar (notification patterns: snackbar = feedback).

## Testing
- Engine: primitive unit tests (expr eval, point specs, arc length) + golden fixtures per block×profile + invariance tests (inches vs cm identical geometry; param override moves only affected points).
- `PatternTiler` unit tests (page windows, calibration square placement).
- ViewModels: Turbine tests with fake repos (existing commonTest toolchain).
- Config: `AppConfigDto` decode-tolerance test.
- Manual QA per slice; tiled-PDF slice includes measuring the printed calibration square with a physical ruler.

## Risks
- **Fit credibility** is the product risk — retired early via PR 0 prototype + designer feedback + tester paper-drafting before the flag opens beyond testers.
- iOS/KMP: no `String.format` in commonMain; typed serializable DTOs only; iOS sim compile gate in CI.
- Config doc fragility: ship app before editing `config/app`; decode-tolerance test.
- Detekt long declarative block definitions: split steps into private per-section vals.

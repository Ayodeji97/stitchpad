# Pattern Drafting Prototype (Phase 0) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An interactive HTML/SVG prototype in `preview/pattern-drafting/` that drafts female skirt, bodice, and sleeve blocks live from typed measurements, showing the to-scale drawing plus step-by-step instructions with computed numbers — the artifact Daniel shows fashion designers, and the formula source the Kotlin port will be golden-tested against.

**Architecture:** A declarative JS drafting engine that is the twin of the future Kotlin engine (spec: `docs/superpowers/specs/2026-07-12-pattern-drafting-design.md`): sealed-style expression trees (as tagged objects) evaluate to `{valueCm, tokens}`; blocks are data (steps of geometric ops); an evaluator resolves points/paths/steps/outputs; an SVG renderer and a small app shell sit on top. No frameworks, no build step — ES modules + `node --test`.

**Tech Stack:** Vanilla ES modules, `node:test` (Node 18+), SVG, Google Fonts (Fraunces/Manrope/JetBrains Mono — preview/ already uses CDN fonts).

## Global Constraints

- Everything lives under `preview/pattern-drafting/` — zero app (composeApp/functions/web) code in this phase.
- Canonical unit is **centimeters** everywhere in engine code; inches are a display/input conversion only (×2.54).
- Constants the Kotlin port must reproduce exactly are exported from one place (`core/geometry.mjs`): `ARC_SEGMENTS = 64`, `FLATTEN_SEGMENTS = 32`.
- Formula default values are **initial values by design** — this prototype exists to tune them; every tunable is a declared param with a slider, never a buried literal.
- Palette: Indigo `#2C3E7C`, Sienna `#B85A30`, Paper `#FAF6EC`, Ink `#14110E`. Saffron only as rare accent. Light mode only (it's a design scratchpad, matching other preview/ files).
- Tests run with: `node --test preview/pattern-drafting/test/` from repo root.
- Work happens on branch `feat/pattern-drafting` (already created; spec committed).

---

### Task 1: Expression core

**Files:**
- Create: `preview/pattern-drafting/core/expr.mjs`
- Test: `preview/pattern-drafting/test/expr.test.mjs`

**Interfaces:**
- Produces: `cm(v)`, `inches(v)`, `measure(fieldKey)`, `param(paramKey)`, `output(blockId, outputKey)`, `add/sub/mul/div/max(l, r)`, `neg(e)`, `sqrt(e)` — expression constructors returning plain tagged objects; `evaluate(expr, env) -> {valueCm, tokens}` where `env = {measurementsCm: Object, paramsCm: Object, outputsCm: Object}` (`outputsCm` shaped `{blockId: {key: number}}`); `MissingMeasurementError` with `.fieldKey`. Token shapes: `{t:'number', valueCm, displayUnit}`, `{t:'measure', fieldKey, valueCm}`, `{t:'param', paramKey, valueCm}`, `{t:'output', blockId, outputKey, valueCm}`, `{t:'op', symbol}`, `{t:'lparen'}`, `{t:'rparen'}`.

- [ ] **Step 1: Write the failing test**

```js
// preview/pattern-drafting/test/expr.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  cm, inches, measure, param, add, div, sqrt, max,
  evaluate, MissingMeasurementError,
} from '../core/expr.mjs';

const env = {
  measurementsCm: { bust_circumference: 92 },
  paramsCm: { 'bodice.bust_ease': 10 },
  outputsCm: {},
};

test('evaluates bust/4 + ease/4 with token trail', () => {
  const expr = add(div(measure('bust_circumference'), cm(4)), div(param('bodice.bust_ease'), cm(4)));
  const r = evaluate(expr, env);
  assert.equal(r.valueCm, 92 / 4 + 10 / 4);
  const kinds = r.tokens.map((t) => t.t);
  assert.deepEqual(kinds, ['measure', 'op', 'number', 'op', 'param', 'op', 'number']);
});

test('inches literal converts to cm but remembers display unit', () => {
  const r = evaluate(inches(1), env);
  assert.equal(r.valueCm, 2.54);
  assert.equal(r.tokens[0].displayUnit, 'in');
});

test('parenthesizes additive child inside multiplicative parent', () => {
  const expr = div(add(measure('bust_circumference'), param('bodice.bust_ease')), cm(4));
  const kinds = evaluate(expr, env).tokens.map((t) => t.t);
  assert.deepEqual(kinds, ['lparen', 'measure', 'op', 'param', 'rparen', 'op', 'number']);
});

test('sqrt and max evaluate', () => {
  assert.equal(evaluate(sqrt(cm(9)), env).valueCm, 3);
  assert.equal(evaluate(max(cm(2), cm(5)), env).valueCm, 5);
});

test('missing measurement throws typed error', () => {
  assert.throws(() => evaluate(measure('hip_circumference'), env), (e) => e instanceof MissingMeasurementError && e.fieldKey === 'hip_circumference');
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node --test preview/pattern-drafting/test/`
Expected: FAIL — `Cannot find module '../core/expr.mjs'`

- [ ] **Step 3: Write the implementation**

```js
// preview/pattern-drafting/core/expr.mjs
// Expression tree — JS twin of the future Kotlin engine (spec §Architecture).
// evaluate() returns { valueCm, tokens }: one tree drives geometry AND readable steps.

export class MissingMeasurementError extends Error {
  constructor(fieldKey) {
    super(`Missing measurement: ${fieldKey}`);
    this.fieldKey = fieldKey;
  }
}

export const cm = (v) => ({ kind: 'const', valueCm: v, displayUnit: 'cm' });
export const inches = (v) => ({ kind: 'const', valueCm: v * 2.54, displayUnit: 'in' });
export const measure = (fieldKey) => ({ kind: 'measure', fieldKey });
export const param = (paramKey) => ({ kind: 'param', paramKey });
export const output = (blockId, outputKey) => ({ kind: 'output', blockId, outputKey });
export const add = (l, r) => ({ kind: 'add', l, r });
export const sub = (l, r) => ({ kind: 'sub', l, r });
export const mul = (l, r) => ({ kind: 'mul', l, r });
export const div = (l, r) => ({ kind: 'div', l, r });
export const max = (l, r) => ({ kind: 'max', l, r });
export const neg = (e) => ({ kind: 'neg', e });
export const sqrt = (e) => ({ kind: 'sqrt', e });

const ADDITIVE = new Set(['add', 'sub']);
const MULTIPLICATIVE = new Set(['mul', 'div']);

function wrapIfNeeded(child, parentKind) {
  const needsParens = MULTIPLICATIVE.has(parentKind) && ADDITIVE.has(child.expr.kind);
  return needsParens ? [{ t: 'lparen' }, ...child.tokens, { t: 'rparen' }] : child.tokens;
}

export function evaluate(expr, env) {
  switch (expr.kind) {
    case 'const':
      return { valueCm: expr.valueCm, tokens: [{ t: 'number', valueCm: expr.valueCm, displayUnit: expr.displayUnit }] };
    case 'measure': {
      const v = env.measurementsCm[expr.fieldKey];
      if (v === undefined || v === null || Number.isNaN(v)) throw new MissingMeasurementError(expr.fieldKey);
      return { valueCm: v, tokens: [{ t: 'measure', fieldKey: expr.fieldKey, valueCm: v }] };
    }
    case 'param': {
      const v = env.paramsCm[expr.paramKey];
      if (v === undefined) throw new Error(`Unresolved param: ${expr.paramKey}`);
      return { valueCm: v, tokens: [{ t: 'param', paramKey: expr.paramKey, valueCm: v }] };
    }
    case 'output': {
      const v = env.outputsCm[expr.blockId]?.[expr.outputKey];
      if (v === undefined) throw new Error(`Unresolved output: ${expr.blockId}.${expr.outputKey}`);
      return { valueCm: v, tokens: [{ t: 'output', blockId: expr.blockId, outputKey: expr.outputKey, valueCm: v }] };
    }
    case 'neg': {
      const inner = { expr: expr.e, ...evaluate(expr.e, env) };
      return { valueCm: -inner.valueCm, tokens: [{ t: 'op', symbol: '-' }, ...wrapIfNeeded(inner, 'mul')] };
    }
    case 'sqrt': {
      const inner = evaluate(expr.e, env);
      return { valueCm: Math.sqrt(inner.valueCm), tokens: [{ t: 'op', symbol: '√(' }, ...inner.tokens, { t: 'rparen' }] };
    }
    case 'add': case 'sub': case 'mul': case 'div': case 'max': {
      const l = { expr: expr.l, ...evaluate(expr.l, env) };
      const r = { expr: expr.r, ...evaluate(expr.r, env) };
      const symbols = { add: '+', sub: '−', mul: '×', div: '÷', max: 'max' };
      const value = {
        add: l.valueCm + r.valueCm,
        sub: l.valueCm - r.valueCm,
        mul: l.valueCm * r.valueCm,
        div: l.valueCm / r.valueCm,
        max: Math.max(l.valueCm, r.valueCm),
      }[expr.kind];
      if (expr.kind === 'max') {
        return { valueCm: value, tokens: [{ t: 'op', symbol: 'max(' }, ...l.tokens, { t: 'op', symbol: ',' }, ...r.tokens, { t: 'rparen' }] };
      }
      return { valueCm: value, tokens: [...wrapIfNeeded(l, expr.kind), { t: 'op', symbol: symbols[expr.kind] }, ...wrapIfNeeded(r, expr.kind)] };
    }
    default:
      throw new Error(`Unknown expr kind: ${expr.kind}`);
  }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `node --test preview/pattern-drafting/test/`
Expected: PASS (5 tests)

- [ ] **Step 5: Commit**

```bash
git add preview/pattern-drafting
git commit -m "feat(pattern-preview): expression core with token trail"
```

---

### Task 2: Geometry primitives

**Files:**
- Create: `preview/pattern-drafting/core/geometry.mjs`
- Test: `preview/pattern-drafting/test/geometry.test.mjs`

**Interfaces:**
- Produces: `ARC_SEGMENTS` (64), `FLATTEN_SEGMENTS` (32) — the shared constants the Kotlin port must copy; `cubicPoint(p0, c1, c2, p1, t) -> {x, y}`; `cubicLength(p0, c1, c2, p1) -> number` (polyline at ARC_SEGMENTS); `flattenCubic(p0, c1, c2, p1) -> [{x, y}]` (FLATTEN_SEGMENTS+1 points incl. endpoints); `dist(a, b) -> number`.

- [ ] **Step 1: Write the failing test**

```js
// preview/pattern-drafting/test/geometry.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { cubicPoint, cubicLength, flattenCubic, dist, ARC_SEGMENTS, FLATTEN_SEGMENTS } from '../core/geometry.mjs';

test('shared constants are locked', () => {
  assert.equal(ARC_SEGMENTS, 64);
  assert.equal(FLATTEN_SEGMENTS, 32);
});

test('degenerate cubic (controls on segment) has length = endpoint distance', () => {
  const p0 = { x: 0, y: 0 }, p1 = { x: 9, y: 12 };
  const c1 = { x: 3, y: 4 }, c2 = { x: 6, y: 8 };
  assert.ok(Math.abs(cubicLength(p0, c1, c2, p1) - 15) < 1e-9);
});

test('cubicPoint hits endpoints at t=0 and t=1', () => {
  const p0 = { x: 1, y: 2 }, c1 = { x: 5, y: 0 }, c2 = { x: 9, y: 4 }, p1 = { x: 10, y: 10 };
  assert.deepEqual(cubicPoint(p0, c1, c2, p1, 0), p0);
  assert.deepEqual(cubicPoint(p0, c1, c2, p1, 1), p1);
});

test('flattenCubic returns FLATTEN_SEGMENTS+1 points starting/ending at endpoints', () => {
  const pts = flattenCubic({ x: 0, y: 0 }, { x: 0, y: 5 }, { x: 5, y: 10 }, { x: 10, y: 10 });
  assert.equal(pts.length, FLATTEN_SEGMENTS + 1);
  assert.deepEqual(pts[0], { x: 0, y: 0 });
  assert.deepEqual(pts.at(-1), { x: 10, y: 10 });
});

test('dist', () => {
  assert.equal(dist({ x: 0, y: 0 }, { x: 3, y: 4 }), 5);
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node --test preview/pattern-drafting/test/`
Expected: FAIL — `Cannot find module '../core/geometry.mjs'`

- [ ] **Step 3: Write the implementation**

```js
// preview/pattern-drafting/core/geometry.mjs
// SHARED CONSTANTS with the Kotlin port — changing these invalidates goldens.
export const ARC_SEGMENTS = 64;
export const FLATTEN_SEGMENTS = 32;

export const dist = (a, b) => Math.hypot(b.x - a.x, b.y - a.y);

export function cubicPoint(p0, c1, c2, p1, t) {
  const u = 1 - t;
  const x = u * u * u * p0.x + 3 * u * u * t * c1.x + 3 * u * t * t * c2.x + t * t * t * p1.x;
  const y = u * u * u * p0.y + 3 * u * u * t * c1.y + 3 * u * t * t * c2.y + t * t * t * p1.y;
  return { x, y };
}

export function cubicLength(p0, c1, c2, p1) {
  let len = 0;
  let prev = p0;
  for (let i = 1; i <= ARC_SEGMENTS; i++) {
    const q = cubicPoint(p0, c1, c2, p1, i / ARC_SEGMENTS);
    len += dist(prev, q);
    prev = q;
  }
  return len;
}

export function flattenCubic(p0, c1, c2, p1) {
  const pts = [];
  for (let i = 0; i <= FLATTEN_SEGMENTS; i++) pts.push(cubicPoint(p0, c1, c2, p1, i / FLATTEN_SEGMENTS));
  return pts;
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `node --test preview/pattern-drafting/test/`
Expected: PASS (10 tests total)

- [ ] **Step 5: Commit**

```bash
git add preview/pattern-drafting
git commit -m "feat(pattern-preview): geometry primitives with locked port constants"
```

---

### Task 3: Draft engine (points, ops, steps, outputs)

**Files:**
- Create: `preview/pattern-drafting/core/engine.mjs`
- Test: `preview/pattern-drafting/test/engine.test.mjs`

**Interfaces:**
- Consumes: `evaluate` (Task 1), `cubicLength`, `dist` (Task 2).
- Produces:
  - Point specs: `at(xExpr, yExpr)`, `offset(fromId, dxExpr, dyExpr)`, `along(fromId, towardsId, distExpr)`, `mid(aId, bId, ratio = 0.5)` (ratio is a plain number).
  - Ops: `placePoint(id, spec, {construction} = {})`, `line(id, fromId, toId, style = 'outline')`, `curve(id, fromId, toId, c1Spec, c2Spec, style = 'outline')`. Styles: `'outline' | 'construction' | 'dart'`.
  - `step(id, template, exprs, ops)` — `template` uses `{0}`, `{1}`… slots referencing `exprs` array.
  - Block shape (consumed by Tasks 4–6): `{ id, gender, requiredMeasurements: [..], params: [{key, label, defaultCm, minCm, maxCm}], dependsOn: [..], steps: [..], outputs: [{key, kind:'pathLength'|'distance'|'expr', pathId?|a?,b?|expr?}] }`.
  - `draftBlock(block, env) -> { blockId, points, paths, steps, outputs, bounds }` where `points = {id: {x, y, construction}}`, `paths = [{id, kind, from:{x,y}, to:{x,y}, c1?, c2?, style}]` (resolved coords), `steps = [{id, template, values: [{valueCm, tokens}], pointIds}]`, `outputs = {key: number}`, `bounds = {minX, minY, maxX, maxY}`.
  - `MissingMeasurementError` re-exported.

- [ ] **Step 1: Write the failing test**

```js
// preview/pattern-drafting/test/engine.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { cm, measure, div } from '../core/expr.mjs';
import { at, offset, along, mid, placePoint, line, curve, step, draftBlock } from '../core/engine.mjs';

const block = {
  id: 'demo', gender: 'FEMALE',
  requiredMeasurements: ['waist'],
  params: [{ key: 'demo.drop', label: 'Drop', defaultCm: 10, minCm: 5, maxCm: 20 }],
  dependsOn: [],
  steps: [
    step('s1', 'Start at A. Square down {0} to B.', [div(measure('waist'), cm(2))], [
      placePoint('A', at(cm(0), cm(0))),
      placePoint('B', offset('A', cm(0), div(measure('waist'), cm(2)))),
      line('cb', 'A', 'B'),
    ]),
    step('s2', 'Mark M halfway, C 3cm along A→B from A, and curve out.', [], [
      placePoint('M', mid('A', 'B', 0.5)),
      placePoint('C', along('A', 'B', cm(3))),
      curve('arc', 'A', 'B', at(cm(5), cm(10)), at(cm(5), cm(20))),
    ]),
  ],
  outputs: [
    { key: 'cb_len', kind: 'distance', a: 'A', b: 'B' },
    { key: 'arc_len', kind: 'pathLength', pathId: 'arc' },
  ],
};

const env = { measurementsCm: { waist: 68 }, paramsCm: { 'demo.drop': 10 }, outputsCm: {} };

test('places points via at/offset/mid/along', () => {
  const d = draftBlock(block, env);
  assert.deepEqual({ x: d.points.B.x, y: d.points.B.y }, { x: 0, y: 34 });
  assert.deepEqual({ x: d.points.M.x, y: d.points.M.y }, { x: 0, y: 17 });
  assert.deepEqual({ x: d.points.C.x, y: d.points.C.y }, { x: 0, y: 3 });
});

test('steps carry evaluated formula values and their placed pointIds', () => {
  const d = draftBlock(block, env);
  assert.equal(d.steps[0].values[0].valueCm, 34);
  assert.deepEqual(d.steps[0].pointIds, ['A', 'B']);
});

test('outputs: distance and pathLength', () => {
  const d = draftBlock(block, env);
  assert.equal(d.outputs.cb_len, 34);
  assert.ok(d.outputs.arc_len > 34); // bowed curve is longer than the chord
});

test('bounds cover curve bulge', () => {
  const d = draftBlock(block, env);
  assert.ok(d.bounds.maxX > 3); // curve bulges to x≈5
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node --test preview/pattern-drafting/test/`
Expected: FAIL — `Cannot find module '../core/engine.mjs'`

- [ ] **Step 3: Write the implementation**

```js
// preview/pattern-drafting/core/engine.mjs
import { evaluate, MissingMeasurementError } from './expr.mjs';
import { cubicLength, flattenCubic, dist } from './geometry.mjs';

export { MissingMeasurementError };

// --- Point specs ---
export const at = (x, y) => ({ kind: 'cartesian', x, y });
export const offset = (from, dx, dy) => ({ kind: 'offset', from, dx, dy });
export const along = (from, towards, distance) => ({ kind: 'along', from, towards, distance });
export const mid = (a, b, ratio = 0.5) => ({ kind: 'mid', a, b, ratio });

// --- Ops ---
export const placePoint = (id, spec, { construction = false } = {}) => ({ kind: 'placePoint', id, spec, construction });
export const line = (id, from, to, style = 'outline') => ({ kind: 'line', id, from, to, style });
export const curve = (id, from, to, c1, c2, style = 'outline') => ({ kind: 'curve', id, from, to, c1, c2, style });

// --- Step ---
export const step = (id, template, exprs, ops) => ({ id, template, exprs, ops });

function getPoint(points, id) {
  const p = points[id];
  if (!p) throw new Error(`Unknown point: ${id}`);
  return p;
}

function resolveSpec(spec, points, env) {
  switch (spec.kind) {
    case 'cartesian':
      return { x: evaluate(spec.x, env).valueCm, y: evaluate(spec.y, env).valueCm };
    case 'offset': {
      const from = getPoint(points, spec.from);
      return { x: from.x + evaluate(spec.dx, env).valueCm, y: from.y + evaluate(spec.dy, env).valueCm };
    }
    case 'along': {
      const from = getPoint(points, spec.from);
      const towards = getPoint(points, spec.towards);
      const d = dist(from, towards);
      const t = d === 0 ? 0 : evaluate(spec.distance, env).valueCm / d;
      return { x: from.x + (towards.x - from.x) * t, y: from.y + (towards.y - from.y) * t };
    }
    case 'mid': {
      const a = getPoint(points, spec.a);
      const b = getPoint(points, spec.b);
      return { x: a.x + (b.x - a.x) * spec.ratio, y: a.y + (b.y - a.y) * spec.ratio };
    }
    default:
      throw new Error(`Unknown point spec: ${spec.kind}`);
  }
}

export function draftBlock(block, env) {
  const points = {};
  const paths = [];
  const steps = [];

  for (const s of block.steps) {
    const placedIds = [];
    for (const op of s.ops) {
      if (op.kind === 'placePoint') {
        const { x, y } = resolveSpec(op.spec, points, env);
        points[op.id] = { x, y, construction: op.construction };
        placedIds.push(op.id);
      } else if (op.kind === 'line') {
        paths.push({ id: op.id, kind: 'line', from: { ...getPoint(points, op.from) }, to: { ...getPoint(points, op.to) }, style: op.style });
      } else if (op.kind === 'curve') {
        paths.push({
          id: op.id, kind: 'curve',
          from: { ...getPoint(points, op.from) }, to: { ...getPoint(points, op.to) },
          c1: resolveSpec(op.c1, points, env), c2: resolveSpec(op.c2, points, env),
          style: op.style,
        });
      } else {
        throw new Error(`Unknown op: ${op.kind}`);
      }
    }
    steps.push({ id: s.id, template: s.template, values: s.exprs.map((e) => evaluate(e, env)), pointIds: placedIds });
  }

  const outputs = {};
  for (const o of block.outputs) {
    if (o.kind === 'expr') outputs[o.key] = evaluate(o.expr, env).valueCm;
    else if (o.kind === 'distance') outputs[o.key] = dist(getPoint(points, o.a), getPoint(points, o.b));
    else if (o.kind === 'pathLength') {
      const p = paths.find((x) => x.id === o.pathId);
      if (!p) throw new Error(`Output pathLength: unknown path ${o.pathId}`);
      outputs[o.key] = p.kind === 'line' ? dist(p.from, p.to) : cubicLength(p.from, p.c1, p.c2, p.to);
    } else throw new Error(`Unknown output kind: ${o.kind}`);
  }

  const allPts = [
    ...Object.values(points),
    ...paths.flatMap((p) => (p.kind === 'curve' ? flattenCubic(p.from, p.c1, p.c2, p.to) : [p.from, p.to])),
  ];
  const bounds = {
    minX: Math.min(...allPts.map((p) => p.x)),
    minY: Math.min(...allPts.map((p) => p.y)),
    maxX: Math.max(...allPts.map((p) => p.x)),
    maxY: Math.max(...allPts.map((p) => p.y)),
  };

  return { blockId: block.id, points, paths, steps, outputs, bounds };
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `node --test preview/pattern-drafting/test/`
Expected: PASS (14 tests total)

- [ ] **Step 5: Commit**

```bash
git add preview/pattern-drafting
git commit -m "feat(pattern-preview): declarative draft engine (points, paths, steps, outputs)"
```

---

### Task 4: Skirt block

**Files:**
- Create: `preview/pattern-drafting/blocks/skirt.mjs`
- Test: `preview/pattern-drafting/test/skirt.test.mjs`

**Interfaces:**
- Consumes: expr constructors (Task 1), engine ops (Task 3).
- Produces: `export const skirtBlock` following the block shape from Task 3, `id: 'skirt_female'`, `requiredMeasurements: ['waist', 'hip_circumference']`. All formula constants declared as params (initial values, tuned in the browser later).

- [ ] **Step 1: Write the failing test**

```js
// preview/pattern-drafting/test/skirt.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { draftBlock, MissingMeasurementError } from '../core/engine.mjs';
import { skirtBlock } from '../blocks/skirt.mjs';

const paramsCm = Object.fromEntries(skirtBlock.params.map((p) => [p.key, p.defaultCm]));
const env = { measurementsCm: { waist: 68, hip_circumference: 96 }, paramsCm, outputsCm: {} };

test('drafts with sane structural relationships', () => {
  const d = draftBlock(skirtBlock, env);
  const halfHip = 96 / 2 + paramsCm['skirt.hip_ease'] / 2;
  assert.ok(Math.abs(d.points.D.x - halfHip) < 1e-9, 'CF is half hip + half ease from CB');
  assert.ok(d.points.C.y > d.points.B.y, 'hem is below hip line');
  assert.equal(d.points.C.y, paramsCm['skirt.length']);
});

test('waistline width honors waist target: side take + darts absorb the hip/waist difference', () => {
  const d = draftBlock(skirtBlock, env);
  const backWaistRun = d.points.BSW.x - d.points.A.x;
  const frontWaistRun = d.points.D.x - d.points.FSW.x;
  const target = 68 / 2 + paramsCm['skirt.waist_ease'] / 2
    + paramsCm['skirt.back_dart_width'] + paramsCm['skirt.front_dart_width'];
  assert.ok(Math.abs(backWaistRun + frontWaistRun - target) < 1e-9);
});

test('missing measurement surfaces typed error', () => {
  assert.throws(
    () => draftBlock(skirtBlock, { ...env, measurementsCm: { waist: 68 } }),
    (e) => e instanceof MissingMeasurementError && e.fieldKey === 'hip_circumference',
  );
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node --test preview/pattern-drafting/test/`
Expected: FAIL — `Cannot find module '../blocks/skirt.mjs'`

- [ ] **Step 3: Write the implementation**

```js
// preview/pattern-drafting/blocks/skirt.mjs
// Straight skirt block, half pattern: back (left, from CB at x=0) and front
// (right, to CF) joined at the side seam. y grows downward. All values cm.
// Formula defaults are INITIAL values — tuned live in the prototype.
import { cm, measure, param, add, sub, div, mul, neg } from '../core/expr.mjs';
import { at, offset, mid, placePoint, line, curve, step } from '../core/engine.mjs';

const waist = measure('waist');
const hip = measure('hip_circumference');
const p = (k) => param(`skirt.${k}`);

const halfHip = add(div(hip, cm(2)), div(p('hip_ease'), cm(2)));
const sideX = div(halfHip, cm(2));
const halfWaistTarget = add(div(waist, cm(2)), div(p('waist_ease'), cm(2)));
// Everything the waist must lose vs the hip width, minus the two darts, comes out at the side seam.
const sideTake = sub(sub(sub(halfHip, halfWaistTarget), p('back_dart_width')), p('front_dart_width'));

export const skirtBlock = {
  id: 'skirt_female',
  gender: 'FEMALE',
  requiredMeasurements: ['waist', 'hip_circumference'],
  params: [
    { key: 'skirt.length', label: 'Skirt length', defaultCm: 60, minCm: 40, maxCm: 110 },
    { key: 'skirt.waist_to_hip', label: 'Waist to hip depth', defaultCm: 20, minCm: 15, maxCm: 26 },
    { key: 'skirt.hip_ease', label: 'Hip ease (total)', defaultCm: 4, minCm: 0, maxCm: 10 },
    { key: 'skirt.waist_ease', label: 'Waist ease (total)', defaultCm: 2, minCm: 0, maxCm: 6 },
    { key: 'skirt.back_dart_width', label: 'Back dart width', defaultCm: 4, minCm: 0, maxCm: 6 },
    { key: 'skirt.front_dart_width', label: 'Front dart width', defaultCm: 2.5, minCm: 0, maxCm: 5 },
    { key: 'skirt.back_dart_length', label: 'Back dart length', defaultCm: 13, minCm: 8, maxCm: 18 },
    { key: 'skirt.front_dart_length', label: 'Front dart length', defaultCm: 10, minCm: 6, maxCm: 14 },
    { key: 'skirt.side_rise', label: 'Side seam rise', defaultCm: 1.2, minCm: 0, maxCm: 2.5 },
  ],
  dependsOn: [],
  steps: [
    step('frame', 'Draw the frame: A is the centre-back waist. Square down {0} to the hip line (B) and {1} to the hem (C). Square across {2} to the centre front (D, E, F).', [p('waist_to_hip'), p('length'), halfHip], [
      placePoint('A', at(cm(0), cm(0))),
      placePoint('B', offset('A', cm(0), p('waist_to_hip'))),
      placePoint('C', offset('A', cm(0), p('length'))),
      placePoint('D', at(halfHip, cm(0))),
      placePoint('E', at(halfHip, p('waist_to_hip'))),
      placePoint('F', at(halfHip, p('length'))),
      line('cb', 'A', 'C'),
      line('cf', 'D', 'F'),
      line('hem', 'C', 'F'),
      line('hipline', 'B', 'E', 'construction'),
    ]),
    step('side', 'Mark the side seam {0} from centre back. The waist must come in by {1} at the side; split it evenly and raise the side waist by {2}. Curve each side waist point down to the hip line, then rule straight to the hem.', [sideX, sideTake, p('side_rise')], [
      placePoint('HP', at(sideX, p('waist_to_hip')), { construction: true }),
      placePoint('SH', at(sideX, p('length'))),
      placePoint('BSW', at(sub(sideX, div(sideTake, cm(2))), neg(p('side_rise')))),
      placePoint('FSW', at(add(sideX, div(sideTake, cm(2))), neg(p('side_rise')))),
      curve('side_back', 'BSW', 'HP', offset('BSW', cm(0.5), mul(p('waist_to_hip'), cm(0.35))), offset('HP', cm(0), cm(-4))),
      curve('side_front', 'FSW', 'HP', offset('FSW', cm(-0.5), mul(p('waist_to_hip'), cm(0.35))), offset('HP', cm(0), cm(-4))),
      line('side_down', 'HP', 'SH'),
    ]),
    step('waist', 'Draw the back waistline from A to the raised side point, and the front waistline from the side point to D.', [], [
      line('waist_back', 'A', 'BSW'),
      line('waist_front', 'FSW', 'D'),
    ]),
    step('darts', 'Back dart: halfway along the back waist, width {0}, length {1}. Front dart: halfway along the front waist, width {2}, length {3}.', [p('back_dart_width'), p('back_dart_length'), p('front_dart_width'), p('front_dart_length')], [
      placePoint('BDC', mid('A', 'BSW', 0.5), { construction: true }),
      placePoint('BD1', offset('BDC', neg(div(p('back_dart_width'), cm(2))), cm(0))),
      placePoint('BD2', offset('BDC', div(p('back_dart_width'), cm(2)), cm(0))),
      placePoint('BDA', offset('BDC', cm(0), p('back_dart_length'))),
      line('bdart1', 'BD1', 'BDA', 'dart'),
      line('bdart2', 'BD2', 'BDA', 'dart'),
      placePoint('FDC', mid('FSW', 'D', 0.5), { construction: true }),
      placePoint('FD1', offset('FDC', neg(div(p('front_dart_width'), cm(2))), cm(0))),
      placePoint('FD2', offset('FDC', div(p('front_dart_width'), cm(2)), cm(0))),
      placePoint('FDA', offset('FDC', cm(0), p('front_dart_length'))),
      line('fdart1', 'FD1', 'FDA', 'dart'),
      line('fdart2', 'FD2', 'FDA', 'dart'),
    ]),
  ],
  outputs: [
    { key: 'side_seam_x', kind: 'distance', a: 'A', b: 'HP' },
  ],
};
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `node --test preview/pattern-drafting/test/`
Expected: PASS (17 tests total)

- [ ] **Step 5: Commit**

```bash
git add preview/pattern-drafting
git commit -m "feat(pattern-preview): straight skirt block definition"
```

---

### Task 5: Bodice block

**Files:**
- Create: `preview/pattern-drafting/blocks/bodice.mjs`
- Test: `preview/pattern-drafting/test/bodice.test.mjs`

**Interfaces:**
- Consumes: Tasks 1 + 3.
- Produces: `export const bodiceBlock`, `id: 'bodice_female'`, `requiredMeasurements: ['bust_circumference', 'waist', 'neck_circumference', 'shoulder_width', 'nape_to_waist', 'bust_point', 'bust_span']`. Outputs consumed by the sleeve (Task 6): `armhole_back_len` (pathLength of `armhole_back`), `armhole_front_len` (pathLength of `armhole_front`), `armhole_depth` (expr).

- [ ] **Step 1: Write the failing test**

```js
// preview/pattern-drafting/test/bodice.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { draftBlock } from '../core/engine.mjs';
import { bodiceBlock } from '../blocks/bodice.mjs';

const paramsCm = Object.fromEntries(bodiceBlock.params.map((p) => [p.key, p.defaultCm]));
const M = {
  bust_circumference: 92, waist: 74, neck_circumference: 37,
  shoulder_width: 38, nape_to_waist: 41, bust_point: 26, bust_span: 18,
};
const env = { measurementsCm: M, paramsCm, outputsCm: {} };

test('frame respects bust + ease and nape-to-waist', () => {
  const d = draftBlock(bodiceBlock, env);
  assert.ok(Math.abs(d.points.CF_TOP.x - (92 / 2 + paramsCm['bodice.bust_ease'] / 2)) < 1e-9);
  assert.equal(d.points.CB_WAIST.y, 41);
});

test('armhole outputs exist and are plausible (longer than armhole depth, shorter than 2x)', () => {
  const d = draftBlock(bodiceBlock, env);
  const depth = d.outputs.armhole_depth;
  assert.ok(d.outputs.armhole_back_len > depth * 0.9);
  assert.ok(d.outputs.armhole_front_len > depth * 0.9);
  assert.ok(d.outputs.armhole_back_len + d.outputs.armhole_front_len < depth * 4);
});

test('bust point sits bust_span/2 from centre front', () => {
  const d = draftBlock(bodiceBlock, env);
  const HB = 92 / 2 + paramsCm['bodice.bust_ease'] / 2;
  assert.ok(Math.abs((HB - d.points.BP.x) - 18 / 2) < 1e-9);
});

test('waist reduction is fully allocated: darts + side take close the bust/waist gap', () => {
  const d = draftBlock(bodiceBlock, env);
  const HB = 92 / 2 + paramsCm['bodice.bust_ease'] / 2;
  const targetHalfWaist = 74 / 2 + paramsCm['bodice.waist_ease'] / 2;
  const sideGap = d.points.FSW.x - d.points.BSW.x;
  const removed = sideGap + paramsCm['bodice.back_dart_width'] + paramsCm['bodice.front_dart_width'];
  assert.ok(Math.abs((HB - targetHalfWaist) - removed) < 1e-9);
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node --test preview/pattern-drafting/test/`
Expected: FAIL — `Cannot find module '../blocks/bodice.mjs'`

- [ ] **Step 3: Write the implementation**

```js
// preview/pattern-drafting/blocks/bodice.mjs
// Close-fitting female bodice block, combined half-draft: back at centre-back
// (x=0), front ending at centre-front (x = half bust + half ease). y grows
// downward; the nape sits at y=0, neck points rise above it (negative y).
// Formula defaults are INITIAL values adapted from standard flat-pattern
// systems — the prototype exists to tune them with designers.
import { cm, measure, param, add, sub, div, mul, neg } from '../core/expr.mjs';
import { at, offset, mid, placePoint, line, curve, step } from '../core/engine.mjs';

const bust = measure('bust_circumference');
const waist = measure('waist');
const neck = measure('neck_circumference');
const shoulderW = measure('shoulder_width');
const napeToWaist = measure('nape_to_waist');
const bustPoint = measure('bust_point');
const bustSpan = measure('bust_span');
const p = (k) => param(`bodice.${k}`);

const HB = add(div(bust, cm(2)), div(p('bust_ease'), cm(2)));          // half bust incl. ease
const AD = add(div(bust, cm(8)), p('armhole_depth_add'));               // armhole depth from nape
const neckW = sub(div(neck, cm(5)), cm(0.5));                           // neck width each side
const backW = sub(div(bust, cm(5)), cm(0.4));                           // across-back guide
const frontW = sub(HB, sub(div(bust, cm(5)), cm(1.5)));                 // across-front guide (x from CB)
const sideSeamX = add(div(HB, cm(2)), cm(0.5));
const halfWaistTarget = add(div(waist, cm(2)), div(p('waist_ease'), cm(2)));
const sideTake = sub(sub(sub(HB, halfWaistTarget), p('back_dart_width')), p('front_dart_width'));

export const bodiceBlock = {
  id: 'bodice_female',
  gender: 'FEMALE',
  requiredMeasurements: ['bust_circumference', 'waist', 'neck_circumference', 'shoulder_width', 'nape_to_waist', 'bust_point', 'bust_span'],
  params: [
    { key: 'bodice.bust_ease', label: 'Bust ease (total)', defaultCm: 10, minCm: 4, maxCm: 16 },
    { key: 'bodice.waist_ease', label: 'Waist ease (total)', defaultCm: 4, minCm: 0, maxCm: 10 },
    { key: 'bodice.armhole_depth_add', label: 'Armhole depth addition', defaultCm: 10.5, minCm: 8, maxCm: 13 },
    { key: 'bodice.neck_rise', label: 'Back neck rise', defaultCm: 2, minCm: 1, maxCm: 3 },
    { key: 'bodice.front_neck_drop_add', label: 'Front neck drop addition', defaultCm: 1, minCm: 0, maxCm: 3 },
    { key: 'bodice.back_shoulder_drop', label: 'Back shoulder drop', defaultCm: 3, minCm: 1.5, maxCm: 5 },
    { key: 'bodice.front_shoulder_drop', label: 'Front shoulder drop', defaultCm: 4.5, minCm: 2.5, maxCm: 7 },
    { key: 'bodice.back_dart_width', label: 'Back waist dart', defaultCm: 3, minCm: 0, maxCm: 5 },
    { key: 'bodice.front_dart_width', label: 'Front waist dart', defaultCm: 4, minCm: 0, maxCm: 6 },
    { key: 'bodice.back_dart_length', label: 'Back dart length', defaultCm: 14, minCm: 10, maxCm: 18 },
    { key: 'bodice.dart_gap_below_bp', label: 'Front dart gap below bust point', defaultCm: 3, minCm: 1, maxCm: 6 },
  ],
  dependsOn: [],
  steps: [
    step('frame', 'Frame: A is the nape (centre back, top). Square down the armhole depth {0} to the bust line, and the back length {1} to the waist. Square across the half-bust-plus-ease {2} to the centre front.', [AD, napeToWaist, HB], [
      placePoint('A', at(cm(0), cm(0))),
      placePoint('CB_BUST', at(cm(0), AD)),
      placePoint('CB_WAIST', at(cm(0), napeToWaist)),
      placePoint('CF_TOP', at(HB, cm(0))),
      placePoint('CF_BUST', at(HB, AD)),
      placePoint('CF_WAIST', at(HB, napeToWaist)),
      line('cb', 'A', 'CB_WAIST'),
      line('bustline', 'CB_BUST', 'CF_BUST', 'construction'),
    ]),
    step('back_neck_shoulder', 'Back neck: out {0} from A and up {1}; curve to A. Back shoulder: rule from the neck point to the shoulder tip at half shoulder width plus 1cm ({2}), dropped {3} below the nape line.', [neckW, p('neck_rise'), add(div(shoulderW, cm(2)), cm(1)), p('back_shoulder_drop')], [
      placePoint('NB', at(neckW, neg(p('neck_rise')))),
      curve('back_neck', 'A', 'NB', offset('A', div(neckW, cm(2)), cm(0)), offset('NB', neg(div(neckW, cm(3))), cm(0.3))),
      placePoint('SB', at(add(div(shoulderW, cm(2)), cm(1)), sub(p('back_shoulder_drop'), p('neck_rise')))),
      line('back_shoulder', 'NB', 'SB'),
    ]),
    step('front_neck_shoulder', 'Front neck: in {0} from centre front and down {1} on the centre front; curve between them. Front shoulder: rule from the front neck point to the front shoulder tip, dropped {2}.', [neckW, add(div(neck, cm(5)), p('front_neck_drop_add')), p('front_shoulder_drop')], [
      placePoint('NF', at(sub(HB, neckW), cm(0))),
      placePoint('NFC', at(HB, add(div(neck, cm(5)), p('front_neck_drop_add')))),
      curve('front_neck', 'NF', 'NFC', offset('NF', div(neckW, cm(2.2)), div(neckW, cm(2.2))), offset('NFC', neg(div(neckW, cm(3))), cm(0))),
      placePoint('SF', at(sub(HB, add(div(shoulderW, cm(2)), cm(1))), p('front_shoulder_drop'))),
      line('front_shoulder', 'NF', 'SF'),
      line('cf', 'NFC', 'CF_WAIST'),
    ]),
    step('armhole', 'Mark the underarm point on the bust line at {0} from centre back. Draw the back armhole from the back shoulder tip through the across-back guide ({1}) and the front armhole through the across-front guide.', [sideSeamX, backW], [
      placePoint('U', at(sideSeamX, AD)),
      curve('armhole_back', 'SB', 'U', at(backW, mul(AD, cm(0.55))), at(add(backW, cm(1)), mul(AD, cm(0.92)))),
      curve('armhole_front', 'U', 'SF', at(sub(frontW, cm(1)), mul(AD, cm(0.92))), at(frontW, mul(AD, cm(0.5)))),
    ]),
    step('side_waist', 'The waist must reduce by {0} beyond the darts; take it out at the side seam, half on each side of the underarm point. Rule the side seams and waistline.', [sideTake], [
      placePoint('BSW', at(sub(sideSeamX, div(sideTake, cm(2))), napeToWaist)),
      placePoint('FSW', at(add(sideSeamX, div(sideTake, cm(2))), napeToWaist)),
      line('side_back', 'U', 'BSW'),
      line('side_front', 'U', 'FSW'),
      line('waist_back', 'CB_WAIST', 'BSW'),
      line('waist_front', 'FSW', 'CF_WAIST'),
    ]),
    step('back_dart', 'Back waist dart: halfway between centre back and the side waist, width {0}, length {1} up from the waistline.', [p('back_dart_width'), p('back_dart_length')], [
      placePoint('BDC', mid('CB_WAIST', 'BSW', 0.5), { construction: true }),
      placePoint('BD1', offset('BDC', neg(div(p('back_dart_width'), cm(2))), cm(0))),
      placePoint('BD2', offset('BDC', div(p('back_dart_width'), cm(2)), cm(0))),
      placePoint('BDA', offset('BDC', cm(0), neg(p('back_dart_length')))),
      line('bdart1', 'BD1', 'BDA', 'dart'),
      line('bdart2', 'BD2', 'BDA', 'dart'),
    ]),
    step('front_dart', 'Bust point: {0} down from the shoulder line, half the bust span ({1}) in from centre front. Front waist dart: width {2}, closing {3} below the bust point.', [bustPoint, div(bustSpan, cm(2)), p('front_dart_width'), p('dart_gap_below_bp')], [
      placePoint('BP', at(sub(HB, div(bustSpan, cm(2))), sub(bustPoint, cm(3)))),
      placePoint('FDA', offset('BP', cm(0), p('dart_gap_below_bp'))),
      placePoint('FDC', at(sub(HB, div(bustSpan, cm(2))), napeToWaist), { construction: true }),
      placePoint('FD1', offset('FDC', neg(div(p('front_dart_width'), cm(2))), cm(0))),
      placePoint('FD2', offset('FDC', div(p('front_dart_width'), cm(2)), cm(0))),
      line('fdart1', 'FD1', 'FDA', 'dart'),
      line('fdart2', 'FD2', 'FDA', 'dart'),
    ]),
  ],
  outputs: [
    { key: 'armhole_back_len', kind: 'pathLength', pathId: 'armhole_back' },
    { key: 'armhole_front_len', kind: 'pathLength', pathId: 'armhole_front' },
    { key: 'armhole_depth', kind: 'expr', expr: AD },
  ],
};
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `node --test preview/pattern-drafting/test/`
Expected: PASS (21 tests total)

- [ ] **Step 5: Commit**

```bash
git add preview/pattern-drafting
git commit -m "feat(pattern-preview): female bodice block definition"
```

---

### Task 6: Sleeve block (cross-block dependency)

**Files:**
- Create: `preview/pattern-drafting/blocks/sleeve.mjs`
- Create: `preview/pattern-drafting/blocks/catalog.mjs`
- Test: `preview/pattern-drafting/test/sleeve.test.mjs`

**Interfaces:**
- Consumes: `output('bodice_female', 'armhole_back_len' | 'armhole_front_len')` (Task 5), Tasks 1 + 3.
- Produces: `export const sleeveBlock` (`id: 'sleeve_female'`, `dependsOn: ['bodice_female']`, `requiredMeasurements: ['arm_length', 'wrist_circumference']`); `catalog.mjs` exports `BLOCKS` (`{skirt_female, bodice_female, sleeve_female}`) and `draftAll(blockIds, env) -> {blockId: draft}` which dependency-orders blocks and threads `outputsCm` (throws on unknown id; auto-includes dependencies).

- [ ] **Step 1: Write the failing test**

```js
// preview/pattern-drafting/test/sleeve.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { draftAll, BLOCKS } from '../blocks/catalog.mjs';

const M = {
  bust_circumference: 92, waist: 74, neck_circumference: 37,
  shoulder_width: 38, nape_to_waist: 41, bust_point: 26, bust_span: 18,
  arm_length: 58, wrist_circumference: 16,
};
const paramsCm = Object.fromEntries(
  Object.values(BLOCKS).flatMap((b) => b.params.map((p) => [p.key, p.defaultCm])),
);
const env = { measurementsCm: M, paramsCm, outputsCm: {} };

test('draftAll pulls in the bodice dependency automatically', () => {
  const drafts = draftAll(['sleeve_female'], env);
  assert.ok(drafts.bodice_female, 'bodice drafted as dependency');
  assert.ok(drafts.sleeve_female, 'sleeve drafted');
});

test('sleeve geometry: crown diagonals match armhole lengths (classic construction)', () => {
  const drafts = draftAll(['sleeve_female'], env);
  const s = drafts.sleeve_female;
  const b = drafts.bodice_female;
  const hyp = (a, c) => Math.hypot(a.x - c.x, a.y - c.y);
  assert.ok(Math.abs(hyp(s.points.T, s.points.BB) - b.outputs.armhole_back_len) < 1e-6);
  assert.ok(Math.abs(hyp(s.points.T, s.points.BF) - b.outputs.armhole_front_len) < 1e-6);
});

test('sleeve length and cuff', () => {
  const drafts = draftAll(['sleeve_female'], env);
  const s = drafts.sleeve_female;
  assert.equal(s.points.CB2.y, 58);
  const cuff = s.points.CF2.x - s.points.CB2.x;
  assert.ok(Math.abs(cuff - (16 + paramsCm['sleeve.wrist_ease'])) < 1e-9);
});

test('draftAll rejects unknown block ids', () => {
  assert.throws(() => draftAll(['agbada'], env), /Unknown block/);
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node --test preview/pattern-drafting/test/`
Expected: FAIL — `Cannot find module '../blocks/catalog.mjs'`

- [ ] **Step 3: Write the sleeve block**

```js
// preview/pattern-drafting/blocks/sleeve.mjs
// Basic sleeve block drafted from the bodice armhole. Centre line at x=0,
// crown top T at y=0, back half in negative x. Classic construction: the
// crown diagonals to each bicep point equal the corresponding armhole length.
import { cm, measure, param, output, add, sub, div, mul, neg, sqrt } from '../core/expr.mjs';
import { at, offset, mid, placePoint, line, curve, step } from '../core/engine.mjs';

const armLen = measure('arm_length');
const wrist = measure('wrist_circumference');
const p = (k) => param(`sleeve.${k}`);
const AHB = output('bodice_female', 'armhole_back_len');
const AHF = output('bodice_female', 'armhole_front_len');

const crown = add(div(add(AHB, AHF), cm(4)), p('crown_add'));
const sq = (e) => mul(e, e);
const backHalf = sqrt(sub(sq(AHB), sq(crown)));   // horizontal leg of the back diagonal
const frontHalf = sqrt(sub(sq(AHF), sq(crown)));
const halfCuff = div(add(wrist, p('wrist_ease')), cm(2));

export const sleeveBlock = {
  id: 'sleeve_female',
  gender: 'FEMALE',
  requiredMeasurements: ['arm_length', 'wrist_circumference'],
  params: [
    { key: 'sleeve.crown_add', label: 'Crown height addition', defaultCm: 2.5, minCm: 0, maxCm: 5 },
    { key: 'sleeve.wrist_ease', label: 'Wrist ease (total)', defaultCm: 8, minCm: 2, maxCm: 14 },
  ],
  dependsOn: ['bodice_female'],
  steps: [
    step('frame', 'T is the top of the crown. The crown height is a quarter of the full armhole ({0} + {1}) plus {2} = {3}. Square down the sleeve length {4} to the cuff line.', [AHB, AHF, p('crown_add'), crown, armLen], [
      placePoint('T', at(cm(0), cm(0))),
      placePoint('UC', at(cm(0), crown), { construction: true }),
      placePoint('HEM', at(cm(0), armLen), { construction: true }),
      line('centre', 'T', 'HEM', 'construction'),
    ]),
    step('bicep', 'From T, swing the back armhole length {0} to the crown line for the back bicep point, and the front armhole length {1} for the front — the diagonals guarantee the sleeve fits the armhole.', [AHB, AHF], [
      placePoint('BB', at(neg(backHalf), crown)),
      placePoint('BF', at(frontHalf, crown)),
      line('bicep', 'BB', 'BF', 'construction'),
    ]),
    step('crown', 'Draw the crown: back rises above the diagonal then hollows to the bicep; the front mirrors it more shallowly.', [], [
      placePoint('MB', mid('BB', 'T', 0.5), { construction: true }),
      placePoint('MF', mid('T', 'BF', 0.5), { construction: true }),
      curve('crown_back', 'BB', 'T', offset('MB', neg(div(backHalf, cm(4))), div(crown, cm(3))), offset('MB', div(backHalf, cm(6)), neg(div(crown, cm(3.5))))),
      curve('crown_front', 'T', 'BF', offset('MF', neg(div(frontHalf, cm(6))), neg(div(crown, cm(3.5)))), offset('MF', div(frontHalf, cm(4)), div(crown, cm(3)))),
    ]),
    step('cuff', 'Cuff: half of wrist plus ease ({0}) each side of the centre line at the sleeve length; rule the underarm seams.', [halfCuff], [
      placePoint('CB2', at(neg(halfCuff), armLen)),
      placePoint('CF2', at(halfCuff, armLen)),
      line('underarm_back', 'BB', 'CB2'),
      line('underarm_front', 'BF', 'CF2'),
      line('cuff', 'CB2', 'CF2'),
    ]),
  ],
  outputs: [
    { key: 'crown_height', kind: 'expr', expr: crown },
  ],
};
```

- [ ] **Step 4: Write the catalog**

```js
// preview/pattern-drafting/blocks/catalog.mjs
import { draftBlock } from '../core/engine.mjs';
import { skirtBlock } from './skirt.mjs';
import { bodiceBlock } from './bodice.mjs';
import { sleeveBlock } from './sleeve.mjs';

export const BLOCKS = {
  [skirtBlock.id]: skirtBlock,
  [bodiceBlock.id]: bodiceBlock,
  [sleeveBlock.id]: sleeveBlock,
};

/** Drafts blocks in dependency order, threading outputs. Returns {blockId: draft}. */
export function draftAll(blockIds, env) {
  const order = [];
  const visit = (id, trail = new Set()) => {
    if (order.includes(id)) return;
    if (trail.has(id)) throw new Error(`Dependency cycle at ${id}`);
    const block = BLOCKS[id];
    if (!block) throw new Error(`Unknown block: ${id}`);
    trail.add(id);
    for (const dep of block.dependsOn) visit(dep, trail);
    order.push(id);
  };
  blockIds.forEach((id) => visit(id));

  const outputsCm = { ...env.outputsCm };
  const drafts = {};
  for (const id of order) {
    const draft = draftBlock(BLOCKS[id], { ...env, outputsCm });
    outputsCm[id] = draft.outputs;
    drafts[id] = draft;
  }
  return drafts;
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `node --test preview/pattern-drafting/test/`
Expected: PASS (25 tests total)

- [ ] **Step 6: Commit**

```bash
git add preview/pattern-drafting
git commit -m "feat(pattern-preview): sleeve block + dependency-ordering catalog"
```

---

### Task 7: SVG renderer

**Files:**
- Create: `preview/pattern-drafting/render/svg.mjs`
- Test: `preview/pattern-drafting/test/svg.test.mjs`

**Interfaces:**
- Consumes: draft objects from `draftAll` (Task 6).
- Produces: `renderSvg(draft, {highlightPointIds = [], showConstruction = true} = {}) -> string` — a complete `<svg>` element string. World units are cm mapped 1:10 to SVG user units (`SCALE = 10`); viewBox derived from `draft.bounds` + 3cm margin; 1cm grid lines; styles: outline = Ink `#14110E` width 2, construction = `#9A8F7F` width 1 dashed `6 4`, dart = Indigo `#2C3E7C` width 1.5; non-construction points as r=4 circles with labels; highlighted points as r=7 Sienna `#B85A30` circles.

- [ ] **Step 1: Write the failing test**

```js
// preview/pattern-drafting/test/svg.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { draftAll, BLOCKS } from '../blocks/catalog.mjs';
import { renderSvg } from '../render/svg.mjs';

const env = {
  measurementsCm: { waist: 68, hip_circumference: 96 },
  paramsCm: Object.fromEntries(BLOCKS.skirt_female.params.map((p) => [p.key, p.defaultCm])),
  outputsCm: {},
};
const draft = draftAll(['skirt_female'], env).skirt_female;

test('renders svg with viewBox, paths, labels', () => {
  const svg = renderSvg(draft);
  assert.match(svg, /^<svg[^>]*viewBox="/);
  assert.match(svg, /<path /);
  assert.match(svg, />A<\/text>/);
  assert.ok(!svg.includes('NaN'));
});

test('highlight paints selected points sienna', () => {
  const svg = renderSvg(draft, { highlightPointIds: ['A'] });
  assert.match(svg, /#B85A30/);
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node --test preview/pattern-drafting/test/`
Expected: FAIL — `Cannot find module '../render/svg.mjs'`

- [ ] **Step 3: Write the implementation**

```js
// preview/pattern-drafting/render/svg.mjs
const SCALE = 10; // 1 cm = 10 svg units
const STYLES = {
  outline: 'fill="none" stroke="#14110E" stroke-width="2" stroke-linecap="round"',
  construction: 'fill="none" stroke="#9A8F7F" stroke-width="1" stroke-dasharray="6 4"',
  dart: 'fill="none" stroke="#2C3E7C" stroke-width="1.5"',
};

const sx = (v) => (v * SCALE).toFixed(2);

export function renderSvg(draft, { highlightPointIds = [], showConstruction = true } = {}) {
  const m = 3; // cm margin
  const { minX, minY, maxX, maxY } = draft.bounds;
  const vb = `${sx(minX - m)} ${sx(minY - m)} ${sx(maxX - minX + 2 * m)} ${sx(maxY - minY + 2 * m)}`;
  const parts = [`<svg xmlns="http://www.w3.org/2000/svg" viewBox="${vb}">`];

  // 1cm grid
  parts.push('<g opacity="0.35">');
  for (let x = Math.ceil(minX - m); x <= maxX + m; x++) {
    parts.push(`<line x1="${sx(x)}" y1="${sx(minY - m)}" x2="${sx(x)}" y2="${sx(maxY + m)}" stroke="#E8E0CE" stroke-width="${x % 5 === 0 ? 0.8 : 0.3}"/>`);
  }
  for (let y = Math.ceil(minY - m); y <= maxY + m; y++) {
    parts.push(`<line x1="${sx(minX - m)}" y1="${sx(y)}" x2="${sx(maxX + m)}" y2="${sx(y)}" stroke="#E8E0CE" stroke-width="${y % 5 === 0 ? 0.8 : 0.3}"/>`);
  }
  parts.push('</g>');

  for (const p of draft.paths) {
    if (!showConstruction && p.style === 'construction') continue;
    const d = p.kind === 'line'
      ? `M ${sx(p.from.x)} ${sx(p.from.y)} L ${sx(p.to.x)} ${sx(p.to.y)}`
      : `M ${sx(p.from.x)} ${sx(p.from.y)} C ${sx(p.c1.x)} ${sx(p.c1.y)}, ${sx(p.c2.x)} ${sx(p.c2.y)}, ${sx(p.to.x)} ${sx(p.to.y)}`;
    parts.push(`<path d="${d}" ${STYLES[p.style]}/>`);
  }

  const highlights = new Set(highlightPointIds);
  for (const [id, pt] of Object.entries(draft.points)) {
    if (pt.construction && !highlights.has(id)) continue;
    const hot = highlights.has(id);
    parts.push(`<circle cx="${sx(pt.x)}" cy="${sx(pt.y)}" r="${hot ? 7 : 4}" fill="${hot ? '#B85A30' : '#2C3E7C'}"/>`);
    parts.push(`<text x="${sx(pt.x)}" y="${sx(pt.y)}" dx="9" dy="-6" font-size="14" font-family="JetBrains Mono, monospace" fill="#14110E">${id}</text>`);
  }

  parts.push('</svg>');
  return parts.join('\n');
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `node --test preview/pattern-drafting/test/`
Expected: PASS (27 tests total)

- [ ] **Step 5: Commit**

```bash
git add preview/pattern-drafting
git commit -m "feat(pattern-preview): SVG renderer with grid, styles, highlights"
```

---

### Task 8: Interactive page

**Files:**
- Create: `preview/pattern-drafting/index.html`
- Create: `preview/pattern-drafting/styles.css`
- Create: `preview/pattern-drafting/app.js`

**Interfaces:**
- Consumes: `draftAll`, `BLOCKS` (Task 6), `renderSvg` (Task 7), `MissingMeasurementError` (Task 3).
- Produces: the designer-facing prototype. No test file — verification is manual in the browser (ES modules require a server: `python3 -m http.server 8788` from repo root, open `http://localhost:8788/preview/pattern-drafting/`).

- [ ] **Step 1: Write `index.html`**

```html
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>StitchPad — Pattern Drafting Prototype</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link href="https://fonts.googleapis.com/css2?family=Fraunces:opsz,wght@9..144,600;9..144,700&family=Manrope:wght@400;500;700&family=JetBrains+Mono:wght@400;600&display=swap" rel="stylesheet">
<link rel="stylesheet" href="styles.css">
</head>
<body>
<header>
  <h1>Pattern Drafting <span class="tag">Prototype — formulas are being tuned</span></h1>
  <div class="unit-toggle" role="group" aria-label="Units">
    <button id="unit-in" class="active">inches</button>
    <button id="unit-cm">cm</button>
  </div>
</header>
<main>
  <aside id="controls">
    <section>
      <h2>Customer measurements</h2>
      <form id="measurements"></form>
    </section>
    <section>
      <h2>Ease &amp; fit <span class="hint">(tailor-tunable)</span></h2>
      <form id="params"></form>
    </section>
  </aside>
  <section id="stage">
    <nav id="block-tabs"></nav>
    <div id="canvas-wrap">
      <div id="canvas"></div>
      <div id="error" hidden></div>
    </div>
  </section>
  <aside id="steps-pane">
    <h2>Drafting steps</h2>
    <ol id="steps"></ol>
  </aside>
</main>
<script type="module" src="app.js"></script>
</body>
</html>
```

- [ ] **Step 2: Write `styles.css`**

```css
:root {
  --indigo: #2C3E7C; --sienna: #B85A30; --paper: #FAF6EC; --ink: #14110E;
  --line: #E4DCC8;
}
* { box-sizing: border-box; }
body { margin: 0; background: var(--paper); color: var(--ink); font-family: Manrope, system-ui, sans-serif; }
header { display: flex; align-items: center; justify-content: space-between; padding: 14px 22px; border-bottom: 1px solid var(--line); }
h1 { font-family: Fraunces, serif; font-size: 22px; margin: 0; }
h1 .tag { font-family: Manrope; font-size: 12px; font-weight: 500; color: var(--sienna); margin-left: 10px; }
h2 { font-family: Fraunces, serif; font-size: 15px; margin: 0 0 10px; }
h2 .hint { font-family: Manrope; font-size: 11px; font-weight: 400; color: #7a6f5d; }
.unit-toggle button { border: 1px solid var(--indigo); background: transparent; color: var(--indigo); padding: 5px 14px; cursor: pointer; font: inherit; }
.unit-toggle button.active { background: var(--indigo); color: var(--paper); }
main { display: grid; grid-template-columns: 270px 1fr 330px; gap: 0; height: calc(100vh - 61px); }
#controls, #steps-pane { overflow-y: auto; padding: 16px; }
#controls { border-right: 1px solid var(--line); }
#steps-pane { border-left: 1px solid var(--line); }
#controls section + section { margin-top: 22px; }
form label { display: flex; align-items: center; justify-content: space-between; gap: 8px; font-size: 12.5px; margin-bottom: 7px; }
form input[type="number"] { width: 74px; padding: 4px 6px; border: 1px solid var(--line); background: #fff; font-family: "JetBrains Mono", monospace; font-size: 12.5px; }
form input[type="range"] { width: 110px; accent-color: var(--sienna); }
.param-val { font-family: "JetBrains Mono", monospace; font-size: 11.5px; min-width: 52px; text-align: right; }
#stage { display: flex; flex-direction: column; min-width: 0; }
#block-tabs { display: flex; gap: 6px; padding: 12px 16px 0; }
#block-tabs button { border: 1px solid var(--line); border-bottom: none; background: #fff; padding: 8px 16px; cursor: pointer; font: inherit; font-size: 13px; }
#block-tabs button.active { background: var(--indigo); color: var(--paper); border-color: var(--indigo); }
#canvas-wrap { flex: 1; position: relative; margin: 0 16px 16px; border: 1px solid var(--line); background: #fff; overflow: hidden; }
#canvas, #canvas svg { width: 100%; height: 100%; }
#error { position: absolute; inset: 0; display: grid; place-content: center; background: rgba(250, 246, 236, 0.95); color: var(--sienna); font-size: 14px; padding: 24px; text-align: center; }
#steps li { margin-bottom: 12px; font-size: 13px; line-height: 1.5; cursor: pointer; padding: 8px 10px; border-left: 3px solid transparent; }
#steps li:hover { background: #fff; }
#steps li.active { border-left-color: var(--sienna); background: #fff; }
#steps .formula { font-family: "JetBrains Mono", monospace; font-size: 12px; color: var(--indigo); white-space: nowrap; overflow-x: auto; display: block; margin-top: 3px; }
```

- [ ] **Step 3: Write `app.js`**

```js
// preview/pattern-drafting/app.js
import { draftAll, BLOCKS } from './blocks/catalog.mjs';
import { renderSvg } from './render/svg.mjs';
import { MissingMeasurementError } from './core/engine.mjs';

// Field keys match BodyProfileTemplate.kt. Sample profile ≈ a UK-12 body, in cm.
const FIELDS = [
  { key: 'bust_circumference', label: 'Bust', sampleCm: 92 },
  { key: 'waist', label: 'Waist', sampleCm: 74 },
  { key: 'hip_circumference', label: 'Hip', sampleCm: 96 },
  { key: 'neck_circumference', label: 'Neck', sampleCm: 37 },
  { key: 'shoulder_width', label: 'Shoulder width', sampleCm: 38 },
  { key: 'nape_to_waist', label: 'Nape to waist', sampleCm: 41 },
  { key: 'bust_point', label: 'Shoulder to bust point', sampleCm: 26 },
  { key: 'bust_span', label: 'Bust span', sampleCm: 18 },
  { key: 'arm_length', label: 'Arm length', sampleCm: 58 },
  { key: 'wrist_circumference', label: 'Wrist', sampleCm: 16 },
];
const FIELD_LABELS = Object.fromEntries(FIELDS.map((f) => [f.key, f.label]));
const TAB_LABELS = { bodice_female: 'Bodice', sleeve_female: 'Sleeve', skirt_female: 'Skirt' };

const state = {
  unit: 'in', // display unit; the store below is always cm
  measurementsCm: Object.fromEntries(FIELDS.map((f) => [f.key, f.sampleCm])),
  paramsCm: Object.fromEntries(Object.values(BLOCKS).flatMap((b) => b.params.map((p) => [p.key, p.defaultCm]))),
  activeBlock: 'bodice_female',
  activeStep: null,
};

const toDisplay = (cmVal) => (state.unit === 'in' ? cmVal / 2.54 : cmVal);
const fromDisplay = (v) => (state.unit === 'in' ? v * 2.54 : v);
const fmt = (cmVal) => `${toDisplay(cmVal).toFixed(1)}${state.unit}`;

function tokensToString(tokens) {
  return tokens.map((t) => {
    if (t.t === 'measure') return FIELD_LABELS[t.fieldKey] ?? t.fieldKey;
    if (t.t === 'param') return 'ease';
    if (t.t === 'output') return t.outputKey.replaceAll('_', ' ');
    if (t.t === 'number') return fmt(t.valueCm);
    if (t.t === 'lparen') return '(';
    if (t.t === 'rparen') return ')';
    return ` ${t.symbol} `;
  }).join('');
}

function renderMeasurementForm() {
  const form = document.getElementById('measurements');
  form.innerHTML = FIELDS.map((f) => `
    <label>${f.label}
      <input type="number" step="0.1" data-key="${f.key}" value="${toDisplay(state.measurementsCm[f.key]).toFixed(1)}">
    </label>`).join('');
  form.querySelectorAll('input').forEach((el) => el.addEventListener('input', () => {
    state.measurementsCm[el.dataset.key] = fromDisplay(parseFloat(el.value));
    redraw();
  }));
}

function renderParamsForm() {
  const block = BLOCKS[state.activeBlock];
  const ids = [state.activeBlock, ...block.dependsOn];
  const params = ids.flatMap((id) => BLOCKS[id].params);
  const form = document.getElementById('params');
  form.innerHTML = params.map((pd) => `
    <label>${pd.label}
      <input type="range" data-key="${pd.key}" min="${pd.minCm}" max="${pd.maxCm}" step="0.1" value="${state.paramsCm[pd.key]}">
      <span class="param-val" data-val="${pd.key}">${fmt(state.paramsCm[pd.key])}</span>
    </label>`).join('');
  form.querySelectorAll('input').forEach((el) => el.addEventListener('input', () => {
    state.paramsCm[el.dataset.key] = parseFloat(el.value); // sliders operate in cm
    form.querySelector(`[data-val="${el.dataset.key}"]`).textContent = fmt(state.paramsCm[el.dataset.key]);
    redraw();
  }));
}

function renderTabs() {
  const nav = document.getElementById('block-tabs');
  nav.innerHTML = Object.keys(TAB_LABELS).map((id) =>
    `<button data-block="${id}" class="${id === state.activeBlock ? 'active' : ''}">${TAB_LABELS[id]}</button>`).join('');
  nav.querySelectorAll('button').forEach((el) => el.addEventListener('click', () => {
    state.activeBlock = el.dataset.block;
    state.activeStep = null;
    renderTabs();
    renderParamsForm();
    redraw();
  }));
}

function redraw() {
  const canvas = document.getElementById('canvas');
  const errBox = document.getElementById('error');
  const stepsOl = document.getElementById('steps');
  try {
    const drafts = draftAll([state.activeBlock], {
      measurementsCm: state.measurementsCm, paramsCm: state.paramsCm, outputsCm: {},
    });
    const draft = drafts[state.activeBlock];
    const highlight = draft.steps.find((s) => s.id === state.activeStep)?.pointIds ?? [];
    canvas.innerHTML = renderSvg(draft, { highlightPointIds: highlight });
    errBox.hidden = true;

    stepsOl.innerHTML = draft.steps.map((s) => {
      const text = s.template.replace(/\{(\d+)\}/g, (_, i) => `<b>${fmt(s.values[i].valueCm)}</b>`);
      const formulas = s.values.map((v) => `${tokensToString(v.tokens)} = ${fmt(v.valueCm)}`).join(' · ');
      return `<li data-step="${s.id}" class="${s.id === state.activeStep ? 'active' : ''}">${text}${formulas ? `<span class="formula">${formulas}</span>` : ''}</li>`;
    }).join('');
    stepsOl.querySelectorAll('li').forEach((el) => el.addEventListener('click', () => {
      state.activeStep = state.activeStep === el.dataset.step ? null : el.dataset.step;
      redraw();
    }));
  } catch (e) {
    if (e instanceof MissingMeasurementError) {
      errBox.textContent = `Missing measurement: ${FIELD_LABELS[e.fieldKey] ?? e.fieldKey}. Enter it on the left to draft this block.`;
      errBox.hidden = false;
    } else {
      errBox.textContent = `Drafting error: ${e.message}`;
      errBox.hidden = false;
    }
  }
}

document.getElementById('unit-in').addEventListener('click', () => setUnit('in'));
document.getElementById('unit-cm').addEventListener('click', () => setUnit('cm'));
function setUnit(u) {
  state.unit = u;
  document.getElementById('unit-in').classList.toggle('active', u === 'in');
  document.getElementById('unit-cm').classList.toggle('active', u === 'cm');
  renderMeasurementForm();
  renderParamsForm();
  redraw();
}

renderMeasurementForm();
renderParamsForm();
renderTabs();
redraw();
```

- [ ] **Step 4: Verify manually in the browser**

Run: `python3 -m http.server 8788` (repo root), open `http://localhost:8788/preview/pattern-drafting/`
Expected: bodice draws on load with grid + labeled points; steps show computed inches; clicking a step highlights its points in sienna; tabs switch blocks (sleeve shows bodice-dependent crown); sliders reshape the draft live; unit toggle converts all displayed numbers; blanking the hip field and opening Skirt shows the missing-measurement message.

- [ ] **Step 5: Commit**

```bash
git add preview/pattern-drafting
git commit -m "feat(pattern-preview): interactive drafting page (form, tabs, steps, sliders)"
```

---

### Task 9: Golden fixtures export

**Files:**
- Create: `preview/pattern-drafting/goldens.mjs`
- Create (generated): `preview/pattern-drafting/goldens/goldens.json`

**Interfaces:**
- Consumes: `draftAll`, `BLOCKS` (Task 6).
- Produces: `goldens.json` — `{profiles: [{name, measurementsCm}], results: [{profile, blockId, points: {id: [x, y]}, outputs}]}` with values rounded to 6 decimals. The Kotlin-port plan's fixture generator reads this file.

- [ ] **Step 1: Write the script**

```js
// preview/pattern-drafting/goldens.mjs — run: node preview/pattern-drafting/goldens.mjs
import { writeFileSync, mkdirSync } from 'node:fs';
import { draftAll, BLOCKS } from './blocks/catalog.mjs';

const PROFILES = [
  { name: 'petite', measurementsCm: { bust_circumference: 82, waist: 64, hip_circumference: 88, neck_circumference: 34, shoulder_width: 35, nape_to_waist: 38, bust_point: 23, bust_span: 16, arm_length: 54, wrist_circumference: 14.5 } },
  { name: 'average', measurementsCm: { bust_circumference: 92, waist: 74, hip_circumference: 96, neck_circumference: 37, shoulder_width: 38, nape_to_waist: 41, bust_point: 26, bust_span: 18, arm_length: 58, wrist_circumference: 16 } },
  { name: 'plus', measurementsCm: { bust_circumference: 112, waist: 96, hip_circumference: 118, neck_circumference: 41, shoulder_width: 41, nape_to_waist: 43, bust_point: 30, bust_span: 22, arm_length: 60, wrist_circumference: 18 } },
  { name: 'tall_slim', measurementsCm: { bust_circumference: 86, waist: 66, hip_circumference: 92, neck_circumference: 35, shoulder_width: 39, nape_to_waist: 45, bust_point: 28, bust_span: 17, arm_length: 63, wrist_circumference: 15 } },
];

const paramsCm = Object.fromEntries(Object.values(BLOCKS).flatMap((b) => b.params.map((p) => [p.key, p.defaultCm])));
const r6 = (v) => Math.round(v * 1e6) / 1e6;

const results = [];
for (const profile of PROFILES) {
  const drafts = draftAll(Object.keys(BLOCKS), { measurementsCm: profile.measurementsCm, paramsCm, outputsCm: {} });
  for (const [blockId, d] of Object.entries(drafts)) {
    results.push({
      profile: profile.name,
      blockId,
      points: Object.fromEntries(Object.entries(d.points).map(([id, p]) => [id, [r6(p.x), r6(p.y)]])),
      outputs: Object.fromEntries(Object.entries(d.outputs).map(([k, v]) => [k, r6(v)])),
    });
  }
}

mkdirSync(new URL('./goldens/', import.meta.url), { recursive: true });
writeFileSync(new URL('./goldens/goldens.json', import.meta.url), JSON.stringify({ profiles: PROFILES, paramsCm, results }, null, 2));
console.log(`Wrote ${results.length} golden drafts for ${PROFILES.length} profiles.`);
```

- [ ] **Step 2: Run it and sanity-check**

Run: `node preview/pattern-drafting/goldens.mjs && node -e "const g=require('./preview/pattern-drafting/goldens/goldens.json'); console.log(g.results.length, 'results;', Object.keys(g.results[0].points).length, 'points in first')"`
Expected: `Wrote 12 golden drafts for 4 profiles.` then `12 results; <n> points in first` with no NaN anywhere (spot-check the file).

- [ ] **Step 3: Run full test suite once more**

Run: `node --test preview/pattern-drafting/test/`
Expected: PASS (27 tests)

- [ ] **Step 4: Commit**

```bash
git add preview/pattern-drafting
git commit -m "feat(pattern-preview): golden fixture export for the Kotlin port"
```

---

### Task 10: Design review handoff (main session — not a subagent task)

**Files:** none (uses browser + Figma MCP).

- [ ] **Step 1:** Open the prototype in the browser with Daniel; walk through each block, tune param defaults live where the shapes look off; commit any default changes (`git commit -m "feat(pattern-preview): tune initial ease defaults"`) and re-run `node preview/pattern-drafting/goldens.mjs` + commit regenerated goldens.
- [ ] **Step 2:** Push the prototype screens to Figma for fashion-designer feedback: load the `figma:figma-generate-design` skill and create frames in a new Figma file ("StitchPad — Pattern Drafting Concept") for: block picker, draft view (drawing + steps), tweak sheet — using the prototype's layout and Adire Atelier tokens as the reference.
- [ ] **Step 3:** Open a draft PR (`gh pr create --draft`) titled "feat: pattern drafting prototype (Phase 0)" with QA smoke steps: serve, open page, verify the Task 8 Step 4 checklist; note that formulas are pending fit validation.
- [ ] **Step 4:** Gate: Daniel validates fit by drafting one bodice on paper from the app's steps for a real body (or has a tester do it) and collects designer feedback from Figma. Formula corrections loop through Tasks 4–6 defaults + goldens regeneration. Only after sign-off do we write the Phase 1 plan (Kotlin port), seeding block definitions from these validated `.mjs` files and goldens.

---

## Verification (end-to-end)

1. `node --test preview/pattern-drafting/test/` — 27 tests pass.
2. `python3 -m http.server 8788` → `http://localhost:8788/preview/pattern-drafting/` — all three blocks render, steps show computed numbers in both units, sliders live-update, missing-measurement error is friendly.
3. `node preview/pattern-drafting/goldens.mjs` — regenerates cleanly, no NaN.
4. Physical check (Task 10): a paper draft from the app's steps produces a wearable-shaped bodice for a real measurement set.

## Self-Review Notes

- Spec coverage for Phase 0: JS engine twin ✓, three blocks ✓, cross-block dependency ✓, steps with computed numbers ✓, tunable params ✓, unit handling ✓, goldens ✓, Figma push ✓, designer/fit validation gate ✓. Kotlin-side spec items (flag, entitlements, Firestore, Compose, exports) are Phase 1 by design.
- Formula values are declared initial values, not placeholders — tuning them is Task 10's explicit purpose.
- Type consistency: token shapes (Task 1) match `tokensToString` (Task 8); block shape (Task 3 doc) matches Tasks 4–6; `draftAll` signature (Task 6) matches Tasks 7–9 usage; `goldens.json` shape documented for the Phase 1 plan.

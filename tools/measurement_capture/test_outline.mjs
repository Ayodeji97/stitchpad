import test from "node:test";
import assert from "node:assert/strict";
import { suggestOutline, OUTLINE_VERSION } from "./outline.mjs";
import { viewDimensions } from "./engine.mjs";
export function fixture(view = "front") {
    const width = 200,
        height = 400,
        data = new Float32Array(width * height);
    const rect = (l, t, r, b, value = 1) => {
        for (let y = Math.floor(t * height); y < Math.floor(b * height); y++)
            for (let x = Math.floor(l * width); x < Math.floor(r * width); x++)
                data[y * width + x] = value;
    };
    rect(0.43, 0.05, 0.57, 0.2);
    rect(0.35, 0.18, 0.65, 0.6);
    rect(0.4, 0.59, 0.47, 0.95);
    rect(0.53, 0.59, 0.6, 0.95);
    const p = Array.from({ length: 33 }, () => ({
        x: 0.5,
        y: 0.5,
        z: 0,
        visibility: 0.99,
    }));
    const put = (i, x, y) => Object.assign(p[i], { x, y });
    put(0, 0.5, 0.1);
    put(11, view === "front" ? 0.38 : 0.49, 0.25);
    put(12, view === "front" ? 0.62 : 0.51, 0.25);
    put(23, 0.45, 0.5);
    put(24, 0.55, 0.5);
    put(25, 0.44, 0.7);
    put(26, 0.56, 0.7);
    for (const [i, x, y] of [
        [27, 0.44, 0.88],
        [28, 0.56, 0.88],
        [29, 0.44, 0.92],
        [30, 0.56, 0.92],
        [31, 0.44, 0.94],
        [32, 0.56, 0.94],
        [13, 0.2, 0.34],
        [14, 0.8, 0.34],
        [15, 0.18, 0.45],
        [16, 0.82, 0.45],
    ])
        put(i, x, y);
    return { landmarks: [p], mask: { width, height, data }, rect };
}
const run = (f, view = "front") => suggestOutline(f, view);
const fails = (f, code, view = "front") =>
    assert.throws(
        () => run(f, view),
        (e) => e.code === code,
    );
for (const view of ["front", "side"])
    test(`${view}: extracts connected torso without treating suggestions as truth`, () => {
        const f = fixture(view),
            answer = run(f, view);
        assert.equal(Object.keys(answer.points).length, 8);
        assert.equal(answer.provenance.method, OUTLINE_VERSION);
        assert.equal(answer.provenance.requiresAnatomicalReview, true);
        assert.equal(answer.provenance.confidence, null);
        assert.ok(
            answer.points.chestLeft.x < 0.36 &&
                answer.points.chestRight.x > 0.64,
        );
        const dimensions = viewDimensions(
            {
                points: answer.points,
                width: 800,
                height: 1400,
                qualityConfirmed: true,
            },
            1800,
        );
        assert.ok(dimensions.chest.diameterMm > 0);
    });
test("zero and multiple people are rejected", () => {
    const f = fixture();
    f.landmarks = [];
    fails(f, "personCount");
    f.landmarks = [fixture().landmarks[0], fixture().landmarks[0]];
    fails(f, "personCount");
});
test("SDK visibility works without nonexistent presence field", () => {
    assert.doesNotThrow(() => run(fixture()));
    const f = fixture();
    f.landmarks[0][11].presence = 0.1;
    fails(f, "poseUncertain");
});
test("missing, nonfinite, occluded and cropped landmarks fail closed", () => {
    for (const key of ["x", "visibility"]) {
        const f = fixture();
        f.landmarks[0][11][key] = NaN;
        fails(f, "poseUncertain");
    }
    const f = fixture();
    f.landmarks[0][27].visibility = 0.1;
    fails(f, "poseUncertain");
});
test("upright pose and orientation are checked", () => {
    const f = fixture();
    f.landmarks[0][11].y = 0.1;
    fails(f, "poseUncertain");
    fails(fixture("side"), "wrongView");
    fails(fixture(), "wrongView", "side");
});
test("blank, malformed and NaN masks are rejected", () => {
    const f = fixture();
    f.mask.data.fill(0);
    fails(f, "maskUncertain");
    const g = fixture();
    g.mask.data[0] = NaN;
    fails(g, "maskUncertain");
    g.mask.data = new Float32Array(2);
    fails(g, "maskUncertain");
});
test("detached noise does not move head or floor", () => {
    const f = fixture(),
        before = run(f);
    f.rect(0.02, 0.025, 0.03, 0.04);
    f.rect(0.9, 0.96, 0.92, 0.97);
    assert.deepEqual(run(f), before);
});
test("connected cropped body is rejected", () => {
    const f = fixture();
    f.rect(0.4, 0, 0.6, 0.2);
    fails(f, "framing");
});
test("soft unstable contour is rejected instead of widening body", () => {
    const f = fixture();
    f.rect(0.3, 0.2, 0.35, 0.6, 0.6);
    fails(f, "maskUncertain");
});
test("arms overlapping the torso row are not counted as body width", () => {
    const f = fixture();
    f.landmarks[0][13].x = 0.35;
    f.landmarks[0][13].y = 0.325;
    fails(f, "armOcclusion");
});
test("source mask and landmarks are never mutated", () => {
    const f = fixture(),
        before = structuredClone({ landmarks: f.landmarks, mask: f.mask });
    run(f);
    assert.deepEqual({ landmarks: f.landmarks, mask: f.mask }, before);
});

test("orientation uses pixel aspect ratio rather than normalized image width", () => {
    const f = fixture(),
        original = f.mask;
    const width = original.width * 4;
    const data = new Float32Array(width * original.height);
    const offset = (width - original.width) / 2;
    for (let y = 0; y < original.height; y++)
        for (let x = 0; x < original.width; x++)
            data[y * width + x + offset] =
                original.data[y * original.width + x];
    f.mask = { width, height: original.height, data };
    for (const p of f.landmarks[0])
        p.x = (p.x * original.width + offset) / width;
    assert.doesNotThrow(() => run(f));
});

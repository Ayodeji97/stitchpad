import test from "node:test";
import assert from "node:assert/strict";
import { estimateCapture, ellipsePerimeter } from "./engine.mjs";
function view(width = 1000, height = 2000) {
    return {
        width,
        height,
        qualityConfirmed: true,
        points: {
            top: { x: 0.5, y: 0.05 },
            floor: { x: 0.5, y: 0.95 },
            chestLeft: { x: 0.3, y: 0.3 },
            chestRight: { x: 0.7, y: 0.3 },
            waistLeft: { x: 0.35, y: 0.42 },
            waistRight: { x: 0.65, y: 0.42 },
            hipsLeft: { x: 0.28, y: 0.54 },
            hipsRight: { x: 0.72, y: 0.54 },
        },
    };
}
const capture = () => ({
    consent: true,
    journey: "existing_customer",
    heightMm: 1800,
    front: view(),
    side: view(),
});
const fails = (c, code) =>
    assert.throws(
        () => estimateCapture(c),
        (e) => e.code === code,
    );
test("analytic circle and eccentric ellipse geometry", () => {
    assert.ok(Math.abs(ellipsePerimeter(300, 300) - Math.PI * 300) < 1e-9);
    assert.ok(Math.abs(ellipsePerimeter(400, 200) - 968.8448) < 0.01);
});
for (const journey of ["existing_customer", "designer_discovery"])
    test(`${journey}: calibrated pixels produce provisional mm only`, () => {
        const c = capture();
        c.journey = journey;
        const result = estimateCapture(c);
        assert.equal(result.estimates.chest.valueMm, Math.round(Math.PI * 400));
        assert.equal(result.canSaveToCustomer, false);
        assert.equal(result.validatedForTailoring, false);
        assert.equal(result.confidence, null);
    });
test("independent image resizing does not change physical dimensions", () => {
    const c = capture(),
        before = estimateCapture(c);
    c.side.width *= 2;
    c.side.height *= 2;
    assert.deepEqual(estimateCapture(c), before);
});
test("known height scales output, never guessed from photos", () => {
    const c = capture();
    c.heightMm = 900;
    assert.equal(
        estimateCapture(c).estimates.chest.valueMm,
        Math.round(Math.PI * 200),
    );
    c.heightMm = NaN;
    fails(c, "height");
});
test("requires explicit consent and supported journey", () => {
    const c = capture();
    c.consent = false;
    fails(c, "consent");
    c.consent = true;
    c.journey = "unknown";
    fails(c, "journey");
});
test("rejects low-resolution or unconfirmed captures", () => {
    const c = capture();
    c.front.width = 599;
    fails(c, "resolution");
    c.front = view();
    c.front.qualityConfirmed = false;
    fails(c, "captureChecks");
});
test("rejects cropped, off-axis and incomplete outlines", () => {
    const c = capture();
    delete c.front.points.waistLeft;
    fails(c, "framing");
    c.front = view();
    c.front.points.floor.x = 0.7;
    fails(c, "framing");
    c.front = view();
    c.front.points.top.y = 0;
    fails(c, "framing");
});
test("rejects swapped, tilted or anatomically unordered landmarks", () => {
    const c = capture();
    c.front.points.chestRight.x = 0.2;
    fails(c, "landmarks");
    c.front = view();
    c.front.points.waistLeft.y = 0.5;
    fails(c, "landmarks");
    c.front = view();
    c.front.points.hipsLeft.y = c.front.points.hipsRight.y = 0.4;
    fails(c, "landmarks");
});
test("rejects unmatched anatomical levels across views", () => {
    const c = capture();
    c.side.points.chestLeft.y = c.side.points.chestRight.y = 0.35;
    fails(c, "viewMismatch");
});
test("marks any synthetic source and never mutates input", () => {
    const c = capture();
    c.front.synthetic = true;
    const before = structuredClone(c);
    assert.equal(estimateCapture(c).synthetic, true);
    assert.deepEqual(c, before);
});
test("does not convert invalid numeric strings or infinite points", () => {
    const c = capture();
    c.heightMm = "1800";
    fails(c, "height");
    c.heightMm = 1800;
    c.side.points.chestLeft.x = Infinity;
    fails(c, "framing");
});

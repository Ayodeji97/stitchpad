/** Owned, unvalidated two-view ellipse baseline. No learned model or customer writes. */
export const ENGINE_VERSION = "two-view-ellipse-v0.2";
export const FIELDS = ["chest", "waist", "hips"];
export const POINTS = [
    "top",
    "floor",
    "chestLeft",
    "chestRight",
    "waistLeft",
    "waistRight",
    "hipsLeft",
    "hipsRight",
];
export class CaptureError extends Error {
    constructor(code) {
        super(code);
        this.code = code;
    }
}
const fail = (code) => {
    throw new CaptureError(code);
};
const finite = (n) => typeof n === "number" && Number.isFinite(n);
export function viewDimensions(view, heightMm) {
    if (
        !view ||
        !view.points ||
        !finite(view.width) ||
        !finite(view.height) ||
        Math.min(view.width, view.height) < 600
    )
        fail("resolution");
    if (view.qualityConfirmed !== true) fail("captureChecks");
    for (const name of POINTS) {
        const p = view.points[name];
        if (
            !p ||
            !finite(p.x) ||
            !finite(p.y) ||
            p.x < 0.02 ||
            p.x > 0.98 ||
            p.y < 0.02 ||
            p.y > 0.98
        )
            fail("framing");
    }
    const { top, floor } = view.points;
    const span = floor.y - top.y;
    if (span < 0.6 || Math.abs(top.x - floor.x) > 0.12) fail("framing");
    let previous = top.y;
    const dimensions = {};
    for (const field of FIELDS) {
        const left = view.points[`${field}Left`],
            right = view.points[`${field}Right`];
        if (left.x >= right.x || Math.abs(left.y - right.y) > 0.015)
            fail("landmarks");
        const y = (left.y + right.y) / 2;
        if (y <= previous + 0.025 || y >= floor.y) fail("landmarks");
        previous = y;
        const mm =
            ((right.x - left.x) * view.width * heightMm) / (span * view.height);
        // These broad limits reject implausible annotation, not body-size eligibility.
        if (mm < 30 || mm > heightMm * 0.65) fail("landmarks");
        dimensions[field] = { diameterMm: mm, level: (y - top.y) / span };
    }
    return dimensions;
}
export function ellipsePerimeter(diameterA, diameterB) {
    if (![diameterA, diameterB].every((n) => finite(n) && n > 0))
        fail("landmarks");
    const a = diameterA / 2,
        b = diameterB / 2;
    const h = ((a - b) / (a + b)) ** 2;
    return Math.PI * (a + b) * (1 + (3 * h) / (10 + Math.sqrt(4 - 3 * h)));
}
export function estimateCapture(capture) {
    if (!capture || capture.consent !== true) fail("consent");
    if (!["existing_customer", "designer_discovery"].includes(capture.journey))
        fail("journey");
    if (
        !finite(capture.heightMm) ||
        capture.heightMm < 800 ||
        capture.heightMm > 2500
    )
        fail("height");
    const front = viewDimensions(capture.front, capture.heightMm);
    const side = viewDimensions(capture.side, capture.heightMm);
    const sources = Object.fromEntries(
        ["front", "side"].map((view) => [
            view,
            capture[view].provenance
                ? { ...capture[view].provenance }
                : { method: "manual" },
        ]),
    );
    const pipelineVersion =
        ENGINE_VERSION +
        ["front", "side"]
            .map((view) => {
                const source = sources[view];
                return `|${view}=${source.method}:${source.sdk ?? "none"}:${source.modelSha256 ?? "none"}:${source.edited ? "edited" : "original"}`;
            })
            .join("");
    const estimates = {};
    for (const field of FIELDS) {
        if (Math.abs(front[field].level - side[field].level) > 0.04)
            fail("viewMismatch");
        estimates[field] = {
            valueMm: Math.round(
                ellipsePerimeter(
                    front[field].diameterMm,
                    side[field].diameterMm,
                ),
            ),
            frontDiameterMm: front[field].diameterMm,
            sideDiameterMm: side[field].diameterMm,
            manuallyVerified: false,
        };
    }
    return {
        schemaVersion: 1,
        engineVersion: ENGINE_VERSION,
        pipelineVersion,
        journey: capture.journey,
        synthetic:
            capture.front.synthetic === true || capture.side.synthetic === true,
        status: "experimental_requires_tape_verification",
        validatedForTailoring: false,
        canSaveToCustomer: false,
        confidence: null,
        outlineSources: sources,
        estimates,
        limitations: [
            "reviewed_outline",
            "ellipse_assumption",
            "uncalibrated_camera",
            "clothing_bias",
            "no_accuracy_validation",
        ],
    };
}

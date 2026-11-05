/** Candidate contours, never anatomical truth or accuracy confidence. */
import { CaptureError } from "./engine.mjs";
export const OUTLINE_VERSION = "pose-mask-candidates-v1";
const reject = (code) => {
    throw new CaptureError(code);
};
const valid = (value) => typeof value === "number" && Number.isFinite(value);
const median = (a) => a.sort((x, y) => x - y)[Math.floor(a.length / 2)];
export function suggestOutline({ landmarks, mask }, view) {
    if (!["front", "side"].includes(view)) reject("viewMismatch");
    if (!Array.isArray(landmarks) || landmarks.length !== 1)
        reject("personCount");
    const pose = landmarks[0];
    if (!Array.isArray(pose) || pose.length !== 33) reject("poseUncertain");
    const w = mask?.width,
        h = mask?.height,
        data = mask?.data;
    if (
        !Number.isInteger(w) ||
        !Number.isInteger(h) ||
        w < 32 ||
        h < 32 ||
        w * h > 4_000_000 ||
        data?.length !== w * h
    )
        reject("maskUncertain");
    for (const value of data)
        if (!valid(value) || value < 0 || value > 1) reject("maskUncertain");
    const visible = (i) => {
        const p = pose[i];
        return (
            p &&
            [p.x, p.y, p.visibility].every(valid) &&
            p.x > 0.02 &&
            p.x < 0.98 &&
            p.y > 0.02 &&
            p.y < 0.98 &&
            p.visibility >= 0.65 &&
            (p.presence === undefined ||
                (valid(p.presence) && p.presence >= 0.65))
        );
    };
    for (const pair of [
        [11, 12],
        [23, 24],
        [25, 26],
        [27, 28],
        [29, 30],
        [31, 32],
    ]) {
        if (view === "front" && !pair.every(visible)) reject("poseUncertain");
        if (view === "side" && !pair.some(visible)) reject("poseUncertain");
    }
    if (!visible(0)) reject("poseUncertain");
    const middle = (a, b) => {
        const points = [a, b].filter(visible).map((i) => pose[i]);
        return {
            x: points.reduce((s, p) => s + p.x, 0) / points.length,
            y: points.reduce((s, p) => s + p.y, 0) / points.length,
        };
    };
    const shoulder = middle(11, 12),
        hip = middle(23, 24),
        ankle = middle(27, 28);
    const torso = hip.y - shoulder.y;
    if (
        torso < 0.14 ||
        Math.abs(shoulder.x - hip.x) > 0.08 ||
        hip.y >= ankle.y ||
        Math.abs(hip.x - ankle.x) > 0.12
    )
        reject("poseUncertain");
    if (
        view === "front" &&
        (Math.abs(pose[11].y - pose[12].y) > 0.035 ||
            Math.abs(pose[23].y - pose[24].y) > 0.035)
    )
        reject("poseUncertain");
    // 2D orientation heuristic only; depth estimates are not metric tape measurements.
    const shoulderWidth = Math.abs(pose[11].x - pose[12].x);
    const shoulderTorsoRatio = (shoulderWidth * w) / (torso * h);
    if (view === "front" && shoulderTorsoRatio < 0.35) reject("wrongView");
    if (view === "side" && shoulderTorsoRatio > 0.3) reject("wrongView");
    // Flood-fill the foreground containing the torso, excluding detached objects/noise.
    const startX = Math.round(((shoulder.x + hip.x) / 2) * (w - 1));
    const startY = Math.round(((shoulder.y + hip.y) / 2) * (h - 1));
    const seed = startY * w + startX;
    if (data[seed] < 0.65) reject("maskUncertain");
    const component = new Uint8Array(w * h),
        queue = new Int32Array(w * h);
    let head = 0,
        tail = 1;
    queue[0] = seed;
    component[seed] = 1;
    while (head < tail) {
        const i = queue[head++],
            x = i % w,
            y = Math.floor(i / w);
        const neighbours = [];
        if (x > 0) neighbours.push(i - 1);
        if (x < w - 1) neighbours.push(i + 1);
        if (y > 0) neighbours.push(i - w);
        if (y < h - 1) neighbours.push(i + w);
        for (const n of neighbours)
            if (!component[n] && data[n] >= 0.5) {
                component[n] = 1;
                queue[tail++] = n;
            }
    }
    if (tail < w * h * 0.025) reject("maskUncertain");
    let minY = h,
        maxY = 0;
    for (let i = 0; i < tail; i++) {
        const index = queue[i],
            x = index % w,
            y = Math.floor(index / w);
        if (x <= 1 || x >= w - 2 || y <= 1 || y >= h - 2) reject("framing");
        if (
            Math.abs(x / (w - 1) - pose[0].x) < 0.07 &&
            y / (h - 1) < shoulder.y
        )
            minY = Math.min(minY, y);
        maxY = Math.max(maxY, y);
    }
    const top = minY / (h - 1),
        floor = maxY / (h - 1),
        span = floor - top;
    if (
        !valid(top) ||
        span < 0.6 ||
        span > 0.96 ||
        top >= pose[0].y ||
        floor < ankle.y
    )
        reject("framing");
    // Levels are approximate candidates. A human must confirm anatomical definitions.
    const levels = {
        chest: shoulder.y + torso * 0.3,
        waist: shoulder.y + torso * 0.68,
        hips: hip.y + torso * 0.06,
    };
    const points = { top: { x: hip.x, y: top }, floor: { x: hip.x, y: floor } };
    let maximumThresholdShift = 0;
    function edge(y, center, threshold) {
        const row = Math.round(y * (h - 1)),
            cx = Math.round(center * (w - 1));
        if (!component[row * w + cx] || data[row * w + cx] < threshold)
            reject("maskUncertain");
        let l = cx,
            r = cx;
        while (
            l > 0 &&
            component[row * w + l - 1] &&
            data[row * w + l - 1] >= threshold
        )
            l--;
        while (
            r < w - 1 &&
            component[row * w + r + 1] &&
            data[row * w + r + 1] >= threshold
        )
            r++;
        if (l <= 1 || r >= w - 2 || r - l < 6) reject("maskUncertain");
        return [l / (w - 1), r / (w - 1)];
    }
    for (const [field, y] of Object.entries(levels)) {
        const fraction = (y - shoulder.y) / torso,
            center = shoulder.x + (hip.x - shoulder.x) * fraction;
        const rows = [-2, -1, 0, 1, 2].map((offset) =>
            edge(y + offset / (h - 1), center, 0.5),
        );
        const l = median(rows.map((p) => p[0])),
            r = median(rows.map((p) => p[1]));
        const high = edge(y, center, 0.7),
            low = edge(y, center, 0.5);
        const shift = Math.max(
            Math.abs(high[0] - low[0]),
            Math.abs(high[1] - low[1]),
        );
        maximumThresholdShift = Math.max(maximumThresholdShift, shift);
        if (shift / (r - l) > 0.06) reject("maskUncertain");
        // A hand/elbow overlapping a measurement line can enlarge the torso silhouette.
        for (const i of [13, 14, 15, 16])
            if (
                visible(i) &&
                Math.abs(pose[i].y - y) < 0.045 &&
                pose[i].x > l - 0.025 &&
                pose[i].x < r + 0.025
            )
                reject("armOcclusion");
        points[`${field}Left`] = { x: l, y };
        points[`${field}Right`] = { x: r, y };
    }
    return {
        points,
        provenance: {
            method: OUTLINE_VERSION,
            requiresAnatomicalReview: true,
            thresholdShiftFraction: maximumThresholdShift,
            confidence: null,
        },
    };
}

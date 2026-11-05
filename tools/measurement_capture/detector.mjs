/** Lazy, pinned local detector. No CDN calls or image transfer at runtime. */
import { suggestOutline } from "./outline.mjs";
import { CaptureError } from "./engine.mjs";
let pending = null,
    detector = null,
    generation = 0,
    modelDigest = null;
async function load() {
    if (detector) return detector;
    if (pending) return pending;
    const started = generation;
    pending = (async () => {
        const manifest = await fetch("./assets.lock.json").then((r) => {
            if (!r.ok) throw Error();
            return r.json();
        });
        const model = manifest.files.find(
            (x) => x.path === "pose_landmarker_full.task",
        );
        const bytes = await fetch("./assets/pose_landmarker_full.task").then(
            (r) => {
                if (!r.ok) throw Error();
                return r.arrayBuffer();
            },
        );
        const digest = Array.from(
            new Uint8Array(await crypto.subtle.digest("SHA-256", bytes)),
            (x) => x.toString(16).padStart(2, "0"),
        ).join("");
        if (digest !== model.sha256) throw Error("asset checksum");
        const { FilesetResolver, PoseLandmarker } =
            await import("./assets/vision_bundle.mjs");
        const files = await FilesetResolver.forVisionTasks(
            new URL("./assets/wasm", import.meta.url).href,
        );
        const value = await PoseLandmarker.createFromOptions(files, {
            baseOptions: {
                modelAssetBuffer: new Uint8Array(bytes),
                delegate: "CPU",
            },
            runningMode: "IMAGE",
            numPoses: 2,
            outputSegmentationMasks: true,
            minPoseDetectionConfidence: 0.65,
            minPosePresenceConfidence: 0.65,
        });
        if (started !== generation) {
            value.close();
            throw Error("cancelled");
        }
        detector = value;
        modelDigest = digest;
        return value;
    })().catch((error) => {
        if (started === generation) pending = null;
        throw new CaptureError("modelUnavailable");
    });
    return pending;
}
export async function detectOutline(image, view) {
    const model = await load();
    const started = performance.now();
    const result = model.detect(image);
    try {
        const mask = result.segmentationMasks?.[0];
        const answer = suggestOutline(
            {
                landmarks: result.landmarks,
                mask: mask
                    ? {
                          width: mask.width,
                          height: mask.height,
                          data: mask.getAsFloat32Array(),
                      }
                    : null,
            },
            view,
        );
        return {
            ...answer,
            provenance: {
                ...answer.provenance,
                sdk: "@mediapipe/tasks-vision@0.10.32",
                model: "pose_landmarker_full/float16/1",
                modelSha256: modelDigest,
                inferenceMs: performance.now() - started,
            },
        };
    } finally {
        result.close();
    }
}
export function closeDetector() {
    generation++;
    detector?.close();
    detector = null;
    pending = null;
}

import { COPY } from "./copy.mjs";
import { POINTS, FIELDS, estimateCapture, viewDimensions } from "./engine.mjs";
const $ = (id) => document.getElementById(id);
let lang = navigator.language.startsWith("fr") ? "fr" : "en";
let state = fresh();
function fresh() {
    return {
        step: 0,
        journey: "existing_customer",
        heightCm: "",
        consent: false,
        front: null,
        side: null,
        active: "top",
        tape: {},
        result: null,
        revision: 0,
    };
}
const t = (key) => COPY[lang][key] ?? key;
const text = (tag, content, className) => {
    const e = document.createElement(tag);
    e.textContent = content;
    if (className) e.className = className;
    return e;
};
function button(key, onClick, secondary = false) {
    const b = text("button", t(key), secondary ? "secondary" : "");
    b.type = "button";
    b.onclick = onClick;
    return b;
}
function error(key) {
    $("error").textContent = t(key);
    $("error").hidden = false;
    $("error").focus();
}
function clearError() {
    $("error").hidden = true;
}
function changed() {
    state.result = null;
    state.revision++;
    clearError();
}
function advance(step) {
    state.step = step;
    state.active = "top";
    render();
    $("heading").focus();
}
function render() {
    document.documentElement.lang = lang;
    $("language").value = lang;
    $("language-label").textContent = t("language");
    $("footer").textContent = t("footer");
    const root = $("app");
    root.replaceChildren();
    const steps = text("ol", "", "steps");
    t("steps").forEach((label, i) => {
        const li = text("li", label, state.step === i ? "current" : "");
        if (state.step === i) li.setAttribute("aria-current", "step");
        steps.append(li);
    });
    root.append(steps);
    const h = text(
        "h1",
        t(
            state.step === 0
                ? "title"
                : state.step === 1
                  ? "frontTitle"
                  : state.step === 2
                    ? "sideTitle"
                    : "resultTitle",
        ),
    );
    h.id = "heading";
    h.tabIndex = -1;
    root.append(h);
    const message = text("div", "", "error");
    message.id = "error";
    message.role = "alert";
    message.hidden = true;
    message.tabIndex = -1;
    root.append(message);
    if (state.step === 0) prepare(root);
    else if (state.step < 3) capture(root);
    else review(root);
    const actions = text("div", "", "actions");
    actions.append(button("reset", reset, true));
    root.append(actions);
}
function prepare(root) {
    root.append(text("p", t("intro")), text("p", t("warning"), "notice"));
    const choices = text("div", "", "choices");
    for (const [value, key] of [
        ["existing_customer", "existing"],
        ["designer_discovery", "discovery"],
    ]) {
        const label = text("label", "", "choice"),
            input = document.createElement("input");
        input.type = "radio";
        input.name = "journey";
        input.value = value;
        input.checked = state.journey === value;
        input.onchange = () => {
            state.journey = value;
            changed();
        };
        const copy = text("span", "");
        copy.append(text("strong", t(key)), text("small", t(`${key}Help`)));
        label.append(input, copy);
        choices.append(label);
    }
    root.append(choices);
    const label = text("label", t("heightLabel"), "field"),
        input = document.createElement("input");
    input.type = "number";
    input.min = 80;
    input.max = 250;
    input.step = 0.1;
    input.inputMode = "decimal";
    input.id = "height";
    input.value = state.heightCm;
    input.oninput = () => {
        state.heightCm = input.value;
        changed();
    };
    label.append(input);
    root.append(label);
    const consent = checkbox("consent", state.consent, (value) => {
        state.consent = value;
        changed();
    });
    root.append(consent);
    const actions = text("div", "", "actions");
    actions.append(
        button("start", () => {
            if (!state.consent) return error("consentError");
            const height = Number(state.heightCm);
            if (!Number.isFinite(height) || height < 80 || height > 250)
                return error("heightError");
            advance(1);
        }),
        button("demo", demo, true),
    );
    root.append(actions, definitions());
}
function checkbox(key, checked, update) {
    const label = text("label", "", "check"),
        input = document.createElement("input");
    input.type = "checkbox";
    input.checked = checked;
    input.onchange = () => update(input.checked);
    label.append(input, text("span", t(key)));
    return label;
}
function definitions() {
    const block = text("details", "");
    block.append(text("summary", t("defs")));
    const grid = text("div", "", "definition");
    for (const field of FIELDS) {
        const item = text("div", "");
        item.append(text("strong", t(field)), text("p", t(`${field}Def`)));
        grid.append(item);
    }
    block.append(grid);
    return block;
}
function currentKey() {
    return state.step === 1 ? "front" : "side";
}
function capture(root) {
    const key = currentKey(),
        view = state[key];
    root.append(text("p", t(`${key}Help`)), text("p", t("privacy"), "small"));
    const label = text("label", t("choose"), "file"),
        file = document.createElement("input");
    file.type = "file";
    file.accept = "image/jpeg,image/png,image/webp";
    file.setAttribute("capture", "environment");
    file.id = "photo";
    file.onchange = () => loadFile(file.files[0], key);
    label.append(file);
    root.append(label);
    if (view) {
        if (view.synthetic) root.append(text("p", t("synthetic"), "synthetic"));
        const work = text("div", "", "workspace"),
            imageArea = text("div", "", "image-area");
        const canvas = document.createElement("canvas");
        canvas.id = "photo-canvas";
        canvas.width = 600;
        canvas.height = Math.round((600 * view.height) / view.width);
        canvas.setAttribute("aria-label", t("annotate"));
        canvas.setAttribute("role", "img");
        canvas.onclick = (event) => {
            const rect = canvas.getBoundingClientRect(),
                point = {
                    x: (event.clientX - rect.left) / rect.width,
                    y: (event.clientY - rect.top) / rect.height,
                };
            setPoint(view, state.active, point);
            const index = POINTS.indexOf(state.active);
            state.active = POINTS[Math.min(index + 1, POINTS.length - 1)];
            render();
        };
        imageArea.append(canvas);
        const tools = text("section", "");
        tools.append(
            text("h2", t("annotate")),
            text("p", t("annotationHelp"), "small"),
        );
        const list = text("div", "", "point-list");
        for (const name of POINTS) {
            const row = text(
                    "div",
                    "",
                    `point ${state.active === name ? "active" : ""}`,
                ),
                select = text(
                    "button",
                    `${view.points[name] ? "✓ " : ""}${t(name)}`,
                );
            select.type = "button";
            select.setAttribute("aria-pressed", String(state.active === name));
            select.onclick = () => {
                state.active = name;
                render();
            };
            row.append(select);
            for (const axis of ["x", "y"]) {
                const n = document.createElement("input");
                n.type = "number";
                n.min = 2;
                n.max = 98;
                n.step = 0.1;
                n.inputMode = "decimal";
                n.setAttribute("aria-label", `${t(name)} · ${t(axis)}`);
                n.value = view.points[name]
                    ? (view.points[name][axis] * 100).toFixed(1)
                    : "";
                n.onchange = () => {
                    const value = n.valueAsNumber;
                    if (!Number.isFinite(value)) {
                        delete view.points[name];
                        changed();
                        draw();
                        return;
                    }
                    const p = view.points[name] ?? { x: 0.5, y: 0.5 };
                    setPoint(view, name, { ...p, [axis]: value / 100 });
                    draw();
                };
                row.append(n);
            }
            list.append(row);
        }
        tools.append(
            list,
            button(
                "undo",
                () => {
                    const last = view.history.pop();
                    if (last) {
                        if (last.before) view.points[last.name] = last.before;
                        else delete view.points[last.name];
                        state.active = last.name;
                        changed();
                        render();
                    }
                },
                true,
            ),
        );
        work.append(imageArea, tools);
        root.append(work);
        root.append(
            checkbox("checks", view.qualityConfirmed, (value) => {
                view.qualityConfirmed = value;
                changed();
            }),
        );
        queueMicrotask(draw);
    }
    root.append(definitions());
    const actions = text("div", "", "actions");
    actions.append(
        button("back", () => advance(state.step - 1), true),
        button("next", () => {
            if (!view || POINTS.some((p) => !view.points[p]))
                return error("missing");
            try {
                viewDimensions(view, Number(state.heightCm) * 10);
                if (state.step === 1) advance(2);
                else {
                    state.result = estimateCapture({
                        ...state,
                        heightMm: Number(state.heightCm) * 10,
                    });
                    advance(3);
                }
            } catch (e) {
                error(COPY[lang][e.code] ? e.code : "unknown");
            }
        }),
    );
    root.append(actions);
}
function setPoint(view, name, point) {
    view.history.push({
        name,
        before: view.points[name] ? { ...view.points[name] } : null,
    });
    view.points[name] = point;
    changed();
}
function draw() {
    const canvas = $("photo-canvas");
    if (!canvas) return;
    const view = state[currentKey()];
    const ctx = canvas.getContext("2d");
    ctx.drawImage(view.image, 0, 0, canvas.width, canvas.height);
    for (const name of POINTS) {
        const p = view.points[name];
        if (!p) continue;
        const x = p.x * canvas.width,
            y = p.y * canvas.height;
        ctx.strokeStyle = name === state.active ? "#e8a800" : "#fff";
        ctx.fillStyle = "#2c3e7c";
        ctx.lineWidth = 3;
        ctx.beginPath();
        ctx.arc(x, y, 7, 0, 2 * Math.PI);
        ctx.fill();
        ctx.stroke();
        if (name.endsWith("Left")) {
            const right = view.points[name.replace("Left", "Right")];
            if (right) {
                ctx.beginPath();
                ctx.moveTo(x, y);
                ctx.lineTo(right.x * canvas.width, right.y * canvas.height);
                ctx.stroke();
            }
        }
    }
}
async function loadFile(file, key) {
    if (!file) return;
    const revision = ++state.revision;
    state.result = null;
    // Invalidate the previous capture immediately: a failed replacement must not leave it usable.
    if (state[key]) {
        state[key].image.width = 0;
        state[key] = null;
    }
    render();
    if (
        !["image/jpeg", "image/png", "image/webp"].includes(file.type) ||
        file.size > 15 * 1024 * 1024
    )
        return error("fileError");
    const url = URL.createObjectURL(file),
        image = new Image();
    try {
        image.src = url;
        await image.decode();
        if (revision !== state.revision) return;
        if (
            Math.min(image.naturalWidth, image.naturalHeight) < 600 ||
            image.naturalWidth * image.naturalHeight > 40_000_000
        )
            return error("fileError");
        const canvas = document.createElement("canvas");
        const scale = Math.min(
            1,
            1600 / Math.max(image.naturalWidth, image.naturalHeight),
        );
        canvas.width = Math.round(image.naturalWidth * scale);
        canvas.height = Math.round(image.naturalHeight * scale);
        canvas
            .getContext("2d")
            .drawImage(image, 0, 0, canvas.width, canvas.height);
        const sample = document.createElement("canvas");
        sample.width = sample.height = 64;
        const ctx = sample.getContext("2d");
        ctx.drawImage(canvas, 0, 0, 64, 64);
        const data = ctx.getImageData(0, 0, 64, 64).data;
        let luminance = 0;
        for (let i = 0; i < data.length; i += 4)
            luminance +=
                0.2126 * data[i] + 0.7152 * data[i + 1] + 0.0722 * data[i + 2];
        luminance /= 4096;
        if (luminance < 18 || luminance > 245) return error("dark");
        state[key] = {
            width: image.naturalWidth,
            height: image.naturalHeight,
            image: canvas,
            points: {},
            history: [],
            qualityConfirmed: false,
            synthetic: false,
        };
        render();
    } catch {
        if (revision === state.revision) error("fileError");
    } finally {
        URL.revokeObjectURL(url);
        image.src = "";
    }
}
function review(root) {
    const result = state.result;
    if (!result) return advance(2);
    root.append(text("p", t("resultIntro")), text("p", t("warning"), "notice"));
    if (result.synthetic) root.append(text("p", t("synthetic"), "synthetic"));
    const table = text("table", "", "review-table"),
        head = document.createElement("thead"),
        heading = document.createElement("tr");
    for (const label of ["field", "estimate", "tape", "difference"])
        heading.append(text("th", t(label)));
    head.append(heading);
    table.append(head);
    const body = document.createElement("tbody");
    for (const field of FIELDS) {
        const row = document.createElement("tr");
        row.append(text("th", t(field)));
        const amount = document.createElement("td");
        amount.append(
            text(
                "strong",
                `${(result.estimates[field].valueMm / 10).toLocaleString(lang, { maximumFractionDigits: 1 })} cm`,
            ),
        );
        row.append(amount);
        const tape = document.createElement("td"),
            input = document.createElement("input");
        input.type = "number";
        input.min = 1;
        input.max = 400;
        input.step = 0.1;
        input.inputMode = "decimal";
        input.setAttribute("aria-label", `${t(field)} · ${t("tape")}`);
        input.value = state.tape[field] ?? "";
        tape.append(input);
        row.append(tape);
        const delta = text("td", "—");
        delta.setAttribute("aria-live", "polite");
        row.append(delta);
        const update = () => {
            const value = Number(input.value);
            state.tape[field] = input.value;
            delta.textContent =
                input.value !== "" && value > 0 && value <= 400
                    ? `${((result.estimates[field].valueMm - value * 10) / 10).toLocaleString(lang, { maximumFractionDigits: 1, signDisplay: "always" })} cm`
                    : "—";
        };
        input.oninput = update;
        update();
        body.append(row);
    }
    table.append(body);
    root.append(table, text("p", t("noSave")), definitions());
    const actions = text("div", "", "actions");
    actions.append(
        button("edit", () => advance(1), true),
        button("export", download),
    );
    root.append(actions);
}
function download() {
    const tapeReferencesMm = {};
    for (const field of FIELDS) {
        const value = Number(state.tape[field]);
        if (
            state.tape[field] &&
            Number.isFinite(value) &&
            value > 0 &&
            value <= 400
        )
            tapeReferencesMm[field] = value * 10;
    }
    const report = {
        ...state.result,
        heightMm: Number(state.heightCm) * 10,
        tapeReferencesMm,
        locale: lang,
    };
    const url = URL.createObjectURL(
        new Blob([JSON.stringify(report, null, 2)], {
            type: "application/json",
        }),
    );
    const a = document.createElement("a");
    a.href = url;
    a.download = "stitchpad-experimental-measurement.json";
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
}
function demo() {
    state.revision++;
    state.heightCm = "180";
    state.consent = false;
    for (const [key, w] of [
        ["front", 0.2],
        ["side", 0.13],
    ]) {
        const canvas = document.createElement("canvas");
        canvas.width = 800;
        canvas.height = 1600;
        const ctx = canvas.getContext("2d");
        ctx.fillStyle = "#e4dfd5";
        ctx.fillRect(0, 0, 800, 1600);
        ctx.fillStyle = "#938b7c";
        ctx.beginPath();
        ctx.ellipse(400, 200, 65, 120, 0, 0, Math.PI * 2);
        ctx.fill();
        ctx.beginPath();
        ctx.ellipse(400, 680, w * 800, 320, 0, 0, Math.PI * 2);
        ctx.fill();
        ctx.fillRect(320, 880, 65, 640);
        ctx.fillRect(415, 880, 65, 640);
        state[key] = {
            width: 800,
            height: 1600,
            image: canvas,
            points: {
                top: { x: 0.5, y: 0.05 },
                floor: { x: 0.5, y: 0.95 },
                chestLeft: { x: 0.5 - w, y: 0.3 },
                chestRight: { x: 0.5 + w, y: 0.3 },
                waistLeft: { x: 0.5 - w * 0.9, y: 0.42 },
                waistRight: { x: 0.5 + w * 0.9, y: 0.42 },
                hipsLeft: { x: 0.5 - w, y: 0.54 },
                hipsRight: { x: 0.5 + w, y: 0.54 },
            },
            history: [],
            qualityConfirmed: false,
            synthetic: true,
        };
    }
    advance(0);
}
function reset() {
    for (const key of ["front", "side"])
        if (state[key]) state[key].image.width = 0;
    const revision = state.revision + 1;
    state = fresh();
    state.revision = revision;
    render();
}
$("language").onchange = (event) => {
    lang = event.target.value;
    render();
};
window.addEventListener("pagehide", reset);
render();

/** Actual WASM/model execution using optional generated native-Anny images.
 * First run generate_fixtures.py. No personal photographs or accuracy cohort.
 */
async (page) => {
    const base = "http://127.0.0.1:8765";
    await page.goto(base);
    const manifest = await page.request.get(
        base + "/assets/fixtures/metadata.json",
    );
    if (!manifest.ok()) throw Error("Generate synthetic fixtures first");
    const metadata = await manifest.json();
    const b = (name) => page.getByRole("button", { name, exact: true });
    await page.locator("#height").fill(String(metadata.heightMm / 10));
    await page.getByRole("checkbox").check();
    await b("Begin capture").click();
    const evidence = [];
    for (const view of ["front", "side"]) {
        const file = await page.request.get(
            base + `/assets/fixtures/${view}.png`,
        );
        await page.locator("#photo").setInputFiles({
            name: `synthetic-${view}.png`,
            mimeType: "image/png",
            buffer: await file.body(),
        });
        await page.waitForSelector("#photo-canvas");
        await page
            .getByRole("checkbox", {
                name: "This is a synthetic image or a test fixture (exclude from real accuracy studies).",
                exact: true,
            })
            .check();
        await b("Suggest outlines automatically").click();
        await page.waitForFunction(
            () =>
                ![...document.querySelectorAll("button")].some(
                    (x) => x.textContent === "Detecting locally…",
                ),
        );
        const diagnostics = await page.evaluate(() => ({
            error: document.getElementById("error").hidden
                ? null
                : document.getElementById("error").textContent,
            points: [...document.querySelectorAll(".point input")].map(
                (x) => x.value,
            ),
        }));
        if (diagnostics.error) throw Error(`${view}: ${diagnostics.error}`);
        if (diagnostics.points.some((x) => x === ""))
            throw Error("Missing automatic points");
        await page.locator("input[data-capture-confirmation]").check();
        await b("Continue").click();
        evidence.push({ view, points: diagnostics.points });
    }
    if ((await page.locator("table tbody tr").count()) !== 3)
        throw Error(await page.locator("#error").innerText());
    const [download] = await Promise.all([
        page.waitForEvent("download"),
        b("Download research result (no photos)").click(),
    ]);
    let raw = "";
    for await (const chunk of await download.createReadStream())
        raw += chunk.toString();
    const result = JSON.parse(raw);
    if (
        !result.synthetic ||
        !result.outlineSources.front.modelSha256 ||
        !result.outlineSources.side.modelSha256 ||
        result.canSaveToCustomer
    )
        throw Error("Detector provenance or synthetic label missing");
    return {
        passed: true,
        syntheticOnly: true,
        accuracyEvidence: false,
        views: evidence,
        estimatedMm: Object.fromEntries(
            Object.entries(result.estimates).map(([k, v]) => [k, v.valueMm]),
        ),
        inferenceMs: {
            front: result.outlineSources.front.inferenceMs,
            side: result.outlineSources.side.inferenceMs,
        },
    };
}

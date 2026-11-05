/** Run with playwright-cli run-code --filename=tools/measurement_capture/verify_browser.cjs.
 * Uses only artificial images. Requires the local server and setup_assets.py for detector tests.
 */
async (page) => {
    const base = "http://127.0.0.1:8765";
    const unexpected = [];
    await page.route("**/*", async (route) => {
        const url = new URL(route.request().url());
        if (url.origin !== base) {
            unexpected.push(url.origin);
            await route.abort();
        } else await route.continue();
    });
    const check = (condition, label) => {
        if (!condition) throw Error(label);
    };
    const button = (name) => page.getByRole("button", { name, exact: true });
    await page.goto(base);
    await button("Begin capture").click();
    check(
        (await page.locator("#error").innerText()).includes("permission"),
        "consent gate",
    );
    await page.getByRole("checkbox").check();
    await button("Begin capture").click();
    check(
        (await page.locator("#error").innerText()).includes("height"),
        "height gate",
    );
    const evidence = [];
    for (const journey of ["existing_customer", "designer_discovery"]) {
        await button("Clear session").click();
        await page.locator(`input[value=${journey}]`).check();
        await button("Try a synthetic demo").click();
        await page.getByRole("checkbox").check();
        await button("Begin capture").click();
        await button("Continue").click();
        check(
            (await page.locator("#error").innerText()).includes("Confirm"),
            "capture checks",
        );
        await page.getByRole("checkbox").check();
        await button("Undo last mark").click();
        check(await page.getByRole("checkbox").isChecked(), "empty undo leaves review unchanged");
        const point = page.getByRole("spinbutton", {
            name: "Chest/bust · left · Across (%)",
            exact: true,
        });
        await point.fill("31");
        await point.press("Tab");
        check(
            !(await page.getByRole("checkbox").isChecked()),
            "editing invalidates quality confirmation",
        );
        await page.getByRole("checkbox").check();
        await button("Undo last mark").click();
        check(!(await page.getByRole("checkbox").isChecked()), "undoing an edit invalidates review");
        await page.getByRole("checkbox").check();
        await button("Continue").click();
        await page.getByRole("checkbox").check();
        await button("Continue").click();
        check(
            (await page.locator("table tbody tr").count()) === 3,
            "three results",
        );
        await page.locator("table input").first().fill("100");
        check(
            (await page.locator("table tbody tr").first().innerText()).includes(
                "cm",
            ),
            "tape comparison",
        );
        const [download] = await Promise.all([
            page.waitForEvent("download"),
            button("Download research result (no photos)").click(),
        ]);
        // Read the actual download stream to inspect its contents without saving a study file.
        const stream = await download.createReadStream();
        let raw = "";
        for await (const chunk of stream) raw += chunk.toString();
        const result = JSON.parse(raw);
        check(
            result.synthetic && result.journey === journey,
            "synthetic/journey provenance",
        );
        check(
            result.canSaveToCustomer === false &&
                result.validatedForTailoring === false &&
                result.confidence === null,
            "unvalidated status",
        );
        check(
            result.pipelineVersion && result.tapeReferencesMm.chest === 1000,
            "pipeline and tape export",
        );
        check(
            !raw.includes("data:image") && !raw.includes("base64"),
            "no photos in export",
        );
        evidence.push(journey);
    }
    await button("Clear session").click();
    check((await page.locator("#height").inputValue()) === "", "reset height");
    check(!(await page.getByRole("checkbox").isChecked()), "reset consent");
    await button("Try a synthetic demo").click();
    await page.getByRole("checkbox").check();
    await button("Begin capture").click();
    // Missing model assets fail closed and allow an explicit retry/manual fallback.
    await page.route("**/assets/pose_landmarker_full.task", (route) =>
        route.fulfill({ status: 404, body: "missing fixture" }),
    );
    await button("Suggest outlines automatically").click();
    await page.waitForFunction(
        () =>
            ![...document.querySelectorAll("button")].some(
                (x) => x.textContent === "Detecting locally…",
            ),
    );
    check(
        (await page.locator("#error").innerText()).includes("unavailable"),
        "missing assets failure",
    );
    await page.unroute("**/assets/pose_landmarker_full.task");
    // Exercise the actual model, not a mock, on a deliberately simple synthetic drawing.
    await button("Suggest outlines automatically").click();
    await page.waitForFunction(
        () =>
            ![...document.querySelectorAll("button")].some(
                (x) => x.textContent === "Detecting locally…",
            ),
    );
    check(
        !(await page.getByRole("checkbox").isChecked()),
        "automatic detection cannot confirm anatomy",
    );
    check(
        !(await page.locator("#error").isHidden()),
        "simple nonhuman demo must not be accepted by detector",
    );
    await button("Continue").click();
    check(
        (await page.locator("#error").innerText()).includes("mark every point"),
        "no stale demo markers after failed detector",
    );
    // Corrupt replacement must discard the previous image immediately.
    await page.locator("#photo").setInputFiles({
        name: "bad.png",
        mimeType: "image/png",
        buffer: Buffer.from("corrupt"),
    });
    await page.waitForFunction(() => !document.getElementById("error").hidden);
    check(
        (await page.locator("#photo-canvas").count()) === 0,
        "corrupt replacement clears image",
    );
    await button("Back").click();
    await button("Try a synthetic demo").click();
    await page.getByRole("checkbox").check();
    await button("Begin capture").click();
    // Reset while model bytes are loading; stale completion cannot resurrect a capture.
    let release;
    const gate = new Promise((resolve) => (release = resolve));
    await page.route("**/assets/pose_landmarker_full.task", async (route) => {
        await gate;
        await route.continue();
    });
    await button("Suggest outlines automatically").click();
    await button("Clear session").click();
    release();
    await page.unroute("**/assets/pose_landmarker_full.task");
    await page.waitForLoadState("networkidle");
    await page.waitForFunction(
        () => document.querySelector("#height")?.value === "",
    );
    check(
        (await page.locator("#photo-canvas").count()) === 0,
        "reset during detection remains cleared",
    );
    await page.setViewportSize({ width: 390, height: 844 });
    await page.locator("#language").selectOption("fr");
    check(
        (await page.locator("h1").innerText()).includes("prise de vue"),
        "French copy",
    );
    check(
        !(await page.evaluate(
            () => document.documentElement.scrollWidth > innerWidth,
        )),
        "mobile overflow",
    );
    check(unexpected.length === 0, "unexpected external request");
    return {
        passed: true,
        journeys: evidence,
        checks: [
            "consent",
            "height",
            "capture review",
            "edited marker invalidation",
            "comparison",
            "export provenance",
            "actual detector rejection",
            "corrupt file",
            "reset race",
            "French mobile",
            "no external requests",
        ],
    };
}

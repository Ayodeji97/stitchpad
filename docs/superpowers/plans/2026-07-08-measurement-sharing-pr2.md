# Measurement Sharing (PR 2) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Share a measurement from the detail view as a branded image, a PDF, or plain WhatsApp text.

**Architecture:** Mirrors the proven order-receipt pipeline: a pure commonMain formatter builds a `MeasurementShareData` snapshot (labels pre-resolved by the ViewModel, sections reused from PR 1's `measurementDetailSections`), a commonMain `MeasurementSharer` **interface** with plain platform implementations renders/shares it (Android `Canvas`/`PdfDocument` + FileProvider chooser; iOS `UIGraphicsImageRenderer`/`UIGraphicsPDFRenderer` + `UIActivityViewController` with the 450ms Compose-sheet-dismiss delay), and the WhatsApp option reuses `WhatsAppLauncher` with the customer's phone (generic text share as fallback when blank).

**Tech Stack:** Kotlin Multiplatform + Compose Multiplatform, Koin platform modules, kotlin.test + Turbine, detekt.

**Spec:** `docs/superpowers/specs/2026-07-08-measurement-detail-view-design.md` (Sharing section). Mockups: `preview/measurement-visibility-redesign.html` row 5 (share sheet, shared card, WhatsApp bubble). Reference implementation to mirror throughout: `core/sharing/OrderReceiptSharer.kt` + its android/ios actuals.

## Global Constraints

- Worktree `/Users/danzucker/Desktop/Project/StitchPad/.claude/worktrees/measurement-detail-view`, branch **`feat/measurement-sharing`** (stacked on PR 1). Run all commands from the worktree root. Commit per task; do NOT push.
- The shared card is ALWAYS paper-light (`#FAF6EC` background) for both image and PDF — it must print well (spec decision).
- Brand colors on the card: Indigo `#2C3E7C` (wordmark), Ink `#14110E` (values/name), Sienna dark `#8E4524` (section titles), grays `#57534C`/`#7D7970` (labels/meta/footer). Values in platform monospace (`Typeface.MONOSPACE` / `UIFont.monospacedSystemFontOfSize`).
- WhatsApp text format uses literal `*bold*` / `_italic_` markers (WhatsApp markup), built by the formatter — never by UI code.
- iOS share presentation MUST wait `450L` ms after the Compose sheet dismisses before presenting `UIActivityViewController` (`SHARE_PRESENT_DELAY_MS` precedent, `OrderReceiptSharer.ios.kt:764`).
- Android share files go under `context.cacheDir` with the existing FileProvider authority `"${context.packageName}.fileprovider"` (no manifest change needed).
- MVI: gated actions (Share included) go through `requireUnlocked` (locked → Upgrade; unknown → no-op). All user-facing strings in `strings.xml` (reuse `share_as_image_title/_description`, `share_as_pdf_title/_description`). Every new Screen-level composable gets light+dark previews.
- KMP: no JVM-only APIs in commonMain; backtick test names without `(` `)`; positional string args; never pipe gradle output.
- Analytics: `AnalyticsEvent.MeasurementShared(format)` → GA4 name `measurement_shared`, param `{"format": format}`, format ∈ `image | pdf | whatsapp_text` (spec).
- Repository writes are already offline-safe via `OfflineWriteDispatcher` — sharing performs NO Firestore writes.

---

### Task 1: `MeasurementShareData` + `MeasurementShareFormatter` (pure, commonMain)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/sharing/MeasurementShareData.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/share/MeasurementShareFormatter.kt` (feature layer, NOT core — it depends on the feature-level section resolver; the pure `MeasurementShareData` stays in core/sharing so the platform sharers never import feature code)
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/share/MeasurementShareFormatterTest.kt`

**Interfaces:**
- Consumes: `Measurement` (core.domain.model), `measurementDetailSections(measurement, customFieldLabels): List<MeasurementDetailSection>` and `MeasurementPreviewField(label, value)` from `feature.measurement.presentation.detail` / `.presentation`, `formatMeasurementValue(value): String` from `feature.measurement.presentation`.
- Produces (Tasks 2–4 rely on these EXACT shapes):

```kotlin
data class MeasurementShareSection(val title: String, val rows: List<MeasurementShareRow>)
data class MeasurementShareRow(val label: String, val value: String) // value already formatted, NO unit suffix
data class MeasurementShareData(
    val customerName: String,
    val measurementName: String,      // display name, never blank (caller passes fallback)
    val genderLabel: String,          // pre-localized, e.g. "Women's"
    val unitLabel: String,            // pre-localized, e.g. "Inches"
    val unitSuffix: String,           // "″" or "cm" — renderers append to values
    val dateFormatted: String?,       // null when dateTaken == 0L (legacy docs)
    val businessName: String?,
    val sections: List<MeasurementShareSection>,
    val notes: String?,
)
object MeasurementShareFormatter {
    fun format(
        measurement: Measurement,
        customerName: String,
        measurementName: String,
        genderLabel: String,
        unitLabel: String,
        unitSuffix: String,
        dateFormatted: String?,
        businessName: String?,
        customFieldLabels: Map<String, String>,
        sectionTitles: Map<String, String>,   // titleKey -> localized title
        customSectionTitle: String,           // title for the null-titleKey group
    ): MeasurementShareData
    fun buildWhatsAppText(data: MeasurementShareData): String
    fun formatShareDate(epochMillis: Long): String?   // "12 Jun 2026", null for <= 0L
}
```

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.danzucker.stitchpad.feature.measurement.presentation.share

import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.domain.model.Measurement
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MeasurementShareFormatterTest {

    private fun measurement(
        fields: Map<String, Double> = mapOf("shoulder_width" to 15.0, "waist" to 31.0, "trouser_waist" to 31.5),
        notes: String? = "Loose at the hip.",
        dateTaken: Long = 1_750_000_000_000L,
    ) = Measurement(
        id = "m1", customerId = "c1", gender = CustomerGender.FEMALE, name = "Wedding gown",
        fields = fields, unit = MeasurementUnit.INCHES, notes = notes,
        dateTaken = dateTaken, createdAt = 1L,
    )

    private fun format(m: Measurement = measurement()) = MeasurementShareFormatter.format(
        measurement = m,
        customerName = "Chidinma Eze",
        measurementName = "Wedding gown",
        genderLabel = "Women's",
        unitLabel = "Inches",
        unitSuffix = "″",
        dateFormatted = "12 Jun 2026",
        businessName = "Zucker Styles",
        customFieldLabels = emptyMap(),
        sectionTitles = mapOf(
            "section_upper_body" to "Upper Body",
            "section_body_lengths" to "Body Lengths",
            "section_trouser" to "Trouser",
        ),
        customSectionTitle = "Custom",
    )

    @Test
    fun `format groups values into localized sections in template order`() {
        val data = format()
        assertEquals(listOf("Upper Body", "Trouser"), data.sections.map { it.title })
        assertEquals(listOf("Shoulder" to "15", "Waist" to "31"), data.sections[0].rows.map { it.label to it.value })
        assertEquals(listOf("Waist" to "31.5"), data.sections[1].rows.map { it.label to it.value })
    }

    @Test
    fun `format falls back to raw titleKey when a localized title is missing`() {
        val data = MeasurementShareFormatter.format(
            measurement = measurement(),
            customerName = "C", measurementName = "M", genderLabel = "G", unitLabel = "U",
            unitSuffix = "″", dateFormatted = null, businessName = null,
            customFieldLabels = emptyMap(), sectionTitles = emptyMap(), customSectionTitle = "Custom",
        )
        assertEquals(listOf("section_upper_body", "section_trouser"), data.sections.map { it.title })
    }

    @Test
    fun `whatsapp text has bold header sections and footer`() {
        val text = MeasurementShareFormatter.buildWhatsAppText(format())
        assertTrue(text.startsWith("📏 *Chidinma Eze — Wedding gown*"))
        assertTrue(text.contains("Women's · Inches · 12 Jun 2026"))
        assertTrue(text.contains("*Upper Body*"))
        assertTrue(text.contains("Shoulder: 15"))
        assertTrue(text.contains("Waist: 31.5"))
        assertTrue(text.contains("_Loose at the hip._"))
        assertTrue(text.trimEnd().endsWith("_Sent from StitchPad · getstitchpad.com_"))
    }

    @Test
    fun `whatsapp text omits date and notes when absent`() {
        val data = format(measurement(notes = null)).copy(dateFormatted = null)
        val text = MeasurementShareFormatter.buildWhatsAppText(data)
        assertTrue(text.contains("Women's · Inches\n"))
        assertFalse(text.contains("· 12 Jun"))
        assertFalse(text.contains("_Loose"))
    }

    @Test
    fun `formatShareDate returns null for legacy zero epoch`() {
        assertNull(MeasurementShareFormatter.formatShareDate(0L))
        assertEquals("12 Jun 2026", MeasurementShareFormatter.formatShareDate(1_749_686_400_000L))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*MeasurementShareFormatterTest*"`
Expected: FAIL — unresolved reference `MeasurementShareFormatter`.

- [ ] **Step 3: Implement**

`MeasurementShareData.kt`:
```kotlin
package com.danzucker.stitchpad.core.sharing

/** One card/text section, labels and title already localized. */
data class MeasurementShareSection(val title: String, val rows: List<MeasurementShareRow>)

/** [value] is pre-formatted (trailing .0 dropped) WITHOUT the unit suffix — renderers append it. */
data class MeasurementShareRow(val label: String, val value: String)

/** Everything the platform renderers and the WhatsApp text builder need — no resource lookups downstream. */
data class MeasurementShareData(
    val customerName: String,
    val measurementName: String,
    val genderLabel: String,
    val unitLabel: String,
    val unitSuffix: String,
    val dateFormatted: String?,
    val businessName: String?,
    val sections: List<MeasurementShareSection>,
    val notes: String?,
)
```

`MeasurementShareFormatter.kt`:
```kotlin
package com.danzucker.stitchpad.feature.measurement.presentation.share

import com.danzucker.stitchpad.core.domain.model.Measurement
import com.danzucker.stitchpad.core.sharing.MeasurementShareData
import com.danzucker.stitchpad.core.sharing.MeasurementShareRow
import com.danzucker.stitchpad.core.sharing.MeasurementShareSection
import com.danzucker.stitchpad.feature.measurement.presentation.detail.measurementDetailSections
import com.danzucker.stitchpad.feature.measurement.presentation.formatMeasurementValue
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Builds the share payload for a measurement. Pure: every label arrives
 * pre-localized (the ViewModel resolves string resources), so this object is
 * unit-testable without resource loading. Mirrors [ReceiptFormatter]'s role
 * for order receipts.
 */
object MeasurementShareFormatter {

    fun format(
        measurement: Measurement,
        customerName: String,
        measurementName: String,
        genderLabel: String,
        unitLabel: String,
        unitSuffix: String,
        dateFormatted: String?,
        businessName: String?,
        customFieldLabels: Map<String, String>,
        sectionTitles: Map<String, String>,
        customSectionTitle: String,
    ): MeasurementShareData {
        val sections = measurementDetailSections(measurement, customFieldLabels).map { section ->
            MeasurementShareSection(
                title = when (section.titleKey) {
                    null -> customSectionTitle
                    else -> sectionTitles[section.titleKey] ?: section.titleKey
                },
                rows = section.rows.map { MeasurementShareRow(it.label, formatMeasurementValue(it.value)) },
            )
        }
        return MeasurementShareData(
            customerName = customerName,
            measurementName = measurementName,
            genderLabel = genderLabel,
            unitLabel = unitLabel,
            unitSuffix = unitSuffix,
            dateFormatted = dateFormatted,
            businessName = businessName,
            sections = sections,
            notes = measurement.notes?.takeIf { it.isNotBlank() },
        )
    }

    /** WhatsApp markup: *bold*, _italic_. Values intentionally carry no unit suffix (header names the unit). */
    fun buildWhatsAppText(data: MeasurementShareData): String = buildString {
        append("📏 *").append(data.customerName).append(" — ").append(data.measurementName).appendLine("*")
        append(data.genderLabel).append(" · ").append(data.unitLabel)
        data.dateFormatted?.let { append(" · ").append(it) }
        appendLine()
        data.sections.forEach { section ->
            appendLine()
            append("*").append(section.title).appendLine("*")
            section.rows.forEach { row -> append(row.label).append(": ").appendLine(row.value) }
        }
        data.notes?.let {
            appendLine()
            append("_").append(it).appendLine("_")
        }
        appendLine()
        append("_Sent from StitchPad · getstitchpad.com_")
    }

    /** "12 Jun 2026"; null for the 0L legacy sentinel — same policy as the detail screen's Taken chip. */
    fun formatShareDate(epochMillis: Long): String? {
        if (epochMillis <= 0L) return null
        val date = Instant.fromEpochMilliseconds(epochMillis)
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
        val month = date.month.name.lowercase().replaceFirstChar(Char::uppercase).take(3)
        return "${date.dayOfMonth} $month ${date.year}"
    }
}
```

Note: the `whatsapp text omits date and notes` test asserts `"Women's · Inches\n"` — the meta line ends with a plain `\n` (`appendLine()` after the unit when no date). If `appendLine` emits `\r\n` on any platform the assertion would still pass on JVM (it does not — kotlin appendLine uses `\n`).

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*MeasurementShareFormatterTest*"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/sharing/MeasurementShareData.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/share/ composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/share/
git commit -m "feat(sharing): MeasurementShareFormatter + share payload model"
```

---

### Task 2: `MeasurementSharer` interface + platform renderers + Koin registration

The platform renderer/sharer. **Mirror `OrderReceiptSharer` structurally** — same file layout, same cache/share plumbing, same threading — but the card is a single LIGHT paper design for both image and PDF. Deliberate deviation from the receipt precedent: a commonMain **interface** with plain platform classes instead of `expect class`, because the ViewModel (Task 3) injects it and commonTest must fake it — an `expect class` cannot be implemented in commonTest. No expect/actual is needed at all: only the platform Koin modules reference the concrete classes.

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/sharing/MeasurementSharer.kt` (interface)
- Create: `composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/core/sharing/AndroidMeasurementSharer.kt`
- Create: `composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/core/sharing/IosMeasurementSharer.kt`
- Modify: `composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/di/PlatformModule.android.kt` (next to the `OrderReceiptSharer` line ~45): `single<MeasurementSharer> { AndroidMeasurementSharer(androidContext()) }`
- Modify: `composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/di/PlatformModule.ios.kt` (next to line ~68): `single<MeasurementSharer> { IosMeasurementSharer() }`

**Interfaces:**
- Consumes: `MeasurementShareData` (Task 1).
- Produces (Task 3 relies on): the interface below. Methods are `suspend`, return `Unit`, and THROW on render/IO failure (caller catches — same contract as `OrderReceiptSharer`).

- [ ] **Step 1: Write the interface**

```kotlin
package com.danzucker.stitchpad.core.sharing

/**
 * Renders a measurement card and hands it to the platform share sheet.
 * Same contract as [OrderReceiptSharer]: suspend, Unit, throws on failure.
 * Interface (not expect class) so the ViewModel can inject it and commonTest
 * can fake it. [shareAsText] is the WhatsApp fallback when the customer has
 * no phone — plain text via the generic share sheet.
 */
interface MeasurementSharer {
    suspend fun shareAsImage(data: MeasurementShareData)
    suspend fun shareAsPdf(data: MeasurementShareData)
    suspend fun shareAsText(text: String)
}
```

- [ ] **Step 2: Write the Android implementation**

Open `composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/core/sharing/OrderReceiptSharer.android.kt` and mirror it. Structure (`class AndroidMeasurementSharer(private val context: Context) : MeasurementSharer`, methods `override suspend fun ...`):

- `shareAsImage`: `withContext(Dispatchers.Default) { saveBitmapToCache(renderCardBitmap(data), "measurement") }` then `shareFile(file, "image/png")`.
- `shareAsPdf`: `withContext(Dispatchers.Default) { renderCardPdf(data) }` then `shareFile(file, "application/pdf")`.
- `shareAsText`: `Intent(ACTION_SEND) { type = "text/plain"; putExtra(EXTRA_TEXT, text); FLAG_ACTIVITY_NEW_TASK }` → `startActivity(Intent.createChooser(intent, null).apply { addFlags(FLAG_ACTIVITY_NEW_TASK) })`.
- Copy these helpers verbatim from the receipt actual, renaming the cache prefix/dir to `measurements`: `saveBitmapToCache`, `cacheFile` (dir `context.cacheDir/"measurements"`, name `measurement_<prefix>_<millis>.<ext>`), `pruneOldFiles` (keep newest 10), `shareFile` (FileProvider authority `"${context.packageName}.fileprovider"`), `makePaint(color, size, bold)`.
- `renderCardBitmap(data): Bitmap` — width `800`, padding `40f`, ARGB_8888. Estimate height generously (header ~200f + per-section: 56f title + 44f per row + per-notes-line 34f + footer 120f + 200f slack), draw, then crop to actual content height with `Bitmap.createBitmap(bmp, 0, 0, w, finalY)` exactly like `renderDarkBitmap`'s final step. Card layout, top to bottom (all colors as `Color.parseColor("#...")`, monospace values via `Paint().apply { typeface = Typeface.MONOSPACE }`):
  1. Background fill `#FAF6EC`.
  2. Header row: "StitchPad" bold `#2C3E7C` 34f at left; "MEASUREMENT CARD" `#7D7970` 18f letter-spaced (`paint.letterSpacing = 0.15f`) right-aligned.
  3. 1px divider `#E5E3DF`, 24f below header.
  4. Customer name bold `#14110E` 44f.
  5. Meta line `#57534C` 22f: `measurementName · genderLabel · unitLabel[ · Taken dateFormatted][ · businessName]` (skip null parts; join with " · ").
  6. Per section: 28f gap, 1px divider `#E5E3DF`, section title UPPERCASE bold `#8E4524` 20f with `letterSpacing = 0.12f`; then per row (44f line height): label `#57534C` 24f left, `"${row.value}${data.unitSuffix}"` bold monospace `#14110E` 24f right-aligned.
  7. Notes (when non-null): 28f gap, divider, italic (`Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)`) `#57534C` 22f, word-wrapped to the content width using `paint.measureText` (accumulate words until the line exceeds `width - 2*padding`, then break — write a small `wrapText(text, paint, maxWidth): List<String>` helper).
  8. Footer: 32f gap, divider, centered `#7D7970` 18f: `"Made with StitchPad — the smart work pad for tailors · getstitchpad.com"`.
- `renderCardPdf(data): File` — mirror `renderLightPdf`: `pageWidth = 420`, padding `30f`, `pageHeight = max(595, ceil(estimate))`, `android.graphics.pdf.PdfDocument`, same layout at HALF the image font sizes (17/9/22/11/10/12/11/9), white→`#FAF6EF`? No — fill the page `#FAF6EC` like the image. Write with `FileOutputStream(cacheFile("pdf", "pdf")).use { doc.writeTo(it) }`, `doc.close()`.

(Card literals — "StitchPad", "MEASUREMENT CARD", "Made with StitchPad…" — are canvas-drawn English literals, same precedent as the receipt renderers.)

- [ ] **Step 3: Write the iOS implementation**

Open `OrderReceiptSharer.ios.kt` and mirror. `class IosMeasurementSharer : MeasurementSharer` (no ctor args, methods `override suspend fun ...`):

- `shareAsImage`: render `UIImage` via `UIGraphicsImageRenderer` (width 800.0, estimated height — NO post-crop on iOS, so compute the height by summing the same advances the drawing code uses; extract a shared `layoutHeight(data): Double` used by both the estimate and a paranoid final assertion comment), `UIImagePNGRepresentation`, `tempFileUrl("png")`, `shareUrl(url)`.
- `shareAsPdf`: `UIGraphicsPDFRenderer` pageWidth 420.0, pageHeight `max(595.0, estimate)`, `PDFDataWithActions { beginPage(); draw }`, write to `tempFileUrl("pdf")`, `shareUrl(url)`.
- `shareAsText`: `delay(SHARE_PRESENT_DELAY_MS)` then `withContext(Dispatchers.Main)` present `UIActivityViewController(activityItems = listOf(text), applicationActivities = null)` via the same `activeKeyWindow()?.rootViewController` + `topmostPresenter` walk (copy `topmostPresenter` from the receipt actual; `activeKeyWindow()` is `internal` in iosMain — import `com.danzucker.stitchpad.core.platform.activeKeyWindow`).
- Copy verbatim (renaming file prefix to `measurement_`): `tempFileUrl`, `shareUrl` (INCLUDING `delay(SHARE_PRESENT_DELAY_MS)` with `const val SHARE_PRESENT_DELAY_MS = 450L`), `topmostPresenter`, `drawText`/`drawTextRight`/`drawCentered`, `darkColor(hex)` (rename `cardColor`), font helpers — values use `UIFont.monospacedSystemFontOfSize(size, weight = UIFontWeightBold)`.
- Same card layout & colors as Android (image 800.0/pad 40.0, PDF 420.0/pad 30.0, half sizes).

- [ ] **Step 4: Register in Koin + compile both platforms**

Add the two `single { ... }` lines shown in **Files**. Then run:
`./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64 detekt`
Expected: BUILD SUCCESSFUL on all three. (No unit tests — platform renderers are verified visually in QA, same as the receipt renderers, which have none.)

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/sharing/MeasurementSharer.kt composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/core/sharing/ composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/core/sharing/ composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/di/PlatformModule.android.kt composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/di/PlatformModule.ios.kt
git commit -m "feat(sharing): MeasurementSharer interface + platform card renderers + Koin registration"
```

---

### Task 3: ViewModel wiring (share actions, WhatsApp event, analytics) + tests

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/detail/MeasurementDetailState.kt` (add `customer: Customer? = null`, `showShareSheet: Boolean = false`)
- Modify: `.../detail/MeasurementDetailAction.kt` (add `OnShareClick, OnShareAsImageClick, OnShareAsPdfClick, OnShareWhatsAppClick, OnDismissShareSheet`)
- Modify: `.../detail/MeasurementDetailEvent.kt` (add `data class LaunchWhatsApp(val phone: String, val message: String)`)
- Modify: `.../detail/MeasurementDetailViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/analytics/domain/AnalyticsEvent.kt` (after `MeasurementDetailViewed`)
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/MeasurementModule.kt` — no change needed (`viewModelOf` resolves the new ctor param via `get()`), but verify it compiles.
- Create: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/sharing/FakeMeasurementSharer.kt`
- Test: extend `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/detail/MeasurementDetailViewModelTest.kt`

**Interfaces:**
- Consumes: Tasks 1–2 (`MeasurementShareFormatter`, `MeasurementSharer`), existing `observeLockState` collector (extended to keep the whole `Customer`), string resources via `org.jetbrains.compose.resources.getString`.
- Produces (Task 4 relies on): the new actions/events above; state fields `showShareSheet: Boolean`, `customer: Customer?`; analytics `AnalyticsEvent.MeasurementShared(format: String)` → name `measurement_shared`, params `{"format": format}`.

Key implementation points (write these into the ViewModel):

1. New ctor param (last position): `private val measurementSharer: MeasurementSharer`.
2. `observeLockState` also stores the customer: `_state.update { it.copy(customer = result.data, isLocked = result.data.slotState == CustomerSlotState.LOCKED) }` — rename the function `observeCustomer` for honesty.
3. Action handling:
```kotlin
MeasurementDetailAction.OnShareClick -> requireUnlocked {
    _state.update { it.copy(showShareSheet = true) }
}
MeasurementDetailAction.OnDismissShareSheet ->
    _state.update { it.copy(showShareSheet = false) }
MeasurementDetailAction.OnShareAsImageClick -> shareMeasurement(FORMAT_IMAGE)
MeasurementDetailAction.OnShareAsPdfClick -> shareMeasurement(FORMAT_PDF)
MeasurementDetailAction.OnShareWhatsAppClick -> shareMeasurement(FORMAT_WHATSAPP)
```
4. The share pipeline (companion constants `FORMAT_IMAGE = "image"`, `FORMAT_PDF = "pdf"`, `FORMAT_WHATSAPP = "whatsapp_text"`):
```kotlin
private fun shareMeasurement(format: String) {
    val measurement = _state.value.measurement ?: return
    val customer = _state.value.customer ?: return
    _state.update { it.copy(showShareSheet = false) }
    viewModelScope.launch {
        try {
            val data = buildShareData(measurement, customer)
            when (format) {
                FORMAT_IMAGE -> measurementSharer.shareAsImage(data)
                FORMAT_PDF -> measurementSharer.shareAsPdf(data)
                FORMAT_WHATSAPP -> {
                    val text = MeasurementShareFormatter.buildWhatsAppText(data)
                    if (customer.phone.isBlank()) {
                        // No number on file — fall back to the generic share sheet.
                        measurementSharer.shareAsText(text)
                    } else {
                        _events.send(MeasurementDetailEvent.LaunchWhatsApp(customer.phone, text))
                    }
                }
            }
            analytics.logEvent(AnalyticsEvent.MeasurementShared(format))
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // Same contract as receipt sharing: renderers throw on failure.
            _state.update {
                it.copy(errorMessage = UiText.StringResourceText(Res.string.measurement_share_error))
            }
        }
    }
}

private suspend fun buildShareData(measurement: Measurement, customer: Customer): MeasurementShareData =
    MeasurementShareFormatter.format(
        measurement = measurement,
        customerName = customer.name,
        measurementName = measurement.name.ifBlank { getString(Res.string.measurement_detail_title) },
        genderLabel = getString(
            if (measurement.gender == CustomerGender.FEMALE) Res.string.measurement_gender_women
            else Res.string.measurement_gender_men,
        ),
        unitLabel = getString(
            if (measurement.unit == MeasurementUnit.INCHES) Res.string.measurement_unit_inches
            else Res.string.measurement_unit_cm,
        ),
        unitSuffix = if (measurement.unit == MeasurementUnit.INCHES) "″" else "cm",
        dateFormatted = MeasurementShareFormatter.formatShareDate(measurement.dateTaken),
        businessName = authRepository.getCurrentUser()?.businessName?.takeIf { it.isNotBlank() },
        customFieldLabels = _state.value.customFieldLabels,
        sectionTitles = mapOf(
            "section_upper_body" to getString(Res.string.section_upper_body),
            "section_body_lengths" to getString(Res.string.section_body_lengths),
            "section_trouser" to getString(Res.string.section_trouser),
            "section_neck_shoulders" to getString(Res.string.section_neck_shoulders),
            "section_bust" to getString(Res.string.section_bust),
            "section_waist_hip" to getString(Res.string.section_waist_hip),
            "section_arms" to getString(Res.string.section_arms),
        ),
        customSectionTitle = getString(Res.string.custom_field_section_title),
    )
```
Imports to add: `org.jetbrains.compose.resources.getString`, `stitchpad.composeapp.generated.resources.Res` + the named string resources, `com.danzucker.stitchpad.core.sharing.*`, `com.danzucker.stitchpad.feature.measurement.presentation.share.MeasurementShareFormatter`, `com.danzucker.stitchpad.core.presentation.UiText`, `com.danzucker.stitchpad.core.domain.model.Customer/CustomerGender/MeasurementUnit`. NOTE: `getString(...)` (suspend, non-composable) is the same API `WhatsAppMessageBuilder` uses — safe from a ViewModel coroutine. If the `@Suppress("TooManyFunctions")`/complexity thresholds trip, extract the `sectionTitles` map into a private `suspend fun localizedSectionTitles(): Map<String, String>` — do NOT add new class-level suppressions without a comment stating the true before/after count.

Analytics event (after `MeasurementDetailViewed`):
```kotlin
    data class MeasurementShared(val format: String) : AnalyticsEvent {
        override val name = "measurement_shared"
        override val params = mapOf("format" to format)
    }
```

New string in `strings.xml` (next to `measurement_detail_*`): `<string name="measurement_share_error">Could not share measurement. Please try again.</string>`

`FakeMeasurementSharer.kt` (`MeasurementSharer` is an interface — Task 2 chose that shape precisely so this fake is possible):
```kotlin
package com.danzucker.stitchpad.core.sharing

class FakeMeasurementSharer : MeasurementSharer {
    var lastImageData: MeasurementShareData? = null
    var lastPdfData: MeasurementShareData? = null
    var lastSharedText: String? = null
    var throwOnShare: Boolean = false
    override suspend fun shareAsImage(data: MeasurementShareData) {
        if (throwOnShare) error("boom"); lastImageData = data
    }
    override suspend fun shareAsPdf(data: MeasurementShareData) {
        if (throwOnShare) error("boom"); lastPdfData = data
    }
    override suspend fun shareAsText(text: String) {
        if (throwOnShare) error("boom"); lastSharedText = text
    }
}
```

Tests to add to `MeasurementDetailViewModelTest`: add `private lateinit var measurementSharer: FakeMeasurementSharer` to the fixture, initialize it in `setUp`, and pass it as the new last ctor arg in `createViewModel` (`measurementSharer = measurementSharer`):

```kotlin
@Test
fun `share click opens sheet and image share builds data and logs analytics`() = runTest {
    measurementRepository.measurementsList = listOf(fakeMeasurement())
    customerRepository.customersList = listOf(fakeCustomer())
    val vm = createViewModel()
    vm.onAction(MeasurementDetailAction.OnShareClick)
    assertTrue(vm.state.value.showShareSheet)
    vm.onAction(MeasurementDetailAction.OnShareAsImageClick)
    assertEquals(false, vm.state.value.showShareSheet)
    val data = measurementSharer.lastImageData
    assertEquals("Chidinma Eze", data?.customerName)
    assertEquals("Wedding gown", data?.measurementName)
    val event = analytics.events.filterIsInstance<AnalyticsEvent.MeasurementShared>().single()
    assertEquals("image", event.format)
}

@Test
fun `whatsapp share emits LaunchWhatsApp with customer phone and bold text`() = runTest {
    measurementRepository.measurementsList = listOf(fakeMeasurement())
    customerRepository.customersList = listOf(fakeCustomer())
    val vm = createViewModel()
    vm.events.test {
        vm.onAction(MeasurementDetailAction.OnShareWhatsAppClick)
        val event = assertIs<MeasurementDetailEvent.LaunchWhatsApp>(awaitItem())
        assertEquals("0705 991 2340", event.phone)
        assertTrue(event.message.contains("*Chidinma Eze — Wedding gown*"))
    }
    assertEquals("whatsapp_text", analytics.events.filterIsInstance<AnalyticsEvent.MeasurementShared>().single().format)
}

@Test
fun `whatsapp share with blank phone falls back to text share`() = runTest {
    measurementRepository.measurementsList = listOf(fakeMeasurement())
    customerRepository.customersList = listOf(fakeCustomer().copy(phone = ""))
    val vm = createViewModel()
    vm.events.test {
        vm.onAction(MeasurementDetailAction.OnShareWhatsAppClick)
        expectNoEvents()
    }
    assertTrue(measurementSharer.lastSharedText.orEmpty().contains("*Chidinma Eze — Wedding gown*"))
}

@Test
fun `share failure surfaces error message`() = runTest {
    measurementRepository.measurementsList = listOf(fakeMeasurement())
    customerRepository.customersList = listOf(fakeCustomer())
    measurementSharer.throwOnShare = true
    val vm = createViewModel()
    vm.onAction(MeasurementDetailAction.OnShareAsPdfClick)
    assertNotNull(vm.state.value.errorMessage)
}

@Test
fun `share on locked customer routes to upgrade and unknown lock ignores tap`() = runTest {
    measurementRepository.measurementsList = listOf(fakeMeasurement())
    customerRepository.customersList = listOf(fakeCustomer(slotState = CustomerSlotState.LOCKED))
    val vm = createViewModel()
    vm.events.test {
        vm.onAction(MeasurementDetailAction.OnShareClick)
        assertIs<MeasurementDetailEvent.NavigateToUpgrade>(awaitItem())
    }
    assertEquals(false, vm.state.value.showShareSheet)
}
```
(`getString` in commonTest: these tests call `buildShareData` → `getString(...)`. If resource loading fails on the JVM test source set, the existing `WhatsAppMessageBuilder` tests are the precedent to copy; if NO such precedent exists and `getString` throws in tests, refactor `buildShareData` to accept a `ShareLabels` value object resolved in a tiny `suspend fun resolveShareLabels()` and have tests drive `shareMeasurement` with a test override — but FIRST check `grep -rn "getString" composeApp/src/commonTest` for precedent. Report which path you took.)

TDD steps: write the failing tests (Step 1), run (Step 2, expect unresolved references), implement (Step 3), run the detail + form suites (Step 4: `./gradlew :composeApp:testDebugUnitTest --tests "*MeasurementDetail*" --tests "*MeasurementShareFormatter*"` → PASS), commit (Step 5):

```bash
git add -A composeApp/src
git commit -m "feat(measurement): share actions, WhatsApp event, measurement_shared analytics"
```

---

### Task 4: Share UI — bottom-bar button + share sheet + Root wiring

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/detail/MeasurementDetailScreen.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/detail/ShareMeasurementSheet.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`

**Interfaces:**
- Consumes: Task 3 actions/events/state, existing strings `share_as_image_title`, `share_as_image_description`, `share_as_pdf_title`, `share_as_pdf_description`, `WhatsAppLauncher` (koinInject in Root — copy the pattern from `OrderDetailScreen.kt:208+247` / `CustomerDetailScreen`'s Root), `whatsapp_launch_failed` string.

- [ ] **Step 1: Add strings**

Next to `measurement_share_error`:
```xml
    <string name="measurement_share_sheet_title">Share measurement</string>
    <string name="measurement_share_whatsapp_title">Send text on WhatsApp</string>
    <string name="measurement_share_whatsapp_description">Plain text — readable on any phone</string>
    <string name="cd_measurement_share">Share measurement</string>
```

- [ ] **Step 2: Bottom bar gains the square Share button** (mockup: primary Edit + square outlined Share)

In `MeasurementDetailScreen`'s `bottomBar`, wrap the existing `StitchPadButton` in a `Row` and add the share button after it:
```kotlin
bottomBar = {
    if (measurement != null) {
        Surface {
            Row(
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = DesignTokens.space4,
                        end = DesignTokens.space4,
                        top = DesignTokens.space3,
                        bottom = DesignTokens.space4,
                    ),
            ) {
                StitchPadButton(
                    text = stringResource(Res.string.measurement_detail_edit_button),
                    onClick = { onAction(MeasurementDetailAction.OnEditClick) },
                    leadingIcon = Icons.Default.Edit,
                    modifier = Modifier.weight(1f),
                )
                OutlinedIconButton(
                    onClick = { onAction(MeasurementDetailAction.OnShareClick) },
                    shape = RoundedCornerShape(DesignTokens.radiusLg),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier.size(52.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = stringResource(Res.string.cd_measurement_share),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
},
```
(If `OutlinedIconButton` is unavailable in the Material3 version, use `OutlinedButton(contentPadding = PaddingValues(0.dp), modifier = Modifier.size(52.dp), shape = RoundedCornerShape(DesignTokens.radiusLg))` with the same Icon child.)

- [ ] **Step 3: `ShareMeasurementSheet` composable** (new file — mirrors `ShareReceiptBottomSheet`'s option-card pattern, simplified)

```kotlin
package com.danzucker.stitchpad.feature.measurement.presentation.detail

// imports per MeasurementDetailScreen.kt conventions + ModalBottomSheet/rememberModalBottomSheetState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareMeasurementSheet(
    measurementName: String,
    customerName: String,
    onShareAsImage: () -> Unit,
    onShareAsPdf: () -> Unit,
    onShareWhatsApp: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DesignTokens.space4)
                .padding(bottom = DesignTokens.space6),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.space2),
        ) {
            Text(
                text = stringResource(Res.string.measurement_share_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "$measurementName — $customerName",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(DesignTokens.space2))
            ShareOptionRow(
                icon = Icons.Default.Image,
                title = stringResource(Res.string.share_as_image_title),
                description = stringResource(Res.string.share_as_image_description),
                onClick = onShareAsImage,
            )
            ShareOptionRow(
                icon = Icons.Default.PictureAsPdf,
                title = stringResource(Res.string.share_as_pdf_title),
                description = stringResource(Res.string.share_as_pdf_description),
                onClick = onShareAsPdf,
            )
            ShareOptionRow(
                icon = Icons.AutoMirrored.Filled.Chat,
                title = stringResource(Res.string.measurement_share_whatsapp_title),
                description = stringResource(Res.string.measurement_share_whatsapp_description),
                onClick = onShareWhatsApp,
            )
        }
    }
}

@Composable
private fun ShareOptionRow(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusMd),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
            modifier = Modifier.padding(DesignTokens.space3),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(DesignTokens.radiusMd),
                    ),
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
```
Add light + dark previews of the sheet CONTENT (extract a `ShareMeasurementSheetContent` private composable if `ModalBottomSheet` blocks previews — same trick as `MeasurementDetailSheet.kt`'s previews).

- [ ] **Step 4: Wire the sheet + WhatsApp event in the screen/Root**

In `MeasurementDetailScreen` (after the dialogs):
```kotlin
if (state.showShareSheet && measurement != null) {
    ShareMeasurementSheet(
        measurementName = measurement.name.ifBlank { stringResource(Res.string.measurement_detail_title) },
        customerName = state.customer?.name.orEmpty(),
        onShareAsImage = { onAction(MeasurementDetailAction.OnShareAsImageClick) },
        onShareAsPdf = { onAction(MeasurementDetailAction.OnShareAsPdfClick) },
        onShareWhatsApp = { onAction(MeasurementDetailAction.OnShareWhatsAppClick) },
        onDismiss = { onAction(MeasurementDetailAction.OnDismissShareSheet) },
    )
}
```
In `MeasurementDetailRoot`: add `val whatsAppLauncher: WhatsAppLauncher = koinInject()`, `val scope = rememberCoroutineScope()`, `val whatsAppFailed = stringResource(Res.string.whatsapp_launch_failed)`, and the event arm (copy the pattern from `CustomerDetailScreen.kt`'s Root):
```kotlin
is MeasurementDetailEvent.LaunchWhatsApp -> scope.launch {
    if (!whatsAppLauncher.launch(event.phone, event.message)) {
        snackbarHostState.showSnackbar(whatsAppFailed)
    }
}
```

- [ ] **Step 5: Compile + detekt, commit**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid detekt`
Expected: BUILD SUCCESSFUL.

```bash
git add -A composeApp/src
git commit -m "feat(measurement): share button + share sheet on detail view"
```

---

### Task 5: Full verification

- [ ] **Step 1:** `./gradlew detekt :composeApp:testDebugUnitTest` → BUILD SUCCESSFUL.
- [ ] **Step 2:** `./gradlew :composeApp:compileKotlinIosSimulatorArm64` → BUILD SUCCESSFUL (renderers are iOS-heavy — this gate matters).
- [ ] **Step 3:** `./gradlew :composeApp:assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 4:** Report done with QA smoke steps (do NOT push):
  1. Detail view → Share (square button) → sheet shows Image / PDF / WhatsApp options (light + dark).
  2. Share as Image → chooser opens with a paper-light branded card: wordmark, MEASUREMENT CARD, customer name, meta line, sectioned values with unit suffix, notes, footer. Send to yourself on WhatsApp and eyeball it.
  3. Share as PDF → chooser opens; open the PDF — same card, prints on one page for a typical measurement.
  4. Send text on WhatsApp → WhatsApp opens a chat with the customer's number, message shows bold name/sections and the footer.
  5. Customer with no phone → WhatsApp option falls back to the generic share sheet with the text.
  6. Locked customer → Share routes to Upgrade. iPhone: repeat 2–4 (450ms present delay, iPad popover anchor).
  7. Legacy measurement with no date → card and text omit the date cleanly.

## Out of scope

Dashboard quick-access entry point and CustomerActionsSheet "View measurements" row (PR 3); receipt-renderer refactors (unit-suffix/date helper consolidation noted in PR 1's review can ride PR 3 or a cleanup PR).

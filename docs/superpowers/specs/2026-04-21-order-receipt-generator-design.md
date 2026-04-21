# Order Receipt Generator — Design Spec

## Context

StitchPad's Nigerian tailor users need to share professional order receipts with customers, primarily via WhatsApp. The current implementation has divergent quality across platforms — Android generates a bitmap PNG with hardcoded colors, iOS produces plain text with emoji. This feature rebuilds receipt generation with a consistent, professional design on both platforms, adds PDF support, and aligns with the StitchPad brand.

## Requirements

### Share Flow
- Tapping "Share" on the order detail screen opens a **bottom sheet** with two options:
  - **Share as Image (PNG)** — dark theme, optimized for WhatsApp/social sharing
  - **Share as PDF** — light theme, optimized for printing and email
- Both options use the platform share sheet (Android: `Intent.ACTION_SEND`, iOS: `UIActivityViewController`)

### Receipt Content (Standard)
Each receipt displays:
1. **Header**: Business name + phone number (from `User.businessName` and `User.phoneNumber`)
2. **Customer & Date**: Customer name + order creation date
3. **Items**: `OrderItem` entries grouped by garment type — shows "quantity × garment name" with combined price right-aligned. Ungrouped items (unique garment types) show as "1 × garment name"
4. **Payment Summary**:
   - Total (saffron accent)
   - Deposit paid (green)
   - Balance: shows **"₦X DUE"** badge (saffron background) when balance > 0, or **"PAID IN FULL"** badge (green background) when balance = 0
5. **Status & Deadline**: Current order status (color-coded) + deadline date
6. **Priority Badge**: Only shown for URGENT or RUSH orders — red badge next to deadline
7. **Footer**: Truncated order ID (e.g., "Order #ORD-7A3F")

### Dual Theme
- **Dark theme** for image (PNG) sharing: `#121110` background, `#E8A800` saffron header band, `#E5E3DF` body text
- **Light theme** for PDF: `#FFFFFF` background, saffron accent border on header, `#1E1C1A` body text
- Theme selection is automatic based on chosen format — not user-configurable

### Branding
- Business name and phone only (no email, no address)
- Scissors emoji (✂️) prefix on business name
- Phone emoji (📞) prefix on phone number

## Architecture

### Common Layer (shared layout model)
A `ReceiptData` model in common code that holds all the pre-resolved, display-ready values for a receipt. This decouples the "what to show" from "how to render it."

```
core/sharing/
  ReceiptData.kt          — Data model with all display-ready receipt values
  ReceiptFormatter.kt     — Builds ReceiptData from Order + User (resolves garment names, formats prices, etc.)
  OrderReceiptSharer.kt   — Existing expect class, updated to add shareReceiptAsImage() and shareReceiptAsPdf()
```

**ReceiptData fields:**
- `businessName: String` (with ✂️ prefix)
- `businessPhone: String` (with 📞 prefix)
- `customerName: String`
- `dateFormatted: String` (e.g., "21 Apr 2026")
- `items: List<ReceiptItem>` (quantity, garment name, formatted price)
- `totalFormatted: String`
- `depositFormatted: String`
- `balanceFormatted: String`
- `isFullyPaid: Boolean`
- `statusLabel: String`
- `statusColorHex: String`
- `deadlineFormatted: String?`
- `priorityLabel: String?` (null for NORMAL, "URGENT" or "RUSH" otherwise)
- `orderIdShort: String` (e.g., "ORD-7A3F")

### Platform Layer

**Android** (`androidMain/core/sharing/`):
- `OrderReceiptSharer.android.kt` — Updated actual class
  - `shareReceiptAsImage(receiptData)`: Renders dark-theme receipt to Canvas → Bitmap → PNG → FileProvider → share intent
  - `shareReceiptAsPdf(receiptData)`: Renders light-theme receipt using `android.graphics.pdf.PdfDocument` → FileProvider → share intent
- Reuses existing cache/pruning logic for generated files

**iOS** (`iosMain/core/sharing/`):
- `OrderReceiptSharer.ios.kt` — Updated actual class
  - `shareReceiptAsImage(receiptData)`: Renders dark-theme receipt using `UIGraphicsImageRenderer` → PNG data → temp file → `UIActivityViewController`
  - `shareReceiptAsPdf(receiptData)`: Renders light-theme receipt using `UIGraphicsPDFRenderer` → PDF data → temp file → `UIActivityViewController`

### Presentation Layer Changes

**OrderDetailViewModel** — Updated to:
- Fetch current `User` to populate branding fields
- Build `ReceiptData` via `ReceiptFormatter`
- Handle new actions: `OnShareAsImageClick`, `OnShareAsPdfClick` (replacing single `OnShareClick`)

**OrderDetailScreen** — Updated to:
- Replace direct share button with share button that triggers a bottom sheet
- Bottom sheet with two options: "Share as Image" / "Share as PDF"
- New `ShareReceiptBottomSheet` composable

**OrderDetailState** — Add:
- `showShareSheet: Boolean`
- `user: User?` (for branding)

**OrderDetailAction** — Replace `OnShareClick` with:
- `OnShareClick` (opens bottom sheet)
- `OnShareAsImageClick` (triggers image generation)
- `OnShareAsPdfClick` (triggers PDF generation)
- `OnDismissShareSheet`

### String Resources
New strings needed:
- `share_receipt_title` — "Share Receipt"
- `share_as_image_title` — "Share as Image"
- `share_as_image_description` — "Best for WhatsApp & social media"
- `share_as_pdf_title` — "Share as PDF"
- `share_as_pdf_description` — "Best for printing & email"
- `receipt_balance_due` — "DUE"
- `receipt_paid_in_full` — "PAID IN FULL"
- `receipt_order_id_prefix` — "Order #"

### DI Changes
- `ReceiptFormatter` added to sharing/core Koin module (no platform dependencies, pure common code)
- `UserRepository` injected into `OrderDetailViewModel` (may already be available)

## Design Tokens Used

### Dark Theme (Image)
| Element | Color | Token |
|---------|-------|-------|
| Background | #121110 | neutral900 |
| Header bg | #E8A800 | primary500 |
| Header text | #121110 | neutral900 |
| Body text | #E5E3DF | neutral200 |
| Labels | #7D7970 | neutral500 |
| Dividers | #3A3731 | neutral700 |
| Total price | #E8A800 | primary500 |
| Deposit | #2D9E6B | statusReady |
| Balance due bg | rgba(232,168,0,0.12) | primary500 @ 12% |
| Rush badge | #D93B3B | statusOverdue |

### Light Theme (PDF)
| Element | Color | Token |
|---------|-------|-------|
| Background | #FFFFFF | neutral0 |
| Header border | #E8A800 | primary500 |
| Body text | #1E1C1A | neutral800 |
| Labels | #7D7970 | neutral500 |
| Dividers | #E8E6E3 | neutral200 |
| Total price | #C48E00 | primary600 |
| Deposit | #2D9E6B | statusReady |
| Balance due bg | rgba(196,142,0,0.1) | primary600 @ 10% |
| Rush badge | #D93B3B | statusOverdue |

### Status Colors
Use existing `DesignTokens` status colors for the status label.

## Receipt Dimensions
- **Image (PNG)**: 800px wide, height dynamic based on content
- **PDF**: A5 page size (148 × 210 mm) — compact, suitable for receipt printing

## Error Handling
- If `User.businessName` is null, fall back to "StitchPad" as business name
- If `User.phoneNumber` is null, omit phone line from header
- If deadline is null, omit deadline section
- File generation errors → show snackbar error message
- Share intent failures → show snackbar error message

## Testing
- Unit test `ReceiptFormatter`: verify all field mappings, edge cases (no deadline, fully paid, rush priority, null business name)
- Manual smoke tests:
  1. Share receipt as image from order detail → verify dark theme PNG appears in share sheet
  2. Share receipt as PDF from order detail → verify light theme PDF opens correctly
  3. Verify "PAID IN FULL" badge when balance = 0
  4. Verify "DUE" badge when balance > 0
  5. Verify RUSH/URGENT badge appears, NORMAL has no badge
  6. Verify receipt with no deadline omits deadline section
  7. Verify receipt when business name is not set falls back to "StitchPad"
  8. Test on both Android and iOS

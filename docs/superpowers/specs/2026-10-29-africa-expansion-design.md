# StitchPad Africa expansion: wave 1 (English-speaking + French-speaking Africa)

## Context
StitchPad only works for Nigerian tailors. Daniel wants tailors in other African countries to be able to use it properly: local currency, phone numbers, time zones and French. A code audit found **no country or currency concept anywhere**:
- About 90 places hardcode ₦ and kobo.
- Phone handling is `+234` only (`PhoneNormaliser.kt`).
- Every server job runs on `Africa/Lagos` time.
- Billing accepts NGN only (`paystackBilling.ts`).
- Bank details accept Nigerian NUBAN account numbers only.
- English is the only language.

**Decisions Daniel made:**
- **Wave 1 countries:**
  - **English:** Ghana and Kenya.
  - **French-speaking:**
    - West African CFA franc (XOF): Côte d'Ivoire, Senegal, Benin, Togo, Mali, Burkina Faso and Niger.
    - Central African CFA franc (XAF): Cameroon (English and French), Gabon and Congo-Brazzaville.
    - Own currencies: Guinea (GNF) and DR Congo (CDF).
  - Because store billing handles local currency, adding a French-speaking country costs only an entry in the country table, plus its store availability and data-protection filing.
- **StitchPad is strictly paid everywhere.** Every new signup in every country, including Nigeria, gets a **30-day free trial**. After that they must pay.
- **When the trial ends without payment, the app goes read-only.** Tailors can still view their customers, measurements and orders, but can't add or edit anything.
- **Payment outside Nigeria:** Android pays through Google Play Billing and iOS through Apple, which already exists. Both charge in local currency, so no business registration is needed abroad.
- **Prices outside Nigeria match Nigeria's in USD:** Pro about $1.30/month and Atelier about $2.60/month. Annual plans follow the same ratio as in Nigeria. Each store rounds to its nearest local price.
- Current free launch grants stop for new signups. **Existing grant holders keep theirs until the end date**, and then the same read-only rule applies.
- The store listings in each new country open **only after** the app is adapted for it.

**Out of scope:** Swahili; South Africa; the marketplace idea; the referral and marketer programme (stays Nigeria-only); moving Nigerian Android users off Paystack (see Known risk).

## Approach

### 1. Country foundation (domain + data)
- Add a `Country` registry in `core/domain/model/` for NG, GH and KE, plus the French-speaking set: CI, SN, BJ, TG, ML, BF, NE, CM, GA, CG, GN and CD. Adding a country later is just a new table entry. Each entry holds:
  - ISO code, dial code and trunk prefix ("0")
  - national number length or pattern
  - currency code, symbol and number of decimals (XOF has 0)
  - IANA time zone and default language
  - `androidBilling`: `PAYSTACK` for NG, `PLAY` for the others (iOS always uses Apple)
- Add `country`, `currency`, `timeZone` and `locale` to `User` / `UserDto` / mapper. **Missing values fall back to NG**, so the ~146 existing users need no data migration.
- **Order money gets a `currency` field.** It is stamped when the order is created and falls back to NGN in `OrderMapper`, so changing a user's country later never re-labels old amounts.
- Allow the new fields in `firestore.rules` with type checks. Changing country after setup is allowed, because only new orders pick up the new currency.

### 2. Onboarding
- Add a country picker to `WorkshopSetupScreen.kt`, pre-selected from the device region. It replaces the fixed `+234` chip and flag at lines 277-302.
- Allow changing the country later in profile settings, which also covers `edit_profile_phone_prefix`.

### 3. Money display
- Replace `core/sharing/PriceFormatter.kt` and `DashboardFormat.formatNaira()` with a single `formatMoney(amount, currency)` that respects the currency's decimals.
- Rename `ui/components/NairaAmountField.kt` to `MoneyAmountField(currency)`. Field prefixes (`RecordPaymentDialogV2`, `CostsEditorSheet`) take the symbol from the currency.
- Remove the ~29 ₦ literals in `strings.xml` and use `%s` placeholders for the formatted amount, including the WhatsApp reminder templates. Then sweep the ~63 `"₦${…}"` call sites (receipts, order payment card, order form, reports, goals).

### 4. Phone and WhatsApp
- Make `PhoneNormaliser` country-aware: `normalise(raw, country)` and `isValid(raw, country)`, driven by the registry table instead of the `234[789][01]` regex.
- `buildWhatsAppUrl` uses the customer's country. **For now that is the tailor's country** (my call), because customers normally live where the tailor works.
- Update all 21 call sites and the phone and WhatsApp error strings.
- **Server:** `whatsapp/accountLinking.ts` builds number variants for the 5 dial codes.

### 5. Time zones (Cloud Functions)
- Replace `notifications/lagosTime.ts` with `userLocalTime(tz)`, built on `Intl.DateTimeFormat`.
- **The daily digest and engagement push get one schedule per UTC offset** (my call). Each run handles only the users in its zone, and users without a time zone count as Lagos.
  - UTC+0 (`Africa/Abidjan`): GH, CI, SN, TG, ML, BF and GN.
  - UTC+1 (`Africa/Lagos`): NG, BJ, NE, CM, GA, CG and western DR Congo (Kinshasa).
  - UTC+2: eastern DR Congo (Lubumbashi).
  - UTC+3 (`Africa/Nairobi`): KE.
  - The country picker asks Kinshasa or Lubumbashi only for DR Congo.
- The Smart quota rollover (`smart/freeTierCounter.ts`) and the date in draft messages use the user's time zone.
- The client's entitlement day count (`UserDocEntitlementsProvider.kt:95`) reads `user.timeZone`.
- The referral jobs stay on Lagos time, since that programme is Nigeria-only.

### 6a. 30-day trial and read-only lock (all countries)
- **Trial grant at signup.** `grantLaunchFreeOnSignup` becomes the trial grant for every new signup: top tier, `grantSource: 'trial'`, `subscriptionEndsAt` = signup + 30 days.
  - Retire the `config/app.launchFreeGrantEnabled` path.
  - Existing `launch_free` grants are left untouched.
- **The trial is run by the app, not the stores** (my call). No card is needed to start, and the same rule works for Paystack, Play and Apple.
- **Read-only after expiry.** In `core/domain/entitlement/EntitlementsCalculator.kt` / `UserEntitlements.kt`, an expired trial, grant or subscription with no active paid plan resolves to a new `readOnly` state.
  - The client blocks every create and edit entry point and routes it to the paywall: new customer, measurement or order; payments; editing records.
  - Viewing stays allowed.
  - The paywall shows days left during the trial and a clear "Your trial has ended" state afterwards.
- **Server enforcement.** `firestore.rules` denies creates and updates on customer, measurement and order documents when the user doc is read-only, so the lock can't be bypassed.
  - The server writes a derived `accessState` field on the user doc, set by `expirePrepaidSubscriptions`.
  - The rules check that field instead of recomputing dates.
- **Notices.** The existing renewal-reminder job (`billing/subscriptionReminder.ts`) also sends "trial ends in 3 days" and "trial ended" in English or French.

### 6b. Google Play Billing (Android, outside Nigeria)
- **Android client.**
  - Add Play Billing Library to `androidMain`, behind the existing payment repository interface. Today that interface is `CloudFunctionsPaymentRepository`; add a `PlayBillingPaymentRepository`.
  - The repository is chosen by `country.androidBilling`: NG keeps Paystack, everyone else uses Play.
  - Prices shown come from Play's localized product details, the same way iOS already uses StoreKit's prices (`UpgradeState.kt:15-18`).
- **Products.** Create 4 Play subscriptions mirroring the Apple product IDs in `appleBilling.ts:24-30`: Pro and Atelier, monthly and annual. Prices are at USD parity, and Daniel creates them in Play Console.
- **Server, new file `billing/playBilling.ts`** modeled on `appleBilling.ts` / `appleVerifier.ts`:
  - A callable `verifyPlayPurchase` checks the purchase token with the Play Developer API (`purchases.subscriptionsv2.get`), acknowledges it, and writes the tier and `subscriptionEndsAt` with `subscriptionSource: 'play'`.
  - A Pub/Sub handler for Play Real-time Developer Notifications updates the user on renewals, cancellations and expiries.
  - A daily `reconcilePlaySubscriptions` job, modeled on `reconcileAppleSubscriptions`, catches anything a notification missed.
- **Setup (Daniel + me):**
  - Link the Play Console to the GCP project.
  - Grant the functions service account finance access in Play Console.
  - Create the Real-time Developer Notifications Pub/Sub topic.
- `initializeSubscriptionCheckout` (Paystack) refuses non-NG users with a typed error.
- **Nigeria Paystack billing is otherwise unchanged.**

### 7. Bank details on invoices
- Make `BankDetailsValidator` country-aware. NG keeps the 10-digit NUBAN check. Other countries get a free-form account number plus an optional mobile-money number (MoMo / M-Pesa Till).
- Receipt text uses whichever of these is set.

### 8. French
- Add `composeResources/values-fr/strings.xml`, a full translation of the 2,007 lines. It needs review by a French-speaking tailor, with correct garment terms ("tour de poitrine", boubou, pagne).
- The app follows the device language (my call). An in-app language switch is deferred.
- Move the ~23 leftover hardcoded `Text("…")` strings into resources. Replace the hardcoded English month names (`CustomDatePickerDialog.kt:458`, `PlanCard.kt:803`, `CustomerDetailScreen.kt:960`) with resource-based month names.
- **Server templates** (digest email `digestEmailTemplate.ts`, push `pushSummary.ts`, `orderCollectNotify.ts`, auth emails, subscription reminders) choose English or French from `user.locale` and format money with `user.currency`.
- **AI drafts:** add French to `DraftLanguage` and `smart/types.ts`. `smart/promptBuilder.ts` gets the country and currency as parameters and drops the hardcoded "Nigerian tailor" and ₦. Pidgin stays offered for NG only.

### 9. Garment types (my call, small)
- Add 4 regional garments: KABA_AND_SLIT (Ghana), KITENGE (Kenya), BOUBOU and PAGNE (West Africa).
- Show all garments to everyone, sorted with the user's own region first.

### 10. Analytics and copy
- Set a `country` user property in GA4.
- Update `dailyOnboardingMetrics.ts` so it no longer assumes Nigeria.
- Replace "Nigerian tailors" with "African tailors" in `strings.xml:1456` and on the website (`web/public/index.html`).

### 12. Market parity (from competitor research, 2026-10-29)
**What the research found:**
- No app dominates any target market. The real competitor is the paper notebook plus WhatsApp.
- Francophone apps are small (1K–10K installs) and often buggy: OTP and country-code sign-up failures, broken reminders, apps not updated since 2024.
- StitchPad's edge: the WhatsApp bot, AI drafting, profit tracking, staff roles, branded receipts, reliable sign-up (email or Google, no SMS code), and steady updates.

**What local users expect that StitchPad lacks. Wave 1 includes:**
- **Mobile-money payments.** Add `MOBILE_MONEY` to `PaymentMethod` (`core/domain/model/Payment.kt`, today cash, transfer, POS and other), with a provider chosen from the country's list:
  - MTN MoMo, Orange Money, Wave and Moov in West Africa
  - M-Pesa and Airtel Money in Kenya
  - MTN and Telecel in Ghana
- **Fitting appointments.** Add a fitting date and time on the order (the FITTING status already exists). The tailor gets a reminder the day before, and a one-tap WhatsApp message tells the customer. This is the core habit in francophone apps.
- **Unpaid-balances list ("impayés").** One view of every customer who owes money, with a one-tap WhatsApp reminder each. It builds on existing balances and reminder templates.
- **Clear trial terms.** Pricing and the 30-day rule show up front on the welcome screen. The research found reviews punishing an undisclosed paywall that locked tailors out of their data. Read-only access after the trial (6a) already avoids the lock-out.

**Deferred to a later release** (ranked):
1. Fabric and supplies inventory.
2. PDF invoices and quotes (devis).
3. Photographing a paper notebook page into a customer record.
4. Voice notes on orders.
5. Assigning tasks to apprentices at each stage.
6. SMS fallback for customers without WhatsApp.

### 11. Store launch (after code ships)
**Daniel does in the consoles:**
- **Play:** add all wave-1 countries to country availability. Add a French store listing and localized screenshots.
- **App Store:** add the same territories and a French localization.

**Non-code (Daniel):**
- Register with Ghana's Data Protection Commission.
- File a declaration with ARTCI in Côte d'Ivoire and the CDP in Senegal, including transfer authorisation for data stored in the EU.
- Register with Kenya's ODPC if the business isn't exempt.
- **Other French-speaking countries** (Benin, Togo, Mali, Burkina Faso, Niger, Cameroon, Gabon, Congo, Guinea, DR Congo) each have a data-protection authority, and many require a declaration. I'll research these per country before each one opens in the stores. Countries can open in batches.

## Delivery order (separate PRs)
1. Country model, onboarding picker, phone and WhatsApp (sections 1, 2, 4).
2. Money formatting and currency stamping (section 3).
3. 30-day trial and read-only lock, all countries (section 6a). This can ship to Nigeria first.
3b. Play Billing (section 6b) and bank details (section 7).
4. Server time zones and templates (section 5, plus the server half of 8).
5. French translation and AI drafts (section 8).
6. Market parity: mobile money, fitting appointments, unpaid-balances list, trial terms (section 12).
7. Garments, analytics and copy (sections 9, 10), then the store launch (11).

## Known risk (separate decision, not in this plan)
Google Play's Payments policy requires Play Billing for in-app digital subscriptions in Nigeria, Ghana and Kenya. **StitchPad's Android Paystack flow in Nigeria probably violates it.** Once 6b exists, moving Nigeria to Play is a one-line change to `androidBilling`, plus a decision about the 15% store fee. That decision is Daniel's, later.

## Verification
- **Unit tests** (JUnit5 + AssertK, as in the existing suites):
  - `formatMoney` for NGN, GHS, KES and XOF (XOF has no decimals).
  - `PhoneNormaliser` with valid and invalid numbers for each country, both with and without a leading 0.
  - Mapper fallbacks: an old user becomes NG, an old order becomes NGN.
  - `BankDetailsValidator` for each country.
- **Functions tests:**
  - The digest only picks up users in its time zone.
  - A new signup gets a 30-day `trial` grant.
  - An expired trial sets `accessState` to read-only.
  - The rules emulator denies an order create for a read-only user and allows reads.
  - `verifyPlayPurchase` and the notification handler are tested against a stubbed Play API.
  - Paystack checkout refuses non-NG users.
  - Templates render in French.
- **Build and test:** `./gradlew :composeApp:allTests`, `./gradlew detekt`, and `npm test` in `functions/`.
- **Emulator run:**
  - Device set to fr-SN: sign up as a Senegal tailor, add a customer with a `77…` number, create an order in XOF, send the WhatsApp reminder, and check the receipt is in French with no ₦.
  - Repeat as a Ghana tailor in English, then as an existing NG account to confirm nothing changed.
- **Market parity checks:**
  - Record an Orange Money payment as a Côte d'Ivoire tailor.
  - Set a fitting for tomorrow and confirm the reminder fires.
  - The unpaid-balances list matches the orders that have balances.
- **Trial and Play Billing checks:**
  - Move a test account's trial end date into the past and confirm read-only: lists open, "New order" opens the paywall.
  - Buy a subscription with a Play test account on an internal-track build. The account unlocks, and a cancel in the test environment expires it.
- **On a test account:** `debugSendMyDigest` and `debugSendMyEngagementPush` produce French, local-currency output.

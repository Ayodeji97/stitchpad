# Africa Expansion PR 1: Country Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give every tailor a country (and its currency and time zone), let them pick it in onboarding and settings, and make phone and WhatsApp handling work for all 15 wave-1 countries instead of only +234.

**Architecture:**
- A static `Country` / `Currency` enum registry in `core/domain/model`.
- New country fields on `User`. Any missing field falls back to Nigeria in the mapper, so existing users need no migration.
- A `CurrentCountryProvider` session service exposes the signed-in tailor's country to the phone and WhatsApp code.
- Orders store their currency in the money doc, stamped when the order is created.

**Tech Stack:**
- Kotlin Multiplatform with Compose Multiplatform
- GitLive Firestore
- Koin
- kotlin.test (commonTest)
- Firestore rules tests in `functions/src/__tests__/firestore.rules.test.ts`
- TypeScript Cloud Functions, tested with Jest

**Spec:** `docs/superpowers/specs/2026-10-29-africa-expansion-design.md`, sections 1, 2 and 4.

**Execution order:** 1, 2, 3, 5, 4, 6, 7, 8. Task 4 needs the provider from Task 5.

## Global Constraints
- **Wave-1 countries:** NG, GH, KE, CI, SN, BJ, TG, ML, BF, NE, CM, GA, CG, GN, CD.
- **Missing values fall back to NG:** `country` → NG, `currency` → NGN, `timeZone` → `Africa/Lagos`. This applies to any user doc or order money doc without the field.
- **Order money is stamped with its currency at create time.** Changing country later never re-labels old orders.
- **Customer WhatsApp numbers use the tailor's country** (spec section 4, "my call").
- **Store E.164 with a leading `+`** for the tailor's own WhatsApp number, as today.
- **No hardcoded user-facing strings.** Use compose.resources, and every Screen keeps a `@Preview` (CLAUDE.md).
- **All state lives in the ViewModel.** Follow the MVI State/Action/Event + Root/Screen split.
- **Out of scope for this PR:**
  - Money display/formatting (PR 2)
  - trial, read-only lock and billing (PR 3)
  - server time zones (PR 4)
  - French strings (PR 5)

## Review Focus
1. **Pasting a number that already has its country code.** A Ghana tailor pastes `+233 24 412 3456`, or `233244123456`, into a field with an implied +233 chip. It must not become `233233…`. Covered in Task 2.
2. **A customer number from another country.** A Kenya tailor saves a Nigerian customer as `+234 803 123 4567`. The WhatsApp link must go to 234…, not 254…. Covered in Task 2.
3. **Countries whose leading 0 is part of the number.** Gabon `06 12 34 56` and Congo `06 123 4567` must keep the 0, not drop it as a trunk prefix. Covered in Task 2.
4. **A Firestore doc with an unknown or garbage country.** A doc with `country: "ZZ"`, or a lowercase `"gh"`, must resolve: an unknown code falls back to NG and lowercase is accepted. It must not crash. Covered in Task 3.
5. **Staff.** A staff member's own user doc has no country, so their WhatsApp links fall back to NG even when the workshop is in Ghana. This is a **known limitation of this PR**, written down in Task 5 and fixed in PR 2 when workshop currency is introduced. Task 5 has a test documenting the fallback.

---

### Task 1: Country and Currency registry

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/Country.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/Currency.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/domain/model/CountryTest.kt`

**Interfaces:**
- Produces:
  - `enum class Currency(val code: String, val symbol: String, val decimals: Int)` with `companion fun fromCode(code: String?): Currency?` (case-insensitive).
  - `enum class AndroidBilling { PAYSTACK, PLAY }`.
  - `enum class Country(val iso: String, val dialCode: String, val trunkPrefix: String?, val nationalLength: Int, val mobilePattern: Regex, val currency: Currency, val timeZones: List<String>, val defaultLanguage: String, val androidBilling: AndroidBilling)`.
  - `val Country.defaultTimeZone: String get() = timeZones.first()`.
  - `companion fun fromIso(iso: String?): Country?` (case-insensitive) and `val DEFAULT = NG`.

- [ ] **Step 1: Write failing tests** in `CountryTest`:
  - `every country has a unique iso and dial code`
  - `fromIso is case insensitive and rejects unknown`: `assertEquals(Country.GH, Country.fromIso("gh"))`, `assertNull(Country.fromIso("ZZ"))`, `assertNull(Country.fromIso(null))`
  - `nigeria keeps paystack and everyone else uses play`: `assertEquals(AndroidBilling.PAYSTACK, Country.NG.androidBilling)`, and all others are `PLAY`
  - `currencies`: NG→NGN (2 decimals), GH→GHS, KE→KES, the XOF set {CI, SN, BJ, TG, ML, BF, NE} → XOF (0 decimals), the XAF set {CM, GA, CG} → XAF (0), GN→GNF (0), CD→CDF (2)
  - `languages`: NG, GH and KE are `"en"`; all others are `"fr"`
  - `time zones`: NG `Africa/Lagos`, GH `Africa/Accra`, KE `Africa/Nairobi`, CD has `["Africa/Kinshasa", "Africa/Lubumbashi"]`, and every other country has exactly one zone
  - `mobile patterns match a sample number`: one valid national number per country from the table below, checked against `mobilePattern.matches(…)` and `nationalLength`

- [ ] **Step 2: Run and see them fail.**
  Run `./gradlew :composeApp:testDebugUnitTest --tests "*CountryTest*"`.
  Expected: compilation failure because `Country` is unresolved.

- [ ] **Step 3: Implement the two enums with these values.** The trunk prefix and mobile patterns below are a starting point. **Check each row against Google libphonenumber's `PhoneNumberMetadata.xml`** (mobile `nationalNumberPattern`) before committing. Note that Benin moved to 10 digits with a `01` prefix in Nov 2024, and Côte d'Ivoire moved to 10 digits in 2021.

| ISO | dial | trunk | len | mobile pattern | currency | zones | sample |
|---|---|---|---|---|---|---|---|
| NG | 234 | 0 | 10 | `[789][01]\d{8}` | NGN ₦ 2 | Africa/Lagos | 8031234567 |
| GH | 233 | 0 | 9 | `[25]\d{8}` | GHS GH₵ 2 | Africa/Accra | 244123456 |
| KE | 254 | 0 | 9 | `(7\d|1[01])\d{7}` | KES KSh 2 | Africa/Nairobi | 712345678 |
| CI | 225 | – | 10 | `0[157]\d{8}` | XOF F CFA 0 | Africa/Abidjan | 0701234567 |
| SN | 221 | – | 9 | `7[05-8]\d{7}` | XOF | Africa/Dakar | 771234567 |
| BJ | 229 | – | 10 | `01\d{8}` | XOF | Africa/Porto-Novo | 0190123456 |
| TG | 228 | – | 8 | `[79]\d{7}` | XOF | Africa/Lome | 90123456 |
| ML | 223 | – | 8 | `[5-9]\d{7}` | XOF | Africa/Bamako | 76123456 |
| BF | 226 | – | 8 | `[5-7]\d{7}` | XOF | Africa/Ouagadougou | 70123456 |
| NE | 227 | – | 8 | `[89]\d{7}` | XOF | Africa/Niamey | 93123456 |
| CM | 237 | – | 9 | `6\d{8}` | XAF FCFA 0 | Africa/Douala | 671234567 |
| GA | 241 | – | 8 | `0[2-7]\d{6}` | XAF | Africa/Libreville | 06123456 |
| CG | 242 | – | 9 | `0[456]\d{7}` | XAF | Africa/Brazzaville | 061234567 |
| GN | 224 | – | 9 | `6\d{8}` | GNF FG 0 | Africa/Conakry | 621234567 |
| CD | 243 | 0 | 9 | `[89]\d{8}` | CDF FC 2 | Africa/Kinshasa, Africa/Lubumbashi | 812345678 |

  Keep the patterns as national-number regexes, without the dial code, anchored with `Regex("^…$")`.

- [ ] **Step 4: Run the tests.** Same command; expected PASS.

- [ ] **Step 5: Commit:** `feat(country): Country and Currency registry for wave-1 markets`

---

### Task 2: Country-aware phone normalisation and WhatsApp URLs

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/sharing/PhoneNormaliser.kt` (replace the three Nigerian functions)
- Modify: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/sharing/PhoneNormaliserTest.kt` (port the existing NG cases to the new API, then add the cases below)
- Modify call sites:
  - `feature/onboarding/presentation/workshop/WorkshopSetupViewModel.kt:284-301,384`
  - `feature/settings/presentation/editprofile/EditProfileViewModel.kt:293-299,386-387,426`
  - `feature/settings/presentation/foundersnote/FoundersNoteRoot.kt:35`
  - `feature/settings/presentation/home/SettingsEventEffect.kt:72`
  - `feature/foundingtailors/presentation/FoundingTailorsRoot.kt:36`
  - `feature/gift/presentation/sharelink/ShareGiftLinkRoot.kt:45`
  - `androidMain/.../core/sharing/WhatsAppLauncher.android.kt` and the iOS actual

**Interfaces:**
- Consumes: `Country` from Task 1.
- Produces these, which replace `normaliseNigerianPhone`, `applyImpliedNigerianCountryCode` and `validateNigerianMobileE164`; delete the old names:
  - `fun normalisePhone(raw: String, country: Country): String` returns digits only, with the country code.
  - `fun applyImpliedCountryCode(raw: String, country: Country): String`
  - `fun isValidMobile(raw: String, country: Country): Boolean`
  - `fun buildWhatsAppUrl(phone: String, message: String, country: Country): String`

**Rules for `normalisePhone`, applied in this order:**
1. Strip everything except digits. If the raw input started with `+`, or with `00` followed by a dial code of any registry country, return the digits without the `00`. The country code is already explicit.
2. If the digits start with `country.dialCode` and have length `dialCode.length + nationalLength`, return them as they are.
3. If `country.trunkPrefix != null`, the digits start with it, and their length is `nationalLength + 1`, return `dialCode + digits.drop(1)`.
4. Otherwise return the digits unchanged. Today's rule of never guessing a country for bare numbers stays.

**Rule for `applyImpliedCountryCode`** (used only behind a visible chip): if the digits have exactly `nationalLength` and don't already start with `dialCode` as a full-length number, prefix `dialCode`.

**Rule for `isValidMobile`:** `normalisePhone(applyImpliedCountryCode(raw, c), c)` must start with `c.dialCode`, and the remaining national part must match `c.mobilePattern`.

- [ ] **Step 1: Write failing tests:**
  - `ghana trunk number normalises`: `assertEquals("233244123456", normalisePhone("024 412 3456", Country.GH))`
  - `pasted full number is not double prefixed`: `assertEquals("233244123456", normalisePhone(applyImpliedCountryCode("+233 24 412 3456", Country.GH), Country.GH))`, and the same for `"233244123456"`
  - `foreign customer number keeps its own code`: `assertEquals("2348031234567", normalisePhone("+234 803 123 4567", Country.KE))`
  - `gabon leading zero is part of the number`: `assertEquals("24106123456", normalisePhone(applyImpliedCountryCode("06 12 34 56", Country.GA), Country.GA))`, and `assertTrue(isValidMobile("06123456", Country.GA))`
  - `senegal implied chip`: `assertTrue(isValidMobile("77 123 45 67", Country.SN))`, `assertFalse(isValidMobile("33 123 45 67", Country.SN))` (a landline)
  - `valid sample per country`: loop over `Country.entries` with the Task 1 sample numbers; `isValidMobile` is true for each
  - `nigeria behaviour unchanged`: port every existing assertion with `Country.NG`, including the bare US `2125550123` staying untouched and invalid
  - `whatsapp url uses country`: `assertEquals("https://wa.me/254712345678?text=hi", buildWhatsAppUrl("0712 345678", "hi", Country.KE))`; a blank phone still gives `https://wa.me/?text=…`

- [ ] **Step 2: Run and see them fail.** `./gradlew :composeApp:testDebugUnitTest --tests "*PhoneNormaliserTest*"`; expected compilation failure.

- [ ] **Step 3: Implement the four functions** following the rules above. Keep the existing `urlEncode` helper as it is.

- [ ] **Step 4: Update call sites.**
  - Founders note: the support number is Nigerian, so pass `Country.NG`.
  - Founding tailors and gift share links: these use a blank phone, so pass `Country.DEFAULT`.
  - Settings `OpenWhatsApp` and both ViewModels: temporarily pass `Country.DEFAULT`. Tasks 5–7 replace that with the real country.
  - `WhatsAppLauncher` actuals: add a `country: Country` parameter to `launch(phone, message)` for now. Task 5 removes it again by injecting the provider. Pass `Country.DEFAULT` at existing callers.

- [ ] **Step 5: Run the phone tests and the full common suite.** `./gradlew :composeApp:testDebugUnitTest` and `./gradlew detekt`; expected PASS.

- [ ] **Step 6: Commit:** `feat(phone): country-aware phone normalisation and WhatsApp links`

---

### Task 3: User country fields, persistence and Firestore rules

**Files:**
- Modify: `core/domain/model/User.kt`, `core/data/dto/UserDto.kt`, `core/data/mapper/UserMapper.kt`
- Modify: `core/domain/repository/UserRepository.kt` (`createUserProfile`, `updateProfile`), `core/data/repository/FirebaseUserRepository.kt:60-75,135-165`, and every fake `UserRepository` in `commonTest`
- Modify: `firestore.rules` (the `users/{uid}` `allow create` / `allow update`, around lines 184-240)
- Test: `commonTest/.../core/data/mapper/UserMapperTest.kt` and `functions/src/__tests__/firestore.rules.test.ts`

**Interfaces:**
- Consumes: `Country`, `Currency`.
- Produces:
  - `User.country: Country = Country.DEFAULT`, `User.currency: Currency = Country.DEFAULT.currency`, `User.timeZone: String = Country.DEFAULT.defaultTimeZone`, `User.locale: String? = null`.
  - DTO fields `country: String?`, `currency: String?`, `timeZone: String?`, `locale: String?`, using the same Firestore field names.
  - `createUserProfile(…, country: Country, timeZone: String, locale: String?)` and `updateProfile(…, country: Country? = null, timeZone: String? = null)`, where null means unchanged. **The repository always writes `currency = country.currency.code` alongside `country`**, so callers can never set a mismatched pair.

- [ ] **Step 1: Write failing mapper tests:**
  - `legacy doc without country falls back to nigeria`: `UserDto(id = "u")` gives `country == NG`, `currency == NGN`, `timeZone == "Africa/Lagos"` and `locale == null`
  - `ghana doc maps`: `UserDto(country = "GH", currency = "GHS", timeZone = "Africa/Accra")` gives GH / GHS / `Africa/Accra`
  - `unknown or lowercase codes`: `country = "zz"` gives NG; `country = "gh"` gives GH; `currency = null` with `country = "GH"` gives GHS (derived from the country)
  - `missing time zone uses the country default`: `country = "KE"` with `timeZone = null` gives `Africa/Nairobi`
  - `round trip`: `user.toUserDto().toUser() == user` for a CD user with `timeZone = "Africa/Lubumbashi"`

- [ ] **Step 2: Run** `./gradlew :composeApp:testDebugUnitTest --tests "*UserMapperTest*"`; expected FAIL.

- [ ] **Step 3: Implement the fields, the mapper fallbacks (`Country.fromIso(country) ?: DEFAULT`, `Currency.fromCode(currency) ?: country.currency`, `timeZone ?: country.defaultTimeZone`) and the repository writes.** Update the fakes so they compile.

- [ ] **Step 4: Rules.** Add `function countryFieldsOk(d)`. For each of `country`, `currency`, `timeZone` and `locale` that is present, check the type and size:
  - `country`: string of size 2
  - `currency`: string of size 3
  - `timeZone`: string, 1–64 characters
  - `locale`: string, 2–16 characters

  AND it into both `allow create` and `allow update` on `users/{uid}`. Add rules tests:
  - an owner create with `country: 'GH', currency: 'GHS', timeZone: 'Africa/Accra'` is allowed
  - `country: 'GHANA'` is denied
  - `currency: 5` is denied
  - an update that changes only `country` is allowed
  - an existing doc with no country fields still updates fine

- [ ] **Step 5: Run** `./gradlew :composeApp:testDebugUnitTest` and, in `functions/`, `npm run test:rules` (or the script the repo uses for `firestore.rules.test.ts`; check `functions/package.json`). Expected PASS.

- [ ] **Step 6: Commit:** `feat(user): country, currency, timeZone and locale on the user doc`

---

### Task 4: Order currency stamped on the money doc

**Files:**
- Modify: `core/domain/model/Order.kt` (add `val currency: Currency = Currency.NGN`)
- Modify: `core/data/dto/OrderMoneyDto.kt` (add `val currency: String? = null`)
- Modify: `core/data/mapper/OrderMapper.kt:135-175` (`toOrderMoneyDto` writes `currency.code`; `withMoney` reads `Currency.fromCode(money.currency) ?: Currency.NGN`)
- Modify: the order-create path in `feature/order/presentation/form/OrderFormViewModel` (find where a new `Order` is built) to set `currency` from `CurrentCountryProvider` (Task 5). Edits keep the loaded order's currency.
- Test: `commonTest/.../core/data/mapper/OrderMapperTest.kt`

**Interfaces:**
- Consumes: `Currency`, and `CurrentCountryProvider.currentCountry()` from Task 5 for the create path only. Do the mapper half first; wire the ViewModel after Task 5 lands, still within this task's commit.
- Produces: `Order.currency`.

- [ ] **Step 1: Failing tests:**
  - `money doc without currency reads as naira`
  - `ghs round trip`: an order with `currency = GHS` gives `toOrderMoneyDto().currency == "GHS"`, and `withMoney` restores GHS
  - `editing keeps currency`: after `order.copy(totalPrice = 10.0)`, the currency is unchanged

- [ ] **Step 2:** Run `--tests "*OrderMapperTest*"`; expected FAIL.

- [ ] **Step 3: Implement.** The private/money rules don't key-allowlist the money doc (`firestore.rules:348+`). Confirm that while editing. If they do, add `'currency'`. Don't add `currency` to `orderMoneyKeys()`, because that list is the base-doc *deny* set and `currency` never goes on the base doc.

- [ ] **Step 4:** Run the full unit suite; expected PASS.

- [ ] **Step 5: Commit:** `feat(order): stamp currency on the order money doc`

---

### Task 5: CurrentCountryProvider, device region, and WhatsAppLauncher injection

**Files:**
- Create: `core/domain/session/CurrentCountryProvider.kt`, containing `interface CurrentCountryProvider { val country: StateFlow<Country>; fun currentCountry(): Country }`
- Create: `core/data/session/UserDocCurrentCountryProvider.kt`. It observes `userRepository.observeUser(uid)` for the signed-in uid, maps to `user.country`, and defaults to `Country.DEFAULT`. Model it on `core/data/entitlement/UserDocEntitlementsProvider.kt` for the auth-state wiring and scope.
- Create: `core/sharing/DeviceRegion.kt` with `expect fun deviceRegionIso(): String?`, plus actuals:
  - Android: `Locale.getDefault().country`
  - iOS: `NSLocale.currentLocale.countryCode`
- Modify: `WhatsAppLauncher` expect and actuals. Remove the temporary `country` parameter from Task 2 and inject `CurrentCountryProvider` through the constructor. Update `PlatformModule.android.kt:70` and `PlatformModule.ios.kt:93`, and register the provider with `singleOf(::UserDocCurrentCountryProvider) bind CurrentCountryProvider::class` in `di/CoreModule.kt`.
- Modify: `SettingsEventEffect.kt:72`. Carry the country in `SettingsEvent.OpenWhatsApp` from the ViewModel, which reads the provider, instead of `Country.DEFAULT`.
- Test: `commonTest/.../core/data/session/UserDocCurrentCountryProviderTest.kt`

**Interfaces:**
- Produces: `CurrentCountryProvider` (used by Tasks 4, 6 and 7) and `deviceRegionIso()` (used by Task 6).

- [ ] **Step 1: Failing tests** using a fake `UserRepository` and auth:
  - `signed out is nigeria`
  - `emits user's country`: a GH user gives `Country.GH`
  - `country change propagates`: GH then KE emits KE
  - **staff fallback (Review Focus 5):** a user doc with no country gives `Country.NG`, with a comment pointing to PR 2's workshop-currency follow-up

- [ ] **Step 2:** Run `--tests "*CurrentCountryProvider*"`; expected FAIL.

- [ ] **Step 3:** Implement and wire the DI. Build both targets: `./gradlew :composeApp:assembleDebug` and `./gradlew :composeApp:compileKotlinIosSimulatorArm64`.

- [ ] **Step 4:** Run the unit tests and detekt; expected PASS.

- [ ] **Step 5: Commit:** `feat(session): current-country provider drives WhatsApp links`

---

### Task 6: Country picker in workshop setup

**Files:**
- Modify: `feature/onboarding/presentation/workshop/WorkshopSetupState.kt`, `WorkshopSetupAction.kt`, `WorkshopSetupViewModel.kt`, `WorkshopSetupScreen.kt:277-302` (replace the fixed +234 block and the drawn flag)
- Create: `ui/components/CountryPickerSheet.kt`. A bottom sheet listing the countries: flag emoji, localized name, `+dialCode`. It is stateless and takes `selected`, `onSelect` and `onDismiss`, with a `@Preview`.
- Modify: `composeResources/values/strings.xml`. Add `country_name_<iso>` for all 15 countries, plus `country_picker_title`, `country_time_zone_title`, `time_zone_kinshasa`, `time_zone_lubumbashi`, and a generic `error_whatsapp_invalid_for_country` ("Enter a valid %1$s WhatsApp number"). Update `error_whatsapp_invalid` callers to it.
- Test: `commonTest/.../feature/onboarding/presentation/workshop/WorkshopSetupViewModelTest.kt` (extend the existing test if there is one)

**Interfaces:**
- Consumes: `deviceRegionIso()`, `Country`, `isValidMobile`, `normalisePhone`, `applyImpliedCountryCode`, and `createUserProfile(…, country, timeZone, locale)`.
- Produces:
  - State: `country: Country`, `timeZone: String`, `isCountryPickerOpen: Boolean`
  - Actions: `OnCountryChipClick`, `OnCountrySelected(country)`, `OnTimeZoneSelected(tz)`, `OnDismissCountryPicker`

- [ ] **Step 1: Failing VM tests:**
  - `preselects device region`: inject a region `"GH"` gives `state.country == GH`
  - `unknown region defaults to nigeria`: `"US"` or null gives NG
  - `selecting country resets time zone to its default and revalidates the number`: with `"244123456"` entered under NG (error shown), selecting GH clears the error
  - `dr congo exposes both zones`: selecting CD gives `timeZone == "Africa/Kinshasa"`, and `OnTimeZoneSelected("Africa/Lubumbashi")` sticks
  - `continue saves country, time zone, locale and E164`: GH with `"0244123456"` makes the fake repo receive `country = GH`, `timeZone = "Africa/Accra"`, `whatsappNumber = "+233244123456"`, and `locale` = the device language

- [ ] **Step 2:** Run the VM tests; expected FAIL. Inject `deviceRegionIso` and the device language into the ViewModel as constructor lambdas with defaults, so tests can fake them. Update the `viewModelOf` registration.

- [ ] **Step 3: Implement the state, actions and ViewModel**, then the Screen. The chip shows `flag + "+dialCode"` and opens `CountryPickerSheet`. When `country.timeZones.size > 1`, a second row below the chip picks the zone. Use design tokens from `ui/theme/DesignTokens.kt`; don't add raw hex like the old block's `0xFF2A2825`. Add or extend the Screen `@Preview` with GH selected.

- [ ] **Step 4:** Run the unit tests, detekt and `assembleDebug`; expected PASS.

- [ ] **Step 5: Commit:** `feat(onboarding): country picker replaces the fixed +234 chip`

---

### Task 7: Change country in Edit Profile

**Files:**
- Modify: `feature/settings/presentation/editprofile/EditProfileState.kt`, `EditProfileAction.kt`, `EditProfileViewModel.kt:293-299,386-387,422-430`, and `EditProfileScreen.kt`. Replace the `edit_profile_phone_prefix` "🇳🇬 +234" usage with the same chip plus `CountryPickerSheet`.
- Test: `commonTest/.../feature/settings/presentation/editprofile/EditProfileViewModelTest.kt`

**Interfaces:**
- Consumes: Task 6's `CountryPickerSheet`, `updateProfile(…, country, timeZone)`, and the phone functions.
- Produces: the same action names as Task 6 (`OnCountryChipClick`, `OnCountrySelected`, `OnTimeZoneSelected`, `OnDismissCountryPicker`), so both screens share the pattern.

- [ ] **Step 1: Failing VM tests:**
  - `loads user's country`: a GH user gives `state.country == GH`
  - `changing country revalidates whatsapp`
  - `save sends country and zone`
  - `changing the whatsapp country resets whatsappConfirmed`, matching today's "edited number → unconfirmed" rule

- [ ] **Step 2:** Run; expected FAIL.

- [ ] **Step 3: Implement.** Changing country only affects new orders (Task 4 stamps currency at create). No extra copy is needed in this PR; PR 2 adds a currency notice.

- [ ] **Step 4:** Run the unit tests and detekt; expected PASS.

- [ ] **Step 5: Commit:** `feat(settings): change workshop country from edit profile`

---

### Task 8: Server WhatsApp account linking for non-Nigerian senders

**Files:**
- Test: `functions/src/__tests__/whatsapp/accountLinking.test.ts` (extend the existing file)
- Modify: `functions/src/whatsapp/accountLinking.ts`, only if a test fails

**Interfaces:**
- Consumes: the user docs written by Task 3, which store `whatsapp` as `+<dial><national>`.

- [ ] **Step 1: Add tests:**
  - `phoneCandidates('233244123456')` includes `'+233244123456'`
  - `phoneCandidates('24106123456')` includes `'+24106123456'`
  - the existing NG and "foreign sender is exact-match only" tests still pass

  New-country users are always saved as E.164 by Tasks 6 and 7, so exact matching is enough. Don't add local-variant expansion for other countries; it would reopen the cross-country false-match the file's comment warns about.

- [ ] **Step 2:** Run `npm test -- accountLinking` in `functions/`; expected PASS with no code change. Then update the doc comment on `phoneCandidates` to say non-NG numbers rely on E.164 storage.

- [ ] **Step 3: Commit:** `test(whatsapp): account linking for non-Nigerian E.164 senders`

---

## Final verification (whole PR)
- [ ] Run `./gradlew :composeApp:allTests detekt` and `cd functions && npm test`; everything passes.
- [ ] **Android emulator:**
  - With the device region set to Ghana, sign up. The chip shows 🇬🇭 +233. Enter `024 412 3456` and finish setup.
  - The user doc has `country: "GH"`, `currency: "GHS"`, `timeZone: "Africa/Accra"` and `whatsapp: "+233244123456"`.
  - Create a customer with `024 555 1234` and send a WhatsApp reminder. The link opens `wa.me/233245551234`.
- [ ] **Existing Nigerian test account:** nothing changes. The +234 chip, reminders and orders all work, and new orders' money docs carry `currency: "NGN"`.
- [ ] **iOS simulator, region Senegal:** onboarding pre-selects 🇸🇳 +221, and `77 123 45 67` validates.

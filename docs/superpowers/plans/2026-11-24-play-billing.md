# Google Play Billing (Android, outside Nigeria) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Android users whose workshop country has `androidBilling = PLAY` (every wave-1 country except NG) subscribe through Google Play Billing, verified and kept in sync server-side exactly like Apple IAP.

**Architecture:** A commonMain `RoutingPaymentRepository` picks Paystack or Play per purchase from the workshop's loaded country. The Android `PlayBillingPaymentRepository` wraps the Play Billing Library behind a small `NativePlayBillingClient` interface and hands every purchase token to a new `verifyPlayPurchase` callable. `functions/src/billing/playBilling.ts` is modeled on `appleBilling.ts`: one pure mapper from a Play `SubscriptionPurchaseV2` to the entitlement state, one idempotent commit, a Pub/Sub RTDN handler and a daily reconcile. Every server path re-fetches the subscription from the Play Developer API, so the client and the notification payload are never trusted.

**Tech Stack:** Kotlin Multiplatform, Compose, Koin, Play Billing Library (`com.android.billingclient:billing-ktx`, latest stable 8.x), Firebase Functions v1 (TypeScript), `@googleapis/androidpublisher` (subscriptionsv2), Jest.

**Spec:** `docs/superpowers/specs/2026-10-29-africa-expansion-design.md` §6b (also the "Known risk" and "Trial and Play Billing checks" sections).

## Global Constraints

- Nigeria Android stays on Paystack. Only `Country.androidBilling == AndroidBilling.PLAY` routes to Play. iOS always uses Apple and is untouched apart from the product-id rename.
- Play product ids are identical to the Apple ids: `com.danzucker.stitchpad.pro.monthly`, `com.danzucker.stitchpad.pro.annual`, `com.danzucker.stitchpad.atelier.monthly`, `com.danzucker.stitchpad.atelier.annual`. Four Play subscriptions, one base plan each (`monthly` or `annual`). Daniel creates them in Play Console at USD parity (~$1.30 Pro / ~$2.60 Atelier monthly).
- Prices shown on the Play path come from Play's `ProductDetails` (formatted price of the base plan), never from bundled resources.
- Server writes `subscriptionSource: 'play'`, `subscriptionTier`, `subscriptionStatus`, `subscriptionRenews`, `subscriptionEndsAt`, plus `playPurchaseToken`, `playPurchaseTokenHash` and `playProductId` on `users/{uid}`. All are server-only in `firestore.rules`.
- Account binding: client sets `obfuscatedAccountId = sha256Hex(uid)`; server refuses any purchase whose `externalAccountIdentifiers.obfuscatedExternalAccountId` differs (`account_mismatch`).
- Test purchases (`testPurchase` present) never grant unless `PLAY_ALLOW_TEST_PURCHASES === 'true'` (mirror of `APPLE_ALLOW_SANDBOX`).
- Play must be acknowledged within 3 days or Google refunds. The server acknowledges after a successful commit; the client never acknowledges.
- Functions region `europe-west1`; cron time zone `Africa/Lagos`.
- Rules from CLAUDE.md: Result<T, E> (no throwing for expected failures), UiText, string resources in `values/` and `values-fr/`, Koin constructor refs, every Screen has a @Preview. No em dashes in any user-facing string.
- Server Paystack refusal for non-NG users (Task 4) deploys only after the Play client build is live in the store, or non-NG users on old builds lose their only checkout.

## Review Focus

1. **Pending (cash / carrier) purchases.** Many African Play users pay with cash codes or carrier billing, which arrive as `PENDING` and complete hours later while the app may be closed. Expected: Pending snackbar now; access arrives when the app next opens (sweep) or when RTDN fires. Test in Task 6 (`pendingPurchase_returnsPendingOutcome`) and Task 7 (`sweep_verifiesPurchasedUnacknowledged`).
2. **Tier change with an existing Play subscription.** Buying Atelier while a Pro Play sub is active must replace, not stack, the subscription, or the tailor is charged twice. Expected: `SubscriptionUpdateParams` with the old token. Test in Task 6 (`upgrade_passesOldTokenForReplacement`); server side, the old token's later `EXPIRED` must not revoke the new grant (Task 2 `staleOldTokenExpiry_doesNotRevokeNewToken`).
3. **Country not loaded yet.** A Ghana tailor opens Upgrade before the user doc arrives; `country` is `Country.DEFAULT` (NG). Expected: no checkout until `loadedCountry` is known, never a Paystack redirect. Tests in Task 5 (`routing_nullLoadedCountry_returnsProviderUnavailable`) and Task 7 (`provider_waitsForLoadedCountry`).
4. **Existing Paystack payer moves country.** An NG Paystack payer switches to GH and buys Play. Expected: the still-active Paystack entitlement is not overwritten; Play takes over when Paystack lapses (reconcile). Test in Task 1 (`foreignActive_blocksTakeover`) and Task 2 (`activePaystack_isNotOverwrittenByPlay`).
5. **RTDN for a token the server has never seen** (verify call never reached the server, app crashed). Expected: logged and acknowledged to Pub/Sub without error, no user write; the client sweep or the next verify binds it. Test in Task 3 (`rtdn_unknownToken_noWrite`).

---

### Task 1: Shared store-entitlement ownership rule

Extract the "may this store write the shared user doc" decision out of `commitAppleState` so Play reuses it. Apple behavior must not change.

**Files:**
- Create: `functions/src/billing/storeEntitlement.ts`
- Modify: `functions/src/billing/appleBilling.ts:578-656` (`commitAppleState` calls the helper)
- Test: `functions/src/__tests__/billing/storeEntitlement.test.ts`

**Interfaces:**
- Produces:
  ```ts
  export type BillingTier = 'pro' | 'atelier';           // move here; appleBilling re-exports
  export function tierRank(tier?: string | null): number; // move here
  export interface SharedEntitlement {
    subscriptionTier?: string; subscriptionStatus?: string;
    subscriptionSource?: string; subscriptionEndsAt?: { toMillis(): number };
  }
  export function shouldWriteUserDoc(args: {
    desiredTier: BillingTier | 'free';
    ownsCurrentEntitlement: boolean;
    current: SharedEntitlement;
    nowMs: number;
  }): boolean;
  ```

- [ ] **Step 1: Write the failing tests** in `storeEntitlement.test.ts`:
  - `downgrade_onlyWhenOwned`: `desiredTier:'free'` → `true` when `ownsCurrentEntitlement`, `false` otherwise.
  - `foreignActive_blocksTakeover`: not owned, `current = {subscriptionSource:'paystack', subscriptionStatus:'active', subscriptionTier:'pro', subscriptionEndsAt: now+1d}`, `desiredTier:'atelier'` → `false`.
  - `foreignExpired_allowsTakeover`: same but `subscriptionEndsAt: now-1d` → `true`.
  - `foreignNoEndDate_blocksTakeover`: active pro, no `subscriptionEndsAt` → `false` (launch grant / endless).
  - `ownedGrant_writes`: owned + `desiredTier:'pro'` → `true`.
- [ ] **Step 2: Run** `cd functions && npx jest storeEntitlement` → FAIL (module not found).
- [ ] **Step 3: Implement** `shouldWriteUserDoc` with exactly the logic now inline at `appleBilling.ts:615-641`; replace that block in `commitAppleState` with a call. Keep the comments.
- [ ] **Step 4: Run** `cd functions && npx jest storeEntitlement appleBilling` → all PASS (Apple suite unchanged).
- [ ] **Step 5: Commit** `refactor(billing): extract shared store entitlement ownership rule`

---

### Task 2: `verifyPlayPurchase` callable and the Play state mapper

**Files:**
- Create: `functions/src/billing/playBilling.ts` (pure handlers + mapper + commit)
- Create: `functions/src/billing/playApi.ts` (thin `@googleapis/androidpublisher` wrapper, auth via the functions default service account, `packageName = 'com.danzucker.stitchpad'`)
- Modify: `functions/package.json` (add `@googleapis/androidpublisher`, latest stable)
- Modify: `functions/src/index.ts` (export `verifyPlayPurchase`)
- Test: `functions/src/__tests__/billing/playBilling.test.ts` (reuse the `fakeDb` pattern from `appleBilling.test.ts:20-40`)

**Interfaces:**
- Consumes: `shouldWriteUserDoc`, `tierRank`, `BillingTier` (Task 1); `planForProduct(productId)` from `appleBilling.ts` (same ids).
- Produces:
  ```ts
  export interface PlaySubscription {            // the subscriptionsv2 fields we use
    subscriptionState: string;                   // SUBSCRIPTION_STATE_*
    acknowledgementState?: string;               // ACKNOWLEDGEMENT_STATE_PENDING | _ACKNOWLEDGED
    lineItems: { productId: string; expiryTime?: string; autoRenewingPlan?: { autoRenewEnabled?: boolean } }[];
    externalAccountIdentifiers?: { obfuscatedExternalAccountId?: string };
    linkedPurchaseToken?: string;
    testPurchase?: object;
  }
  export interface PlayApi {
    getSubscription(purchaseToken: string): Promise<PlaySubscription>;
    acknowledge(productId: string, purchaseToken: string): Promise<void>;
  }
  export function playAccountId(uid: string): string;          // sha256 hex, lowercase
  export function playTokenHash(purchaseToken: string): string; // sha256 hex, doc id for the index
  export function testPurchaseGrantsAllowed(): boolean;         // process.env.PLAY_ALLOW_TEST_PURCHASES === 'true'
  export interface PlayDesiredState {
    tier: BillingTier | 'free'; status: 'active' | 'expired' | 'pending';
    subscriptionRenews: boolean; endsAtMs: number | null; productId: string | null;
  }
  export function desiredStateForPlay(sub: PlaySubscription, nowMs: number): PlayDesiredState;
  export interface PlayDeps { db: admin.firestore.Firestore; api: PlayApi; now: () => Date; }
  export async function verifyPlayPurchaseHandler(
    data: { purchaseToken?: unknown }, context: functions.https.CallableContext, deps: PlayDeps,
  ): Promise<{ tier: BillingTier | 'free'; status: 'active' | 'expired' | 'pending'; endsAt: number | null }>;
  export async function commitPlayState(tx, refs: { userRef; billingRef; indexRef; uid: string },
    desired: PlayDesiredState, tokenHash: string, fetchedAtMs: number, now: Date): Promise<{ applied: boolean }>;
  ```
  Firestore paths: index `playSubscriptions/{tokenHash}` → `{ uid }`; billing doc `users/{uid}/billingTransactions/play_{tokenHash}`.

`desiredStateForPlay` mapping (decided here; tests pin it):

| `subscriptionState` | result |
|---|---|
| `ACTIVE`, `IN_GRACE_PERIOD` | plan tier, `active`, renews = `autoRenewEnabled ?? true`, endsAt = `expiryTime` |
| `CANCELED` and `expiryTime > now` | plan tier, `active`, renews `false`, endsAt = `expiryTime` |
| `CANCELED` past expiry, `ON_HOLD`, `PAUSED`, `EXPIRED`, `PENDING_PURCHASE_CANCELED` | `free`, `expired` |
| `PENDING` | `free`, `pending`, no user-doc write |
| unknown product id | `free`, `expired` |

- [ ] **Step 1: Write the failing tests:**
  - `mapper_*`: one per table row above, asserting `tier`, `status`, `subscriptionRenews`, `endsAtMs`.
  - `verify_unauthenticated` → HttpsError `unauthenticated`; `verify_missingToken` → `invalid-argument` `missing_purchase_token`.
  - `verify_accountMismatch`: `obfuscatedExternalAccountId = playAccountId('other')` → `failed-precondition` `account_mismatch`; also when absent.
  - `verify_testPurchaseRefused`: `testPurchase: {}` and env unset → `failed-precondition` `test_purchase_not_allowed`; with env `'true'` it grants.
  - `verify_active_grantsAndAcknowledges`: ACTIVE pro monthly, ack PENDING → user doc has `subscriptionTier:'pro'`, `subscriptionSource:'play'`, `subscriptionEndsAt` = expiry, `playPurchaseToken`, `playPurchaseTokenHash`, `playProductId`; index doc `{uid}`; `api.acknowledge` called once with (`com.danzucker.stitchpad.pro.monthly`, token).
  - `verify_alreadyAcknowledged_noAckCall`.
  - `verify_pending_noUserWrite`: returns `status:'pending'`, user doc untouched, no ack.
  - `verify_tokenBoundToOtherUid` → `failed-precondition` `subscription_belongs_to_another_account`.
  - `activePaystack_isNotOverwrittenByPlay`: user doc active Paystack pro ending tomorrow → Play atelier verify leaves user doc unchanged, billing + index docs written.
  - `staleOldTokenExpiry_doesNotRevokeNewToken`: user owns token B (`playPurchaseTokenHash = hash(B)`); commit an `expired` state for token A → user doc unchanged.
  - `olderFetch_isIgnored`: billing doc `playLastFetchedAt = 2000`; commit with `fetchedAtMs = 1000` → `applied:false`, no writes.
  - `apiFailure_mapsToUnavailable`: `getSubscription` rejects → HttpsError `unavailable` `play_unavailable`.
- [ ] **Step 2: Run** `cd functions && npx jest playBilling` → FAIL.
- [ ] **Step 3: Implement** in `playBilling.ts`. `ownsCurrentEntitlement = user.subscriptionSource === 'play' && user.playPurchaseTokenHash === tokenHash`. `commitPlayState` writes the user doc only when `status !== 'pending'` and `shouldWriteUserDoc(...)` is true; it always writes the billing doc (`source:'play'`, `tier`, `productId`, `appliedStatus`, `playLastFetchedAt`) and the index. Like Apple, it writes `subscriptionEndsAt` only when `endsAtMs` is non-null. Acknowledge after the transaction commits, only when `status === 'active'` and `acknowledgementState === 'ACKNOWLEDGEMENT_STATE_PENDING'`; an ack failure is logged, not thrown (reconcile retries). Error codes go in the HttpsError message so `recoverPaymentError` markers can find them.
- [ ] **Step 4: Implement** `playApi.ts` (`createPlayApi(): PlayApi`) with `androidpublisher.purchases.subscriptionsv2.get` and `purchases.subscriptions.acknowledge`; no tests (it is the stubbed seam). Wire `verifyPlayPurchase = functions.region(REGION).https.onCall(...)` and export it from `index.ts`.
- [ ] **Step 5: Run** `cd functions && npx jest playBilling && npm run build` → PASS, clean build.
- [ ] **Step 6: Commit** `feat(billing): verifyPlayPurchase callable with Play subscriptionsv2 verification`

---

### Task 3: RTDN handler and daily reconcile

**Files:**
- Modify: `functions/src/billing/playBilling.ts`, `functions/src/index.ts`
- Test: `functions/src/__tests__/billing/playBilling.test.ts`

**Interfaces:**
- Consumes: Task 2 `PlayDeps`, `desiredStateForPlay`, `commitPlayState`, `playTokenHash`.
- Produces:
  ```ts
  export const PLAY_RTDN_TOPIC = 'play-billing-rtdn';
  export async function playRtdnHandler(messageJson: unknown, deps: PlayDeps): Promise<void>;
  export async function reconcilePlaySubscriptionsHandler(deps: PlayDeps): Promise<void>;
  ```
  Reconcile reads the raw `playPurchaseToken` that Task 2 stores on the user doc.

- [ ] **Step 1: Write the failing tests:**
  - `rtdn_renewal_extendsEndsAt`: message `{ subscriptionNotification: { purchaseToken: T, notificationType: 2 } }`, index has T → uid, API returns ACTIVE with a later expiry → user `subscriptionEndsAt` updated.
  - `rtdn_expired_downgradesOwner`: API returns EXPIRED, user owns T → `subscriptionTier:'free'`, `subscriptionStatus:'expired'`.
  - `rtdn_unknownToken_noWrite`: no index doc → resolves, no writes, no throw.
  - `rtdn_testNotification_ignored`: `{ testNotification: {...} }` → resolves, API not called.
  - `rtdn_garbage_ignored`: non-object / missing token → resolves, no throw (Pub/Sub would otherwise retry forever).
  - `rtdn_apiFailure_throws`: API rejects → handler rejects (so Pub/Sub retries).
  - `reconcile_onlyActivePlayUsers`: query `subscriptionSource == 'play'` and `subscriptionStatus == 'active'`; a user whose sub is now EXPIRED is downgraded; one failing user does not stop the loop.
  - `reconcile_acknowledgesPending`: ACTIVE + ack PENDING → `api.acknowledge` called.
- [ ] **Step 2: Run** `cd functions && npx jest playBilling` → new tests FAIL.
- [ ] **Step 3: Implement.** RTDN ignores `notificationType` for decisions: it always re-fetches and maps (the type is logged only). Linked tokens need no special case: the new token's verify or RTDN binds it, and Task 2's ownership rule protects against the old token's expiry. Test purchases in RTDN/reconcile with the env flag off map to `free` (heal, like Apple reconcile).
- [ ] **Step 4: Wire** `playRealtimeNotifications = functions.region(REGION).pubsub.topic(PLAY_RTDN_TOPIC).onPublish(m => playRtdnHandler(m.json, ...))` and `reconcilePlaySubscriptions` on `.pubsub.schedule('45 2 * * *').timeZone('Africa/Lagos')`. Export both from `index.ts`.
- [ ] **Step 5: Run** `cd functions && npx jest && npm run build` → all PASS.
- [ ] **Step 6: Commit** `feat(billing): Play RTDN handler and daily reconcile`

---

### Task 4: Rules, other server readers of `subscriptionSource`, Paystack country guard

**Files:**
- Modify: `firestore.rules:236-250` (create: `playPurchaseToken`, `playPurchaseTokenHash`, `playProductId` absent), `:274-283` (`serverOnlyField` for the same three), after `:626` add `match /playSubscriptions/{tokenHash} { allow read, write: if false; }`
- Modify: `functions/src/freemium/launchGrant.ts:83-90` (also delete `playPurchaseToken`, `playPurchaseTokenHash`, `playProductId`)
- Modify: `functions/src/notifications/digestAudience.ts:39` (accept `'play'`)
- Modify: `functions/src/billing/subscriptionReminder.ts:167` (skip `'play'` like `'apple'`: the store renews it)
- Modify: `functions/src/billing/paystackBilling.ts` `initializeSubscriptionCheckoutHandler` (read `users/{uid}.country`; missing → NG; any other value → HttpsError `failed-precondition` `provider_unavailable_country`)
- Test: `functions/src/__tests__/firestore.rules.test.ts`, the existing `launchGrant`, `digestAudience`, `subscriptionReminder`, `paystackBilling` suites

- [ ] **Step 1: Write the failing tests:**
  - rules: owner cannot create a user doc with `playPurchaseToken`; cannot update or delete `playProductId`; cannot read `playSubscriptions/x`.
  - `buildLaunchGrantFields` deletes the three play fields.
  - `isPaidSubscriber({subscriptionSource:'play', subscriptionStatus:'active', subscriptionEndsAt: future}) === true`.
  - reminder candidate with `subscriptionSource:'play'` is skipped.
  - Paystack: `country:'GH'` → `failed-precondition` with message containing the existing `PROVIDER_UNAVAILABLE` marker (check `MARKER_PROVIDER_UNAVAILABLE` in `CloudFunctionsPaymentRepository.kt:86-97` and reuse its exact text); `country` missing → proceeds; `'NG'` → proceeds.
- [ ] **Step 2: Run** `cd functions && npx jest` and the rules emulator suite (`npm run test:rules` or the command in `functions/package.json`) → new tests FAIL.
- [ ] **Step 3: Implement** the five changes.
- [ ] **Step 4: Run** the same commands → PASS.
- [ ] **Step 5: Commit** `feat(billing): protect Play fields in rules, count Play payers, Paystack NG-only`

---

### Task 5: Client domain: store product ids, provider choice, routing repository, config flag

**Files:**
- Rename: `feature/freemium/domain/AppleProductIds.kt` → `StoreProductIds.kt` (object `StoreProductIds`; update the comment: same ids on App Store and Play; fix all references incl. iOS and `UpgradeScreen.kt:302`)
- Create: `feature/freemium/domain/CheckoutProvider.kt` (move `enum class CheckoutProvider { PAYSTACK, APPLE, PLAY }` out of `UpgradeState.kt`)
- Create: `feature/freemium/data/RoutingPaymentRepository.kt`
- Modify: `core/config/domain/model/AppConfig.kt`, `core/config/data/dto/AppConfigDto.kt`, `core/config/data/mapper/AppConfigMapper.kt` (add `playBillingEnabled: Boolean = false`)
- Test: `commonTest/.../feature/freemium/domain/CheckoutProviderTest.kt`, `commonTest/.../feature/freemium/data/RoutingPaymentRepositoryTest.kt`, `AppConfigMapperTest.kt`

(All paths under `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/` unless shown.)

**Interfaces:**
- Produces:
  ```kotlin
  fun checkoutProviderFor(isIos: Boolean, country: Country): CheckoutProvider
  // iOS → APPLE; else AndroidBilling.PAYSTACK → PAYSTACK, PLAY → PLAY

  internal class RoutingPaymentRepository(
      private val paystack: PaymentRepository,
      private val play: PaymentRepository,
      private val countryProvider: CurrentCountryProvider,
  ) : PaymentRepository
  ```
  `startCheckout` and `restorePurchases` resolve `countryProvider.countryForWrite()`; null → `Result.Error(PaymentError.PROVIDER_UNAVAILABLE)`. `productCatalog` uses `loadedCountry.value`; null or Paystack → `Result.Success(emptyMap())`.

- [ ] **Step 1: Write the failing tests:**
  - `checkoutProviderFor`: iOS+GH → APPLE, Android+NG → PAYSTACK, Android+GH → PLAY, Android+SN → PLAY.
  - `routing_ng_usesPaystack`, `routing_gh_usesPlay` (fake delegates record calls).
  - `routing_nullLoadedCountry_returnsProviderUnavailable` (neither delegate called).
  - `routing_catalog_ngIsEmpty`, `routing_catalog_ghDelegatesToPlay`.
  - `AppConfigMapperTest`: `playBillingEnabled` defaults false, maps true.
- [ ] **Step 2: Run** `./gradlew :composeApp:testDebugUnitTest --tests "*CheckoutProvider*" --tests "*RoutingPaymentRepository*" --tests "*AppConfigMapper*"` → FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** the same command plus `./gradlew :composeApp:compileKotlinIosSimulatorArm64` (rename touches iOS) → PASS.
- [ ] **Step 5: Commit** `feat(billing): route Android checkout by workshop country, add playBillingEnabled`

---

### Task 6: Android Play Billing repository

**Files:**
- Modify: `gradle/libs.versions.toml` (add `play-billing` version + `play-billing-ktx = { module = "com.android.billingclient:billing-ktx", ... }`), `composeApp/build.gradle.kts:34` (`androidMain.dependencies`)
- Create: `composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/feature/freemium/data/NativePlayBillingClient.kt` (interface + data classes, no Billing Library types in its signatures)
- Create: `.../feature/freemium/data/AndroidPlayBillingClient.kt` (real impl over `BillingClient`, uses `CurrentActivityHolder` for `launchBillingFlow`, main dispatcher for the launch)
- Create: `.../feature/freemium/data/PlayBillingPaymentRepository.kt`
- Modify: `composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/di/PlatformModule.android.kt:89-90` (bind `RoutingPaymentRepository(paystack = CloudFunctionsPaymentRepository(...), play = PlayBillingPaymentRepository(...), countryProvider = get())`)
- Test: `composeApp/src/androidUnitTest/kotlin/com/danzucker/stitchpad/feature/freemium/data/PlayBillingPaymentRepositoryTest.kt` (create the source set if absent; if the KMP target setup makes that costly, put the repository logic in commonMain against `NativePlayBillingClient` declared in commonMain and test in commonTest. Pick one and say which in the PR.)

**Interfaces:**
- Consumes: `StoreProductIds` (Task 5), callable `verifyPlayPurchase` `{ purchaseToken }` (Task 2).
- Produces:
  ```kotlin
  interface NativePlayBillingClient {
      suspend fun fetchProducts(productIds: List<String>): Result<List<PlayProduct>, PlayBillingError>
      /** Launches the purchase sheet; obfuscatedAccountId = sha256Hex(uid). oldPurchaseToken set → replacement. */
      suspend fun purchase(productId: String, obfuscatedAccountId: String, oldPurchaseToken: String?): Result<PlayPurchase, PlayBillingError>
      suspend fun queryActivePurchases(): Result<List<PlayPurchase>, PlayBillingError>
      /** Purchases that reach PURCHASED while the app runs (pending completions, renewals). */
      val purchaseUpdates: Flow<PlayPurchase>
  }
  data class PlayProduct(val id: String, val formattedPrice: String)
  data class PlayPurchase(val productIds: List<String>, val purchaseToken: String, val state: PlayPurchaseState, val isAcknowledged: Boolean)
  enum class PlayPurchaseState { PURCHASED, PENDING }
  enum class PlayBillingError : Error { CANCELLED, NETWORK, BILLING_UNAVAILABLE, PRODUCT_NOT_FOUND, ALREADY_OWNED, UNKNOWN }

  internal class PlayBillingPaymentRepository(
      private val client: NativePlayBillingClient,
      private val functions: FirebaseFunctions,
      private val authRepository: AuthRepository,
  ) : PaymentRepository {
      suspend fun verifyUnacknowledged()  // used by Task 7's sweep
  }
  fun sha256Hex(value: String): String   // must equal functions playAccountId()
  ```
  Replacement mode: `ReplacementMode.WITH_TIME_PRORATION` for every tier or cadence change.

- [ ] **Step 1: Write the failing tests** (fake `NativePlayBillingClient`, fake callable via the same seam `StoreKitPaymentRepository` tests use):
  - `purchase_verifiesTokenAndReturnsGranted`.
  - `cancelled_returnsCancelledOutcome` (no error).
  - `pendingPurchase_returnsPendingOutcome`, verify not called.
  - `upgrade_passesOldTokenForReplacement`: active purchase for `pro.monthly` exists, buying `atelier.monthly` → `oldPurchaseToken` = that token.
  - `samePlanOwned_verifiesInsteadOfBuying`: `ALREADY_OWNED` → re-verify existing token, return granted.
  - `verifyFailure_mapsViaRecoverPaymentError` (`account_mismatch` → UNKNOWN, unavailable → PROVIDER_UNAVAILABLE).
  - `catalog_mapsFormattedPrices` keyed by product id.
  - `restore_verifiesEachActivePurchase`; none → Cancelled.
  - `sha256Hex("abc") == "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"`.
- [ ] **Step 2: Run** the new test class → FAIL.
- [ ] **Step 3: Implement** the repository, then `AndroidPlayBillingClient` (connect lazily, reconnect once on `SERVICE_DISCONNECTED`, `enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())`, map `BillingResponseCode` to `PlayBillingError`). Never acknowledge on the client.
- [ ] **Step 4: Run** the test class, `./gradlew :composeApp:assembleDebug`, `./gradlew detekt` → PASS.
- [ ] **Step 5: Commit** `feat(billing): Android Play Billing payment repository`

---

### Task 7: Upgrade screen on the Play path, and the purchase sweep

**Files:**
- Modify: `feature/freemium/presentation/upgrade/UpgradeState.kt` (rename `appleDisplayPrices` → `storeDisplayPrices`; add `playBillingEnabled: Boolean = false`; `canCheckout` = APPLE → true, PLAY → `playBillingEnabled`, PAYSTACK → `billingEnabled`; provider is `null` until the country loads on Android: make it `CheckoutProvider?` and `canCheckout` false when null)
- Modify: `feature/freemium/presentation/upgrade/UpgradeViewModel.kt:42-140` (provider from `checkoutProviderFor(Platform.isIos, loadedCountry)`, collected; reload the catalog when the provider becomes PLAY; `showBillingCurrencyNote` only for PAYSTACK; collect `playBillingEnabled`)
- Modify: `feature/freemium/presentation/upgrade/UpgradeScreen.kt:136,215,239,275,302` (treat PLAY like APPLE for prices, "Subscribe" CTA and Restore; terms text `upgrade_terms_play`; add a PLAY @Preview)
- Modify: `composeResources/values/strings.xml`, `values-fr/strings.xml` (add `upgrade_terms_play`)
- Create: `composeApp/src/androidMain/.../feature/freemium/data/PlayPurchaseSweeper.kt` (on app foreground after sign-in and on each `purchaseUpdates` emission, calls `verifyUnacknowledged()` when the provider is PLAY); start it from `MainActivity` the same way other app-scope observers start
- Test: `UpgradeViewModelTest.kt`, `PlayPurchaseSweeperTest` (same source set choice as Task 6)

Copy (exact, no em dashes):
- EN `upgrade_terms_play`: `Auto-renewing subscription billed through Google Play. Cancel anytime in the Play Store under Payments and subscriptions.`
- FR `upgrade_terms_play`: `Abonnement renouvelé automatiquement, facturé via Google Play. Résiliable à tout moment dans le Play Store, rubrique Paiements et abonnements.`

- [ ] **Step 1: Write the failing tests:**
  - `provider_waitsForLoadedCountry`: `loadedCountry = null` → provider null, `canCheckout` false, `StartCheckout` does not call the repository.
  - `provider_ghAndroid_isPlay`, `provider_ngAndroid_isPaystack`.
  - `play_canCheckout_followsPlayBillingEnabled` (and ignores `billingEnabled`).
  - `play_hidesBillingCurrencyNote`.
  - `play_loadsStorePrices` once provider becomes PLAY.
  - existing Paystack and Apple tests still pass after the rename.
  - sweeper: `sweep_verifiesPurchasedUnacknowledged`; `sweep_skipsWhenProviderNotPlay`; `sweep_onPurchaseUpdate_verifies`.
- [ ] **Step 2: Run** `./gradlew :composeApp:testDebugUnitTest --tests "*Upgrade*" --tests "*PlayPurchaseSweeper*"` → FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `./gradlew :composeApp:allTests detekt :composeApp:assembleDebug` → PASS.
- [ ] **Step 5: Commit** `feat(billing): Upgrade screen and purchase sweep for Play Billing`

---

### Task 8: Console setup, deploy order and smoke (Daniel + reviewer)

**Files:**
- Create: `docs/qa/2026-11-24-play-billing-smoke.md` (the checklist below, with a result column)
- Modify: `docs/handoff/2026-11-13-NEXT-WORK.md` (link this plan; status)

Setup (Daniel, in consoles; agents do not do these):
1. Play Console → Monetize → Subscriptions: create the 4 ids from Global Constraints, one base plan each (`monthly`, `annual`), auto-renewing, USD parity prices, available in the wave-1 countries only. Leave Nigeria out.
2. Play Console → Setup → API access: link GCP project `stitchpad-30607`; grant the functions runtime service account "View financial data" and "Manage orders and subscriptions".
3. GCP: create Pub/Sub topic `play-billing-rtdn`; grant `google-play-developer-notifications@system.gserviceaccount.com` the Publisher role on it; in Play Console → Monetization setup, set the topic `projects/stitchpad-30607/topics/play-billing-rtdn` and send the test notification.
4. Play Console → License testing: add test Google accounts.

Deploy order:
1. Functions: `verifyPlayPurchase`, `playRealtimeNotifications`, `reconcilePlaySubscriptions` (explicit `--only` allowlist), and rules. Not yet the Paystack country guard.
2. App build with Tasks 5-7 to the internal track; `config/app.playBillingEnabled` stays false in production.
3. Smoke on the internal track (below). Then `playBillingEnabled = true`.
4. After that build is live in production: deploy `initializeSubscriptionCheckout` with the country guard.

Smoke (a license-test account, device or emulator with Play Store, workshop country GH):
- [ ] Upgrade shows Play prices in local currency, "Subscribe" CTA, Google Play terms, Restore.
- [ ] Buy Pro monthly → screen pops, tier Pro; Firestore user doc has `subscriptionSource:'play'`; Play Console order shows acknowledged.
- [ ] Buy Atelier while Pro is active → one subscription in Play, tier Atelier; the old token's expiry does not drop the tier.
- [ ] Cancel in Play Store → `subscriptionRenews:false`, access kept to the end date; let the test sub expire (test renewals are minutes) → tier free.
- [ ] "Slow test card, approves after a few minutes" → Pending snackbar; close the app; reopen after approval → tier granted.
- [ ] Switch workshop country to NG → Paystack path unchanged.
- [ ] With `PLAY_ALLOW_TEST_PURCHASES` unset in production, a license-test purchase is refused (`test_purchase_not_allowed`).

- [ ] **Commit** `docs(billing): Play Billing setup, deploy order and smoke checklist`

---

## Open decisions for Daniel (not blocking Tasks 1-7)

1. **Test purchases in production.** License-test accounts buy against production Play. With the flag off they are refused, so the smoke needs `PLAY_ALLOW_TEST_PURCHASES=true` set temporarily on production functions, or a staging project. Recommendation: set it for the smoke window only, then unset.
2. **Nigeria on Play** (spec "Known risk"): after this ships it is a one-line change in `Country.NG.androidBilling`, plus the 15% fee decision. Not part of this plan.
3. **Timing:** this is not on the NG paywall critical path (29 Nov / 18 Dec / 6 Jan). It gates the store launch in non-NG countries only.

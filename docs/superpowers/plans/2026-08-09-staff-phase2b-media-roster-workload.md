# Staff Phase 2b Implementation Plan — Garment Media, Owner-in-Roster, Workload Overview

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Staff can do the tailoring work on an order (styles, fabric photos, notes); the owner can be an assignee ("You"); the Team screen shows who is working on what; emulator smoke needs zero local file surgery.

**Architecture:** Widen the Firestore staff order-update branch to a work-fields whitelist and add a claims+membership-gated staff branch to Storage rules. Add an items-only repository write path (the owner-only `updateOrder` also writes the money mirror staff cannot touch). Owner-in-roster is a lazy client-side ensure. Workload counts are computed client-side over the already-synced orders stream; tap-through reuses `OrderListRoute(initialFilter)` with a new `assignee:` prefix.

**Tech Stack:** KMP + Compose Multiplatform, GitLive firebase-kotlin-sdk, Firestore/Storage security rules, `@firebase/rules-unit-testing` (jest, `firebase emulators:exec`), Koin, kotlin.test.

**Spec:** `docs/superpowers/specs/2026-08-09-staff-phase2b-media-roster-workload-design.md`

## Global Constraints

- Money never enters a base order doc: staff item writes serialize the money-free `OrderItemBaseDto` shape (no `price`); rules deny top-level money keys (`orderMoneyKeys()`) on every base write.
- `deadline` (due date) stays owner-only — it must NOT enter the staff rules whitelist and its edit affordances stay staff-restricted.
- `firestore.emulator.rules` stays byte-identical to `firestore.rules` (CI-checked; edit both identically).
- MVI (State/Action/Event + ViewModel), Root/Screen split, all state in the ViewModel, `Result<T, E>` (never throw for expected failures), UiText for user-facing strings, no hardcoded strings — compose-resources only.
- GitLive `update()`/`arrayUnion` values must be primitive maps/lists (never @Serializable data classes — iOS hard-crashes); map keys must match the DTO's @Serializable field names.
- Repository staff/owner routing: callers pass `userId = workshopUid` from `ActiveWorkshopProvider`; do not re-derive uids inside repositories.
- Detekt: functions ≤ 60 lines; extract private handlers rather than suppressing.
- Every new Screen-level composable change keeps/adds a `@Preview`.
- Emulator seeding/config is QA-only: `USE_FIREBASE_EMULATOR` stays `false` in committed code.

---

### Task 1: Firestore rules — widen the staff order-update branch to work fields

**Files:**
- Modify: `firestore.rules` (staff branch of orders `allow update`, currently lines ~415–423)
- Modify: `firestore.emulator.rules` (identical edit — byte parity)
- Test: `functions/src/__tests__/firestore.rules.test.ts`

**Interfaces:**
- Consumes: existing `isActiveMember(uid)`, `serverCreatedAtProtectedOnUpdate()`, `activityCreatedAtStableOnUpdate()`, `orderMoneyKeys()` rule functions.
- Produces: staff may update `status`, `subStatus`, `statusHistory`, `updatedAt`, `items`, `notes` on `users/{uid}/orders/{orderId}`. Tasks 3–4 rely on exactly this key set.

- [ ] **Step 1: Write failing rules tests**

In `firestore.rules.test.ts`, locate the existing staff order-update tests (search for `hasOnly` or `status` staff-branch tests near the orders describe block) and add, using the file's existing helpers (`asAdmin`, `db(uid)` with claims — staff contexts are created as `testEnv.authenticatedContext(staffUid, { role: 'staff', workshopUid: ownerUid })`; membership doc seeded at `users/{ownerUid}/memberships/{staffUid}` with `{ status: 'active' }` — copy the exact seeding pattern from the neighboring staff tests):

```ts
describe('orders update — staff work fields (Phase 2b)', () => {
  // seed: owner order doc with items: [{ id: 'i1', garmentType: 'SHIRT', description: '', quantity: 1 }], notes: null

  it('staff may update items + notes + status together', async () => {
    await assertSucceeds(updateDoc(orderRef(staffDb), {
      items: [{ id: 'i1', garmentType: 'SHIRT', description: '', quantity: 1,
                fabricImages: [{ photoUrl: 'u', photoStoragePath: 'p', syncState: 'SYNCED' }] }],
      notes: 'hem to ankle',
      status: 'IN_PROGRESS',
      updatedAt: 2,
    }));
  });

  it('staff items write may not smuggle a money key', async () => {
    await assertFails(updateDoc(orderRef(staffDb), { items: [], totalPrice: 5, updatedAt: 2 }));
  });

  it('staff items write may not smuggle deadline', async () => {
    await assertFails(updateDoc(orderRef(staffDb), { items: [], deadline: 123, updatedAt: 2 }));
  });

  it('staff items write may not smuggle assignment fields', async () => {
    await assertFails(updateDoc(orderRef(staffDb), { items: [], assignedMemberId: 'staff-uid', updatedAt: 2 }));
  });

  it('staff notes-only write succeeds', async () => {
    await assertSucceeds(updateDoc(orderRef(staffDb), { notes: 'x', updatedAt: 2 }));
  });

  it('revoked member may not write items', async () => {
    // membership doc status flipped to 'revoked' via asAdmin first
    await assertFails(updateDoc(orderRef(staffDb), { items: [], updatedAt: 2 }));
  });
});
```

Also assert one owner-branch case still passes unchanged (owner updates `deadline`) so the branch reorder can't silently regress owners.

- [ ] **Step 2: Run tests, verify the new staff-items cases FAIL** — `cd functions && npm run test:rules` (denied by the current status-only `hasOnly`).

- [ ] **Step 3: Widen the whitelist** in BOTH `firestore.rules` and `firestore.emulator.rules`. The staff status branch becomes the staff *work* branch:

```
          || (
            // Phase 2b: staff WORK fields — production status, garment items
            // (styles/fabric photos; base items are money-free post-8d-1, and
            // rules cannot see into arrays — see the create comment above for
            // why that is not a gap), and working notes. `deadline` is
            // deliberately absent: due date is a customer commitment and stays
            // owner-only (2026-08-08 product decision).
            isActiveMember(uid)
            && request.resource.data.diff(resource.data).affectedKeys()
                 .hasOnly(['status', 'subStatus', 'statusHistory', 'updatedAt', 'items', 'notes'])
            && serverCreatedAtProtectedOnUpdate()
            && activityCreatedAtStableOnUpdate()
          )
```

(Replace the existing `['status', 'subStatus', 'statusHistory', 'updatedAt']` branch in place; the claim branch below it is untouched.)

- [ ] **Step 4: Run tests, verify PASS** — `npm run test:rules`. Also `diff firestore.rules firestore.emulator.rules` must be empty.

- [ ] **Step 5: Commit** — `feat(rules): staff order updates widen to work fields (items, notes)`

### Task 2: Storage rules — staff branch on the workshop tree

**Files:**
- Modify: `storage.rules`
- Create: `functions/src/__tests__/storage.rules.test.ts`
- Modify: `functions/package.json` (extend `test:rules` to start the storage emulator)

**Interfaces:**
- Consumes: staff custom claims (`role`, `workshopUid`) minted by the existing approve flow; membership doc `users/{uid}/memberships/{staffUid}.status == 'active'` (exact path/value from `firestore.rules` `isActiveMember`).
- Produces: staff read anywhere under `users/{workshopUid}/**`; staff write only under `users/{workshopUid}/orders/**`. Task 4's staff photo uploads depend on this.

- [ ] **Step 1: Write failing storage-rules tests**

New `storage.rules.test.ts` mirroring the firestore harness (same jest.rules config globs `__tests__/*.rules.test.ts` — verify; if `jest.rules.config.js` matches only the firestore file, widen its `testMatch`):

```ts
import { readFileSync } from 'fs';
import { resolve } from 'path';
import { assertFails, assertSucceeds, initializeTestEnvironment, RulesTestEnvironment } from '@firebase/rules-unit-testing';
import { ref, uploadString, getBytes } from 'firebase/storage';
import { doc, setDoc } from 'firebase/firestore';

const STORAGE_RULES = readFileSync(resolve(__dirname, '../../../storage.rules'), 'utf8');
const FIRESTORE_RULES = readFileSync(resolve(__dirname, '../../../firestore.rules'), 'utf8');

let testEnv: RulesTestEnvironment;
const OWNER = 'owner-uid';
const STAFF = 'staff-uid';

beforeAll(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: 'demo-stitchpad',
    firestore: { rules: FIRESTORE_RULES, host: '127.0.0.1', port: 8080 },
    storage: { rules: STORAGE_RULES, host: '127.0.0.1', port: 9199 },
  });
});
afterAll(async () => { await testEnv.cleanup(); });
beforeEach(async () => {
  await testEnv.clearStorage();
  await testEnv.clearFirestore();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), `users/${OWNER}/memberships/${STAFF}`), { status: 'active' });
  });
});

const staffStorage = () =>
  testEnv.authenticatedContext(STAFF, { role: 'staff', workshopUid: OWNER }).storage();

it('staff may write under the owner order-media subtree', async () => {
  await assertSucceeds(uploadString(ref(staffStorage(), `users/${OWNER}/orders/o1/fabrics/i1-abc.jpg`), 'x'));
});
it('staff may read anywhere in the workshop tree', async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) =>
    uploadString(ref(ctx.storage(), `users/${OWNER}/logo.png`), 'x'));
  await assertSucceeds(getBytes(ref(staffStorage(), `users/${OWNER}/logo.png`)));
});
it('staff may NOT write outside orders (brand logo)', async () => {
  await assertFails(uploadString(ref(staffStorage(), `users/${OWNER}/logo.png`), 'x'));
});
it('revoked staff may not write order media', async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) =>
    setDoc(doc(ctx.firestore(), `users/${OWNER}/memberships/${STAFF}`), { status: 'revoked' }));
  await assertFails(uploadString(ref(staffStorage(), `users/${OWNER}/orders/o1/fabrics/i1.jpg`), 'x'));
});
it('a foreign user may neither read nor write the tree', async () => {
  const foreign = testEnv.authenticatedContext('other-uid').storage();
  await assertFails(uploadString(ref(foreign, `users/${OWNER}/orders/o1/fabrics/i1.jpg`), 'x'));
  await assertFails(getBytes(ref(foreign, `users/${OWNER}/logo.png`)));
});
it('owner keeps full read/write', async () => {
  const owner = testEnv.authenticatedContext(OWNER).storage();
  await assertSucceeds(uploadString(ref(owner, `users/${OWNER}/logo.png`), 'x'));
});
```

Update `functions/package.json`: `"test:rules": "firebase emulators:exec --only firestore,storage --project=demo-stitchpad \"jest --config jest.rules.config.js\""`. If `emulators:exec` needs a storage emulator entry in the config it loads, add a `storage` block (port 9199) to the firebase config file the functions dir uses for tests (check `functions/firebase.json` vs repo-root `firebase.json`; add `"storage": { "rules": "storage.rules" }` + emulator port alongside the existing firestore entries, WITHOUT touching production deploy targets — if the root `firebase.json` is deploy-only, prefer a dedicated `firebase.rules-test.json` passed via `--config`).

- [ ] **Step 2: Run, verify the staff cases FAIL** — `npm run test:rules` (current rules are owner-only).

- [ ] **Step 3: Implement the storage rules**

```
service firebase.storage {
  match /b/{bucket}/o {
    // Active staff of workshop `uid`: same claims + membership-doc gate as
    // firestore.rules' isActiveMember — the claim alone can't enforce
    // revocation inside the token's lifetime (see that function's comment).
    function isActiveStaffOf(uid) {
      return request.auth != null
        && request.auth.token.get('role', '') == 'staff'
        && request.auth.token.get('workshopUid', '') == uid
        && firestore.get(/databases/(default)/documents/users/$(uid)/memberships/$(request.auth.uid)).data.status == 'active';
    }

    match /users/{uid}/{allPaths=**} {
      // Owner: full read/write of their own tree. Staff: read-only here —
      // their write grant is the order-media match below.
      allow read: if request.auth != null && (request.auth.uid == uid || isActiveStaffOf(uid));
      allow write: if request.auth != null && request.auth.uid == uid;
    }

    // Phase 2b: staff add style/fabric photos to orders. Write-scope is the
    // order-media subtree ONLY (FirebaseOrderRepository's
    // fabricStoragePath/styleStoragePath both live under users/{uid}/orders/…)
    // — brand logo, customer style folders, and inspiration images stay
    // owner-write-only. Storage grants are additive across matches, so this
    // widens the match above for exactly this prefix.
    match /users/{uid}/orders/{orderPaths=**} {
      allow write: if isActiveStaffOf(uid);
    }

    match /tutorials/{allPaths=**} {
      allow read: if request.auth != null;
    }
  }
}
```

Keep the existing header comment block, extending its notes with the staff branch.

- [ ] **Step 4: Run tests, verify PASS** — `npm run test:rules` (both rules files' suites green).

- [ ] **Step 5: Commit** — `feat(storage): staff read workshop tree, write order media`

### Task 3: `OrderRepository.updateItems` — items-only base write

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/repository/OrderRepository.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/data/FirebaseOrderRepository.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/repository/FakeOrderRepository.kt` (commonTest fake — record last call)
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/order/data/OrderItemsWriteFieldsTest.kt` (new)

**Interfaces:**
- Consumes: existing `OrderItem` domain model, `toOrderItemBaseDto()` mapper (in `core/data/mapper/OrderMapper.kt` — if item mapping is inlined inside `toOrderBaseDto()` rather than a named per-item function, extract `internal fun OrderItem.toOrderItemBaseDto(): OrderItemBaseDto` first and reuse it in both places).
- Produces: `suspend fun updateItems(userId: String, orderId: String, items: List<OrderItem>): EmptyResult<DataError.Network>` — writes exactly `items` + `updatedAt`. Task 4 switches the detail VM's item-persist call sites to it.

- [ ] **Step 1: Write the failing test** — the write payload is pure and must (a) contain only `items` + `updatedAt`, (b) carry no `price` key anywhere, (c) mirror `OrderItemBaseDto`'s @Serializable field names:

```kotlin
class OrderItemsWriteFieldsTest {

    private val item = OrderItem(
        id = "i1",
        garmentType = GarmentType.SHIRT,
        description = "desc",
        price = 9_999.0, // domain still carries price — the write must not
        quantity = 2,
        fabricName = "Ankara",
        styleImages = listOf(
            StyleImageRef(source = StyleImageSource.UPLOADED, photoUrl = "su", photoStoragePath = "sp"),
        ),
        fabricImages = listOf(
            FabricImageRef(photoUrl = "fu", photoStoragePath = "fp", syncState = ImageSyncState.PENDING),
        ),
    )

    @Test
    fun payload_isItemsAndUpdatedAtOnly() {
        val fields = orderItemsWriteFields(listOf(item), now = 42L)
        assertEquals(setOf("items", "updatedAt"), fields.keys)
        assertEquals(42L, fields["updatedAt"])
    }

    @Test
    fun itemMaps_carryNoPriceAnywhere() {
        val fields = orderItemsWriteFields(listOf(item), now = 42L)
        @Suppress("UNCHECKED_CAST")
        val itemMap = (fields["items"] as List<Map<String, Any?>>).single()
        assertFalse(itemMap.containsKey("price"))
        assertEquals("i1", itemMap["id"])
        assertEquals("SHIRT", itemMap["garmentType"])
        assertEquals(2, itemMap["quantity"])
        assertEquals("Ankara", itemMap["fabricName"])
        @Suppress("UNCHECKED_CAST")
        val fabric = (itemMap["fabricImages"] as List<Map<String, Any?>>).single()
        assertEquals("fp", fabric["photoStoragePath"])
        assertEquals("PENDING", fabric["syncState"])
        @Suppress("UNCHECKED_CAST")
        val style = (itemMap["styleImages"] as List<Map<String, Any?>>).single()
        assertEquals("UPLOADED", style["source"])
        assertEquals("sp", style["photoStoragePath"])
    }
}
```

- [ ] **Step 2: Run, verify FAIL** — `./gradlew :composeApp:testDebugUnitTest --tests "*.OrderItemsWriteFieldsTest"` (helper doesn't exist).

- [ ] **Step 3: Implement.** In `FirebaseOrderRepository.kt`, alongside the other pure write helpers (same GitLive primitive-map constraint — see `PaymentDto.toFirestoreMap`'s comment):

```kotlin
internal fun StyleImageRefDto.toFirestoreMap(): Map<String, Any?> = mapOf(
    "source" to source,
    "styleId" to styleId,
    "photoUrl" to photoUrl,
    "photoStoragePath" to photoStoragePath,
    "syncState" to syncState,
)

internal fun FabricImageRefDto.toFirestoreMap(): Map<String, Any?> = mapOf(
    "photoUrl" to photoUrl,
    "photoStoragePath" to photoStoragePath,
    "syncState" to syncState,
)

internal fun OrderItemBaseDto.toFirestoreMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "garmentType" to garmentType,
    "customGarmentName" to customGarmentName,
    "description" to description,
    "quantity" to quantity,
    "measurementId" to measurementId,
    "fabricName" to fabricName,
    "styleImages" to styleImages.map { it.toFirestoreMap() },
    "fabricImages" to fabricImages.map { it.toFirestoreMap() },
    "styleId" to styleId,
    "stylePhotoUrl" to stylePhotoUrl,
    "stylePhotoStoragePath" to stylePhotoStoragePath,
    "fabricPhotoUrl" to fabricPhotoUrl,
    "fabricPhotoStoragePath" to fabricPhotoStoragePath,
)

/**
 * Write payload for [FirebaseOrderRepository.updateItems] — items + updatedAt only,
 * serialized through the money-free [OrderItemBaseDto] shape so an item write can
 * never (re)introduce `price` into the base doc. This is the staff garment-media
 * write path (Phase 2b) and is also used by the owner's detail-screen item edits;
 * the rules' staff work-fields hasOnly admits exactly these keys.
 */
internal fun orderItemsWriteFields(items: List<OrderItem>, now: Long): Map<String, Any?> = mapOf(
    "items" to items.map { it.toOrderItemBaseDto().toFirestoreMap() },
    "updatedAt" to now,
)
```

Interface + impl (mirror `assignOrder`'s shape exactly, including the spread-helper pattern and offline enqueue):

```kotlin
// OrderRepository.kt
/** Items-only base-doc update (detail-screen garment edits: style/fabric photos,
 *  fabric name, measurement link). Never touches /private/money — item PRICES are
 *  not part of this write (they live in the money mirror). Both roles use it. */
suspend fun updateItems(userId: String, orderId: String, items: List<OrderItem>): EmptyResult<DataError.Network>
```

```kotlin
// FirebaseOrderRepository.kt
@Suppress("SpreadOperator") // same GitLive vararg constraint as assignOrder
override suspend fun updateItems(
    userId: String,
    orderId: String,
    items: List<OrderItem>,
): EmptyResult<DataError.Network> {
    val fields = orderItemsWriteFields(items, Clock.System.now().toEpochMilliseconds())
    val accepted = offlineWrites.enqueue("updateItems orderId=$orderId") {
        ordersCollection(userId).document(orderId)
            .update(*fields.entries.map { it.key to it.value }.toTypedArray())
    }
    if (!accepted) return Result.Error(DataError.Network.UNKNOWN)
    return Result.Success(Unit)
}
```

Add to `FakeOrderRepository`: `var lastUpdatedItems: Pair<String, List<OrderItem>>? = null` (orderId to items), returning `Result.Success(Unit)` (follow the fake's existing error-injection convention).

- [ ] **Step 4: Run tests, verify PASS** — same gradle command; then `./gradlew :composeApp:allTests` for the fake's compile impact.

- [ ] **Step 5: Commit** — `feat(order): items-only base write path (updateItems)`

### Task 4: Order detail — enable staff garment media + notes, route item edits through `updateItems`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailAction.kt` (`isStaffRestricted()`)
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/components/OrderGarmentDetailsCard.kt` and `OrderNotesCard.kt` (only if they ALSO gate on an isStaff flag — reconcile with the action gate; the action gate is authoritative)
- Test: `composeApp/src/commonTest/.../detail/OrderDetailStaffGuardTest.kt` (existing — update expectations)

**Interfaces:**
- Consumes: `OrderRepository.updateItems` (Task 3), rules whitelist (Task 1).
- Produces: staff-usable garment media + notes; all item-only persists (both roles) go through `updateItems`.

- [ ] **Step 1: Update the guard tests first.** In `OrderDetailStaffGuardTest`, the garment-media actions (`OnAddStyleClick`, `OnAddStylePhoto`, `OnRemoveStyleImage`, `OnAddFabricPhoto`, `OnRemoveFabricImage`, `OnAddFabricNameClick`, `OnFabricNameDraftChange`, `OnSaveFabricName`) and the notes actions (`OnNotesEditClick`, `OnNotesDraftChange`, `OnNotesSaveClick`) flip from restricted to UNRESTRICTED. Due-date (`OnSetDeadlineClick`, `OnDeadlineSelected`), measurements-link (`OnLinkMeasurementsClick`, `OnSelectMeasurement`), and everything else stay restricted — assert both directions explicitly.

- [ ] **Step 2: Run, verify FAIL** — `./gradlew :composeApp:testDebugUnitTest --tests "*.OrderDetailStaffGuardTest"`.

- [ ] **Step 3: Edit `isStaffRestricted()`** — remove the garment-media and notes cases from the restricted `when` (they fall to `else -> false`), and rewrite the KDoc's audit block: garment media + notes are staff-ENABLED as of Phase 2b (rules work-fields whitelist); due date and measurements-link remain owner-only (measurements-link touches `items[].measurementId`, which the rules now technically admit, but linking measurements is an order-setup decision — keep it owner-only and say so).

- [ ] **Step 4: Route item persists through `updateItems`.** In `OrderDetailViewModel`, every persist site that writes `order.copy(items = updatedItems)` via `orderRepository.updateOrder(...)` (style-photo add/remove, fabric-photo add/remove, fabric-name save, measurement link — currently ~lines 524, 552, 610, 638, 659, 972, 1048) switches to:

```kotlin
when (val res = orderRepository.updateItems(userId, order.id, updatedItems)) { ... }
```

with identical success/error handling per site. The DEADLINE persist (`order.copy(deadline = …)` + `updateOrder`) is NOT an item write — leave it on `updateOrder`. Do not change `saveNotes()` — `updateNotes` already writes `notes` + `updatedAt`, which the Task 1 whitelist now admits.

- [ ] **Step 5: Reconcile card-level gating.** Grep `OrderGarmentDetailsCard.kt`, `OrderNotesCard.kt`, and `OrderDetailScreen.kt` for any `isActiveStaff`/`isStaff` conditions hiding the add-style/add-fabric/notes-edit affordances; remove those so staff see the working controls. The due-date pencil's staff-hidden state stays.

- [ ] **Step 6: Run detail + list test suites** — `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.order.*"`, then `./gradlew detekt`.

- [ ] **Step 7: Commit** — `feat(staff): garment media + notes editable by staff; item edits via updateItems`

### Task 5: Owner in the roster — `OWNER` kind + lazy ensure

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/staff/TeamMember.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/staff/repository/TeamRosterRepository.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/staff/FirebaseTeamRosterRepository.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/staff/presentation/team/TeamViewModel.kt`
- Modify: the commonTest fake for `TeamRosterRepository` (add `ensureOwnerMember` recording)
- Modify: `functions/scripts/emulatorSetupStaff.js` (seed the owner roster doc; keep in sync with `docs/staff/founding-tailors-emulator-smoke-runbook.md` if it lists the seeded docs)
- Test: `composeApp/src/commonTest/.../staff/FirebaseTeamRosterRepositoryTest.kt` area (wherever `toTeamMember` tests live) + `TeamViewModelTest`

**Interfaces:**
- Consumes: `authRepository.getCurrentUser()` (id + displayName/email fields — reuse `resolveClaimDisplayName` from `OrderAssignment.kt` for the preference chain).
- Produces: `TeamMemberKind.OWNER`; `suspend fun ensureOwnerMember(workshopUid: String, name: String): EmptyResult<DataError.Network>`; roster streams sort the owner row first. Tasks 6–8 rely on `kind == OWNER` and owner-first ordering.

- [ ] **Step 1: Failing tests.**
  - `TeamMemberKind.fromWire("owner") == OWNER` (and unknown still defaults NAMED).
  - Sorting: a roster of [named "Ada" active, owner "Zed" active, staff "Bob" archived] sorts owner first, then actives alphabetically, then archived.
  - `TeamViewModel`: when the roster emission has no doc with `id == ownerUid`, `ensureOwnerMember(ownerUid, <resolved name>)` is called exactly once (not again on a second emission that includes the owner row; not at all when the row exists).

- [ ] **Step 2: Run, verify FAIL.**

- [ ] **Step 3: Implement.**
  - `TeamMemberKind`: add `OWNER` first; `fromWire`: `"owner" -> OWNER`, `"staff" -> STAFF`, else NAMED. Update its KDoc.
  - `FirebaseTeamRosterRepository.observeTeam` sort becomes `sortedWith(compareBy({ it.kind != TeamMemberKind.OWNER }, { it.status }, { it.name.lowercase() }))`.
  - Repository:

```kotlin
override suspend fun ensureOwnerMember(
    workshopUid: String,
    name: String,
): EmptyResult<DataError.Network> {
    val now = Clock.System.now().toEpochMilliseconds()
    val dto = TeamMemberDto(
        name = name.trim(),
        kind = "owner",
        status = "active",
        colorSeed = 0,
        createdAt = now,
        updatedAt = now,
    )
    // merge=true: callers only invoke this when the roster emission lacks the
    // owner row, so a lost race just rewrites identical values.
    val accepted = offlineWrites.enqueue("ensureOwnerMember workshopUid=$workshopUid") {
        teamCollection(workshopUid).document(workshopUid).set(dto, merge = true)
    }
    if (!accepted) return Result.Error(DataError.Network.UNKNOWN)
    return Result.Success(Unit)
}
```

  - `TeamViewModel.observeRoster`: after a `Result.Success`, if `result.data.none { it.id == workshopUid }` and a `private var ownerEnsureAttempted = false` is still false, set it true and call `ensureOwnerMember(workshopUid, ownerName)` (failure → snackbar via existing `toTeamRosterUiText`). Resolve `ownerName` once from `authRepository.getCurrentUser()` with `resolveClaimDisplayName(user?.name, user?.email, fallback = workshopUid)` — move `resolveClaimDisplayName` from `feature/order/.../OrderAssignment.kt` to a shared spot (`core/domain/staff/DisplayName.kt`) if the import direction is awkward; keep the original as a deprecated alias only if other call sites make the move noisy.
  - Seeder: add the owner roster doc (`team/{ownerUid}`, name "Fola", kind "owner", status "active", colorSeed 0) next to the existing Gabby/Paul seeds.

- [ ] **Step 4: Run tests, verify PASS** — `./gradlew :composeApp:allTests`.

- [ ] **Step 5: Commit** — `feat(staff): owner auto-joins the roster (kind=owner, lazy ensure)`

### Task 6: Roster + assign-picker UI — "You", pinned, unarchivable

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/staff/presentation/team/TeamScreen.kt` (RosterSection rows)
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/components/OrderAssigneeCard.kt` (picker sheet)
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailViewModel.kt` + `OrderDetailState.kt` (roster ensure on the detail path too, if the picker can be reached before the Team screen ever ran — see Step 3)
- Modify: string resources (`composeApp/src/commonMain/composeResources/values/strings.xml`): add `team_member_you` ("You"), reuse if an equivalent exists.
- Test: pure-logic tests beside the existing roster/picker logic tests (e.g. a new `RosterDisplayTest`)

**Interfaces:**
- Consumes: `TeamMemberKind.OWNER` + owner-first sort (Task 5); session `authUid`.
- Produces: `internal fun rosterDisplayName(member: TeamMember, currentAuthUid: String?, youLabel: String): String` returning `youLabel` when `member.id == currentAuthUid`, else `member.name` — used by both the Team rows and the assign picker.

- [ ] **Step 1: Failing tests** for `rosterDisplayName` (owner viewing self → "You"; staff viewing owner row → owner's name; named member unaffected) and for "owner row exposes no rename/archive" (if row menu construction is pure; otherwise assert via the row's menu-items builder extracted as a pure function).

- [ ] **Step 2: Run, verify FAIL.**

- [ ] **Step 3: Implement.**
  - Team rows: owner row shows `rosterDisplayName(...)`, gets no overflow/rename/archive affordances (`member.kind == TeamMemberKind.OWNER` guard), and is already pinned first by the Task 5 sort.
  - Assign picker (`OrderAssigneeCard`): entries render `rosterDisplayName(...)`; picking the owner writes the REAL name (`member.name`) into `assignedMemberName` (never the literal "You" — staff devices display that string as-is).
  - Detail-path ensure: `OrderDetailViewModel`'s roster observation (`shouldObserveRoster` branch) mirrors TeamViewModel's one-shot ensure so an owner who opens the assign picker before ever visiting the Team screen still gets their row. Same `ownerEnsureAttempted` once-guard, same name resolution.
  - Previews for changed composables updated.

- [ ] **Step 4: Run tests + detekt, verify PASS.**

- [ ] **Step 5: Commit** — `feat(staff): owner appears as "You" in roster and assign picker`

### Task 7: My-work for owners

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/list/OrderListState.kt` (rename `staffAuthUid` → `sessionAuthUid`, populated for BOTH roles)
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/list/OrderListViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/list/OrderListScreen.kt` (chip no longer gated on `isActiveStaff`)
- Test: `OrderListStaffTest.kt` + `OrderListViewModelTest.kt`

**Interfaces:**
- Consumes: `ActiveWorkshopProvider.flow` session (`authUid`, `isActiveStaff`).
- Produces: My work chip for every signed-in role; `filterAndSort(..., myWorkOnly, sessionAuthUid)` (the `isActiveStaff` parameter drops out of the my-work condition).

- [ ] **Step 1: Rewrite the affected tests first.**
  - New: owner session toggling `OnToggleMyWork` filters to `assignedMemberId == ownerAuthUid` (seed one order assigned to the owner, one to someone else).
  - Rewrite `myWorkOnly_isIgnoredAfterKillSwitchRevokesStaffStatus` → `myWorkOnly_staysActiveAcrossKillSwitchAsOwnerFilter`: after the staff→owner-of-self flip, `myWorkOnly` stays on and now filters the user's OWN tree by their own assignments (assert `owned-by-s-1` — assigned to `s` — is shown and `owned-by-s-2` is not), and `OnToggleMyWork` clears it. Update the comment: the chip is visible to owners now, so the filter is clearable, not stranded.
  - `onToggleMyWork_forOwner_isNoOp` is deleted (behavior intentionally reversed).

- [ ] **Step 2: Run, verify FAIL.**

- [ ] **Step 3: Implement.**
  - `observeActiveWorkshop`: `sessionAuthUid = session.authUid.takeIf { it.isNotBlank() }` (no role condition). Keep `isActiveStaff` for the other gates.
  - `toggleMyWork`: drop the `isActiveStaff` return; keep the archived-view select semantics.
  - `filterAndSort`: my-work condition becomes `myWorkOnly && sessionAuthUid != null`, filtering `it.assignedMemberId == sessionAuthUid`; delete the now-obsolete staleness comment and the `isActiveStaff` parameter if nothing else uses it.
  - `OrderListScreen`: the chip's `if (index == 0 && isActiveStaff)` becomes `if (index == 0)`; remove the now-unused `isActiveStaff` plumbing from `OrderStatusFilterChips` if it has no other consumer.
  - Rename all `staffAuthUid` references (state, VM, tests) to `sessionAuthUid`.

- [ ] **Step 4: Run list suites + detekt, verify PASS.**

- [ ] **Step 5: Commit** — `feat(order): My-work filter for owners`

### Task 8: Assignee filter in the order list (`assignee:` deep link)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/list/OrderListEvent.kt` (`OrderListFilter`)
- Modify: `OrderListState.kt`, `OrderListViewModel.kt`, `OrderListScreen.kt`, `OrderListAction.kt` (same package)
- Modify: string resources: `order_filter_assigned_to` ("Assigned to %1$s"), `order_filter_unassigned` ("Unassigned")
- Test: `OrderListViewModelTest.kt` + `OrderChipSelectionTest.kt`

**Interfaces:**
- Consumes: `OrderListRoute(initialFilter)` + SavedStateHandle seeding (Phase 2a Task 9 mechanism).
- Produces:

```kotlin
object OrderListFilter {
    // …existing constants…
    const val ASSIGNEE_PREFIX = "assignee:"
    const val ASSIGNEE_UNASSIGNED = "assignee:none"
    fun assignee(memberId: String): String = "$ASSIGNEE_PREFIX$memberId"
}
```

State: `assigneeFilter: String? = null` (a member id, or `"none"` for unassigned). Task 9 navigates with these values.

- [ ] **Step 1: Failing tests.**
  - Seeding `initialFilter = "assignee:m1"` filters to orders with `assignedMemberId == "m1"`; `"assignee:none"` filters to `assignedMemberId == null`; composes with a status chip tap.
  - `OnClearAssigneeFilter` clears it back to the full list.
  - `allChipSelected` gains an `assigneeFilter == null` conjunct: All must NOT highlight while an assignee filter is active (extend `OrderChipSelectionTest`).

- [ ] **Step 2: Run, verify FAIL.**

- [ ] **Step 3: Implement.**
  - VM seeding block: `assigneeFilter = initialFilter?.removePrefix(OrderListFilter.ASSIGNEE_PREFIX)?.takeIf { initialFilter.startsWith(OrderListFilter.ASSIGNEE_PREFIX) }` (yielding `"m1"` / `"none"`).
  - `filterAndSort` gains `assigneeFilter: String?`: after the my-work step, `when (assigneeFilter) { null -> filtered; "none" -> filtered.filter { it.assignedMemberId == null }; else -> filtered.filter { it.assignedMemberId == assigneeFilter } }`. Thread it through every call site.
  - Action `OnClearAssigneeFilter` → sets `assigneeFilter = null` + recomputes.
  - Label lives in STATE (the Screen never sees `allOrders`): add `assigneeFilterName: String? = null` to `OrderListState`, recomputed in the VM wherever `allOrders` refreshes while `assigneeFilter != null`, via a pure helper (new sibling of `OrderChipSelection.kt`) `internal fun assigneeFilterLabelName(orders: List<Order>, assigneeFilter: String): String? = if (assigneeFilter == "none") null else orders.firstOrNull { it.assignedMemberId == assigneeFilter }?.assignedMemberName ?: assigneeFilter` (+ test).
  - Chip UI: when `assigneeFilter != null`, render one selected `OrderFilterChip` right after the My-work slot; label = `order_filter_unassigned` for `"none"`, else `order_filter_assigned_to` formatted with `state.assigneeFilterName`. Tap → `OnClearAssigneeFilter`.
  - `allChipSelected(showArchived, selectedStatus, myWorkOnly, assigneeFilter)` — update signature, docs, and all callers.

- [ ] **Step 4: Run list suites + detekt, verify PASS.**

- [ ] **Step 5: Commit** — `feat(order): assignee filter with clearable chip`

### Task 9: Team-screen workload overview

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/staff/presentation/team/TeamWorkload.kt`
- Modify: `TeamState.kt`, `TeamEvent.kt`, `TeamAction.kt`, `TeamViewModel.kt`, `TeamScreen.kt` (same package), plus the Team screen's Root/NavGraph wiring for the new navigation callback
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/…` module registering `TeamViewModel` (new `OrderRepository` dependency — `viewModelOf` picks it up once the constructor grows)
- Modify: string resources: `team_workload_open_orders` ("%1$d open"), `team_workload_unassigned` ("Unassigned")
- Test: `TeamWorkloadTest.kt` (new) + `TeamViewModelTest`

**Interfaces:**
- Consumes: `OrderRepository.observeOrders(ownerUid)` (already excludes archived), `OrderListFilter.assignee(...)`/`ASSIGNEE_UNASSIGNED` (Task 8), roster rows incl. the owner (Task 5).
- Produces: `internal fun openOrderCountsByAssignee(orders: List<Order>): Map<String?, Int>`; `TeamEvent.NavigateToMemberOrders(val initialFilter: String)`.

- [ ] **Step 1: Failing tests.**

```kotlin
class TeamWorkloadTest {
    @Test
    fun countsOpenOrdersPerAssignee_excludingDelivered() {
        val orders = listOf(
            order(id = "1", assignedMemberId = "a", status = OrderStatus.PENDING),
            order(id = "2", assignedMemberId = "a", status = OrderStatus.DELIVERED), // excluded
            order(id = "3", assignedMemberId = null, status = OrderStatus.READY),
            order(id = "4", assignedMemberId = "b", status = OrderStatus.IN_PROGRESS),
        )
        assertEquals(mapOf<String?, Int>("a" to 1, null to 1, "b" to 1), openOrderCountsByAssignee(orders))
    }
}
```

(`observeOrders` already filters archived; a defensive `archivedAt == null` in the helper is still asserted with one archived order in the input.)
Plus VM tests: counts land in state from the orders stream; row tap emits `NavigateToMemberOrders(OrderListFilter.assignee("a"))` / `(ASSIGNEE_UNASSIGNED)`.

- [ ] **Step 2: Run, verify FAIL.**

- [ ] **Step 3: Implement.**

```kotlin
// TeamWorkload.kt
/** Open = not archived, not delivered — matches the spec's workload definition. */
internal fun openOrderCountsByAssignee(orders: List<Order>): Map<String?, Int> =
    orders
        .filter { it.archivedAt == null && it.status != OrderStatus.DELIVERED }
        .groupingBy { it.assignedMemberId }
        .eachCount()
```

  - `TeamViewModel`: new constructor param `orderRepository: OrderRepository`; `observeWorkload()` launched from `init`, collecting `orderRepository.observeOrders(ownerUid)` and mapping successes into `_state.update { it.copy(workloadCounts = openOrderCountsByAssignee(result.data)) }` (errors: ignore — the roster stays usable; do not clobber `errorMessage`, mirroring `observeRoster`'s comment).
  - `TeamState`: `val workloadCounts: Map<String?, Int> = emptyMap()`.
  - Actions/events: `TeamAction.OnMemberOrdersClick(val memberId: String?)` → `TeamEvent.NavigateToMemberOrders(initialFilter)` where `initialFilter = memberId?.let(OrderListFilter::assignee) ?: OrderListFilter.ASSIGNEE_UNASSIGNED`.
  - `TeamScreen` roster rows: trailing count label (`team_workload_open_orders`), row clickable only when count > 0; an "Unassigned" pseudo-row (label `team_workload_unassigned`) rendered after the roster whenever `workloadCounts[null] ?: 0 > 0`. Archived members with count 0 unchanged. Preview updated with counts.
  - Root/NavGraph: follow the exact wiring the staff dashboard's `NavigateToOrders("my-work")` event uses (Phase 2a Task 8) to navigate to `OrderListRoute(initialFilter = event.initialFilter)` — same nav mechanism, new event source.

- [ ] **Step 4: Run team + order suites + detekt, verify PASS.**

- [ ] **Step 5: Commit** — `feat(staff): who-is-working-on-what counts on the Team screen`

### Task 10: Smoke infra — Storage emulator + committed debug cleartext config + docs

**Files:**
- Modify: `firebase.emulator.json`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/config/EmulatorConfig.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/StitchPadApp.kt`
- Create: `composeApp/src/androidDebug/AndroidManifest.xml`
- Create: `composeApp/src/androidDebug/res/xml/network_security_config.xml`
- Modify: `docs/staff/founding-tailors-emulator-smoke-runbook.md` (storage emulator + no-local-files note)
- Modify: `docs/superpowers/specs/2026-08-07-owner-staff-collaboration-design.md` (§9: mark item 2 kill-switch RESOLVED — fixed in Phase 2a, verified live 2026-08-09; mark item 3 storage-emulator/cleartext RESOLVED by this task; items 1/4/5 point to the Phase 2b spec)

**Interfaces:**
- Produces: `STORAGE_EMULATOR_PORT = 9199`; debug builds carry the cleartext-to-emulator network config permanently; release manifests untouched.

- [ ] **Step 1: firebase.emulator.json** — add alongside the existing blocks:

```json
  "storage": {
    "rules": "storage.rules"
  },
```
and inside `"emulators"`: `"storage": { "port": 9199 },`

- [ ] **Step 2: EmulatorConfig.kt** — add `const val STORAGE_EMULATOR_PORT = 9199` beside the other ports.

- [ ] **Step 3: StitchPadApp.kt** — in `connectFirebaseEmulatorsIfEnabled()` add `Firebase.storage.useEmulator(host, STORAGE_EMULATOR_PORT)` (import `dev.gitlive.firebase.storage.storage`), and extend the KDoc to say Auth + Firestore + Storage.

- [ ] **Step 4: Debug source set.** `composeApp/src/androidDebug/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <!-- DEBUG-ONLY overlay: allows cleartext HTTP to the local Firebase emulators
         (10.0.2.2 = host loopback from the Android emulator). Merged into debug
         builds only; release builds never see this attribute. -->
    <application android:networkSecurityConfig="@xml/network_security_config" />
</manifest>
```

`composeApp/src/androidDebug/res/xml/network_security_config.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- DEBUG-ONLY: cleartext strictly for local Firebase emulator hosts. -->
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="false">10.0.2.2</domain>
        <domain includeSubdomains="false">127.0.0.1</domain>
        <domain includeSubdomains="false">localhost</domain>
    </domain-config>
</network-security-config>
```

- [ ] **Step 5: Verify** — `./gradlew :composeApp:assembleDebug :composeApp:assembleRelease` both green; inspect the merged release manifest (`composeApp/build/intermediates/merged_manifests/release*/AndroidManifest.xml`) contains NO `networkSecurityConfig`; the merged debug manifest does. Run `./gradlew :composeApp:allTests` + `detekt`.

- [ ] **Step 6: Docs** — runbook: storage emulator now part of `firebase emulators:start --config firebase.emulator.json`; delete the "create local network config" manual step. Parent-spec §9 updates as listed above.

- [ ] **Step 7: Commit** — `chore(qa): storage emulator + committed debug cleartext config; spec bookkeeping`

---

## Final verification (whole branch)

- `./gradlew :composeApp:allTests detekt` and `cd functions && npm test && npm run test:rules` all green.
- `diff firestore.rules firestore.emulator.rules` empty.
- Grep: no staff-reachable write path uses `updateOrder` (money mirror) — item edits all route through `updateItems`.
- Emulator smoke (owner + staff devices): staff adds a fabric photo end-to-end (upload lands under `users/{workshopUid}/orders/...` via the Storage emulator, visible on the owner device); staff edits notes; due-date pencil absent for staff; owner assign picker shows "You" pinned first and self-assignment works; owner My-work chip filters; Team screen counts match reality and tap-through lands on the filtered list with a clearable chip; Unassigned row works.

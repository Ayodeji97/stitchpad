# Slice 8e — Unblock Staff Read Access — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Staff members see the workshop's orders and customers on their devices — by stripping money/contact off base docs, flipping the Firestore `list` rules to active members, and removing the client's swallow-the-denial empty states.

**Architecture:** Phase 1 of the approved spec `docs/superpowers/specs/2026-08-07-owner-staff-collaboration-design.md`. The stop-dual-write code already exists as committed branch `feat/staff-slice8d1-stop-dual-write` (one commit, `d01c47be`). This plan merges it, adds the missing base-doc strip script, promotes the emulator-only `allow list` flip into production rules, inverts the affected rules tests, removes the ViewModel denial-swallow branches, and documents the production rollout order.

**Tech Stack:** Kotlin Multiplatform + GitLive Firebase SDK (composeApp), Firebase Cloud Functions (TypeScript + plain-Node scripts), Firestore security rules, `@firebase/rules-unit-testing` via Jest, kotlin.test/Turbine for ViewModels.

## Global Constraints

- Firebase project `stitchpad-30607`, region `europe-west1`. Never commit `google-services.json` / `GoogleService-Info.plist`.
- **Production rollout order is sacred** (from `docs/staff/slice8b-backfill-runbook.md` / `slice8c-version-floor-runbook.md`): 8b backfill (ownerId stamped on every `/private` doc) → release the 8d-1 client → 8c version floor set + adopted → **Firestore export** → strip script `--commit` → deploy flipped rules. Code in this plan can all merge first; the runbook (Task 6) gates the deploys.
- The `ownerId` field on `/private/money` and `/private/contact` is the **completeness sentinel**: after the strip, base money fields default to `0.0`/empty on read, so a doc without a stamped ownerId would silently read ₦0. The strip script must therefore skip (and count) any doc whose private mirror is missing or unstamped.
- Kotlin verification commands: `./gradlew :composeApp:allTests` and `./gradlew detekt`. Functions: `cd functions && npm test`; rules: `cd functions && npm run test:rules` (needs `firebase-tools` on PATH; it self-starts the Firestore emulator).
- Scripts are dry-run by default; only `--commit` writes (house pattern from `backfillSensitiveFields.js`).
- Work happens on a new branch `feat/staff-slice8e` cut from `main`. Do NOT use the directory `.claude/worktrees/staff-slice8d1-stop-dual-write` — its `.git` pointer is stale and broken; the branch itself is intact in the main repo.

---

### Task 1: Merge the existing stop-dual-write branch (Slice 8d-1)

**Files:**
- No new files — merges branch `feat/staff-slice8d1-stop-dual-write` (touches `FirebaseOrderRepository.kt`, `FirebaseCustomerRepository.kt`, `OfflineUploadOutbox.kt`, `OrderDto.kt`, `CustomerDto.kt`, `OrderBaseMapper.kt` (new), `CustomerMapper.kt`, plus 4 test files; +423/−22)

**Interfaces:**
- Produces: `Order.toOrderBaseDto()`, `OrderDto.toBaseDto()`, `Customer.toCustomerBaseDto()`, `CustomerDto.toBaseDto()` — money/contact-free write DTOs used by all seven client write sites. Later tasks assume base docs written by this client contain **no** `totalPrice`/`discount`/`discountReason`/`depositPaid`/`balanceRemaining`/`payments`/`costs`/`items[].price` and no `phone`/`email`/`address`.

- [ ] **Step 1: Cut the branch and merge**

```bash
git checkout main && git pull
git checkout -b feat/staff-slice8e
git merge --no-ff feat/staff-slice8d1-stop-dual-write -m "merge: Slice 8d-1 — stop dual-writing money/contact to base docs"
```

Expected: clean merge (branch is one commit ahead of `a23f4331`; conflicts only if main moved over the same write paths — resolve keeping the Base-DTO write sites).

- [ ] **Step 2: Run the Kotlin test suite**

Run: `./gradlew :composeApp:allTests`
Expected: PASS, including the merged `OrderBaseMapperTest`, `CustomerBaseMapperTest`, `CustomerBaseWriteTest`, `OrderOfflineWriteRegressionTest`.

- [ ] **Step 3: Run detekt**

Run: `./gradlew detekt`
Expected: PASS. Known risk from the branch: the `@Suppress("SpreadOperator")` annotation moved from `updateCosts` to `recordPayment` — if detekt flags either, fix the suppression placement, do not add new suppressions.

- [ ] **Step 4: Verify no base-doc money writes remain**

Run: `grep -n "toOrderDto()" composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/data/FirebaseOrderRepository.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/offline/OfflineUploadOutbox.kt`
Expected: zero hits on write paths (`toOrderDto()` may remain only in read/mapper code; every `docRef.set` of a full order uses `toOrderBaseDto()`/`.toBaseDto()`).

- [ ] **Step 5: Commit is the merge commit — push the branch**

```bash
git push -u origin feat/staff-slice8e
```

---

### Task 2: Base-doc strip script

**Files:**
- Create: `functions/scripts/stripBaseSensitiveFields.js`
- Test: `functions/src/__tests__/staff/stripBaseSensitiveFields.test.ts`

**Interfaces:**
- Consumes: base docs in the pre-8d shape (money/contact present) and `/private` mirrors stamped by the 8b backfill (`buildMoneyDoc`/`buildContactDoc` shape from `functions/scripts/backfillSensitiveFields.js`).
- Produces: exported pure helpers `buildOrderStrip(order, fieldValue)` and `buildCustomerStrip(customer, fieldValue)` returning Firestore update maps; CLI `GOOGLE_CLOUD_PROJECT=stitchpad-30607 node scripts/stripBaseSensitiveFields.js [--commit]` printing `DRY RUN|COMMITTED — users=<N> ordersStripped=<a> ordersClean=<b> ordersSkippedUnstamped=<c> customersStripped=<d> customersClean=<e> customersSkippedUnstamped=<f>`.

- [ ] **Step 1: Write the failing unit test**

Create `functions/src/__tests__/staff/stripBaseSensitiveFields.test.ts`:

```typescript
// eslint-disable-next-line @typescript-eslint/no-var-requires
const {
  buildOrderStrip,
  buildCustomerStrip,
} = require('../../../scripts/stripBaseSensitiveFields.js');

const DELETE = Symbol('delete');
const fieldValue = { delete: () => DELETE };

describe('buildOrderStrip', () => {
  it('deletes every money field present and rewrites items[] without price', () => {
    const update = buildOrderStrip(
      {
        customerName: 'Ada',
        totalPrice: 5000,
        discount: 200,
        discountReason: 'loyal',
        depositPaid: 1000,
        balanceRemaining: 3800,
        payments: [{ id: 'p1', amount: 1000 }],
        costs: [{ id: 'c1', amount: 300 }],
        items: [
          { id: 'i1', garmentType: 'Agbada', price: 5000 },
          { id: 'i2', garmentType: 'Cap' },
        ],
      },
      fieldValue,
    );
    expect(update.totalPrice).toBe(DELETE);
    expect(update.discount).toBe(DELETE);
    expect(update.discountReason).toBe(DELETE);
    expect(update.depositPaid).toBe(DELETE);
    expect(update.balanceRemaining).toBe(DELETE);
    expect(update.payments).toBe(DELETE);
    expect(update.costs).toBe(DELETE);
    expect(update.items).toEqual([
      { id: 'i1', garmentType: 'Agbada' },
      { id: 'i2', garmentType: 'Cap' },
    ]);
    expect(update.customerName).toBeUndefined();
  });

  it('returns an empty map for an already-clean order', () => {
    const update = buildOrderStrip(
      { customerName: 'Ada', status: 'PENDING', items: [{ id: 'i1', garmentType: 'Cap' }] },
      fieldValue,
    );
    expect(update).toEqual({});
  });
});

describe('buildCustomerStrip', () => {
  it('deletes contact fields present and nothing else', () => {
    const update = buildCustomerStrip(
      { name: 'Ada', phone: '0801', email: 'a@x.com', address: 'Lagos', slotState: 'active' },
      fieldValue,
    );
    expect(update).toEqual({ phone: DELETE, email: DELETE, address: DELETE });
  });

  it('returns an empty map for an already-clean customer', () => {
    expect(buildCustomerStrip({ name: 'Ada', slotState: 'active' }, fieldValue)).toEqual({});
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd functions && npx jest src/__tests__/staff/stripBaseSensitiveFields.test.ts`
Expected: FAIL — `Cannot find module '../../../scripts/stripBaseSensitiveFields.js'`

- [ ] **Step 3: Write the script**

Create `functions/scripts/stripBaseSensitiveFields.js` (mirrors `backfillSensitiveFields.js` pagination/batching/dry-run; ADC auth):

```javascript
#!/usr/bin/env node
/**
 * Slice 8d-2 (enables 8e): strip money fields off base order docs and contact
 * fields off base customer docs. Lockstep with the client's OrderBaseDto /
 * CustomerBaseDto (Slice 8d-1) — the fields deleted here are exactly the ones
 * the new client no longer writes.
 *
 * HARD PREREQS (see docs/staff/slice8e-rollout-runbook.md):
 *   - 8b backfill has stamped ownerId on every /private/money and /private/contact
 *   - 8c version floor is live (old clients can no longer re-write these fields)
 *   - A Firestore export has been taken (this is the irreversible step)
 *
 * Safety: a doc whose /private mirror is missing or has a blank ownerId is
 * SKIPPED and counted, never stripped — stripping it would zero the owner's
 * money/contact on read (ownerId is the completeness sentinel in Order.withMoney
 * / Customer.withContact).
 *
 * Dry run by default. Usage:
 *   GOOGLE_CLOUD_PROJECT=stitchpad-30607 node scripts/stripBaseSensitiveFields.js [--commit]
 */
const admin = require('firebase-admin');

const USER_PAGE_SIZE = 200;
const BATCH_LIMIT = 400;

const ORDER_MONEY_FIELDS = [
  'totalPrice',
  'discount',
  'discountReason',
  'depositPaid',
  'balanceRemaining',
  'payments',
  'costs',
];
const CUSTOMER_CONTACT_FIELDS = ['phone', 'email', 'address'];

// Pure. Update map deleting every present money field; items[] is rewritten as a
// whole array minus price (array elements cannot be field-deleted individually).
function buildOrderStrip(order, fieldValue) {
  const update = {};
  for (const field of ORDER_MONEY_FIELDS) {
    if (field in order) {
      update[field] = fieldValue.delete();
    }
  }
  if (Array.isArray(order.items) && order.items.some((item) => item && 'price' in item)) {
    update.items = order.items.map((item) => {
      const { price, ...rest } = item;
      return rest;
    });
  }
  return update;
}

// Pure. Update map deleting every present contact field.
function buildCustomerStrip(customer, fieldValue) {
  const update = {};
  for (const field of CUSTOMER_CONTACT_FIELDS) {
    if (field in customer) {
      update[field] = fieldValue.delete();
    }
  }
  return update;
}

async function commitBatched(db, writes) {
  for (let i = 0; i < writes.length; i += BATCH_LIMIT) {
    const batch = db.batch();
    for (const write of writes.slice(i, i + BATCH_LIMIT)) {
      batch.update(write.ref, write.data);
    }
    await batch.commit();
  }
}

async function isStamped(db, privatePath) {
  const snap = await db.doc(privatePath).get();
  return snap.exists && typeof snap.get('ownerId') === 'string' && snap.get('ownerId').length > 0;
}

async function main() {
  const commit = process.argv.includes('--commit');
  admin.initializeApp();
  const db = admin.firestore();
  const fieldValue = admin.firestore.FieldValue;

  let users = 0;
  const counts = {
    ordersStripped: 0,
    ordersClean: 0,
    ordersSkippedUnstamped: 0,
    customersStripped: 0,
    customersClean: 0,
    customersSkippedUnstamped: 0,
  };

  let cursor = null;
  for (;;) {
    let query = db
      .collection('users')
      .orderBy(admin.firestore.FieldPath.documentId())
      .limit(USER_PAGE_SIZE);
    if (cursor) {
      query = query.startAfter(cursor);
    }
    const page = await query.get();
    if (page.empty) {
      break;
    }

    for (const userDoc of page.docs) {
      users += 1;
      const uid = userDoc.id;
      const writes = [];

      const ordersSnap = await db.collection(`users/${uid}/orders`).get();
      for (const orderDoc of ordersSnap.docs) {
        const update = buildOrderStrip(orderDoc.data(), fieldValue);
        if (Object.keys(update).length === 0) {
          counts.ordersClean += 1;
        } else if (await isStamped(db, `users/${uid}/orders/${orderDoc.id}/private/money`)) {
          counts.ordersStripped += 1;
          writes.push({ ref: orderDoc.ref, data: update });
        } else {
          counts.ordersSkippedUnstamped += 1;
          console.warn(`SKIP unstamped money: users/${uid}/orders/${orderDoc.id}`);
        }
      }

      const customersSnap = await db.collection(`users/${uid}/customers`).get();
      for (const customerDoc of customersSnap.docs) {
        const update = buildCustomerStrip(customerDoc.data(), fieldValue);
        if (Object.keys(update).length === 0) {
          counts.customersClean += 1;
        } else if (await isStamped(db, `users/${uid}/customers/${customerDoc.id}/private/contact`)) {
          counts.customersStripped += 1;
          writes.push({ ref: customerDoc.ref, data: update });
        } else {
          counts.customersSkippedUnstamped += 1;
          console.warn(`SKIP unstamped contact: users/${uid}/customers/${customerDoc.id}`);
        }
      }

      if (commit && writes.length > 0) {
        await commitBatched(db, writes);
      }
    }

    if (page.size < USER_PAGE_SIZE) {
      break;
    }
    cursor = page.docs[page.docs.length - 1];
  }

  const label = commit ? 'COMMITTED' : 'DRY RUN';
  console.log(
    `${label} — users=${users} ` +
      `ordersStripped=${counts.ordersStripped} ordersClean=${counts.ordersClean} ` +
      `ordersSkippedUnstamped=${counts.ordersSkippedUnstamped} ` +
      `customersStripped=${counts.customersStripped} customersClean=${counts.customersClean} ` +
      `customersSkippedUnstamped=${counts.customersSkippedUnstamped}`,
  );
}

module.exports = { buildOrderStrip, buildCustomerStrip, ORDER_MONEY_FIELDS, CUSTOMER_CONTACT_FIELDS };

if (require.main === module) {
  main().catch((err) => {
    console.error(err);
    process.exitCode = 1;
  });
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd functions && npx jest src/__tests__/staff/stripBaseSensitiveFields.test.ts`
Expected: PASS (4 tests). Then the full suite: `npm test` — PASS. And `npm run lint` — PASS (fix any eslint complaints inline; the `require` in the .ts test needs the `no-var-requires` disable shown above).

- [ ] **Step 5: Commit**

```bash
git add functions/scripts/stripBaseSensitiveFields.js functions/src/__tests__/staff/stripBaseSensitiveFields.test.ts
git commit -m "feat(staff): base-doc strip script for Slice 8d-2 (money + contact)"
```

---

### Task 3: Flip the `allow list` rules + invert rules tests

**Files:**
- Modify: `firestore.rules` (customers `allow list` ~line 275; orders `allow list` ~line 333)
- Modify: `firestore.emulator.rules` (restore byte-parity with `firestore.rules`)
- Test: `functions/src/__tests__/firestore.rules.test.ts` (the `active staff member access` describe, ~lines 764–830)

**Interfaces:**
- Consumes: `isActiveMember(uid)` / `isOwner(uid)` helpers (unchanged); `staffDb(staffUid, workshopUid)` and `asAdmin(work)` test helpers already defined in `firestore.rules.test.ts`.
- Produces: production rules where active members can `list` orders and customers. The per-doc field-absence guards on `allow get` are **kept** (defence-in-depth); the money/contact `/private` walls are untouched.

- [ ] **Step 1: Update the rules tests first (they must fail against current rules)**

In `functions/src/__tests__/firestore.rules.test.ts`, inside `describe('active staff member access')`:

Replace the test at ~line 815 titled `'cannot LIST the orders or customers collections (the GET-gate does not cover list)'` with:

```typescript
    it('can LIST stripped orders and customers collections (Slice 8e flip)', async () => {
      await asAdmin(async (db) => {
        await setDoc(doc(db, 'users/alice/memberships/chidi'), {
          role: 'staff',
          status: 'active',
          workshopUid: 'alice',
        });
        // Base docs in the post-8d stripped shape: no money, no contact.
        await setDoc(doc(db, 'users/alice/orders/o-stripped'), {
          customerName: 'Ada',
          status: 'PENDING',
          items: [{ id: 'i1', garmentType: 'Agbada' }],
          createdAt: 1,
          updatedAt: 1,
        });
        await setDoc(doc(db, 'users/alice/customers/c-stripped'), {
          name: 'Ada',
          slotState: 'active',
          createdAt: 1,
          updatedAt: 1,
        });
      });
      // NOTE: `allow list` has no per-doc field guard — rules are not query
      // filters. The 8d strip + version floor are the guarantee that no base doc
      // carries money/contact by the time this rule is deployed.
      await assertSucceeds(getDocs(collection(staffDb('chidi', 'alice'), 'users/alice/orders')));
      await assertSucceeds(getDocs(collection(staffDb('chidi', 'alice'), 'users/alice/customers')));
    });

    it('staff of another workshop still cannot LIST', async () => {
      await asAdmin(async (db) => {
        await setDoc(doc(db, 'users/alice/orders/o1'), {
          customerName: 'Ada',
          status: 'PENDING',
          createdAt: 1,
          updatedAt: 1,
        });
      });
      await assertFails(getDocs(collection(staffDb('mallory', 'bob'), 'users/alice/orders')));
      await assertFails(getDocs(collection(staffDb('mallory', 'bob'), 'users/alice/customers')));
    });
```

Keep unchanged: the `'is denied a base doc that still carries sensitive fields (dual-write window)'` GET test (~line 799 — the get-guard stays), the `'owner can still LIST…'` test (~line 826), and the whole `'owner-only /private sub-docs (money + contact wall)'` describe (~line 731) — that is the money-wall regression suite and it must stay green throughout.

Adjust imports/seeding to match the file's existing style (it already imports `setDoc`, `doc`, `getDocs`, `collection`, `assertSucceeds`, `assertFails`; membership seeding via `asAdmin` follows the existing tests in this describe — reuse their exact seeding lines if they differ from the above).

- [ ] **Step 2: Run rules tests to verify the new test fails**

Run: `cd functions && npm run test:rules`
Expected: FAIL — `can LIST stripped orders and customers` fails with permission-denied; everything else passes.

- [ ] **Step 3: Flip the two lines in `firestore.rules`**

Customers block (~line 275) — replace:

```
        allow list: if isOwner(uid);
```

with:

```
        // Slice 8e: members may LIST — base docs are contact-free (8d strip +
        // version floor). Rules are not query filters; the strip is the guard.
        // The field-absence guard on `get` above stays as defence-in-depth.
        allow list: if isOwner(uid) || isActiveMember(uid);
```

Orders block (~line 333) — replace:

```
        allow list: if isOwner(uid);
```

with:

```
        // Slice 8e: members may LIST — base docs are money-free (8d strip +
        // version floor). Rules are not query filters; the strip is the guard.
        // The field-absence guard on `get` above stays as defence-in-depth.
        allow list: if isOwner(uid) || isActiveMember(uid);
```

Also update the now-stale sentence in each block's leading comment ("So members are denied LIST until Slice 8 strips…flip `allow list`") to past tense ("Members were denied LIST during the dual-write window; Slice 8e flipped it after the 8d strip.").

- [ ] **Step 4: Restore emulator-rules parity**

Copy the final production file over the preview: the two files must be byte-identical again.

```bash
cp firestore.rules firestore.emulator.rules
diff firestore.rules firestore.emulator.rules && echo PARITY-OK
```

Expected: `PARITY-OK`.

- [ ] **Step 5: Run rules tests to verify they pass**

Run: `cd functions && npm run test:rules`
Expected: PASS — including the kept money-wall describe and the kept get-guard test.

- [ ] **Step 6: Commit**

```bash
git add firestore.rules firestore.emulator.rules functions/src/__tests__/firestore.rules.test.ts
git commit -m "feat(staff): Slice 8e — open orders/customers LIST to active members"
```

---

### Task 4: Remove the client swallow-denial empty states

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/list/OrderListViewModel.kt` (~lines 125–200, two branches)
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/list/CustomerListViewModel.kt` (~lines 231–251)
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/order/presentation/list/OrderListStaffTest.kt`, `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/customer/presentation/list/CustomerListViewModelTest.kt` (staff section ~lines 183–224)

**Interfaces:**
- Consumes: `Result.Error` from `FakeOrderRepository`/`FakeCustomerRepository` flows; `toOrderUiText()` / `toCustomerUiText()` mappers.
- Produces: staff sessions surface repository errors exactly like owners (`errorMessage != null`). `DashboardViewModel` needs **no change** — it never swallowed the denial (it degrades via `as? Result.Success` and still sets `errorMessage`); its `moneyFree()` scrub and `goalFlowFor` short-circuit are the state-level money wall and MUST be kept.

- [ ] **Step 1: Update the staff ViewModel tests to the new expected behavior**

In `OrderListStaffTest.kt` and `CustomerListViewModelTest.kt`, find the tests asserting that a repository error under a staff session yields an empty list with `errorMessage == null` (the swallow). Change the assertions: a repository `Result.Error` now yields `errorMessage != null` for staff too, e.g.:

```kotlin
    @Test
    fun `staff session surfaces list errors like an owner`() = runTest {
        setStaffSession()
        fakeOrderRepository.ordersResult = Result.Error(DataError.Network.PERMISSION_DENIED)
        val viewModel = createViewModel()
        assertNotNull(viewModel.state.value.errorMessage)
    }
```

(Adapt the fake's error-injection to each file's existing mechanism — both fakes already drive `Result.Error` emissions in the current swallow tests; keep their setup lines and flip only the assertions. Keep every other staff test — money-affordance hiding, `isActiveStaff` wiring — untouched.)

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :composeApp:allTests`
Expected: FAIL — only the flipped assertions (current code still swallows to `errorMessage = null`).

- [ ] **Step 3: Remove the swallow branches**

`OrderListViewModel.kt` `observeOrders` error branch — replace the whole `if (state.isActiveStaff) … else …` block with the owner path only:

```kotlin
                    is Result.Error -> {
                        _state.update { state ->
                            state.copy(isLoading = false, errorMessage = result.error.toOrderUiText())
                        }
                    }
```

(Also delete the now-dead `allOrders = emptyList()` line that lived inside the staff branch.)

`OrderListViewModel.kt` `observeArchivedOrders` branch — remove the `state.isActiveStaff -> null` arm:

```kotlin
                        _state.update { state ->
                            state.copy(
                                isArchivedLoading = false,
                                errorMessage = if (state.showArchived) {
                                    result.error.toOrderUiText()
                                } else {
                                    state.errorMessage
                                },
                            )
                        }
```

`CustomerListViewModel.kt` error branch — same surgery:

```kotlin
                    is Result.Error -> {
                        _state.update { state ->
                            state.copy(isLoading = false, errorMessage = result.error.toCustomerUiText())
                        }
                    }
```

(Delete the staff-branch-only `allCustomers = emptyList()` / `allLockedCustomers = emptyList()` lines with it.)

Keep `isActiveStaff` in both states — it still drives money-affordance hiding elsewhere.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :composeApp:allTests` then `./gradlew detekt`
Expected: both PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/list/OrderListViewModel.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/list/CustomerListViewModel.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/order/presentation/list/OrderListStaffTest.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/customer/presentation/list/CustomerListViewModelTest.kt
git commit -m "feat(staff): surface list errors to staff — denial-swallow removed with the 8e flip"
```

---

### Task 5: Emulator seeder + smoke runbook match the post-strip world

**Files:**
- Modify: `functions/scripts/emulatorSetupStaff.js` (seeds base docs "in the full dual-write shape" today)
- Modify: `docs/staff/slice8-emulator-smoke-runbook.md` (references the emulator-only rules preview that no longer differs)

**Interfaces:**
- Consumes: the seeder's fixed UIDs (owner Fola `8J1YLUh32yQTLcBlAaOe7MUZKay2`, staff Gabby `pulzPGPXnRgRk8hZnof3LoFJPaj1`) and existing seed docs.
- Produces: seeded base order docs with **no** `totalPrice`/`discount`/`discountReason`/`depositPaid`/`balanceRemaining`/`payments`/`costs` and no `items[].price`; seeded base customer docs with no `phone`/`email`/`address`; all money/contact living only in `/private/money` / `/private/contact` with `ownerId` stamped (the private-doc seeding already exists — only the base shape changes).

- [ ] **Step 1: Read the seeder and move sensitive fields out of base seeds**

Open `functions/scripts/emulatorSetupStaff.js`. For every seeded order object written to `users/{fola}/orders/{id}`: delete the seven money fields and each `items[].price` from the base payload (they must already appear in the corresponding `/private/money` payload — verify `ownerId` is set there; add any missing values rather than losing them). For every seeded customer at `users/{fola}/customers/{id}`: delete `phone`/`email`/`address` from the base payload (verify they exist in `/private/contact` with `ownerId`). Update the file's comment from "full dual-write shape" to "post-8d stripped shape (base clean, /private authoritative)".

- [ ] **Step 2: Verify against the emulator**

```bash
firebase emulators:start --config firebase.emulator.json &
sleep 15 && cd functions && node scripts/emulatorSetupStaff.js
```

Expected: seeder exits 0. In the Emulator UI (http://localhost:4000): a seeded base order doc shows no money keys; its `private/money` shows `ownerId` + values; a base customer shows no contact keys. Then stop the emulator.

- [ ] **Step 3: Update the smoke runbook**

In `docs/staff/slice8-emulator-smoke-runbook.md`: remove/replace wording that describes `firestore.emulator.rules` as a "Slice 8e preview" that differs from prod (Task 3 made them identical — keep the two-file mechanism, it still exists for future previews). Update the expected smoke outcome: the staff account (Gabby) now sees the seeded orders and customers lists populated, with no money amounts anywhere and no contact fields on customer rows; the owner (Fola) still sees money. Add one line: "If staff lists are empty, check the membership doc status is `active` and the claims were set by the seeder."

- [ ] **Step 4: Commit**

```bash
git add functions/scripts/emulatorSetupStaff.js docs/staff/slice8-emulator-smoke-runbook.md
git commit -m "chore(staff): seed post-strip base docs; smoke runbook covers populated staff lists"
```

---

### Task 6: Production rollout runbook

**Files:**
- Create: `docs/staff/slice8e-rollout-runbook.md`

**Interfaces:**
- Consumes: `functions/scripts/backfillSensitiveFields.js` (8b), `functions/scripts/setUpdateFloor.js` (8c), `functions/scripts/stripBaseSensitiveFields.js` (Task 2), the flipped `firestore.rules` (Task 3).
- Produces: the ordered, gated deploy procedure. No code depends on this; humans do.

- [ ] **Step 1: Write the runbook**

Create `docs/staff/slice8e-rollout-runbook.md`:

```markdown
# Slice 8e Rollout — Strip Base Docs + Flip Staff LIST

Sequencing (from 8b/8c runbooks): 8a → 8b (backfill) → release 8d-1 client →
8c (version floor) → wait for adoption → **8d-2 strip (this, irreversible)** →
**8e rules flip (this)**.

## Gate checklist — all YES before the strip

- [ ] 8b backfill ran with `--commit` and counts verified
      (`node scripts/backfillSensitiveFields.js` dry run now reports every doc
      already mirrored; spot-check `/private/money.ownerId` on a legacy order).
- [ ] The 8d-1 client (branch `feat/staff-slice8d1-stop-dual-write`, merged in
      `feat/staff-slice8e`) is released on BOTH stores.
- [ ] 8c floor set to the 8d-1 build numbers via
      `ANDROID_FLOOR=<versionCode> IOS_FLOOR=<CFBundleVersion> GOOGLE_CLOUD_PROJECT=stitchpad-30607 node scripts/setUpdateFloor.js --commit`
      and a below-floor build verifiably shows the force-update screen.
- [ ] Firestore export taken:
      `gcloud firestore export gs://stitchpad-30607-exports/pre-8d-strip-$(date +%Y%m%d) --project=stitchpad-30607`

## Strip (irreversible)

1. Dry run: `cd functions && GOOGLE_CLOUD_PROJECT=stitchpad-30607 node scripts/stripBaseSensitiveFields.js`
   - Expect `DRY RUN — …`; investigate every `ordersSkippedUnstamped` /
     `customersSkippedUnstamped` warning BEFORE committing (unstamped docs are
     never stripped; fix by re-running the 8b backfill for those users).
2. Apply: same command + `--commit`. Counts must match the dry run.
3. Verify: owner device still shows correct money and contact (reads come from
   `/private` via the collection-group join); console spot-check shows clean
   base docs.

## Rules flip

1. `firebase deploy --only firestore:rules --project=stitchpad-30607`
2. Verify on devices: staff account's Orders + Customers lists populate; no
   money or contact visible anywhere on the staff build; owner unaffected.

## Rollback

- Rules: redeploy the previous `firestore.rules` (git revert of the Task 3
  commit) — staff lists go dark again, nothing else changes.
- Strip: restore from the export
  (`gcloud firestore import gs://…/pre-8d-strip-<date>`) — full-DB restore;
  only for catastrophic data loss, expect to lose writes made since the export.
- Panic switch: set `staffFeatureEnabled: false` on `config/app` — every staff
  session resolves to owner-of-self and stops reading the workshop tree.
```

- [ ] **Step 2: Commit**

```bash
git add docs/staff/slice8e-rollout-runbook.md
git commit -m "docs(staff): Slice 8e rollout runbook — gates, strip, flip, rollback"
```

---

### Task 7: Full-suite verification + PR

**Files:**
- No new files.

**Interfaces:**
- Consumes: everything above.
- Produces: green branch `feat/staff-slice8e` ready for review.

- [ ] **Step 1: Run everything**

```bash
./gradlew :composeApp:allTests detekt
cd functions && npm test && npm run lint && npm run test:rules && npm run build
```

Expected: all PASS.

- [ ] **Step 2: Open the PR**

```bash
git push
gh pr create --base main --title "feat(staff): Slice 8e — unblock staff read (strip + rules flip + client cleanup)" \
  --body "Merges Slice 8d-1 (stop dual-write), adds the base-doc strip script, flips orders/customers LIST to active members, removes the client denial-swallow, aligns emulator seeds, and adds the rollout runbook. Spec: docs/superpowers/specs/2026-08-07-owner-staff-collaboration-design.md (Phase 1). Deploy is gated by docs/staff/slice8e-rollout-runbook.md — merging this PR does NOT strip or flip anything in prod.

🤖 Generated with [Claude Code](https://claude.com/claude-code)"
```

# Staff Phase 2a — Roster, Assignment & Session Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Owners assign orders to team members (including name-only members without the app), staff self-claim unassigned orders and filter to "My work" — plus the kill-switch propagation fix and role-gating of every staff affordance the rules deny.

**Architecture:** Phase 2 of `docs/superpowers/specs/2026-08-07-owner-staff-collaboration-design.md`. Assignment lives as two denormalized fields on the base order doc (plain offline-capable writes); the roster is a new `users/{workshopUid}/team/{memberId}` collection where logged-in staff docs are Admin-SDK-written by the approve/revoke callables and name-only members are owner-written from the Team screen. Staff garment media (rules `items` widening + storage.rules staff branch) is deliberately NOT here — it is the follow-up Phase 2b plan.

**Tech Stack:** Kotlin Multiplatform + GitLive Firebase SDK, Firebase Cloud Functions (TypeScript), Firestore security rules, kotlin.test/Turbine, Jest + @firebase/rules-unit-testing.

## Global Constraints

- Work happens on branch `feat/staff-phase2-assignment` cut from `main` AFTER PR #350 (Slice 8e) merges. All paths below are repo-relative; `cA/` = `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/`.
- MVI patterns per CLAUDE.md: State/Action/Event + ViewModel; Root/Screen split; `UiText` for user copy; compose-resources strings (never hardcoded); previews for every new Screen composable; all state in ViewModels.
- Money/contact walls are untouchable: assignment fields are non-sensitive by design; nothing in this plan may widen staff access to `/private` docs or money keys.
- Staff order-writes stay whitelist-based. This plan adds ONLY the claim write (`assignedMemberId`/`assignedMemberName`/`updatedAt`, null→self). `items`, `notes`, `deadline` remain staff-denied (Phase 2b decides media; due date is owner-only per product decision 2026-08-08).
- Verification commands: `./gradlew :composeApp:testDebugUnitTest detekt` (Kotlin gate; `allTests` where the env has iOS pods), `cd functions && npm test && npm run lint`, `cd functions && npm run test:rules`.
- `firestore.emulator.rules` must be byte-identical to `firestore.rules` after every rules task (`cp firestore.rules firestore.emulator.rules`).
- Firestore paths: roster `users/{workshopUid}/team/{memberId}`; orders `users/{workshopUid}/orders/{orderId}`. Roster doc ids for logged-in staff ARE their auth uid; name-only members get generated ids.
- Roster doc shape (spec §3.1): `{ name: String, colorSeed: Int, kind: "staff"|"named", status: "active"|"archived", createdAt: Long, updatedAt: Long }`.

---

### Task 1: Kill-switch / session propagation fix

The smoke finding: flipping `config/app.staffFeatureEnabled=false` only takes effect on app restart. Root cause (exploration 2026-08-08): the session flow itself propagates correctly, but (a) `NavGraph.kt:99-126` resolves the start destination once via `flow.first { }`, and (b) every repository listener pins the workshop uid from a one-shot `workshopUidOrNull()` call. A demoted staff session keeps its live listeners on the owner's tree.

**Files:**
- Modify: `cA/navigation/NavGraph.kt:99-126` (start-route resolution) and the post-auth composable that hosts it
- Modify: `cA/feature/order/presentation/list/OrderListViewModel.kt:125-131` (`observeOrders`), the archived variant, `cA/feature/customer/presentation/list/CustomerListViewModel.kt` (same pattern), `cA/feature/dashboard/presentation/DashboardViewModel.kt` (its `loadData` workshop-uid read)
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/order/presentation/list/OrderListStaffTest.kt` (new re-resolution test), sibling customer/dashboard tests

**Interfaces:**
- Consumes: `ActiveWorkshopProvider.flow: StateFlow<WorkshopSession>` (exists), `WorkshopSession.workshopUid`/`role`.
- Produces: list/dashboard ViewModels re-subscribe their Firestore listeners whenever `workshopUid` changes; the nav host re-routes when `role` changes mid-session. No signature changes.

- [ ] **Step 1: Write the failing ViewModel test** — in `OrderListStaffTest.kt`, using the file's existing `FakeActiveWorkshopProvider` (it exposes a `MutableStateFlow`-backed session):

```kotlin
    @Test
    fun `workshop change mid-session re-subscribes the orders listener`() = runTest {
        setStaffSession() // workshopUid = "o"
        fakeOrderRepository.setOrdersFor("o", listOf(order(id = "owned-by-o")))
        fakeOrderRepository.setOrdersFor("s", listOf(order(id = "owned-by-s")))
        val viewModel = createViewModel()
        assertEquals(listOf("owned-by-o"), viewModel.state.value.orders.map { it.id })

        // Kill switch drops the session to owner-of-self: workshopUid becomes the auth uid.
        fakeActiveWorkshopProvider.emit(WorkshopSession.ownerOfSelf("s"))
        runCurrent()
        assertEquals(listOf("owned-by-s"), viewModel.state.value.orders.map { it.id })
    }
```

(Adapt `setOrdersFor`/`emit` to the fakes' real APIs — if `FakeOrderRepository` keys a single flow, add a per-uid map variant to the fake in this step.)

- [ ] **Step 2: Run to verify it fails** — `./gradlew :composeApp:testDebugUnitTest --tests "*OrderListStaffTest*"`. Expected: FAIL — the list still shows `owned-by-o` (listener pinned at subscribe time).

- [ ] **Step 3: Re-structure `observeOrders` around the session flow** — in `OrderListViewModel.kt` replace the one-shot uid read:

```kotlin
    private fun observeOrders() {
        viewModelScope.launch {
            activeWorkshopProvider.flow
                .map { it.workshopUid }
                .distinctUntilChanged()
                .flatMapLatest { workshopUid ->
                    if (workshopUid == null) flowOf(null)
                    else orderRepository.observeOrders(workshopUid)
                }
                .collect { result ->
                    if (result == null) {
                        _state.update { it.copy(isLoading = false, orders = emptyList()) }
                        return@collect
                    }
                    // existing Result.Success / Result.Error handling body unchanged
                }
        }
    }
```

Apply the same shape to `observeArchivedOrders`, `CustomerListViewModel.observeCustomers`, and the dashboard's data flow (`DashboardViewModel` — wrap its combined load in the same `flatMapLatest` over `workshopUid`). Keep every existing success/error branch verbatim; only the subscription head changes.

- [ ] **Step 4: Live nav re-route** — in the NavGraph host (where `:99-126` resolves the start destination), add a session observer that reacts to role changes after initial resolution:

```kotlin
    // Kill-switch / revocation must bite mid-session, not on next restart: when the
    // resolved role changes (staff↔owner), pop to Home so every screen re-renders
    // under the new session. Initial resolution above stays one-shot.
    LaunchedEffect(authUid) {
        if (authUid == null) return@LaunchedEffect
        activeWorkshopProvider.flow
            .map { it.role }
            .distinctUntilChanged()
            .drop(1) // skip the initially-resolved role
            .collect {
                navController.navigate(HomeRoute) {
                    popUpTo(navController.graph.id) { inclusive = false }
                    launchSingleTop = true
                }
            }
    }
```

(Match the file's actual route object for Home and its NavController idioms; the exploration notes `session.isActiveStaff -> HomeRoute` in the one-shot resolution.)

- [ ] **Step 5: Run tests + detekt** — `./gradlew :composeApp:testDebugUnitTest detekt`. Expected: PASS incl. the new test and all existing staff tests.

- [ ] **Step 6: Commit** — `git add -A && git commit -m "fix(session): propagate workshop/role changes to live listeners and navigation"`

---

### Task 2: Roster lifecycle in the staff Cloud Functions

**Files:**
- Modify: `functions/src/staff/staffConstants.ts`, `functions/src/staff/approveStaffMember.ts:34-79`, `functions/src/staff/revokeStaffMember.ts:29-46`, `functions/src/staff/cancelStaffMembership.ts:35-61`
- Test: `functions/src/__tests__/staff/approveRevokeStaffMember.test.ts`, `functions/src/__tests__/staff/cancelStaffMembership.test.ts`

**Interfaces:**
- Consumes: `membershipDocPath`, `StaffClaimsDeps`, the in-memory `makeStaffDb` harness.
- Produces: `teamMemberDocPath(ownerUid: string, memberId: string): string` and `colorSeedFor(id: string): number` in `staffConstants.ts`; approve writes the roster doc `{name, kind:'staff', status:'active', colorSeed, createdAt, updatedAt}` inside its existing transaction; revoke/cancel set roster `status:'archived', updatedAt`.

- [ ] **Step 1: Write the failing tests** — extend `approveRevokeStaffMember.test.ts`:

```ts
  it('approve creates the staff roster doc in the same transaction', async () => {
    const claims = makeClaimsRecorder();
    const { db, store } = makeStaffDb({
      'users/alice/memberships/chidi': { status: 'pending', staffName: 'Chidi O' },
    });
    await approveStaffMemberHandler({ staffAuthUid: 'chidi' }, authedCtx('alice'), deps(db, claims));
    expect(store.get('users/alice/team/chidi')).toMatchObject({
      name: 'Chidi O', kind: 'staff', status: 'active',
    });
    expect(typeof (store.get('users/alice/team/chidi') as { colorSeed?: number }).colorSeed).toBe('number');
  });

  it('revoke archives the roster doc but keeps it resolvable', async () => {
    const { db, store } = makeStaffDb({
      'users/alice/memberships/chidi': { status: 'active' },
      'users/alice/team/chidi': { name: 'Chidi O', kind: 'staff', status: 'active', colorSeed: 3 },
    });
    await revokeStaffMemberHandler({ staffAuthUid: 'chidi' }, authedCtx('alice'), deps(db));
    expect(store.get('users/alice/team/chidi')).toMatchObject({ status: 'archived', name: 'Chidi O' });
  });
```

Add the mirror test in `cancelStaffMembership.test.ts` (staff-initiated leave archives their own roster doc). Also: approve when the roster doc already exists (re-approve after cancel) must set `status: 'active'` again via merge.

- [ ] **Step 2: Run to verify they fail** — `cd functions && npx jest src/__tests__/staff/approveRevokeStaffMember.test.ts src/__tests__/staff/cancelStaffMembership.test.ts`. Expected: FAIL (no roster writes yet).

- [ ] **Step 3: Implement** — `staffConstants.ts`:

```ts
export function teamMemberDocPath(ownerUid: string, memberId: string): string {
  return `users/${ownerUid}/team/${memberId}`;
}

// Deterministic avatar hue bucket (0..9) so a member keeps their color across
// devices without storing UI state anywhere else.
export function colorSeedFor(id: string): number {
  let h = 0;
  for (let i = 0; i < id.length; i += 1) {
    h = (h * 31 + id.charCodeAt(i)) >>> 0;
  }
  return h % 10;
}
```

`approveStaffMember.ts` — inside the existing `runTransaction`, after the membership `tx.update`, read `staffName` from the tx snapshot and add:

```ts
      const staffName =
        ((snap.data() as { staffName?: string }).staffName ?? '').trim() || 'Staff member';
      tx.set(
        deps.db.doc(teamMemberDocPath(ownerUid, staffAuthUid)),
        {
          name: staffName,
          kind: 'staff',
          status: 'active',
          colorSeed: colorSeedFor(staffAuthUid),
          createdAt: nowMs,
          updatedAt: nowMs,
        },
        { merge: true },
      );
```

(merge:true so a re-approve reactivates without clobbering an owner rename.) `revokeStaffMember.ts` — after the membership `ref.update`, best-effort:

```ts
  try {
    await deps.db.doc(teamMemberDocPath(ownerUid, staffAuthUid)).set(
      { status: 'archived', updatedAt: nowMs },
      { merge: true },
    );
  } catch { /* roster archive is best-effort; attribution stays resolvable either way */ }
```

`cancelStaffMembership.ts` — same best-effort archive after its transaction (owner uid is `workshopUid`, member id is `staffAuthUid`).

- [ ] **Step 4: Run to green** — same jest command, then `npm test && npm run lint && npm run build`. Expected: all PASS.

- [ ] **Step 5: Commit** — `git commit -m "feat(staff): roster docs follow the membership lifecycle (approve creates, revoke/cancel archive)"`

---

### Task 3: Rules — team collection + staff claim write

**Files:**
- Modify: `firestore.rules` (orders `allow update` staff branch at ~L406-424; new `match /team/{memberId}` block beside `match /memberships/{staffAuthUid}`), then `cp firestore.rules firestore.emulator.rules`
- Test: `functions/src/__tests__/firestore.rules.test.ts`

**Interfaces:**
- Consumes: `isOwner`, `isActiveMember`, `canReadWorkshop`, `serverCreatedAtProtectedOnUpdate`, `activityCreatedAtStableOnUpdate`, the `staffDb`/`asAdmin` test helpers.
- Produces: owner assignment writes already pass (assignment keys are not money keys); staff gain EXACTLY the claim write; team collection readable by workshop members, writable by owner.

- [ ] **Step 1: Write the failing rules tests** — in the `active staff member access` describe:

```typescript
    it('staff can CLAIM an unassigned order (null -> self) and nothing else', async () => {
      await asAdmin(async (db) => {
        await setDoc(doc(db, 'users/alice/memberships/chidi'), { role: 'staff', status: 'active', workshopUid: 'alice' });
        await setDoc(doc(db, 'users/alice/orders/o-claim'), { customerName: 'Ada', status: 'PENDING', createdAt: 1, updatedAt: 1 });
      });
      const staff = staffDb('chidi', 'alice');
      await assertSucceeds(updateDoc(doc(staff, 'users/alice/orders/o-claim'), {
        assignedMemberId: 'chidi', assignedMemberName: 'Chidi O', updatedAt: 2,
      }));
    });

    it('staff cannot claim an already-assigned order, assign someone else, or unassign', async () => {
      await asAdmin(async (db) => {
        await setDoc(doc(db, 'users/alice/memberships/chidi'), { role: 'staff', status: 'active', workshopUid: 'alice' });
        await setDoc(doc(db, 'users/alice/orders/o-taken'), {
          customerName: 'Ada', status: 'PENDING', createdAt: 1, updatedAt: 1,
          assignedMemberId: 'someone-else', assignedMemberName: 'Else',
        });
        await setDoc(doc(db, 'users/alice/orders/o-free'), { customerName: 'Ada', status: 'PENDING', createdAt: 1, updatedAt: 1 });
      });
      const staff = staffDb('chidi', 'alice');
      await assertFails(updateDoc(doc(staff, 'users/alice/orders/o-taken'), {
        assignedMemberId: 'chidi', assignedMemberName: 'Chidi O', updatedAt: 2,
      }));
      await assertFails(updateDoc(doc(staff, 'users/alice/orders/o-free'), {
        assignedMemberId: 'someone-else', assignedMemberName: 'Else', updatedAt: 2,
      }));
      await assertFails(updateDoc(doc(staff, 'users/alice/orders/o-taken'), {
        assignedMemberId: deleteField(), assignedMemberName: deleteField(), updatedAt: 2,
      }));
    });

    it('owner assigns, reassigns, and unassigns freely', async () => {
      await asAdmin(async (db) => {
        await setDoc(doc(db, 'users/alice/orders/o-own'), { customerName: 'Ada', status: 'PENDING', createdAt: 1, updatedAt: 1 });
      });
      await assertSucceeds(updateDoc(doc(db('alice'), 'users/alice/orders/o-own'), {
        assignedMemberId: 'paul', assignedMemberName: 'Paul', updatedAt: 2,
      }));
      await assertSucceeds(updateDoc(doc(db('alice'), 'users/alice/orders/o-own'), {
        assignedMemberId: null, assignedMemberName: null, updatedAt: 3,
      }));
    });

    it('team roster: members read, only owner writes, staff cannot write', async () => {
      await asAdmin(async (db) => {
        await setDoc(doc(db, 'users/alice/memberships/chidi'), { role: 'staff', status: 'active', workshopUid: 'alice' });
        await setDoc(doc(db, 'users/alice/team/paul'), { name: 'Paul', kind: 'named', status: 'active', colorSeed: 1 });
      });
      await assertSucceeds(getDoc(doc(staffDb('chidi', 'alice'), 'users/alice/team/paul')));
      await assertSucceeds(getDocs(collection(staffDb('chidi', 'alice'), 'users/alice/team')));
      await assertSucceeds(setDoc(doc(db('alice'), 'users/alice/team/new-named'), { name: 'Ngozi', kind: 'named', status: 'active', colorSeed: 4 }));
      await assertFails(setDoc(doc(staffDb('chidi', 'alice'), 'users/alice/team/hax'), { name: 'Hax', kind: 'named', status: 'active', colorSeed: 0 }));
      await assertFails(deleteDoc(doc(db('alice'), 'users/alice/team/paul')));
    });
```

(Import `deleteField`, `deleteDoc`, `getDoc` alongside the file's existing imports if absent.)

- [ ] **Step 2: Run to verify the new tests fail** — `cd functions && npm run test:rules`. Expected: the four new tests FAIL; everything else green.

- [ ] **Step 3: Implement the rules** — in the orders `allow update`, add a third OR-branch after the existing staff status branch:

```
          || (
            // Phase 2: the CLAIM. Staff may take an UNASSIGNED order for
            // themselves — never reassign, never unassign, never touch anything
            // else in the same write. Owner assignment needs no branch: the
            // assignment keys are not money keys, so the owner branch already
            // permits them.
            isActiveMember(uid)
            && request.resource.data.diff(resource.data).affectedKeys()
                 .hasOnly(['assignedMemberId', 'assignedMemberName', 'updatedAt'])
            && (!('assignedMemberId' in resource.data) || resource.data.assignedMemberId == null)
            && request.resource.data.assignedMemberId == request.auth.uid
            && serverCreatedAtProtectedOnUpdate()
            && activityCreatedAtStableOnUpdate()
          );
```

New block after `match /memberships/{staffAuthUid}` (~L500s):

```
      // ── Team roster (Owner + Staff, Phase 2) ────────────────────────────
      // One doc per assignable member: logged-in staff (doc id == auth uid,
      // Admin-SDK-written by approve/revoke) and owner-created name-only
      // members. Non-sensitive (name + avatar seed), so members may read the
      // whole roster for assignee display. No hard delete — archived members
      // stay resolvable for statusHistory attribution.
      match /team/{memberId} {
        allow read: if canReadWorkshop(uid);
        allow create, update: if isOwner(uid);
        allow delete: if false;
      }
```

- [ ] **Step 4: Parity + green** — `cp firestore.rules firestore.emulator.rules && cd functions && npm run test:rules`. Expected: all PASS (count grows by 4).

- [ ] **Step 5: Commit** — `git commit -m "feat(rules): team roster collection + staff claim write (null -> self only)"`

---

### Task 4: Assignment fields through the Kotlin data layer

**Files:**
- Modify: `cA/core/domain/model/Order.kt` (add after `archivedAt`), `cA/core/data/dto/OrderDto.kt` (both `OrderDto` and `OrderBaseDto`), `cA/core/data/mapper/OrderMapper.kt:58,97`, `cA/core/data/mapper/OrderBaseMapper.kt:18`
- Modify: `cA/core/domain/repository/OrderRepository.kt` + `cA/feature/order/data/FirebaseOrderRepository.kt` (new `assignOrder`), `composeApp/src/commonTest/.../core/data/repository/FakeOrderRepository.kt`
- Test: existing `OrderBaseMapperTest.kt` (wire-shape), new assertions in `OrderOfflineWriteRegressionTest.kt`

**Interfaces:**
- Consumes: existing DTO/mapper structure.
- Produces: `Order.assignedMemberId: String?`, `Order.assignedMemberName: String?` (defaults null) mapped through all four mapper functions; `suspend fun assignOrder(userId: String, orderId: String, memberId: String?, memberName: String?): EmptyResult<DataError.Network>` on `OrderRepository` (null/null = unassign); pure helper `orderAssignmentWriteFields(memberId: String?, memberName: String?, now: Long): Map<String, Any?>`.

- [ ] **Step 1: Failing wire-shape + payload tests**:

```kotlin
    // OrderBaseMapperTest.kt — extend the existing elementNames assertions:
    @Test
    fun orderBaseDto_carriesAssignmentFields() {
        val names = OrderBaseDto.serializer().descriptor.elementNames.toSet()
        assertTrue("assignedMemberId" in names)
        assertTrue("assignedMemberName" in names)
    }

    @Test
    fun toBaseDto_preservesAssignment() {
        val dto = orderDto().copy(assignedMemberId = "m1", assignedMemberName = "Chidi O")
        val base = dto.toBaseDto()
        assertEquals("m1", base.assignedMemberId)
        assertEquals("Chidi O", base.assignedMemberName)
    }

    // OrderOfflineWriteRegressionTest.kt:
    @Test
    fun orderAssignmentWriteFields_touchesOnlyAssignmentAndUpdatedAt() {
        val fields = orderAssignmentWriteFields(memberId = "m1", memberName = "Chidi O", now = 42L)
        assertEquals(setOf("assignedMemberId", "assignedMemberName", "updatedAt"), fields.keys)
        assertEquals("m1", fields["assignedMemberId"])
    }

    @Test
    fun orderAssignmentWriteFields_unassignWritesExplicitNulls() {
        val fields = orderAssignmentWriteFields(memberId = null, memberName = null, now = 42L)
        assertEquals(setOf("assignedMemberId", "assignedMemberName", "updatedAt"), fields.keys)
        assertNull(fields["assignedMemberId"])
    }
```

(Reuse the files' existing `orderDto()`/fixture builders; add one if absent.)

- [ ] **Step 2: Run to verify they fail** — compile errors for the missing fields/helper. Expected: FAIL.

- [ ] **Step 3: Implement** — add `val assignedMemberId: String? = null` / `val assignedMemberName: String? = null` to `Order`, `OrderDto`, `OrderBaseDto`; thread through `toOrder`, `toOrderDto`, `toBaseDto` (two lines each). Repository:

```kotlin
internal fun orderAssignmentWriteFields(
    memberId: String?,
    memberName: String?,
    now: Long,
): Map<String, Any?> = mapOf(
    "assignedMemberId" to memberId,
    "assignedMemberName" to memberName,
    "updatedAt" to now,
)

    @Suppress("SpreadOperator") // same GitLive vararg constraint as updateCosts
    override suspend fun assignOrder(
        userId: String,
        orderId: String,
        memberId: String?,
        memberName: String?,
    ): EmptyResult<DataError.Network> {
        val fields = orderAssignmentWriteFields(memberId, memberName, Clock.System.now().toEpochMilliseconds())
        val accepted = offlineWrites.enqueue("assignOrder orderId=$orderId") {
            ordersCollection(userId).document(orderId)
                .update(*fields.entries.map { it.key to it.value }.toTypedArray())
        }
        if (!accepted) return Result.Error(DataError.Network.UNKNOWN)
        return Result.Success(Unit)
    }
```

Add the method to `OrderRepository` and a recording implementation to `FakeOrderRepository` (store last call + mutate its backing order list so ViewModel tests observe the change).

- [ ] **Step 4: Run to green** — `./gradlew :composeApp:testDebugUnitTest detekt`. Expected: PASS.

- [ ] **Step 5: Commit** — `git commit -m "feat(order): assignment fields through domain/DTO/mappers + offline assignOrder write"`

---

### Task 5: Team roster repository (client)

**Files:**
- Create: `cA/core/domain/staff/TeamMember.kt`, `cA/core/domain/staff/repository/TeamRosterRepository.kt`, `cA/core/data/dto/TeamMemberDto.kt`, `cA/core/data/staff/FirebaseTeamRosterRepository.kt`
- Create: `composeApp/src/commonTest/.../core/data/repository/FakeTeamRosterRepository.kt`
- Modify: `cA/di/StaffModule.kt` (Koin single)
- Test: `composeApp/src/commonTest/.../core/data/staff/TeamMemberMapperTest.kt`

**Interfaces:**
- Consumes: GitLive Firestore, `decodeDocOrLog`, `Result`/`DataError`.
- Produces:

```kotlin
data class TeamMember(
    val id: String,
    val name: String,
    val kind: TeamMemberKind,   // enum STAFF, NAMED
    val colorSeed: Int,
    val status: TeamMemberStatus, // enum ACTIVE, ARCHIVED
)

interface TeamRosterRepository {
    fun observeTeam(workshopUid: String): Flow<Result<List<TeamMember>, DataError.Network>>
    suspend fun addNamedMember(workshopUid: String, name: String): EmptyResult<DataError.Network>
    suspend fun renameMember(workshopUid: String, memberId: String, name: String): EmptyResult<DataError.Network>
    suspend fun archiveMember(workshopUid: String, memberId: String): EmptyResult<DataError.Network>
}
```

- [ ] **Step 1: Failing mapper test** — `TeamMemberDto(name, kind = "named", colorSeed = 3, status = "active")` → `toTeamMember(docId)` carries the doc id, maps enums case-insensitively, defaults unknown `kind`/`status` to `NAMED`/`ACTIVE`; blank `name` maps to the doc id as a last-resort label.
- [ ] **Step 2: Run to verify it fails.**
- [ ] **Step 3: Implement** — DTO `@Serializable` with defaults; Firebase impl: `observeTeam` = collection snapshots → `decodeDocOrLog` → `toTeamMember(doc.id)` sorted by `status` then `name`; `addNamedMember` = new doc (`collection.document`) with `kind:"named"`, `status:"active"`, `colorSeed = name.hashCode().mod(10)`, timestamps; `renameMember`/`archiveMember` = merge-sets of the single field + `updatedAt`. All through the offline write dispatcher used by the other repositories. Koin: `singleOf(::FirebaseTeamRosterRepository) bind TeamRosterRepository::class` in `StaffModule.kt`.
- [ ] **Step 4: Green + detekt.**
- [ ] **Step 5: Commit** — `git commit -m "feat(staff): client team-roster repository (observe, add/rename/archive name-only members)"`

---

### Task 6: Team screen — roster section with name-only members

**Files:**
- Modify: `cA/feature/staff/presentation/team/TeamState.kt`, `TeamAction.kt`, `TeamViewModel.kt`, `TeamScreen.kt`
- Test: `composeApp/src/commonTest/.../feature/staff/presentation/team/TeamViewModelTest.kt`

**Interfaces:**
- Consumes: `TeamRosterRepository` (Task 5), existing `StaffRepository` membership flow.
- Produces: `TeamState` gains `roster: List<TeamMember> = emptyList()`, `showAddMemberSheet: Boolean = false`, `addMemberName: String = ""`, `renameTarget: TeamMember? = null`; actions `OnAddMemberClick`, `OnAddMemberNameChange(String)`, `OnConfirmAddMember`, `OnDismissAddMember`, `OnRenameMember(TeamMember)`, `OnConfirmRename(String)`, `OnArchiveMember(TeamMember)`.

- [ ] **Step 1: Failing ViewModel tests** (Turbine style per `TeamViewModelTest.kt`): (a) roster flow populates `state.roster`; (b) `OnConfirmAddMember` with a blank name is a no-op; with a real name calls `FakeTeamRosterRepository.addNamedMember` and closes the sheet; (c) `OnArchiveMember` calls `archiveMember`; (d) roster errors surface `errorMessage`.
- [ ] **Step 2: Run to verify they fail.**
- [ ] **Step 3: Implement** — ViewModel: inject `TeamRosterRepository`, `observeRoster()` beside `observeTeam()`, handle the new actions. Screen: below `ActiveSection`, add `RosterSection(roster, onAction)` listing active members as avatar-chip rows (initials on a color derived from `colorSeed` — add a small `memberColor(seed: Int): Color` mapping into `DesignTokens` accent hues), name-only members get rename/archive in a `DropdownMenu` (mirror `ActiveMemberRow`'s `:399-437` pattern); staff members show a "linked account" caption instead of archive (their lifecycle is revoke). "Add member" button opens a `ModalBottomSheet` with one `OutlinedTextField` (state-driven per MVI) + confirm. All copy via compose resources (`team_roster_header`, `team_add_member`, `team_member_linked`, `team_rename`, `team_archive`, etc.). `@Preview` for the roster section with mixed staff/named members.
- [ ] **Step 4: Green + detekt.**
- [ ] **Step 5: Commit** — `git commit -m "feat(staff): team roster UI — name-only members, rename/archive, add sheet"`

---

### Task 7: Order detail — "Assigned to" card (owner picker + staff claim)

**Files:**
- Create: `cA/feature/order/presentation/detail/components/OrderAssigneeCard.kt`
- Modify: `cA/feature/order/presentation/detail/OrderDetailState.kt`, `OrderDetailAction.kt` (+`isStaffRestricted()`), `OrderDetailViewModel.kt`, `OrderDetailScreen.kt:1183-1185` (new item between hero and costs)
- Test: `composeApp/src/commonTest/.../feature/order/presentation/detail/OrderDetailStaffGuardTest.kt` + a new `OrderAssignmentTest.kt`

**Interfaces:**
- Consumes: `Order.assignedMemberId/Name` (Task 4), `assignOrder` (Task 4), `TeamRosterRepository.observeTeam` (Task 5), `WorkshopSession.authUid` (already in the session flow).
- Produces: state fields `roster: List<TeamMember> = emptyList()`, `showAssignSheet: Boolean = false`, `staffAuthUid: String? = null`; actions `OnAssignClick`, `OnAssignMember(memberId: String, memberName: String)`, `OnUnassignClick`, `OnClaimClick`, `OnDismissAssignSheet`.

- [ ] **Step 1: Failing tests**:

```kotlin
    @Test
    fun `owner assigning calls assignOrder with the picked member`() = runTest {
        val viewModel = createViewModel(orderId = "o1")
        viewModel.onAction(OrderDetailAction.OnAssignMember(memberId = "paul", memberName = "Paul"))
        runCurrent()
        assertEquals(Triple("o1", "paul", "Paul"), fakeOrderRepository.lastAssignment)
    }

    @Test
    fun `staff claim assigns to self and staff cannot assign others`() = runTest {
        setStaffSession(authUid = "chidi")
        val viewModel = createViewModel(orderId = "o1")
        viewModel.onAction(OrderDetailAction.OnAssignMember(memberId = "paul", memberName = "Paul"))
        runCurrent()
        assertNull(fakeOrderRepository.lastAssignment) // restricted for staff
        viewModel.onAction(OrderDetailAction.OnClaimClick)
        runCurrent()
        assertEquals("chidi", fakeOrderRepository.lastAssignment?.second)
    }
```

Also extend `OrderDetailStaffGuardTest.kt`'s restricted-action sweep with `OnAssignMember`/`OnUnassignClick` and assert `OnClaimClick` is NOT restricted.

- [ ] **Step 2: Run to verify they fail.**
- [ ] **Step 3: Implement** — Actions: add `OnAssignMember`, `OnUnassignClick`, `OnAssignClick` to `isStaffRestricted()`'s `true` set (claim + dismiss stay unrestricted). ViewModel: observe roster (owner only — skip subscription when `isActiveStaff`), `OnClaimClick` resolves the session's `authUid` + the signed-in user's display name and calls `assignOrder(workshopUid, orderId, authUid, displayName)`; claim is a no-op when the order is already assigned (defense-in-depth mirror of the rules). Card composable:

```kotlin
@Composable
fun OrderAssigneeCard(
    assignedMemberName: String?,
    isActiveStaff: Boolean,
    isAssignedToSelf: Boolean,
    onAssignClick: () -> Unit,   // owner: opens picker
    onClaimClick: () -> Unit,    // staff: claim when unassigned
    modifier: Modifier = Modifier,
)
```

— unassigned + owner → "Assign" affordance; unassigned + staff → "Claim this order" button; assigned → member chip (initials avatar + name), owner additionally gets a change/unassign overflow; staff sees "You" when `isAssignedToSelf`. Picker = `ModalBottomSheet` listing active roster members (reuse the avatar chip from Task 6 — extract it to `cA/ui/components/MemberAvatar.kt` if needed for reuse). Strings via resources; `@Preview` for all four states (owner/staff × assigned/unassigned).

- [ ] **Step 4: Green + detekt.**
- [ ] **Step 5: Commit** — `git commit -m "feat(order): Assigned-to card — owner picker, staff claim, guarded actions"`

---

### Task 8: Order list — assignee chip + "My work" filter

**Files:**
- Modify: `cA/feature/order/presentation/list/OrderListState.kt`, `OrderListAction.kt`, `OrderListViewModel.kt` (`filterAndSort`), `OrderListScreen.kt:388-430` (chips) and `:742-778` (card text column)
- Test: `composeApp/src/commonTest/.../feature/order/presentation/list/OrderListStaffTest.kt`

**Interfaces:**
- Consumes: `Order.assignedMemberId/Name`, session `authUid` (already collected for `isActiveStaff` — also store `staffAuthUid: String?` in state).
- Produces: `OrderListState.myWorkOnly: Boolean = false`; action `OnToggleMyWork`; a "My work" chip rendered for staff only, appended before the Archived chip (the orthogonal-chip precedent at `:424-428`).

- [ ] **Step 1: Failing tests** — (a) `OnToggleMyWork` filters `orders` to `assignedMemberId == staffAuthUid`; (b) toggling off restores the full list; (c) status filter and my-work compose (both applied); (d) owner sessions never see the flag applied (action is a no-op when `!isActiveStaff` — or simply never rendered; test the VM no-op).
- [ ] **Step 2: Run to verify they fail.**
- [ ] **Step 3: Implement** — `filterAndSort` gains the predicate `if (myWorkOnly && staffAuthUid != null) list.filter { it.assignedMemberId == staffAuthUid } else list`; chips row appends (staff only):

```kotlin
        if (isActiveStaff) {
            OrderFilterChip(
                label = stringResource(Res.string.order_filter_my_work),
                isSelected = myWorkOnly && !showArchived,
                onClick = onMyWorkSelected,
            )
        }
```

Card: after `DeadlineLine` (`:773`), render a small assignee label when assigned — initials avatar (reuse `MemberAvatar`) + `assignedMemberName`, same row as `PendingSyncBadge`. Strings via resources.

- [ ] **Step 4: Green + detekt.**
- [ ] **Step 5: Commit** — `git commit -m "feat(order): My-work filter chip + assignee chip on order rows"`

---

### Task 9: Filtered navigation + staff dashboard "Mine" tile

The in-code TODO ("PR-A2", `DashboardViewModel.kt:221-222`) blocks per-tile filtered landing; this task pays that debt for the tiles that need it.

**Files:**
- Modify: `cA/navigation/Routes.kt:114` (`OrderListRoute` gains an optional filter), `cA/feature/dashboard/presentation/DashboardEvent.kt:10`, `DashboardViewModel.kt:220-226`, `DashboardScreen.kt:497`, `cA/feature/main/presentation/MainScreen.kt:549-553`, `cA/feature/order/presentation/list/OrderListViewModel.kt` (seed from `SavedStateHandle`), `cA/feature/dashboard/presentation/components/StaffDashboardContent.kt:111-118,192-232`
- Test: dashboard VM test + `OrderListStaffTest.kt` seeding test

**Interfaces:**
- Produces: `@Serializable data class OrderListRoute(val initialFilter: String? = null)` where `initialFilter` ∈ `{"overdue","due-today","in-progress","my-work", null}`; `DashboardEvent.NavigateToOrders(val filter: String? = null)`; `StaffCountTiles` gains a fourth `mine` tile (`count = orders assigned to session uid`, label `dashboard_staff_tile_mine`) navigating with `"my-work"`.

- [ ] **Step 1: Failing tests** — (a) `OnViewMyWorkClick` emits `NavigateToOrders("my-work")`; (b) `OrderListViewModel` constructed with `SavedStateHandle(mapOf("initialFilter" to "my-work"))` starts with `myWorkOnly = true`; with `"in-progress"` starts with `statusFilter = OrderStatus.IN_PROGRESS`.
- [ ] **Step 2: Run to verify they fail.**
- [ ] **Step 3: Implement** — route change (`data object` → `data class` with default null keeps existing `navigate(OrderListRoute)` call sites compiling as `OrderListRoute()` — update call sites), `MainScreen` forwards the event's filter into the route, `OrderListViewModel` reads `savedStateHandle.toRoute<OrderListRoute>()` (match the codebase's existing typed-route read pattern in other ViewModels) and seeds `statusFilter`/`myWorkOnly`. Dashboard: add `OnViewMyWorkClick` action, wire the fourth tile (staff dashboard only — count computed beside the existing `moneyFree()` staff state), map the three existing tile actions to their own filters (`"overdue"`, `"due-today"`, `"in-progress"`) while you're in the `when` — the list ViewModel maps `"overdue"`/`"due-today"` to a status-agnostic deadline filter ONLY if a matching filter already exists; otherwise map both to plain navigation (do NOT invent a new deadline filter in this task — leave those two on `NavigateToOrders()` and note it).
- [ ] **Step 4: Green + detekt.**
- [ ] **Step 5: Commit** — `git commit -m "feat(dashboard): Mine tile + filtered orders navigation (pays the PR-A2 route debt)"`

---

### Task 10: Staff affordance gating (order detail)

Product decisions (2026-08-08): due date = owner-only; notes = staff-denied for now; measurements-link = staff-denied for now; garment media = staff-denied until Phase 2b ships the capability. All four currently render ungated for staff and fail silently at the rules.

**Files:**
- Modify: `cA/feature/order/presentation/detail/OrderDetailScreen.kt:1198-1260` (garment/measurements/notes items), `components/OrderHeroCard.kt:337-348` (due-date pencil), `components/OrderGarmentDetailsCard.kt` (add/remove affordances), `components/OrderMeasurementsPreviewCard.kt` (link affordance), `components/OrderNotesCard.kt` (edit affordance), `OrderDetailAction.kt:129-171` (`isStaffRestricted()` additions)
- Test: `OrderDetailStaffGuardTest.kt`

**Interfaces:**
- Consumes: `state.isActiveStaff` (already threaded to the screen).
- Produces: for staff sessions — no due-date pencil (date still visible), notes render read-only (existing note text shown, no edit affordance), no link-measurements CTA (linked measurements still viewable), no add/remove style/fabric affordances (existing photos still viewable). Every corresponding Action lands in `isStaffRestricted()`.

- [ ] **Step 1: Failing tests** — extend the `OrderDetailStaffGuardTest.kt` sweep: under a staff session, dispatching each of `OnSetDeadlineClick`, `OnDeadlineSelected`, `OnNotesEditClick`, `OnNotesDraftChange`, `OnNotesSaveClick`, `OnLinkMeasurementsClick`, `OnSelectMeasurement`, and every add/remove style/fabric action dispatched by `OrderGarmentDetailsCard`'s callbacks (read `OrderDetailScreen.kt:1198-1213` for the exact action types wired to `onAddStyleClick`, `onRemoveStyleImage`, `onAddFabricPhotoClick`, `onRemoveFabricImage`, `onAddFabricName`) mutates nothing and emits nothing — assert state unchanged via Turbine `expectNoEvents` on the event flow, matching the file's existing restricted-action test idiom.
- [ ] **Step 2: Run — the new sweep entries FAIL** (those actions currently mutate state/emit).
- [ ] **Step 3: Implement** — add the actions to `isStaffRestricted()`'s `true` set with a comment block: `// Phase 2 affordance audit: rules whitelist staff writes to status+claim only.` Then gate the UI: pass `isActiveStaff` into `OrderGarmentDetailsCard`, `OrderMeasurementsPreviewCard`, `OrderNotesCard` and hide the CTAs (null callbacks or conditional rendering, matching each card's style); in `OrderHeroCard.kt:337-346` render the due date without the pencil for staff and REPLACE the stale comment at `:347-348` ("staff can still see + edit the due date") with the new product decision. Update previews to cover the staff variant of each card.
- [ ] **Step 4: Green + detekt.**
- [ ] **Step 5: Commit** — `git commit -m "fix(staff): gate every order-detail affordance the rules deny (due date, notes, measurements, media)"`

---

### Task 11: Full verification + PR

- [ ] **Step 1:** `./gradlew :composeApp:testDebugUnitTest detekt` and, if the environment has iOS pods, `:composeApp:allTests`.
- [ ] **Step 2:** `cd functions && npm test && npm run lint && npm run build && npm run test:rules`.
- [ ] **Step 3:** Emulator smoke (optional but recommended): `firebase emulators:start --config firebase.emulator.json`, seed via `node scripts/emulatorSetupStaff.js`, verify owner can assign seeded orders to a name-only member and the staff account can claim an unassigned order + filter My work.
- [ ] **Step 4:** Push and open the PR:

```bash
git push -u origin feat/staff-phase2-assignment
gh pr create --base main --title "feat(staff): Phase 2a — team roster, order assignment, session propagation" \
  --body "Roster collection (staff docs via approve/revoke, owner-managed name-only members), assignment on the base order doc (owner assigns, staff claims null->self), My-work filter + Mine tile with filtered navigation, kill-switch/session propagation fix, and the staff affordance audit gating. Spec: docs/superpowers/specs/2026-08-07-owner-staff-collaboration-design.md (Phase 2). Staff garment media follows as Phase 2b (storage.rules staff branch + items whitelist).

🤖 Generated with [Claude Code](https://claude.com/claude-code)"
```

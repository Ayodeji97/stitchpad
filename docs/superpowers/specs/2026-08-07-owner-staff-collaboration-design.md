# Owner ↔ Staff Collaboration — Design Spec

**Date:** 2026-08-07
**Status:** Approved for planning
**Scope:** Unblock staff read access (Slice 8e), team roster with name-only members, order assignment with staff self-claim, workshop order-visibility toggle, cross-member notifications, status attribution, staff sync indicator.

## 1. Problem

Testing on physical devices (owner "Fable" on Android, staff "Gabi" on iOS) showed that an order created by the owner never appears for the staff member. Two causes:

1. **Staff read access is deliberately blocked in production.** `firestore.rules` allows `list` on orders/customers only for the owner, because base order docs still carry money fields (`totalPrice`, `payments`, `costs`) and the staff design promises staff never see money. The client swallows the denial as an empty state (`OrderListViewModel`, `CustomerListViewModel`), so staff see empty screens with no error. Unblocking requires the planned Slice 8e (stop dual-writing money to base docs, backfill, rules flip).
2. **Assignment does not exist.** No `assignedMemberId` on Order, no roster, no assign UI, no "my work" view, no attribution (`statusHistory` lacks `changedBy`), and no cross-member notifications. Only a concept mockup exists (`preview/staff-assignment-mockup.html`).

## 2. Product decisions (confirmed with owner)

- **Staff default view:** the full workshop queue, with a "My work" filter — like a physical shop's rack.
- **Assign power:** the owner assigns anyone; staff may self-claim an unassigned order. Staff cannot reassign or unassign.
- **Roster:** assignment targets include **name-only members** (e.g. an apprentice with no smartphone) alongside logged-in staff.
- **Visibility toggle (workshop setting):** owner can switch the workshop between `ALL` (staff see every order automatically — default) and `ASSIGNED_ONLY` (staff see an order only once it is assigned to them). In `ASSIGNED_ONLY` mode, self-claim is intentionally unavailable (staff cannot see unassigned orders); the owner is the assigner by definition.
- **Notifications this cycle:** staff "Order assigned to you"; owner "Staff advanced an order"; fix `STAFF_PENDING` rendering (currently maps to `UNKNOWN`).
- **In scope:** Slice 8e and the staff sync/offline indicator.

## 3. Architecture

Chosen approach: **assignment lives on the order doc; the server reacts (triggers), never mediates.** Assign/claim are plain Firestore writes so they work offline — the deciding argument for an offline-heavy user base. Rejected alternatives: callable-function-mediated assignment (breaks offline, adds latency to a many-times-daily action) and a separate assignments collection (client-side joins, fragile offline cache, `ASSIGNED_ONLY` list rule cannot join across collections).

Everything stays inside the existing single-owner-tree tenancy model (`workshopUid` = owner's auth uid).

### 3.1 Data model

**Team roster — `users/{workshopUid}/team/{memberId}`** (new)

| Field | Type | Notes |
|---|---|---|
| `name` | string | Display name, owner-editable |
| `colorSeed` | string/int | Avatar chip color in lists |
| `kind` | `"staff"` \| `"named"` | Logged-in staff vs name-only member |
| `status` | `"active"` \| `"archived"` | Archived members remain resolvable for history |

- Logged-in staff: doc id **is their auth uid**; created server-side by `approveStaffMember`, archived by `revokeStaffMember` / `cancelStaffMembership`.
- Name-only members: generated ids; created/renamed/archived directly by the owner in the Team screen.

**Order doc additions** (base doc — non-sensitive, no money-wall implications)

- `assignedMemberId: String?` — roster doc id, absent/null = unassigned
- `assignedMemberName: String?` — denormalized for list rendering without a join
- `statusHistory` entries gain `changedBy` (member id) and `changedByName`

**Workshop settings — `users/{workshopUid}/settings/workshop`** (new doc)

- `orderVisibility: "ALL" | "ASSIGNED_ONLY"`
- `workshopName: String` — staff-readable branding (replaces the stale redeem-time cache as source of truth)
- Read: owner + active members. Write: owner only.
- Also the target document for the staff sync observer (see 3.5).
- Missing doc ⇒ `ALL` mode, enforced in both rules and client. No backfill needed.

**Notifications** — recipient's own `users/{authUid}/notifications` tree (existing pattern). New types: `ORDER_ASSIGNED` (to staff), `ORDER_STATUS_CHANGED` (to owner). `STAFF_PENDING` added to the client enum/mapper.

### 3.2 Security rules

- **Orders `list`/`get`** (after 8e removes money from base docs): owner unrestricted; active member allowed when workshop mode is `ALL`, or in `ASSIGNED_ONLY` only where `resource.data.assignedMemberId == request.auth.uid` (rule `get()`s the settings doc; client issues the matching filtered query). Missing settings doc counts as `ALL`.
- **Staff order update** keys extend from `['status','subStatus','statusHistory','updatedAt']` to also permit the **claim**: `assignedMemberId`/`assignedMemberName` may change only from null → the staff member's own uid/name. Owner may set/clear assignment freely. Reassignment stays owner-only.
- **Team**: read for owner + active members; write owner-only (staff roster docs are written by Admin SDK, which bypasses rules).
- **Settings**: read owner + active members; write owner-only.
- Rules cannot deep-validate `changedBy` inside the `statusHistory` array; the trigger cross-checks the actual writer (3.4). Accepted residual risk.
- **Regression guarantee:** staff still cannot read `orders/{id}/private/money` or `customers/{id}/private/contact` — covered by emulator rules tests.

### 3.3 Client UX

- **Team screen (owner):** roster list of all members as avatar chips; "Add member" bottom sheet (name field) creates a name-only member; inline rename/archive for name-only members; staff members appear on approval, archived via revoke flow.
- **Order detail:** "Assigned to" row. Owner taps → picker bottom sheet of active roster members + "Unassign". Staff sees "Claim this order" when unassigned (`ALL` mode only); shows "You" when self-assigned.
- **Orders list:** assignee chip on cards; staff "My work" filter chip (hidden in `ASSIGNED_ONLY` mode where the whole list is already theirs); owner filter-by-member.
- **Staff dashboard:** "Assigned to me" tile routing to the pre-filtered Orders list (same pattern as urgency tiles).
- **Settings (owner):** "Workshop" section — workshop name field + visibility toggle, copy: "Team sees new orders automatically" vs "Team only sees orders I assign".
- **Notifications screen:** renders the two new types; `STAFF_PENDING` renders "X wants to join your workshop" and routes to the Team screen.
- House patterns throughout: MVI State/Action/Event, Root/Screen split, `UiText`, compose-resources strings, previews for every new Screen composable.

### 3.4 Cloud Functions

- `approveStaffMember` also creates the roster doc; `revokeStaffMember` / `cancelStaffMembership` archive it.
- **New trigger `onOrderWritten`** (europe-west1) on `users/{uid}/orders/{orderId}`:
  - `assignedMemberId` changed to a `kind == "staff"` member who is not the actor ⇒ `ORDER_ASSIGNED` notification to that member's tree.
  - Status change whose `changedBy` ≠ owner ⇒ `ORDER_STATUS_CHANGED` notification to the owner's tree.
  - Uses the v2 with-auth-context trigger variant to cross-check the actual writer against the claimed `changedBy`.
  - Name-only assignees generate no notifications.
  - Trigger-based fan-out means notifications fire correctly for writes queued offline.

### 3.5 Staff sync indicator

When session role is `STAFF`, `SyncStatusViewModel` points the existing `SyncStatusObserver` at `users/{workshopUid}/settings/workshop` (readable by staff) instead of `users/{workshopUid}` (owner-only, currently a permanent denial silently suppressing the banner). Observer logic unchanged. Per-row pending badges already read snapshot metadata on list listeners and start working as soon as the 8e rules flip lands.

## 4. Phasing

Each phase is independently shippable, in dependency order:

1. **Phase 1 — Slice 8e (unblock staff read).** Finish stop-dual-write (money fields off base order docs; existing worktree `staff-slice8d1-stop-dual-write` is the starting point), backfill-strip existing docs (pattern: `migrateSensitiveFields`), enforce app version floor, flip production rules to allow member `list` on orders + customers, delete the client's swallow-denial empty-state special cases. **Deliverable: staff see the workshop's orders and customers.**
2. **Phase 2 — Roster + assignment.** Team collection + function lifecycle, Order fields/DTO/mapper, Team roster UI, "Assigned to" row (owner picker, staff claim), assignee chips, "My work" filter, dashboard tile, claim/assign rules.
3. **Phase 3 — Settings + visibility toggle + staff sync.** Settings doc + owner Settings UI, `ASSIGNED_ONLY` rules + client query switch, sync observer retarget.
4. **Phase 4 — Notifications + attribution.** `changedBy`/`changedByName` on status writes, `onOrderWritten` trigger, client notification types, `STAFF_PENDING` rendering fix.

Migration: existing orders need nothing (absent `assignedMemberId` = unassigned); missing settings doc defaults to `ALL`.

## 5. Error handling

House rules: repository methods return `Result<T, DataError>`; presentation maps to `UiText`.

- **Claim race** (two staff claim, possibly both offline): rules arbitrate — first write to sync wins; the loser's queued write is rejected server-side and their client converges to the actual assignee. No special client logic.
- **Toggle race** (owner flips to `ASSIGNED_ONLY` under live staff listeners): the staff query re-issues filtered; denial paths render as empty state, never an error toast.
- **Archived assignee:** orders keep the denormalized name; picker only offers active members.

## 6. Testing

- **ViewModel unit tests** (JUnit5 + Turbine + AssertK + fakes): roster CRUD, assignment picker, claim, My-work filter, settings toggle, notification mapping incl. `STAFF_PENDING`.
- **Firestore emulator rules tests:** staff list in both modes; claim only null→self; staff cannot reassign/unassign; settings owner-write-only; **money-wall regression suite** (staff can never read `private/money` / `private/contact`).
- **Functions emulator tests:** roster lifecycle on approve/revoke; trigger fan-out for both types; no fan-out for name-only assignees; auth-context cross-check.
- **Maestro e2e:** owner creates order → staff list shows it (`ALL`); owner assigns → staff "My work" shows it; toggle `ASSIGNED_ONLY` → staff list narrows. Update the emulator smoke runbook.

## 7. Known trade-offs (accepted)

- `get()` in the list rule for visibility mode: one extra cached read per evaluation; covered by rules tests.
- Single assignee per order; `assignedMemberIds: []` is an additive later change if ever needed.
- Denormalized `assignedMemberName`/`changedByName` can go stale on rename; acceptable, sweepable by a function later.
- `changedBy` inside arrays is not rules-validatable; trigger-side cross-check is the mitigation.

## 8. Out of scope

Sub-roles ("staff admin"), per-permission editor, staff activity log, comments/messaging, multi-assignee, rename-sweep function, staff profile screens.

## 9. Follow-ups discovered in Phase 1 smoke testing (2026-08-08)

1. **Staff garment media** — staff should be able to add style/fabric photos to an
   order (owner request from device smoke testing). Requires: staff order-update
   whitelist extended to `items` (safe post-strip — base items are money-free),
   Storage rules granting staff upload access to the owner's media paths, the
   upload-outbox path verified under a staff session, and tests. Fold into Phase 2
   (assignment), which also expands staff write scope.
   **Broader finding from the same session:** the staff order-detail screen shows
   several affordances the status-only rules whitelist denies — "Add style",
   "Add fabric photo", the due-date edit pencil, and the Notes field. Each fails
   silently (offline-queued write rejected server-side). Phase 2 must include a
   systematic role-gating audit of the order-detail (and any other staff-reachable
   screen) against the rules whitelist: hide or disable every affordance staff
   cannot complete, and decide per-affordance whether to grant it instead
   (product call so far: due date stays owner-only — it is a customer commitment;
   styles/fabrics become staff-editable; notes TBD).
2. **Kill switch is not live** — flipping `config/app.staffFeatureEnabled` to
   `false` did NOT drop an active staff session; it only took effect after an app
   restart (verified in emulator smoke, both directions). The config doc IS a live
   snapshot listener combined into `FirebaseActiveWorkshopProvider`, so something
   between the listener and the session resolution fails to propagate mid-session.
   The code comments and rollout runbook describe the switch as instant — either
   fix the propagation or re-document the switch as restart-latency. Investigate
   before relying on it as a production panic button.
3. **Storage emulator in the smoke setup** — `firebase.emulator.json` covers only
   Auth + Firestore, so app image uploads go to production Storage and fail with
   403 (emulator-issued token), making image sync appear broken locally. Add the
   Storage emulator + client wiring in `connectFirebaseEmulatorsIfEnabled`, and
   note it in the smoke runbook. Also consider committing a debug-source-set
   `network_security_config.xml` (cleartext to 10.0.2.2) so Android emulator smoke
   works without recreating the local-only file each time.
4. **Owner in the roster** — the owner cannot currently be an assignee (discovered
   in Phase 2a smoke). Phase 2b: auto-create/maintain an owner roster doc (kind
   `owner`) at workshop-tree first use, include it in the assign picker, render
   self-assignment as "You" for the owner, and exclude it from archive/rename.
   Until then owners track their own work implicitly.
5. **Workload overview (who is working on what)** — assignment is currently only
   visible per-order: the assignee chip on Orders-list rows and the Assigned-to
   card in order detail. There is no grouped view of the workshop's load. Phase 2b
   candidate: a per-member breakdown (e.g. on the Team screen or a dashboard
   section) showing each roster member with their open-order count, tappable
   into an assignee-filtered order list. Pairs with follow-up #4 — the owner
   only appears in this overview once they are in the roster.

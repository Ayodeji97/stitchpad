# Staff Phase 2b — Garment Media, Owner-in-Roster, Workload Overview

**Date:** 2026-08-09
**Status:** Approved
**Parent spec:** `2026-08-07-owner-staff-collaboration-design.md` (§9 follow-ups 1, 3, 4, 5)
**Depends on:** Phase 2a (PR #351, merged `312ef29f`) — team roster, assignment,
session propagation. Slice 8e money wall (PR #350) — post-strip base docs are
money-free.

Phase 2b closes the follow-ups discovered in Phase 1/2a smoke testing: staff can
do the tailoring work on an order (styles, fabric photos, notes), the owner can
be an assignee like anyone else, and the owner can see who is working on what.
Plus one infra chore so future emulator smoke needs zero local file surgery.

Product calls locked in during brainstorming (2026-08-09):

- Staff edit **styles + fabric photos + notes** on an order. Garment type,
  description, quantity, and **due date stay owner-only** (customer agreement).
- Workload overview lives on the **Team screen** (owner-only), not the dashboard.
- All four workstreams ship together in one plan.

---

## 1. Staff garment media + affordance audit

### 1.1 Firestore rules — widen the staff order-update branch

The staff branch of the orders `allow update` goes from status-only to a
*work-fields* whitelist:

- `affectedKeys().hasOnly([<current status-branch keys>, 'items', 'notes'])` —
  the plan copies the exact key list verbatim from the current status branch
  (`status`, `statusHistory`, `updatedAt`, …) and adds `items` and `notes`.
- `deadline` is deliberately NOT in the whitelist — due date is owner-only.
- The existing guards stay on the branch unchanged: `isActiveMember`,
  server-stamp protection, and the top-level money-key deny
  (`orderMoneyKeys().hasAny`) that already applies to every base write.
- `firestore.emulator.rules` stays byte-identical to `firestore.rules` in the
  orders block (existing parity requirement).

**Money safety for `items`.** Rules cannot inspect inside an array, so
items-level money safety is structural, not rule-enforced:

- Post-strip (Slice 8e), base `items[]` carry no price fields; item prices live
  only in the `/private/money` mirror.
- The client's base-DTO serializer (the money-free `OrderBaseDto` shape from 8e)
  never writes price fields into base `items[]`. Staff item writes MUST
  serialize through that same shape. A staff write can therefore never
  introduce money into the base doc, and an owner reading the doc back loses
  nothing — prices overlay from the mirror (`Order.withMoney`).
- Rules tests must include smuggle attempts: staff writes mixing the work
  fields with each top-level money key, with `deadline`, and with
  assignment fields outside the claim shape — all denied.

### 1.2 Storage rules — staff branch on the owner's tree

Today `users/{uid}/**` is owner-only, so every staff upload 403s. Add a staff
branch using the custom claims Phase 2a already mints plus the same
membership-doc check `isActiveMember` uses in Firestore rules (claims alone
cannot enforce revocation inside the token's lifetime):

```
function isActiveStaffOf(uid) {
  return request.auth != null
    && request.auth.token.get('role', '') == 'staff'
    && request.auth.token.get('workshopUid', '') == uid
    && firestore.get(/databases/(default)/documents/users/$(uid)/staff/$(request.auth.uid)).data.status == 'ACTIVE';
}
```

(The exact membership doc path and status value are copied from
`firestore.rules`' `isActiveMember` — the plan pins them verbatim.)

- **Read:** staff read the whole `users/{workshopUid}/**` tree (they already
  see these photos in the app via download URLs; this makes direct reads
  consistent).
- **Write:** staff write ONLY under the order-media subtree — the path prefix
  that `photoStoragePath` values for order style/fabric images actually use
  (pinned in the plan from the real upload-path builder). Brand logo, customer
  style folders, and inspiration images stay owner-only for writes.
- Owner access is unchanged. `tutorials/**` unchanged.

### 1.3 Client — enable the work, hide the rest

Systematic audit of every staff-reachable screen (order detail foremost)
against the final rules whitelist:

- **Enabled for staff:** "Add style", "Add fabric photo", removing their own
  additions, and the Notes field — wired to the same actions owners use; the
  repository's staff-path update writes only whitelisted fields.
- **Hidden for staff:** the due-date edit pencil, and any other affordance the
  rules deny (the Phase 2a `isStaffRestricted()` gating pattern extends to
  these). Nothing staff can tap may fail silently server-side — that is the
  bug class this audit exists to kill.
- Owner-side edits to styles/fabrics/notes already propagate to staff via the
  live order stream (verified broken affordances, not broken sync, in smoke).

### 1.4 Upload path under a staff session

The image upload outbox currently derives its storage path from the signed-in
user. Under a staff session it must target the **workshop owner's** tree
(`users/{workshopUid}/…`), mirroring how Firestore writes already resolve
`workshopUid` via `ActiveWorkshopProvider`. Verified end-to-end in emulator
smoke (needs §4's Storage emulator).

## 2. Owner in the roster

Lazy, client-side ensure — no Cloud Function:

- When the owner's roster stream emits without a doc whose id == the owner's
  uid, the client creates `team/{ownerUid}` with `kind: 'owner'`,
  `status: 'active'`, `name` from the owner's profile (business/display name),
  and a fixed `colorSeed`. Existing rules already allow owner create/update on
  `team/**`. Creation is idempotent (merge) and owner-session-only — staff
  sessions never attempt it.
- `kind: 'owner'` joins the existing `staff | named` kinds in `TeamMember`.
- **Roster UI:** the owner row is pinned first, labeled "You" on the owner's
  device, excluded from rename and archive (no overflow menu). Staff viewing
  any roster-driven UI see the owner's actual name.
- **Assign picker:** owner entry pinned first; rendered "You" for the owner.
  Assignment writes use the same `assignedMemberId/Name` fields — no special
  casing downstream.
- **My-work for owners:** the My work chip, staff-only in Phase 2a, turns on
  for owners too. Same filter (`assignedMemberId == authUid`); the
  `toggleMyWork` staff guard and the chip visibility condition drop their
  staff-only checks. The staff dashboard's Mine tile is unchanged; the owner
  dashboard has no count-tile row, so the owner's entry points are the chip
  and their own row in the Team-screen workload overview (§3). With the chip
  now visible to owners, the Phase 2a "stale myWorkOnly after kill-switch"
  guard is replaced: an ex-staff owner-of-self keeps a working, clearable
  My work chip over their own tree instead of a silently ignored filter.
- **Seeder:** `emulatorSetupStaff.js` (and the smoke runbook) gain the owner
  roster doc so smoke starts in the real post-2b shape.

## 3. Workload overview (Team screen)

The owner-only Team screen answers "who is working on what":

- The Team ViewModel additionally streams the orders list (same repository
  stream the order list uses) and computes, per roster member, the count of
  **open** orders — `assignedMemberId == member.id`, not archived, status not
  `DELIVERED`. An **Unassigned** row (assignedMemberId null) appears whenever
  its count is > 0. Counting is client-side over the already-synced list — no
  new queries, no denormalized counters.
- Each roster row shows its count; rows with count > 0 (including Unassigned)
  are tappable and navigate to the Orders list pre-filtered to that member.
- **Navigation/filter:** reuse `OrderListRoute(initialFilter)` with a new
  prefixed constant — `OrderListFilter.assignee(memberId)` producing
  `"assignee:<memberId>"` (and `"assignee:none"` for Unassigned). The
  ViewModel parses the prefix into an `assigneeFilter: String?` state field
  composed into `filterAndSort` alongside the existing filters.
- **List UI:** when `assigneeFilter` is active, a selected chip labeled with
  the member's name ("Assigned to Fola") appears in the chip row; tapping it
  deselects (consistent with the Phase 2a chip-toggle behavior). It is
  orthogonal to status chips, like My work.
- Archived and Delivered orders keep their assignee data; they are only
  excluded from the *open* counts.

## 4. Smoke-infra chore

- **Storage emulator:** add `storage` to `firebase.emulator.json` (rules file:
  the same `storage.rules`), wire `useEmulator` for Storage in
  `connectFirebaseEmulatorsIfEnabled`, note it in the smoke runbook.
- **Debug cleartext config, committed:** `network_security_config.xml`
  (cleartext to `10.0.2.2` / `127.0.0.1` / `localhost` only) moves into
  `composeApp/src/androidDebug/res/xml/`, with the
  `android:networkSecurityConfig` attribute added via a debug-source-set
  manifest overlay. Release manifests are untouched; no more local-only file
  surgery before emulator smoke.
- **Parent spec update:** §9 item 2 (kill switch) is marked resolved — fixed in
  Phase 2a, verified live in emulator smoke 2026-08-09 (drop and recover, no
  restart).

## 5. Testing

- **Firestore rules (emulator, functions test suite):** widened staff branch —
  items+notes+status accepted; each smuggle combo denied (money keys,
  `deadline`, assignment fields, serverCreatedAt tamper). Owner branch
  unchanged-behavior tests stay green.
- **Storage rules (`@firebase/rules-unit-testing`):** staff read anywhere in
  the workshop tree; staff write allowed under order-media prefix, denied
  outside it (logo path); revoked staff (membership doc not ACTIVE) denied
  everywhere; foreign user denied; owner unchanged.
- **Client unit tests:** owner roster ensure (created once, idempotent, never
  from staff session); "You" rendering + pin-first ordering; my-work filter
  for owner sessions; workload counts (open-order definition, unassigned
  bucket); `assignee:` filter parsing + composition with status/my-work
  filters; affordance gating per role on order detail.
- **Emulator smoke:** staff adds a fabric photo end-to-end (upload lands in
  owner tree via Storage emulator, item update visible on owner device);
  due-date pencil absent for staff; owner self-assign via "You"; Team screen
  counts match; tap-through filter works.

## 6. Rollout

No new sequencing. The widened Firestore staff branch and the Storage staff
branch deploy with the same production rules flip the Slice 8e runbook already
gates (post-backfill, post-strip). Client changes ride the normal release; the
`items` whitelist is only reachable by staff sessions, which don't exist in
production until the staff feature launches.

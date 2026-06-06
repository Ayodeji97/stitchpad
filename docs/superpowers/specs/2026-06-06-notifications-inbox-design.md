# Notifications — Slice 2: In-App Inbox (design)

> **Date:** 2026-06-06
> **Status:** approved design, pre-implementation
> **Slice:** 2 of 3 in the notifications feature (email ✅ → **in-app inbox** → push)
> **Branch:** `feature/notifications-inbox`
> **Builds on:** Slice 1 (daily email digest, merged PR #123)

## Context

Slice 1 shipped a daily **email** digest: a scheduled Cloud Function scans each
tailor's orders ~07:00 Africa/Lagos and emails one suppress-when-empty summary of
**overdue / due-soon / to-collect** orders, powered by a pure `digestDetector`.
The email reaches tailors with the app closed, but it's read-only and ephemeral.

Slice 2 adds the **in-app surface**: a notifications inbox the tailor opens from the
dashboard bell, showing a persistent, read/unread feed of the same events. The
dashboard already has a stubbed `BellButton` (with a built-in unread indicator)
waiting for exactly this. This slice also lays the foundation push (Slice 3) builds
on — push events will land in the same `notifications` collection.

## Goal & model

**Goal:** in-session visibility — the tailor sees, in the app, a running feed of what
needed attention, with unread tracking, and can tap straight to the order.

**Model decision — an activity/notifications feed (events + read/unread), NOT a live
to-do list.** A notification is an event raised at a point in time ("ASOEBI became
overdue"); it persists as read/unread history. We don't retract it when the underlying
order is later resolved — the tailor marks it read and acts. New distinct events raise
new entries. (Chosen over a client-derived "needs attention" list, which would
duplicate the TS detection logic in Kotlin and partly duplicate the dashboard's
existing "Next Best Actions".)

## V1 decisions (locked)

- **Events:** the same three as the email — `OVERDUE`, `DUE_SOON`, `TO_COLLECT`.
  Lifecycle/activity events (status changes, payments) are deferred.
- **Producer:** server-written, by **extending the existing daily scan** (no new
  scheduled function, no Firestore triggers). One scan → both channels.
- **Dedup:** deterministic notification IDs → intrinsic, no tracking doc.
- **Tap:** marks read + navigates to the order's detail screen (plain in-app nav — no
  deep-link infrastructure; that's the deferred external email/push case).
- **Bell:** a numeric **unread count** badge.
- **Read-state:** per-tap mark-read + a "Mark all read" action. Persistent history.
- **Inbox is always-on in V1** — *not* gated by the email opt-out or the `STAGING`
  rollout allowlist (in-app has no spam/reputation risk). The existing
  `dailyDigestEmailEnabled` toggle stays email-only.

## Architecture

### 1. Data model + Firestore rules

**Collection:** `users/{uid}/notifications/{notificationId}`, where
**`notificationId = "{orderId}__{type}"`** — deterministic, giving free dedup.

**Doc — structured fields** (the client composes display text; the server stores data,
not sentences — keeps copy/i18n on the client, avoids duplicating the email's wording):

```
orderId:      string
type:         "OVERDUE" | "DUE_SOON" | "TO_COLLECT"
customerName: string
garmentSummary: string      // e.g. "ASOEBI"
amount:       number | null  // set for TO_COLLECT (naira)
deadline:     number | null  // set for OVERDUE / DUE_SOON (epoch millis)
isRead:       boolean        // starts false
createdAt:    number         // when first raised (server time)
```

**Client domain/DTO/mapper** (mirror the order pattern):
- `core/domain/model/Notification.kt` + a `NotificationType` enum.
- `core/data/dto/NotificationDto.kt` — typed `@Serializable` (never `Map<String,Any?>`
  — iOS Native crashes; see `feedback_kmp_native_serializer_any`).
- `core/data/mapper/NotificationMapper.kt`.

**`firestore.rules`** — owner-only, server-creates-only, no client delete in V1:
```
match /notifications/{id} {
  allow read, update: if isOwner(uid);   // client marks isRead
  allow create, delete: if false;         // server (Admin SDK) creates; no client delete in V1
}
```
*Why no client delete:* with deterministic IDs, deleting a notification for a still-
overdue order would just get re-created on the next scan ("resurrection"). Mark-read is
enough for V1; a retention/cleanup sweep is a deferred follow-up. The collection lives
under `users/{uid}` so `onAuthUserDeleted` already cascade-cleans it on account deletion.

### 2. Producer — extend the daily scan (one scan, two channels)

**Detector change:** `digestDetector`'s `DigestItem` gains an **`orderId`** field (the
detector already iterates orders — just carry it through). Harmless to the email;
required for the deterministic ID + tap-to-order.

**Restructure `runDailyDigest`'s per-tailor loop** so notification-writing runs for
**every** tailor *before* the email gates:
```
for each recipient:
  try:
    model = detect(loadOrders(uid))
    io.writeNotifications(uid, model)          // ALWAYS — deduped, create-if-absent
    if !digestEnabled   -> skippedDisabled;    continue   // email gates from here only
    if !isAllowed       -> skippedNotAllowed;  continue
    if alreadySentToday -> skippedAlreadySent; continue
    if isEmpty(model)   -> suppressedEmpty;    continue
    sendEmail; stampLastSent; sent++
  catch: failed++
```
This means `loadOrders` now runs for every tailor (previously disabled users were
skipped before the read) — fine at current scale; the existing scale-path comment still
applies.

**`writeNotifications(uid, model)` — a new `DigestIO` method** (keeps the pure/testable
seam). For each item across the three buckets, attempt
`notifications.doc("{orderId}__{type}").create({...structured fields, isRead:false, createdAt:now})`.
Admin SDK `.create()` throws `ALREADY_EXISTS` if present → caught & skipped. So: first
morning overdue → created; every later morning → no-op; **read-state is never touched by
re-scans**, and a resolved order's notifications persist as history.

**Debug/QA:** extend the existing `debugSendMyDigest` callable to also write
notifications for the caller, so the in-app **Debug → Notifications → "Send daily digest
now"** button populates the inbox on demand.

**Known V1 limitation:** a `TO_COLLECT` notification snapshots `amount` when first raised;
a later partial payment won't rewrite it (create-if-absent never updates). Tapping
through shows the order's true current balance. Amount-refresh is an easy later
refinement, not worth a read-modify-write in V1.

### 3. Client data layer

`core/domain/repository/NotificationRepository.kt`:
```
fun observeNotifications(userId): Flow<Result<List<Notification>, DataError.Network>>  // createdAt desc
fun observeUnreadCount(userId): Flow<Int>                                               // where isRead==false
suspend fun markAsRead(userId, notificationId): EmptyResult<DataError.Network>
suspend fun markAllRead(userId): EmptyResult<DataError.Network>
```
`feature/notification/data/FirebaseNotificationRepository.kt` mirrors
`FirebaseOrderRepository`: collection `.snapshots()` → typed DTO → mapper → `Flow`, with
`.catch` emitting `Result.Error`. `observeUnreadCount` is a separate
`where("isRead","==",false)` snapshot count (single-field index — automatic; no composite
index). `markAsRead`/`markAllRead` write `isRead=true` via the existing
`offlineWrites.enqueue` fire-and-forget path; the snapshot reflects it. DI in a new
`notificationModule` (data + `viewModelOf`), registered in `initKoin`.

### 4. Inbox screen + navigation

- New `NotificationsInboxRoute` in `navigation/Routes.kt`; `composable<NotificationsInboxRoute>`
  in the main nav graph → `NotificationsInboxRoot(onNavigateBack, onNavigateToOrder)`.
- Full MVI set under `feature/notification/presentation/inbox/`
  (`State`/`Action`/`Event`/`ViewModel`/`Root`/`Screen`), following the order-list pattern
  (`koinViewModel()` + `ObserveAsEvents` in the Root).
- **VM** observes notifications → maps each to a UI model whose **display text is composed
  client-side via string resources** keyed by `type` (OVERDUE → "{customer}'s {garment} is
  overdue", DUE_SOON → "{customer}'s {garment} is due soon", TO_COLLECT → "{customer} owes
  {amount}"), plus a relative timestamp from `createdAt`. Unread items render emphasized.
- **Row:** reuse the `AccentedOrderRow`/list-row pattern (accent/icon by type, title,
  context line, unread indicator). Tap → `OnNotificationClick`.
- **Empty + loading:** the app's illustrated empty-state pattern ("No notifications yet")
  + `LoadingDots`. Every Screen gets a `@Preview` (populated + empty).
- **Top bar:** title + a **"Mark all read"** action, shown only when unread > 0.
- **Tap →** `markAsRead(id)` (fire-and-forget) + emit `NavigateToOrderDetail(orderId)` →
  the existing order-detail route (plain in-app nav).

### 5. Bell + dashboard wiring

- `BellButton` changes from `hasUnread: Boolean` → **`unreadCount: Int`** (renders a small
  count bubble; 0 = none; "9+" cap).
- `DashboardViewModel` observes `observeUnreadCount(userId)` → `state.unreadNotificationCount`.
- The dashboard header's `BellButton` dispatches `DashboardAction.OnNotificationsClick` →
  event → `onNavigateToNotifications` → the inbox route. (Wires the long-stubbed bell.)

### 6. Read-state, errors, edge cases

- **Read-state:** per-tap mark-read; "Mark all read" batch-updates unread docs. Unread
  count is its own live query, so the bell stays correct from the dashboard.
- **Errors:** observe `.catch` → snackbar + keep last list; mark-read failures are
  low-stakes (log, no blocking error).
- **Orphaned notification** (order later deleted): tap routes to order-detail with a
  missing id → the order-detail screen handles "not found" gracefully (snackbar/back).
- **Retention:** append-only in V1 (no client delete); cleanup of old read notifications
  is a deferred follow-up.

## Testing

- **Functions (Jest):** `digestDetector` carries `orderId` (new assertion); a pure test
  that a `DigestModel` yields the right deterministic-ID doc set + structured fields +
  dedup (fake IO simulating `ALREADY_EXISTS`); extend `runDailyDigest` fake-IO tests to
  prove notifications are written even for disabled / not-allowed / empty recipients while
  the email still gates.
- **Client:** `NotificationsInboxViewModel` (Turbine) — observe→state, mark-read,
  mark-all-read, tap→event; repo mapping; `BellButton` count preview.
- **iOS compile** gate (`:composeApp:compileKotlinIosSimulatorArm64`).
- **Manual smoke test (Daniel is QA):** Debug → "Send daily digest now" writes
  notifications → open via the bell (shows count) → tap one → order detail + count
  decrements → "Mark all read" → 0; confirm the email still sends.

## Out of scope (this slice)

- Lifecycle/activity events (status changes, payments) + the Firestore-triggered/immediate
  writes that pair with them.
- Retention/cleanup sweep; client delete/dismiss.
- `TO_COLLECT` amount-refresh on partial payment.
- External deep-linking from email/push (deferred to push, Slice 3).
- Per-notification or in-app opt-out preferences.

## Review

Per the rotation: **Cursor Bugbot** (auto) + **`codex review`** (pre-push hook) before
merge. Watch for: client/server field-name drift on the notification doc, the restructured
`runDailyDigest` loop still gating the email correctly, deterministic-ID edge cases
(read-state preserved across re-scans), and plural grammar in the rendered notification copy.

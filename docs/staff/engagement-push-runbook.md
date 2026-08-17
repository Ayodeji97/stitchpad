# Engagement push — operator runbook

The twice-weekly activation nudge (`functions/src/notifications/engagementPush.ts`).
Runs **Tue + Fri at 10:00 Africa/Lagos**, after the 07:00 daily digest.

Copy and cadence live in the Firestore doc `config/engagementPush`. Changing them
needs **no deploy and no app release**.

---

## How a tailor gets picked

A pure ladder (`segmentDetector.ts`) assigns each tailor their **first unmet
milestone**, most specific first, so a tailor with no customers is never told about
staff accounts.

| # | Segment | Condition | What the copy should say |
|---|---|---|---|
| 1 | `no_customer` | 0 customers | Add your first customer |
| 2 | `no_order` | has customers, 0 orders | Turn a customer into an order |
| 3 | `busy_no_team` | >= 10 orders, 0 active staff | Add your staff and assign orders |
| 4 | `welcome_ending` | free tier, First Month ends in <= 3 days | Upgrade before the cap drops |
| 5 | `dormant` | no new order logged in 21+ days | Come back, add this week's jobs |
| 6 | `no_costs` | delivered orders with no cost recorded | Add one job's cost, see your profit |
| 7 | `quiet` | activated, but nothing due/overdue/owed today | Add this week's jobs |
| 8 | `no_referral` | activated, never minted a referral link | Invite a tailor, win a shirt |
| 9 | `all` | everyone — always the last link in the chain | Announcements |

`quiet` deliberately outranks `no_referral`: a message about the tailor's own work
beats a message about our growth. Rungs 4–7 apply only once 1–3 are cleared — asking
someone with zero customers to upgrade, come back, or recruit is the noise this ladder
prevents.

`welcome_ending` sits highest of those because it **expires**: the others will still be
true next week. `dormant` sits above `quiet` because it is the sharper signal on the
same tailor — and `quiet` stays behind it as a fallback once the dormant copy is spent.

`dormant` measures the newest order's `createdAt`, not `updatedAt`: editing an old
order is not starting new work, and `updatedAt` is also bumped by our own server
writes, which would mask the very state being detected.

`no_costs` counts DELIVERED orders whose `/private/money` carries no cost entry — their
profit is simply unknowable. Only delivered orders count: work still in progress may
legitimately have costs still to come. It ranks below dormancy and the expiring window
because getting paid beats bookkeeping.

Its copy is deliberately **singular and unquantified**. "Add what 23 finished jobs cost
you" reads as a backlog rather than a task, and the count was wrong anyway —
`{{orderCount}}` is every order, not the ones actually missing costs. A nudge has to
feel like one small thing the tailor can do right now.

`busy_no_team` counts **active, non-owner** roster rows only — every workshop has
an auto-created owner row, and archived members are never deleted.

Segments are a **chain**, not a single bucket: a tailor gets their most specific
segment, then falls back through to `all`. That is what stops someone going silent
once their specific copy hits `maxSendsPerUser`, and what lets a `segment: "all"`
announcement reach a tailor who is also in `no_customer`.

**Active staff accounts are excluded entirely.** Staff work inside the owner's
workshop, so their own customer and order counts are always zero — without the
exclusion every staff member would be nudged to "add your first customer" forever.

## Why it is not quiet

The first cut shipped Android `IMPORTANCE_LOW` + iOS `interruption-level: passive`, on
the theory that a promo which buzzes a working tailor's phone gets the app muted. Tested
on a real device that proved too quiet to work: passive suppresses the banner AND the
sound on iOS, so the nudge only ever reached Notification Centre, and IMPORTANCE_LOW is
similarly invisible on Android. A notification nobody notices cannot build a habit.

Both platforms now send at normal priority. Fatigue is held down by the four things that
actually matter, all of which survive the volume change: the channel is **separately
mutable** from order reminders, sends are capped at **twice a week**, capped again
**per campaign**, and there is a **dedicated opt-out**.

The Android channel id is `announcements_v2` for a hard reason: Android locks a
channel's importance at creation and no later code change can raise it, so a new id was
the only way to move off LOW. Never edit an id in place — mint the next one.

## Brakes (all must pass before a tailor is sent anything)

1. `enabled: true` in the config **and** today is in `daysOfWeek`.
2. The tailor has not turned off **Settings → Tips & announcements**.
3. `ENGAGEMENT_STAGING` in `functions/src/notifications/rollout.ts` — while `true`,
   only the ~35 allowlisted testers can receive it.
4. They received no push at all today (`lastPushDate`) — this is what stops a nudge
   stacking on the morning digest.
5. At least `minDaysBetween` days since their last engagement push.
6. They are under `maxSendsPerUser` for the chosen campaign.

---

## Paste-this config

Edit `config/engagementPush` by **replacing the whole document**, not by editing
individual fields — the parser validates the document as a unit, and a half-edited
doc is the easiest way to silently send nothing.

```json
{
  "enabled": false,
  "daysOfWeek": [2, 5],
  "minDaysBetween": 3,
  "campaigns": [
    {
      "id": "2026-08-first-customer",
      "segment": "no_customer",
      "title": "Start with one customer",
      "body": "Add your first customer and keep their measurements in one place.",
      "target": "inbox",
      "priority": 0,
      "maxSendsPerUser": 3
    },
    {
      "id": "2026-08-first-order",
      "segment": "no_order",
      "title": "What are you sewing now?",
      "body": "Log it as an order and StitchPad tracks the deadline for you.",
      "target": "inbox",
      "priority": 0,
      "maxSendsPerUser": 3
    },
    {
      "id": "2026-08-team-discovery",
      "segment": "busy_no_team",
      "title": "Do you have staff?",
      "body": "Add them to StitchPad and assign orders, so you can see who is on what.",
      "target": "inbox",
      "priority": 0,
      "maxSendsPerUser": 2
    },
    {
      "id": "2026-08-founding-tailors",
      "segment": "no_referral",
      "title": "Know another tailor?",
      "body": "Invite them. Top 3 each month win a free StitchPad shirt.",
      "target": "inbox",
      "priority": 0,
      "maxSendsPerUser": 2
    },
    {
      "id": "2026-08-quiet-nudge",
      "segment": "quiet",
      "title": "Nothing due today",
      "body": "Add this week's jobs so nothing slips through.",
      "target": "inbox",
      "priority": 0,
      "maxSendsPerUser": 3
    },
    {
      "id": "2026-08-welcome-ending",
      "segment": "welcome_ending",
      "title": "{{businessName}}, your First Month is ending",
      "body": "Upgrade to keep every customer on your list.",
      "target": "inbox",
      "priority": 0,
      "maxSendsPerUser": 2
    },
    {
      "id": "2026-08-come-back",
      "segment": "dormant",
      "title": "It's been a while",
      "body": "Add the jobs you are working on and StitchPad tracks the deadlines again.",
      "target": "inbox",
      "priority": 0,
      "maxSendsPerUser": 3
    },
    {
      "id": "2026-08-add-costs",
      "segment": "no_costs",
      "title": "How much did you make?",
      "body": "Add what your last job cost you and StitchPad works out the profit.",
      "target": "inbox",
      "priority": 0,
      "maxSendsPerUser": 2
    }
  ]
}
```

Every campaign uses `target: "inbox"` deliberately: **every already-shipped client
understands it.** Switch to `founding_tailors` or `dashboard` only once the build
carrying those targets has real adoption. An unknown target is harmless — the
notification still shows and the tap just opens the app — but it wastes the tap.

### Personalising copy

`title` and `body` may use `{{variables}}`:

| Variable | Value |
|---|---|
| `{{businessName}}` | businessName, falling back to displayName, then "Tailor" — never blank |
| `{{points}}` | Founding Tailors points this month; `0` for a tailor with no referral link |
| `{{customerCount}}` | customers on their list |
| `{{orderCount}}` | orders they have logged |

> "**{{businessName}}, you're on {{points}} points**" — Invite one more tailor to climb
> the Founding Tailors board. Top 3 this month win a free shirt.

An unrecognised variable **drops that campaign at parse time** (`unknown_template_variable`
in the logs) rather than rendering `Hi {{bussinessName}}` onto somebody's lock screen.
Names are case-sensitive.

`{{points}}` costs two reads for the whole run — `marketers` maps uid to marketer, and
`leaderboards/current` holds every score in one document — not two reads per user.

### Field reference

| Field | Meaning |
|---|---|
| `enabled` | Must be a real boolean `true`. The string `"true"`, `1`, `"yes"` all leave it OFF, by design. |
| `daysOfWeek` | Cron numbering, `0`=Sun … `6`=Sat. **Can only NARROW the deployed Tue/Fri cron, never move it** — see the warning below. Defaults to `[2,5]`. |
| `minDaysBetween` | Minimum days between engagement pushes to the same tailor. |
| `id` | Unique. Also the key for that campaign's per-user send tally — **never reuse an id for different copy**, or the new copy inherits the old one's caps. |
| `segment` | One of the six above. Unknown value → campaign dropped. |
| `target` | `inbox`, `to_collect`, `dashboard`, `founding_tailors`. |
| `priority` | `0` = normal rotation. `>0` jumps the queue (announcements); highest wins. |
| `startAt` / `endAt` | Optional epoch **milliseconds**. Inclusive. Omit for always-on. |
| `maxSendsPerUser` | Lifetime cap per tailor. `0` = unlimited. This is what stops a churned tailor being nudged every Tuesday forever. |

### `daysOfWeek` cannot move the schedule

The Pub/Sub trigger is deployed with a fixed `0 10 * * 2,5` cron, so the function only
ever *wakes up* on Tue and Fri. `daysOfWeek` is a second check inside the run, which
means it can narrow that set (e.g. `[2]` for Tuesdays only) but **cannot move it**.

Setting `daysOfWeek: [1,4]` for Mon/Thu produces total silence: the function never runs
on Mon/Thu, and on Tue/Fri the check rejects the day. The run logs
`not a configured send day` with both `lagosWeekday` and `configuredDays` so this is
diagnosable — but to genuinely change the days you must edit `SCHEDULE` in
`engagementPush.ts` and redeploy.

### Announcing a release

Add one campaign with `segment: "all"`, a `priority` above 0, a `startAt`/`endAt`
window, and `maxSendsPerUser: 1`. It overrides the rotation for its window and then
stops on its own.

---

## Rollout order

> **Gate `enabled: true` on app adoption.** The `announcements_v2` channel only exists
> on builds carrying this feature. A device on an older build receives the nudge in the
> background, finds no such channel, and the FCM SDK falls back to its own auto-created
> "Miscellaneous" channel — so it still displays and still deep-links, but it sits on a
> channel whose name means nothing to the tailor and which they cannot mute separately.
> Wait for meaningful adoption of the new build before flipping `enabled`.

1. Deploy functions (`cd functions && npm run deploy`).
2. Create `config/engagementPush` with the JSON above (`enabled: false`).
3. Flip `enabled: true`. `ENGAGEMENT_STAGING` still limits delivery to testers.
4. Call `debugSendMyEngagementPush` from a tester account — it bypasses the
   weekday, cadence, and already-pushed-today gates and returns the resolved
   segment plus the chosen copy, so you can see exactly why you got what you got.
5. Watch one real Tuesday: the run logs `engagement push run complete` with a full
   breakdown (`sent`, `skippedOptedOut`, `skippedPushedToday`, `skippedCadence`,
   `skippedNoCampaign`, …).
6. Only then set `ENGAGEMENT_STAGING = false` and redeploy.

## When nothing sends

The system fails safe to silence, so "nothing happened" is the expected result of
any config mistake. Check the logs in this order — each step logs its reason:

- `engagementConfig: doc missing or not an object` — the doc does not exist.
- `engagementConfig parsed` — shows `enabled`, `campaignCount`, `droppedCount`. A
  non-zero `droppedCount` means malformed campaigns; the preceding
  `engagementConfig: campaign dropped` lines name the `id` and the `reason`.
- `engagement push: disabled by config` / `not a configured send day`.
- `engagement push: not a configured send day` — includes `lagosWeekday` and
  `configuredDays`; if they never intersect, see the `daysOfWeek` warning above.
- `engagement push run complete` — per-tailor skip counts. `skippedNoValidCampaigns`
  is run-level (the config parsed to nothing); `skippedNoCampaign` is per-tailor (no
  live campaign matched any segment in their chain).

## Smoke testing

Three tiers. Tier 1 is 2 minutes and catches most regressions; do Tier 2 before any
release that touches notifications; Tier 3 only when you want to see a real push.

### Tier 1 — backend only (fast, no device)

```bash
# terminal 1 — repo root
firebase emulators:start --config firebase.emulator.json

# terminal 2 — from functions/
# ONE LINE. A `\` continuation picks up trailing whitespace when pasted, which zsh
# splits into separate commands ("export: not valid in this context").
export FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099 GCLOUD_PROJECT=stitchpad-30607
npm run build                          # lib/ must be current — the driver loads from it
node scripts/emulatorSetupStaff.js     # seeds Fola (owner) + Gabby (staff)
node scripts/engagementPushSmoke.js    # 10 checks
```

Must end in `ALL CHECKS PASSED`. It runs the REAL `runEngagementPush` +
`productionEngagementIO` and asserts: the owner is nudged, an ACTIVE STAFF account is
excluded, the announcements channel + android tag + APNs passive flag are set, the
deep-link target is carried, state is stamped, and a second run the same day sends
nothing. FCM is stubbed (it has no emulator) so nothing is delivered.

### Tier 2 — device: channels + deep links (no FCM needed)

A notification tap is just `MainActivity` started with `target`/`orderId` extras, so
`am start` exercises the identical path end to end.

```bash
# 1. Point the debug app at the emulators, build, and install OVER the existing build.
#    Set USE_FIREBASE_EMULATOR = true in
#    composeApp/src/commonMain/.../core/config/EmulatorConfig.kt
./gradlew :composeApp:assembleDebug
adb install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk
```

> **Install with `-r`, do NOT uninstall first.** The in-place upgrade IS the regression
> test: `ensureNotificationChannels` used to early-return once `daily_reminders`
> existed, so on an upgraded install the new channel was never created. A clean install
> hides that bug entirely.
> If you hit `INSTALL_FAILED_VERSION_DOWNGRADE`, the device has a newer versionCode —
> uninstall and accept that you lose the upgrade-path check for this run.

```bash
# 2. Sign in as fola@gmail.com / fola123, then allow notifications.
adb shell pm grant com.danzucker.stitchpad android.permission.POST_NOTIFICATIONS

# 3. Both channels must exist at IMPORTANCE_DEFAULT (3).
adb shell dumpsys notification --noredact | grep -oE "mId='(daily_reminders|announcements_v2)'[^}]*mImportance=[0-9]+" | sort -u
# expect: announcements_v2 ... mImportance=3   AND   daily_reminders ... mImportance=3

# 4. Fire each deep link and check where the app lands.
fire() { adb shell am start -n com.danzucker.stitchpad/com.danzucker.stitchpad.MainActivity -e target "$1" --activity-single-top --activity-clear-top; }
fire founding_tailors   # -> Founding Tailors
fire inbox              # -> Notifications
fire to_collect         # -> Money to collect
fire dashboard          # -> Dashboard (run this while on ANOTHER screen; that is the
                        #    warm-resume case where it used to do nothing)
fire some_future_target # -> nothing happens, no crash (forward-compat)
```

### Tier 3 — a real push you can see and tap

FCM has no emulator, so a genuine delivery needs real credentials.

**Against production, on your own phone** (the only way to see it on a physical
device — a phone cannot reach the local emulators):

```bash
cd functions
node scripts/sendTestEngagementPush.js you@example.com            # list devices only
node scripts/sendTestEngagementPush.js you@example.com --send     # deliver
node scripts/sendTestEngagementPush.js you@example.com --send --target inbox
```

Read-only against Firestore, one account, nothing sent without `--send`. The payload is
byte-for-byte what the real job builds. `--quiet` re-adds the iOS passive level if you
want to compare tiers.

**Locally on an emulator**, opt in explicitly — the app must be signed in so it has
registered a real device token:

```bash
ENGAGEMENT_SMOKE_REAL_FCM=1 node scripts/engagementPushSmoke.js
```

The synthetic `tok-*` entries fail by design; the real registered token is what
delivers. Then:

```bash
adb shell dumpsys notification --noredact | grep -A6 "pkg=com.danzucker.stitchpad"
```

Expect `id=2002`, `channel=announcements_v2`, `importance=3`. It banners and makes a
sound, like the daily reminder. Tap it and confirm it lands on the campaign's `target`
screen.

**Against production**, the supported path is the tester-gated callable
`debugSendMyEngagementPush` (see Rollout order above), which bypasses the weekday,
cadence and already-pushed-today gates and returns the resolved segment plus the chosen
copy — so you can see exactly why you got the message you got.

### Afterwards

**Set `USE_FIREBASE_EMULATOR` back to `false`** and rebuild, or you will ship or keep
testing an emulator-pointed build. `git status` should be clean.

### Two traps the driver encodes

- The seeded `users/*` docs have **no `email` field**; uids resolve through Auth, the
  same fallback `productionEngagementIO` uses for legacy docs. Looking them up by the
  doc field silently yields an empty map and every assertion fails against
  `users/undefined/...`.
- `esModuleInterop` compiles `import * as admin` to `__importStar`, giving each module
  its **own copy** of the namespace — so stubbing `admin.messaging` never reaches
  `fcm.ts`, and the driver sends to real FCM instead. Patch the Messaging **singleton
  instance**, which every caller shares.

## Related

- Daily digest (07:00): `functions/src/notifications/dailyDigest.ts`. Its own gate
  is `STAGING`, opened to all users on 2026-08-17.
- Diagnosing push delivery: both jobs log every FCM failure as
  `<label>: FCM send failed` with the error code — the first place to look if
  pushes are silently not arriving, especially on iOS.

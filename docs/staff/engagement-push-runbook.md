# Engagement push — operator runbook

The twice-weekly activation nudge (`functions/src/notifications/engagementPush.ts`).
Runs **Tue + Fri at 10:00 Africa/Lagos**, after the 07:00 daily digest.

Copy and cadence live in the Firestore doc `config/engagementPush`. Changing them
needs **no deploy and no app release**.

---

## How a tailor gets picked

A pure ladder (`segmentDetector.ts`) assigns each tailor their **first unmet
milestone**. First match wins, so a tailor with no customers is never told about
staff accounts.

| Segment | Condition | What the copy should say |
|---|---|---|
| `no_customer` | 0 customers | Add your first customer |
| `no_order` | has customers, 0 orders | Turn a customer into an order |
| `busy_no_team` | >= 10 orders, 0 active staff | You can invite your tailors |
| `no_referral` | never minted a referral link | Founding Tailors invite |
| `quiet` | working, but nothing due/overdue/owed today | Add this week's jobs |
| `all` | everything above satisfied | Announcements |

`busy_no_team` counts **active, non-owner** roster rows only — every workshop has
an auto-created owner row, and archived members are never deleted.

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
      "title": "Turn that into an order",
      "body": "Log your first order and StitchPad tracks the deadline for you.",
      "target": "inbox",
      "priority": 0,
      "maxSendsPerUser": 3
    },
    {
      "id": "2026-08-team-discovery",
      "segment": "busy_no_team",
      "title": "You're running a full shop",
      "body": "Did you know you can invite your tailors and assign orders to them?",
      "target": "inbox",
      "priority": 0,
      "maxSendsPerUser": 2
    },
    {
      "id": "2026-08-founding-tailors",
      "segment": "no_referral",
      "title": "Founding Tailors",
      "body": "Invite a tailor friend. Top 3 each month win a free StitchPad shirt.",
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
    }
  ]
}
```

Every campaign uses `target: "inbox"` deliberately: **every already-shipped client
understands it.** Switch to `founding_tailors` or `dashboard` only once the build
carrying those targets has real adoption. An unknown target is harmless — the
notification still shows and the tap just opens the app — but it wastes the tap.

### Field reference

| Field | Meaning |
|---|---|
| `enabled` | Must be a real boolean `true`. The string `"true"`, `1`, `"yes"` all leave it OFF, by design. |
| `daysOfWeek` | Cron numbering, `0`=Sun … `6`=Sat. Defaults to `[2,5]`. |
| `minDaysBetween` | Minimum days between engagement pushes to the same tailor. |
| `id` | Unique. Also the key for that campaign's per-user send tally — **never reuse an id for different copy**, or the new copy inherits the old one's caps. |
| `segment` | One of the six above. Unknown value → campaign dropped. |
| `target` | `inbox`, `to_collect`, `dashboard`, `founding_tailors`. |
| `priority` | `0` = normal rotation. `>0` jumps the queue (announcements); highest wins. |
| `startAt` / `endAt` | Optional epoch **milliseconds**. Inclusive. Omit for always-on. |
| `maxSendsPerUser` | Lifetime cap per tailor. `0` = unlimited. This is what stops a churned tailor being nudged every Tuesday forever. |

### Announcing a release

Add one campaign with `segment: "all"`, a `priority` above 0, a `startAt`/`endAt`
window, and `maxSendsPerUser: 1`. It overrides the rotation for its window and then
stops on its own.

---

## Rollout order

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
- `engagement push run complete` — per-tailor skip counts.

## Related

- Daily digest (07:00): `functions/src/notifications/dailyDigest.ts`. Its own gate
  is `STAGING`, opened to all users on 2026-08-17.
- Diagnosing push delivery: both jobs log every FCM failure as
  `<label>: FCM send failed` with the error code — the first place to look if
  pushes are silently not arriving, especially on iOS.

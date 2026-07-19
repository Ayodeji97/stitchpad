# Seeing the User Journey — GA4 Explorations + BigQuery SQL

Companion to the Firebase Analytics instrumentation (`feat/analytics-user-journey`).
This is the "how do I actually read the journey" playbook: console explorations you
click together once, and SQL you run once BigQuery export is on.

## The event taxonomy this assumes

| Event | Params | Meaning |
|---|---|---|
| `sign_up` | `method` (`email`/`google`/`apple`) | account created (email or SSO new-user) |
| `login` | `method` (`email`/`google`/`apple`) | existing account signed in |
| `referral_code_applied` | `source` (`manual`/`install_referrer`/`clipboard`), `surface` (`signup`/`settings`) | referral attributed (fresh, replays excluded) |
| `workshop_setup_completed` | — | workshop saved (not skipped) |
| `customer_created` | — | a customer was created (not edited) |
| `order_created` | — | an order was created (not edited) |
| `ai_feature_used` | `feature` | an AI feature ran (e.g. `draft_message`) |
| `upgrade_completed` | `tier` | tier rose to `pro` / `atelier` |
| `screen_view` | `firebase_screen` (standard) | every destination incl. in-app tabs (from 1.2) |

User properties (on every event, for segmentation): `subscription_tier` (`free`/`pro`/`atelier`),
plus GA4's built-in `platform` (`ANDROID`/`IOS`).

Identity: `user_id` = Firebase uid (set on login); `user_pseudo_id` = per-install id
(always present). Use `user_pseudo_id` for in-app funnels; `user_id` for cross-device.

---

## Part 0 — One-time setup (done 2026-06-23)

**GA linked:** Google Analytics is enabled on the Firebase project `stitchpad-30607`
(Project settings → Integrations → Google Analytics). No re-download of the Android
`google-services.json` was needed; the iOS `GoogleService-Info.plist` was refreshed.
(The iOS `IS_ANALYTICS_ENABLED=false` key is legacy/ignored — not a blocker.
Since the analytics-hygiene change: DEBUG builds disable collection at every launch
on both platforms; the debug menu's analytics toggle re-enables it for a DebugView
session. Release builds always collect. A "Developer traffic" data filter is also
Active in GA4 (registered 2026-07-19), excluding debug-mode events from console
reports — BigQuery still receives them.)

**BigQuery export linking recipe** (Project settings → Integrations → BigQuery → Link):
- **Region: `europe-west1`** — matches Firestore for EU data residency. This is the one
  setting you CANNOT change later without recreating the dataset. Don't accept a US default.
- **Daily** export ON; **Streaming** OFF (Daily is the free standard export and is plenty
  pre-launch; add Streaming later only if you need same-day intraday tables).
- **Include advertising identifiers** OFF (not needed, cleaner for privacy).
- Apps exporting: all 3 (Android + iOS + web).
- Not backfilled: data only accrues from link time forward — which is why it was linked
  pre-launch, to bank the earliest-user cohort.

The `analytics_<propertyId>` dataset and `events_YYYYMMDD` tables appear ~24h after linking
(first daily export run). Swap `PROJECT.analytics_PROPERTYID` below for that dataset id.

**Custom dimensions registered** (GA4 → Admin → Data display → Custom definitions, done 2026-06-23)
so the params appear as selectable dimensions in explorations (Part 1):

| Dimension name | Scope | Source param/property |
|---|---|---|
| AI feature | Event | `feature` |
| Upgrade tier | Event | `tier` |
| Subscription tier | **User** | `subscription_tier` |
| Auth method † | Event | `method` |
| Referral source † | Event | `source` |
| Referral surface † | Event | `surface` |

† Added with the 1.1.0 events (`sign_up`/`login` `method`, `referral_code_applied`) —
registered in the console 2026-07-19. (`surface` may lag: GA4's parameter picker only
lists params it has received, and no `referral_code_applied` event had fired yet;
register it once the first one lands.) Dimensions populate from registration onward;
BigQuery has the raw params regardless.

(CORRECTION 2026-07-19: screens land in the SDK-standard `firebase_screen` param — the
custom `screen_name` param never shipped, so use GA4's BUILT-IN "Screen name" dimension
(no custom registration needed; delete the stale one if present). Also: before the
1.2 analytics-hygiene change, only auth/onboarding screens + "Main" were tracked — the
inner tab host was invisible. Raw params are
in Explorations + BigQuery regardless of registration; this just makes them UI-selectable.)

---

## Part 1 — GA4 console explorations (no SQL, ~24h latency)

Open **Firebase console → Analytics → "View in Google Analytics"** (or analytics.google.com
for the linked property) → **Explore**.

### 1.1 Activation + conversion funnel (the main one)

**Explore → Funnel exploration.** Technique: *Funnel exploration*. Set:

- **Steps** (Open funnel — counts users who reach each step at least once, in order):
  1. `sign_up`
  2. `workshop_setup_completed`
  3. `customer_created`
  4. `order_created`
  5. `ai_feature_used`
  6. `upgrade_completed`

  For each step: **Add step → "Event" → choose the event name → Add.**
- **Breakdown:** drag `Platform` (built-in dimension) to see Android vs iOS drop-off.
- Toggle **"Make open funnel"** on to count "ever reached" (recommended for activation);
  leave closed if you want strict in-order sequencing.
- **Show elapsed time** = on → reveals median time between steps (e.g. signup → first order).

This single chart answers "where do users drop off" and "do they reach value + upgrade."

### 1.2 Segmented funnel by tier

Duplicate 1.1. In **Segments → +New segment → User segment**, add condition
`subscription_tier` (User property) `exactly matches` `pro` (and another for `atelier`,
`free`). Drag the segments onto the funnel to compare conversion by plan.

### 1.3 Screen-flow path

**Explore → Path exploration.** Technique: *Path exploration*.
- **Starting point → Event name → `session_start`** (or pick a specific `screen_view`).
- **Node type → Screen name** (built-in dimension, reads `firebase_screen` — see "Register custom
  dimensions" below; until registered, use the param via BigQuery).
- Expand nodes to see "after screen X, where do users go." Good for discovering unexpected
  exits (e.g. most users leave right after `WorkshopSetup`).

### 1.4 Retention / cohorts

**Explore → Cohort exploration.**
- **Cohort inclusion:** `sign_up`. **Return criterion:** `session_start` (or `order_created`
  for value-retention). **Granularity:** Weekly. Answers "of users who signed up this week,
  how many came back / created an order in week 1, 2, 3."

### 1.5 Register custom dimensions (one-time, needed for 1.3 in console)

**Admin → Custom definitions → Create custom dimensions:**
- `feature` — scope Event, event parameter `feature`
- `tier` — scope Event, event parameter `tier`
- `subscription_tier` — scope User, user property `subscription_tier`

(Only events AFTER registration populate these in the console — BigQuery has them
immediately regardless. Register now so the console catches up.)

---

## Part 2 — BigQuery SQL (exact, once export is on)

**Enable first:** GA4 Admin → BigQuery links → link project `stitchpad-30607`, daily +
streaming. Dataset appears as `analytics_<PROPERTY_ID>`; daily tables `events_YYYYMMDD`,
streaming `events_intraday_YYYYMMDD`.

Replace `PROJECT.analytics_PROPERTYID` below with your dataset, and the date range in
`_TABLE_SUFFIX`. `event_timestamp` is **microseconds** → wrap in `TIMESTAMP_MICROS()`.

### Reusable param/property extractors

```sql
-- event param (string):     (SELECT value.string_value FROM UNNEST(event_params) WHERE key = 'feature')
-- event param (int):        (SELECT value.int_value    FROM UNNEST(event_params) WHERE key = 'X')
-- user property (string):   (SELECT value.string_value FROM UNNEST(user_properties) WHERE key = 'subscription_tier')
```

### 2.1 Funnel — users reaching each step (matches the GA4 open funnel)

```sql
WITH per_user AS (
  SELECT
    user_pseudo_id,
    MAX(IF(event_name = 'sign_up', 1, 0))                   AS signed_up,
    MAX(IF(event_name = 'workshop_setup_completed', 1, 0))  AS setup,
    MAX(IF(event_name = 'customer_created', 1, 0))          AS customer,
    MAX(IF(event_name = 'order_created', 1, 0))             AS first_order,
    MAX(IF(event_name = 'ai_feature_used', 1, 0))           AS used_ai,
    MAX(IF(event_name = 'upgrade_completed', 1, 0))         AS upgraded
  FROM `PROJECT.analytics_PROPERTYID.events_*`
  WHERE _TABLE_SUFFIX BETWEEN '20260601' AND '20260630'
  GROUP BY user_pseudo_id
)
SELECT
  SUM(signed_up)    AS s1_sign_up,
  SUM(setup)        AS s2_workshop_setup,
  SUM(customer)     AS s3_first_customer,
  SUM(first_order)  AS s4_first_order,
  SUM(used_ai)      AS s5_used_ai,
  SUM(upgraded)     AS s6_upgraded,
  ROUND(100 * SAFE_DIVIDE(SUM(setup),       SUM(signed_up)), 1)   AS pct_to_setup,
  ROUND(100 * SAFE_DIVIDE(SUM(customer),    SUM(setup)),     1)   AS pct_to_customer,
  ROUND(100 * SAFE_DIVIDE(SUM(first_order), SUM(customer)),  1)   AS pct_to_order,
  ROUND(100 * SAFE_DIVIDE(SUM(upgraded),    SUM(signed_up)), 1)   AS pct_signup_to_paid
FROM per_user;
```

### 2.2 Ordered funnel (each step strictly after the previous, per user)

```sql
WITH first_ts AS (
  SELECT
    user_pseudo_id,
    MIN(IF(event_name='sign_up',                  event_timestamp, NULL)) AS t_signup,
    MIN(IF(event_name='workshop_setup_completed', event_timestamp, NULL)) AS t_setup,
    MIN(IF(event_name='customer_created',         event_timestamp, NULL)) AS t_customer,
    MIN(IF(event_name='order_created',            event_timestamp, NULL)) AS t_order
  FROM `PROJECT.analytics_PROPERTYID.events_*`
  WHERE _TABLE_SUFFIX BETWEEN '20260601' AND '20260630'
  GROUP BY user_pseudo_id
)
SELECT
  COUNTIF(t_signup IS NOT NULL)                                              AS reached_signup,
  COUNTIF(t_setup   >= t_signup)                                             AS then_setup,
  COUNTIF(t_customer >= t_setup AND t_setup >= t_signup)                     AS then_customer,
  COUNTIF(t_order   >= t_customer AND t_customer >= t_setup
                                  AND t_setup >= t_signup)                   AS then_order
FROM first_ts;
```

### 2.3 Time-to-first-order (the headline activation metric)

```sql
WITH s AS (
  SELECT user_pseudo_id, MIN(event_timestamp) AS signup_ts
  FROM `PROJECT.analytics_PROPERTYID.events_*`
  WHERE _TABLE_SUFFIX BETWEEN '20260601' AND '20260630' AND event_name = 'sign_up'
  GROUP BY user_pseudo_id
),
o AS (
  SELECT user_pseudo_id, MIN(event_timestamp) AS order_ts
  FROM `PROJECT.analytics_PROPERTYID.events_*`
  WHERE _TABLE_SUFFIX BETWEEN '20260601' AND '20260630' AND event_name = 'order_created'
  GROUP BY user_pseudo_id
)
SELECT
  COUNT(*) AS activated_users,
  ROUND(APPROX_QUANTILES(hours, 100)[OFFSET(50)], 1) AS median_hours_to_first_order,
  ROUND(APPROX_QUANTILES(hours, 100)[OFFSET(90)], 1) AS p90_hours_to_first_order
FROM (
  SELECT TIMESTAMP_DIFF(TIMESTAMP_MICROS(o.order_ts), TIMESTAMP_MICROS(s.signup_ts), HOUR) AS hours
  FROM s JOIN o USING (user_pseudo_id)
  WHERE o.order_ts >= s.signup_ts
);
```

### 2.4 AI usage by feature

```sql
SELECT
  (SELECT value.string_value FROM UNNEST(event_params) WHERE key = 'feature') AS feature,
  COUNT(*)                        AS uses,
  COUNT(DISTINCT user_pseudo_id)  AS users
FROM `PROJECT.analytics_PROPERTYID.events_*`
WHERE _TABLE_SUFFIX BETWEEN '20260601' AND '20260630'
  AND event_name = 'ai_feature_used'
GROUP BY feature
ORDER BY uses DESC;
```

### 2.5 Upgrades by tier and platform

```sql
SELECT
  platform,
  (SELECT value.string_value FROM UNNEST(event_params) WHERE key = 'tier') AS tier,
  COUNT(*)                       AS upgrades,
  COUNT(DISTINCT user_pseudo_id) AS users
FROM `PROJECT.analytics_PROPERTYID.events_*`
WHERE _TABLE_SUFFIX BETWEEN '20260601' AND '20260630'
  AND event_name = 'upgrade_completed'
GROUP BY platform, tier
ORDER BY upgrades DESC;
```

### 2.6 Top screens + screen flow

```sql
-- Most-viewed screens
SELECT
  (SELECT value.string_value FROM UNNEST(event_params) WHERE key = 'firebase_screen') AS screen,
  COUNT(*)                       AS views,
  COUNT(DISTINCT user_pseudo_id) AS users
FROM `PROJECT.analytics_PROPERTYID.events_*`
WHERE _TABLE_SUFFIX BETWEEN '20260601' AND '20260630'
  AND event_name = 'screen_view'
GROUP BY screen
ORDER BY views DESC;
```

```sql
-- "After screen X, what screen next" (per-user ordered transitions)
WITH views AS (
  SELECT
    user_pseudo_id,
    event_timestamp,
    (SELECT value.string_value FROM UNNEST(event_params) WHERE key = 'firebase_screen') AS screen
  FROM `PROJECT.analytics_PROPERTYID.events_*`
  WHERE _TABLE_SUFFIX BETWEEN '20260601' AND '20260630'
    AND event_name = 'screen_view'
),
seq AS (
  SELECT
    screen AS from_screen,
    LEAD(screen) OVER (PARTITION BY user_pseudo_id ORDER BY event_timestamp) AS to_screen
  FROM views
)
SELECT from_screen, to_screen, COUNT(*) AS transitions
FROM seq
WHERE to_screen IS NOT NULL
GROUP BY from_screen, to_screen
ORDER BY transitions DESC
LIMIT 50;
```

### 2.7 Conversion segmented by subscription_tier (user property)

```sql
SELECT
  (SELECT value.string_value FROM UNNEST(user_properties) WHERE key = 'subscription_tier') AS tier,
  COUNT(DISTINCT user_pseudo_id) AS users,
  COUNT(DISTINCT IF(event_name = 'order_created',   user_pseudo_id, NULL)) AS created_order,
  COUNT(DISTINCT IF(event_name = 'ai_feature_used', user_pseudo_id, NULL)) AS used_ai
FROM `PROJECT.analytics_PROPERTYID.events_*`
WHERE _TABLE_SUFFIX BETWEEN '20260601' AND '20260630'
GROUP BY tier
ORDER BY users DESC;
```

### 2.8 Daily new-signup + activation trend

```sql
SELECT
  event_date,
  COUNTIF(event_name = 'sign_up')          AS sign_ups,
  COUNTIF(event_name = 'workshop_setup_completed') AS setups,
  COUNTIF(event_name = 'order_created')     AS orders_created,
  COUNTIF(event_name = 'upgrade_completed') AS upgrades
FROM `PROJECT.analytics_PROPERTYID.events_*`
WHERE _TABLE_SUFFIX BETWEEN '20260601' AND '20260630'
GROUP BY event_date
ORDER BY event_date;
```

### 2.9 Weekly signup→return retention cohort

```sql
WITH signups AS (
  SELECT user_pseudo_id,
         DATE_TRUNC(DATE(TIMESTAMP_MICROS(MIN(event_timestamp))), WEEK) AS cohort_week,
         MIN(event_timestamp) AS signup_ts
  FROM `PROJECT.analytics_PROPERTYID.events_*`
  WHERE _TABLE_SUFFIX BETWEEN '20260601' AND '20260930' AND event_name = 'sign_up'
  GROUP BY user_pseudo_id
),
activity AS (
  SELECT user_pseudo_id, event_timestamp
  FROM `PROJECT.analytics_PROPERTYID.events_*`
  WHERE _TABLE_SUFFIX BETWEEN '20260601' AND '20260930' AND event_name = 'session_start'
)
SELECT
  s.cohort_week,
  DATE_DIFF(DATE(TIMESTAMP_MICROS(a.event_timestamp)),
            DATE(TIMESTAMP_MICROS(s.signup_ts)), WEEK) AS weeks_since_signup,
  COUNT(DISTINCT s.user_pseudo_id) AS users
FROM signups s
JOIN activity a USING (user_pseudo_id)
WHERE a.event_timestamp >= s.signup_ts
GROUP BY s.cohort_week, weeks_since_signup
ORDER BY s.cohort_week, weeks_since_signup;
```

---

## Notes & gotchas

- **Latency:** `events_` tables land ~daily; use `events_intraday_*` (or the streaming
  export) for near-real-time. Querying `events_*` includes both shapes only if you union
  them — for fresh data query `events_intraday_YYYYMMDD` explicitly.
- **`sign_up` completeness boundary:** before v1.1.0 only email signups were logged
  (SSO missed — undercounted ~50%). From 1.1.0 all methods log with a `method` param.
  Funnels spanning the boundary should treat pre-1.1.0 `sign_up` as email-only and
  cross-check totals against the Firebase Auth dashboard.
- **`upgrade_completed` cold-start caveat** (backlog B1): until fixed, a returning Pro user
  opening the upgrade screen on a cold start can emit a spurious `upgrade_completed`. When
  trusting upgrade counts, dedupe to first-ever per user: `MIN(event_timestamp)` per
  `user_pseudo_id`, or cross-check against Paystack/Apple receipts.
- **Tier strings** are `free`/`pro`/`atelier` in both the `tier` param and the
  `subscription_tier` user property (backlog B2 consolidates them onto one source).
- **Cost:** always bound `_TABLE_SUFFIX` to a date range — `events_*` scans every day's
  table otherwise. Consider a scheduled query materialising a daily funnel table if you
  query often.
- **Looker Studio:** point it at any of these queries (or the dataset) for live dashboards
  once you know which charts you want.

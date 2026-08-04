# Analytics: user-level profiles + retention cohorts — design

**Date:** 2026-08-04
**Status:** Approved design, pending implementation plan
**Author:** Daniel (with Claude)

## Context

The user asked to add Mixpanel alongside Firebase Analytics/GA4 to get (A) better
funnels + retention and (B) user-level profiles/journeys. On investigation we
decided **not** to add Mixpanel: StitchPad already owns a mature analytics pipeline
that covers most of A, and doing B in-house is *better on privacy* than Mixpanel
(per-user data never leaves BigQuery + the local Mac; no third-party processor,
no new App Store / Play data-safety disclosure, no consent surface to build).

Mixpanel stays a documented backlog item with a concrete revisit trigger
(see "Out of scope" below).

This spec closes the two remaining gaps on top of the **existing** pipeline rather
than building anything new.

### The existing pipeline (do not disturb its working parts)

Lives at `~/StitchPad Analytics Data/` (outside the app repo; not version-controlled).

- `refresh/refresh.py` — deterministic, **no LLM in the data path**. Queries BigQuery
  via the `bq` CLI (`--format=json`) for two windows and regenerates outputs:
  - `query_window(start, production)` runs ~13 queries, returns a dict `d`.
  - `build_dataset(d, key)` shapes `d` into a render-ready `ds` dict.
  - `write_outputs(...)` renders `template.html` → `dashboard.html`, writes `*.csv`,
    and an `.xlsx`.
  - Two windows: **launch** (2026-07-01+, production devices only via `PROD_CTE` —
    excludes DebugView-flagged events and Portugal/US/Singapore) and **all**
    (2026-06-23+, every device).
  - `PROJECT = stitchpad-30607`, `DATASET = stitchpad-30607.analytics_530817992`.
- `refresh/run.sh` — launchd agent `com.danzucker.stitchpad-analytics-refresh`,
  daily ~08:17, logs to `refresh/refresh.log`. Creates a venv, `pip install openpyxl`.
- `dashboard.html` is a self-contained page (data embedded as JSON via a
  `__DATASETS_JSON__` template placeholder). It is **manually** republished to a
  claude.ai artifact (`.../artifact/b3ac2705-...`) — the Artifact tool only exists in
  interactive sessions, so the daily job never publishes headlessly.
- The whole pipeline is deliberately **aggregate and PII-free** (counts / % / enums /
  tier only). GA4 events carry no names.

Companion SQL playbook (in-repo): `docs/analytics/ga4-explorations-and-bigquery.md`.

## Goals

- **A — retention cohorts:** add a weekly **sign-up cohort** retention triangle to the
  public dashboard (aggregate, publishable).
- **B — user-level:** add, **local-only**, (a) named segment rosters and (b) a
  single-user journey lookup, resolving Firebase uid → tailor name/phone via Firestore.

## Non-goals

- No Mixpanel / third-party analytics SDK.
- No change to the in-app instrumentation (`Analytics` / `AnalyticsEvent` / the KMP
  app). This is entirely a data-pipeline change outside the app repo.
- No change to the published `dashboard.html`'s PII-free contract — B never touches it.

## The load-bearing principle: one hard line between PUBLIC and LOCAL

| | **PUBLIC surface** (aggregate, PII-free) | **LOCAL-only surface** (named) |
|---|---|---|
| Files | `dashboard.html` (published artifact), `*.csv`, `.xlsx` | new `local/` subfolder: `people.html`, `roster_*.csv` |
| Contains | counts, %, cohorts — **no names** | tailor names / phones, per-user journeys |
| Published to claude.ai artifact? | Yes (manual, as today) | **Never** — separate file, loud banner, never uploaded |

Gap A is entirely PUBLIC. Gap B is entirely LOCAL. The published `dashboard.html`
stays exactly as PII-free as today.

---

## Part A — Retention cohorts (PUBLIC)

**What:** weekly cohort retention triangle. Rows = cohort week (week of first
`sign_up`); columns = weeks-since-signup (W0…Wn); cells = % of that cohort active
(`session_start`) in that week. W0 = cohort size (100% by construction).

**Basis: `sign_up`** (decided) — the product-activation retention cut, not install
retention. Consequence: it will **not** match the existing install-based "Week-1
return" tile; that's expected and both coexist.

**Implementation:**

- New query `d["cohort"]` in `query_window()`, reusing `PROD_CTE`/`ALL_CTE` and both
  windows. Shape (adapt the playbook's §2.9, changing the cohort event to `sign_up`):
  ```sql
  , signups AS (
      SELECT user_pseudo_id,
             DATE_TRUNC(DATE(TIMESTAMP_MICROS(MIN(event_timestamp))), WEEK) AS cohort_week,
             MIN(event_timestamp) AS signup_ts
      FROM src WHERE event_name='sign_up' GROUP BY user_pseudo_id),
    rets AS (
      SELECT s.user_pseudo_id, s.cohort_week,
             DATE_DIFF(DATE(TIMESTAMP_MICROS(a.event_timestamp)),
                       DATE(TIMESTAMP_MICROS(s.signup_ts)), WEEK) AS weeks_since
      FROM signups s
      JOIN (SELECT user_pseudo_id, event_timestamp FROM src WHERE event_name='session_start') a
        ON a.user_pseudo_id = s.user_pseudo_id AND a.event_timestamp >= s.signup_ts
      UNION ALL   -- force every sign-up into W0 so the denominator is the true cohort size
      SELECT user_pseudo_id, cohort_week, 0 AS weeks_since FROM signups)
  SELECT CAST(cohort_week AS STRING) AS cohort_week, weeks_since,
         COUNT(DISTINCT user_pseudo_id) AS users
  FROM rets GROUP BY cohort_week, weeks_since
  ORDER BY cohort_week, weeks_since;
  ```
  W0 = the full cohort (all sign-ups that week), NOT session-derived — otherwise
  sign-ups with no post-signup session drop out and the denominator understates,
  yielding >100% cells and breaking reconciliation.
- `build_dataset()` pivots the long rows into a triangle: per cohort_week a row of
  `[cohort_label, cohort_size, W0%, W1%, W2%, …]`, where `Wn% = users_in_wn / cohort_size`.
- Render in `template.html` as a compact heatmap-style table (cell shading by %,
  reuse existing brand tokens — Indigo). Add a "Retention cohorts" section.
- Outputs: `cohort.csv` (public) + a "Retention" sheet in the xlsx.
- Carry a `data gap` chip (same style as the funnel note) noting the pre-1.1.0
  `sign_up` undercount.

**Independently shippable** — pure PUBLIC/aggregate, no identity join. Build first.

---

## Part B — User-level, local-only (rosters + journey)

Both deliverables render into a single self-contained `local/people.html` (data
embedded as JSON, same pattern as `dashboard.html`, no server). Covers **signed-in
users only** (those with a Firebase `user_id`); unsigned devices can't be named and
appear only as an aggregate "(unsigned device)" count.

### B.1 — Firestore name join (Option A, decided)

- Use the Python `google-cloud-firestore` client via the **existing gcloud
  Application Default Credentials** (`refresh.py` already authenticates to
  `stitchpad-30607` for `bq`; reuse the same ADC).
- `firestore_names(uids)` helper: for each uid seen in GA4 with `user_id`, fetch
  `users/{uid}` and read **only** `businessName` + `phone` (nothing else — no
  customer/measurement/order PII). Return an in-memory `uid -> {name, phone}` map.
- New pip dep `google-cloud-firestore` added alongside `openpyxl` in `run.sh`.
- **Headless caveat:** the launchd job runs non-interactively; gcloud ADC persists a
  refresh token, so this works. Verify on first run; if ADC is missing, fail soft
  (render `people.html` with pseudonymous ids + a "names unavailable: run
  `gcloud auth application-default login`" banner rather than crashing the whole
  refresh — Part A / public outputs must still succeed).
- Missing/deleted Firestore doc for a live uid → "(name unavailable)", no crash.

### B.2 — Per-user facts query

- New query `d["people"]` in `query_window()` (launch/production window), keyed by
  `user_id`. Per user: platform, `subscription_tier` (from `user_properties`),
  first_open day, sign_up day, last-active day, distinct active days, distinct days
  in first 14, workshop-setup flag, first-customer flag, first-order flag, orders
  count, payments count, AI-used flag, referral-applied flag, and an **ordered event
  timeline** (event_name + timestamp) for the journey view.
- Compute per-user **segment membership** in Python mirroring the existing aggregate
  definitions in `build_dataset()` (`activity_rows`): Activated, Qualified, Signed-in-
  but-passive, Dormant, Active-7d. Reuse the exact same thresholds so rosters
  reconcile with the aggregate segment counts.

### B.3 — Rosters (form a)

- For each segment, a named list: name, phone, tier, last-active, distinct-days.
- Rendered as sortable tables in `people.html` + written as `local/roster_<segment>.csv`.

### B.4 — Journey lookup (form b)

- Searchable list of tailors (by name/phone); selecting one shows their profile
  properties + full ordered event timeline. Client-side only (embedded JSON + a little
  JS), no server. At current scale (~130 users) embedding full timelines is fine.

### B.5 — Containment

- `local/people.html` opens with a bold banner: **"LOCAL ONLY — contains tailor
  names/phones. Never publish or share. Not for the claude.ai artifact."**
- `README.txt` documents the PUBLIC/LOCAL split.
- The manual artifact-publish step only ever targets `dashboard.html`, so B cannot
  leak through it. `local/` is never uploaded and never committed to any repo.

---

## Pipeline integration (files touched, all under `~/StitchPad Analytics Data/`)

- `refresh/refresh.py`:
  - `query_window()`: add `d["cohort"]` (both windows) and `d["people"]`
    (launch/production window only).
  - `build_dataset()`: pivot cohort → triangle rows; (people is shaped in a new
    function, not `build_dataset`, to keep the public dataset clean).
  - New `firestore_names(uids)` helper (Option A).
  - New `build_people(d_people, names)` → people dataset (rosters + journeys).
  - New `write_people_local(people_ds)` → renders `local/people.html` from a new
    `refresh/people_template.html`, writes `local/roster_*.csv`.
  - `write_outputs()`: cohort flows into the existing public dashboard/csv/xlsx path;
    people flows only into `local/`.
  - `main()`: call `build_people` + `write_people_local` after the public outputs, and
    wrap it so a Firestore/people failure never breaks the public refresh.
- `refresh/people_template.html`: new self-contained template (banner + rosters +
  journey search), brand tokens reused from `template.html`.
- `refresh/run.sh`: add `google-cloud-firestore` to the venv pip install.
- No new launchd schedule; the existing daily run now also regenerates `local/`.

## Verification & reconciliation

- **Cohort:** sum of cohort W0 sizes == total distinct `sign_up` users in the window.
  Each `Wn%` in [0, 100]. W0 == 100%.
- **Rosters:** signed-in segment roster row-counts ≈ the matching `activity.csv`
  segment counts (differences only from the `user_id` vs `user_pseudo_id` grain —
  documented, not hidden).
- **Names:** total named users ≤ signed-in (`authed`) count; unnameable surfaced as a
  count, never dropped silently.
- **Public unchanged:** `dashboard.html` diff shows only the added cohort section; no
  name/phone string appears anywhere in `dashboard.html`, `*.csv`, or `.xlsx`
  (grep check).
- Run `refresh.py` once end-to-end against live data and eyeball outputs before
  trusting the launchd cadence.

## Risks & caveats

- **pre-1.1.0 `sign_up` gap:** SSO sign-ups weren't logged before v1.1.0 (Android
  Jul 15). Early-July cohort *sizes* undercount; return *percentages* stay valid.
  Surface with a `data gap` chip.
- **`user_id` sparseness:** same gap means some signed-in devices lack a clean
  `user_id` → "(unsigned device)" bucket in B.
- **Firestore/GA4 skew:** uid present in GA4 but doc deleted in Firestore →
  "(name unavailable)".
- **ADC headless:** must be present for the launchd job; fail-soft so public outputs
  survive a missing/expired ADC.
- **Small samples:** cohort tails and TTFO-style metrics are directional; label them.

## Build sequence

1. **Part A** (cohorts) — PUBLIC, no identity join, independently valuable. Ship first.
2. **Part B** (Firestore join → rosters → journey) — LOCAL-only, after A.

## Out of scope / future

- **Mixpanel** — revisit trigger: when the non-technical **PM intern** needs to
  self-serve funnels/retention *without SQL*. Until then, this pipeline is cheaper and
  as capable. (A Looker Studio dashboard on the materialized metrics could be a cheaper
  down-payment on that same need if it arises.)
- Streaming/intraday freshness, UTM-tagged acquisition, and the screen-tracking-goes-
  dark-after-Home instrumentation fix are pre-existing backlog items, untouched here.

# Analytics: user-level profiles + retention cohorts — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close two gaps on the existing StitchPad analytics pipeline — add a public weekly `sign_up` retention-cohort triangle, and a local-only user-level view (named segment rosters + single-user journey) with a Firestore uid→name join — without disturbing the pipeline's PII-free public contract.

**Architecture:** Extract the new pure transformation logic into two small, unit-tested modules (`cohort.py`, `people.py`) imported by the existing `refresh.py`. `refresh.py` keeps all I/O: BigQuery via the `bq` CLI, Firestore via the `google-cloud-firestore` client (existing gcloud ADC), template rendering, and file writing. Part A output flows into the existing PUBLIC path (`dashboard.html` / `*.csv` / `.xlsx`); Part B output flows only into a new `local/` subfolder that is never published.

**Tech Stack:** Python 3 (stdlib + `openpyxl` + `google-cloud-firestore`), `pytest` (dev), BigQuery `bq` CLI, GA4 export dataset `stitchpad-30607.analytics_530817992`, self-contained HTML templates with embedded JSON.

**Spec:** `docs/superpowers/specs/2026-08-04-analytics-user-level-and-retention-design.md`

## Global Constraints

- **Working directory (all impl files):** `~/StitchPad Analytics Data/` — expanded in code as `BASE = os.path.expanduser("~/StitchPad Analytics Data")`, `REFRESH_DIR = BASE/refresh`. This folder is **NOT a git repo** — do not `git init` it. Task checkpoints are "tests pass" / "reconciliation verified", not commits.
- **PUBLIC vs LOCAL line (load-bearing):** No tailor name or phone may ever appear in `dashboard.html`, any `*.csv`, or the `.xlsx`. Names/phones live only under `~/StitchPad Analytics Data/local/`. Part A is PUBLIC; Part B is LOCAL-only.
- **Cohort basis:** `sign_up` (not install). Reconciliation target: sum of cohort W0 sizes == total distinct `sign_up` users in the window.
- **Firestore fields:** read **only** `businessName` and `phone` from `users/{uid}`. Nothing else.
- **Fail-soft:** a Firestore/ADC/people failure must never break the PUBLIC refresh. Public outputs are written first and unconditionally; Part B runs in a guarded block.
- **Determinism:** no LLM in the data path (existing invariant).
- **Constants (already in `refresh.py`):** `PROJECT = "stitchpad-30607"`, `DATASET = "stitchpad-30607.analytics_530817992"`, `TODAY`, `END = TODAY.strftime("%Y%m%d")`, `PROD_CTE`, `ALL_CTE`, `bq(sql)`, `query_window(start, production)`, `build_dataset(d, key)`, `write_outputs(...)`, `i(v)` int-coerce helper.
- **Run command (end-to-end):** `cd ~/StitchPad\ Analytics\ Data/refresh && ./venv/bin/python refresh.py`
- **Test command:** `cd ~/StitchPad\ Analytics\ Data/refresh && ./venv/bin/python -m pytest tests/ -v`

---

## File Structure

All paths under `~/StitchPad Analytics Data/`:

- `refresh/cohort.py` — **NEW.** Pure `pivot_cohort(rows)`. No I/O.
- `refresh/people.py` — **NEW.** Pure `classify_segments`, `build_people_dataset`, `assert_public_pii_free`, `SEGMENTS`. No I/O.
- `refresh/people_template.html` — **NEW.** Self-contained LOCAL-only template (banner + rosters + journey search), `__PEOPLE_JSON__` placeholder.
- `refresh/refresh.py` — **MODIFY.** Add cohort + people queries, `firestore_names`, `write_people_local`, cohort wiring, fail-soft `main`.
- `refresh/template.html` — **MODIFY.** Add a "Retention cohorts" section reading `ds.cohort`.
- `refresh/run.sh` — **MODIFY.** Add `google-cloud-firestore` to the venv pip install.
- `refresh/tests/` — **NEW.** `test_cohort.py`, `test_people.py`, `test_pii_guard.py`.
- `README.txt` — **MODIFY.** Document the PUBLIC/LOCAL split.
- Generated: `cohort.csv` (PUBLIC), `local/people.html` + `local/roster_*.csv` (LOCAL).

---

## PART A — Retention cohorts (PUBLIC)

### Task 1: `pivot_cohort` pure function + test harness

**Files:**
- Create: `refresh/cohort.py`
- Create: `refresh/tests/test_cohort.py`
- (One-time) install pytest into the existing venv.

**Interfaces:**
- Produces: `pivot_cohort(rows: list[dict]) -> dict` where each input row is `{"cohort_week": "YYYY-MM-DD", "weeks_since": "<int-as-str>", "users": "<int-as-str>"}` (bq JSON returns numbers as strings). Returns `{"cohorts": [{"label": str, "size": int, "retention": list[int|None]}], "max_week": int}`. `retention[w]` is the percent of the cohort active in week `w` (W0 == 100), `0` for an observed-but-empty interior week, `None` for a not-yet-observed week beyond the cohort's latest observed week.

- [ ] **Step 1: Install pytest into the venv (one-time)**

Run: `cd ~/StitchPad\ Analytics\ Data/refresh && ./venv/bin/pip install --quiet pytest`

- [ ] **Step 2: Write the failing test**

Create `refresh/tests/test_cohort.py`:

```python
import os, sys
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from cohort import pivot_cohort


def test_pivot_builds_triangle_with_percentages():
    rows = [
        {"cohort_week": "2026-06-29", "weeks_since": "0", "users": "10"},
        {"cohort_week": "2026-06-29", "weeks_since": "1", "users": "5"},
        {"cohort_week": "2026-06-29", "weeks_since": "2", "users": "3"},
        {"cohort_week": "2026-07-06", "weeks_since": "0", "users": "8"},
        {"cohort_week": "2026-07-06", "weeks_since": "1", "users": "2"},
    ]
    out = pivot_cohort(rows)
    assert out["max_week"] == 2
    c0, c1 = out["cohorts"]
    assert c0["label"] == "2026-06-29" and c0["size"] == 10
    assert c0["retention"] == [100, 50, 30]
    assert c1["size"] == 8
    assert c1["retention"] == [100, 25, None]   # week 2 not yet observed


def test_w0_sizes_sum_to_total_signups():
    rows = [
        {"cohort_week": "2026-06-29", "weeks_since": "0", "users": "10"},
        {"cohort_week": "2026-07-06", "weeks_since": "0", "users": "8"},
    ]
    assert sum(c["size"] for c in pivot_cohort(rows)["cohorts"]) == 18


def test_interior_zero_week_is_zero_not_blank():
    rows = [
        {"cohort_week": "2026-06-29", "weeks_since": "0", "users": "10"},
        {"cohort_week": "2026-06-29", "weeks_since": "2", "users": "4"},
    ]
    # week 1 had zero returners but is within the observed range -> 0, not None
    assert pivot_cohort(rows)["cohorts"][0]["retention"] == [100, 0, 40]
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd ~/StitchPad\ Analytics\ Data/refresh && ./venv/bin/python -m pytest tests/test_cohort.py -v`
Expected: FAIL with `ModuleNotFoundError: No module named 'cohort'`.

- [ ] **Step 4: Write minimal implementation**

Create `refresh/cohort.py`:

```python
"""Pure cohort-pivot logic for the retention triangle. No I/O."""
from collections import defaultdict


def pivot_cohort(rows):
    by_cohort = defaultdict(dict)
    max_week = 0
    for r in rows:
        w = int(r["weeks_since"])
        by_cohort[r["cohort_week"]][w] = int(r["users"])
        max_week = max(max_week, w)
    cohorts = []
    for cw in sorted(by_cohort):
        weeks = by_cohort[cw]
        size = weeks.get(0, 0)
        observed = max(weeks) if weeks else 0
        retention = []
        for w in range(max_week + 1):
            if w in weeks:
                retention.append(round(100 * weeks[w] / size) if size else 0)
            elif w <= observed:
                retention.append(0)
            else:
                retention.append(None)
        cohorts.append({"label": cw, "size": size, "retention": retention})
    return {"cohorts": cohorts, "max_week": max_week}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd ~/StitchPad\ Analytics\ Data/refresh && ./venv/bin/python -m pytest tests/test_cohort.py -v`
Expected: 3 passed.

---

### Task 2: Wire cohort into `refresh.py` + template + outputs (PUBLIC)

**Files:**
- Modify: `refresh/refresh.py` (`query_window`, `build_dataset`, `write_outputs`)
- Modify: `refresh/template.html` (new section)

**Interfaces:**
- Consumes: `pivot_cohort` from Task 1; existing `PROD_CTE`/`ALL_CTE`, `q(body)` closure inside `query_window`.
- Produces: `ds["cohort"] = {"cohorts": [...], "max_week": int}` in each dataset; `cohort.csv`; xlsx "Retention" sheet.

- [ ] **Step 1: Add the cohort query in `query_window()`**

In `refresh/refresh.py`, inside `query_window()` (alongside the other `d[...] = q(...)` calls), add:

```python
    d["cohort_raw"] = q(""", signups AS (
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
        UNION ALL
        SELECT user_pseudo_id, cohort_week, 0 AS weeks_since FROM signups)
      SELECT CAST(cohort_week AS STRING) AS cohort_week, weeks_since,
             COUNT(DISTINCT user_pseudo_id) AS users
      FROM rets GROUP BY cohort_week, weeks_since
      ORDER BY cohort_week, weeks_since""")
```

> **Correctness note (fixed during implementation):** W0 must be the true cohort
> size — *every* sign-up in the week — so it is forced in via `UNION ALL SELECT …, 0`.
> Deriving W0 from `session_start` activity alone (an earlier draft) dropped sign-ups
> with no post-signup session and used a too-small denominator, producing >100%
> retention cells and breaking the "W0 sum == total sign-ups" reconciliation.

- [ ] **Step 2: Pivot into the dataset in `build_dataset()`**

At the top of `refresh/refresh.py` add the import:

```python
from cohort import pivot_cohort
```

In `build_dataset()`, before the `ds = {...}` literal, add:

```python
    cohort = pivot_cohort(d["cohort_raw"])
```

Then add two keys inside the `ds = {...}` dict:

```python
        "cohort": cohort,
        "cohortNote": (
            f'<span class="chip warn">data gap</span>&nbsp; Cohorts are keyed on '
            f'<span class="mono">sign_up</span>; SSO sign-ups were not logged before '
            f'v1.1.0 (Android Jul 15), so early-July cohort <b>sizes</b> undercount — '
            f'the return <b>percentages</b> stay valid. Small samples: read direction, not precision.'),
```

- [ ] **Step 3: Add the `cohort.csv` writer + xlsx sheet in `write_outputs()`**

In `write_outputs()`, after the `activity.csv` writer block, add:

```python
    with open(os.path.join(BASE, "cohort.csv"), "w", newline="") as fh:
        w = csv.writer(fh)
        maxw = launch_ds["cohort"]["max_week"]
        w.writerow(["cohort_week", "signups"] + [f"W{k}" for k in range(maxw + 1)])
        for c in launch_ds["cohort"]["cohorts"]:
            w.writerow([c["label"], c["size"]] +
                       ["" if v is None else f"{v}%" for v in c["retention"]])
```

In the xlsx section of `write_outputs()`, add a "Retention" sheet after the existing sheets loop:

```python
    maxw = launch_ds["cohort"]["max_week"]
    cohort_rows = [[c["label"], c["size"]] +
                   ["" if v is None else f"{v}%" for v in c["retention"]]
                   for c in launch_ds["cohort"]["cohorts"]]
    sheet("Retention", [(Lt, "Weekly sign_up cohorts; cells = % of cohort active that week.",
        ["Cohort week", "Sign-ups"] + [f"W{k}" for k in range(maxw + 1)], cohort_rows)])
```

- [ ] **Step 4: Add the "Retention cohorts" section to `template.html`**

In `refresh/template.html`, add a new card section (place it after the activity section). It reads `ds.cohort` from the embedded dataset. Minimal renderer (match existing card/table classes and Indigo `#2C3E7C` shading):

```html
<section class="card">
  <h2>Retention cohorts</h2>
  <p class="sub">Of the tailors who signed up in a given week, the share still active
     (a session) in each following week. Keyed on <span class="mono">sign_up</span>.</p>
  <div id="cohortNote"></div>
  <div style="overflow-x:auto">
    <table class="cohort"><thead><tr id="cohortHead"></tr></thead>
      <tbody id="cohortBody"></tbody></table>
  </div>
</section>
<script>
(function(){
  var ds = window.__DS__ ? window.__DS__() : null; // adapt to how template reads the active dataset
  if(!ds || !ds.cohort) return;
  var c = ds.cohort, head = document.getElementById('cohortHead');
  document.getElementById('cohortNote').innerHTML = ds.cohortNote || '';
  head.innerHTML = '<th>Cohort</th><th>Sign-ups</th>' +
    Array.from({length:c.max_week+1}, (_,k)=>'<th>W'+k+'</th>').join('');
  document.getElementById('cohortBody').innerHTML = c.cohorts.map(function(row){
    var cells = row.retention.map(function(v){
      if(v===null) return '<td class="na"></td>';
      var a = (v/100)*0.85 + 0.06;
      return '<td style="background:rgba(44,62,124,'+a.toFixed(2)+');color:'+(v>55?'#fff':'#14110E')+'">'+v+'%</td>';
    }).join('');
    return '<tr><td>'+row.label+'</td><td>'+row.size+'</td>'+cells+'</tr>';
  }).join('');
})();
</script>
```

Note: `template.html` already selects between the `launch`/`all` datasets on tab switch — hook this renderer into the same tab-switch path the existing sections use (search `template.html` for how `days`/`funnel` re-render on tab change and mirror it). If the existing code re-renders via a single `render(ds)` function, add the cohort block there instead of the standalone IIFE above.

- [ ] **Step 5: Run end-to-end and verify reconciliation**

Run: `cd ~/StitchPad\ Analytics\ Data/refresh && ./venv/bin/python refresh.py`
Then verify:
```bash
cd ~/StitchPad\ Analytics\ Data
cat cohort.csv                       # triangle present, W0 column all 100%
# Reconciliation: sum of the signups column == total distinct sign_up users:
./refresh/venv/bin/python - <<'PY'
import csv, subprocess, json
rows=list(csv.DictReader(open('cohort.csv')))
print("sum W0 sizes:", sum(int(r['signups']) for r in rows))
PY
# Compare against the funnel note's signed_up figure / a direct bq count for the same window.
open dashboard.html                  # "Retention cohorts" card renders on the launch tab
```
Expected: triangle renders; W0 == 100% every row; sizes sum to the window's distinct `sign_up` count; `data gap` chip visible.

- [ ] **Step 6: Confirm no PII and no regressions**

Run:
```bash
cd ~/StitchPad\ Analytics\ Data
grep -c "Retention cohorts" dashboard.html   # 1
git --no-pager diff --stat 2>/dev/null || true   # (folder isn't git; visual check instead)
```
Expected: existing sections (funnel, activity, daily) still render unchanged; only the cohort section is added.

---

## PART B — User-level, LOCAL-only (rosters + journey)

### Task 3: `classify_segments` pure function

**Files:**
- Create: `refresh/people.py`
- Create: `refresh/tests/test_people.py`

**Interfaces:**
- Produces: `SEGMENTS: dict[str, str]` (segment key → human label) and `classify_segments(u: dict, as_of: datetime.date) -> set[str]`. Input `u` is a per-user facts dict with ISO-date string fields (`last_active_day`, `first_open_day`, `signup_day`), string "0"/"1" flags (`setup`, `first_customer`, `first_order`), and `distinct_days_first14` (int-as-str). Segment keys: `active_7d`, `dormant`, `activated`, `qualified`, `passive`. Every user is exactly one of {`active_7d`, `dormant`} and exactly one of {`activated`, `passive`}; `qualified` is independent.

- [ ] **Step 1: Write the failing test**

Create `refresh/tests/test_people.py`:

```python
import os, sys
from datetime import date
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from people import classify_segments, build_people_dataset

AS_OF = date(2026, 8, 4)


def _user(**kw):
    base = {"user_id": "u1", "platform": "ANDROID", "tier": "free",
            "first_open_day": "2026-07-01", "signup_day": "2026-07-01",
            "last_active_day": "2026-08-01", "distinct_days": "5",
            "distinct_days_first14": "4", "setup": "1", "first_customer": "1",
            "first_order": "0", "orders": "0", "payments": "0", "ai_used": "0",
            "referral": "0", "timeline": []}
    base.update(kw)
    return base


def test_active_within_7_days():
    segs = classify_segments(_user(last_active_day="2026-08-01"), AS_OF)
    assert "active_7d" in segs and "dormant" not in segs


def test_dormant_when_quiet_7_plus_days():
    segs = classify_segments(_user(last_active_day="2026-07-20"), AS_OF)
    assert "dormant" in segs and "active_7d" not in segs


def test_activated_requires_setup_and_customer_or_order():
    assert "activated" in classify_segments(_user(setup="1", first_customer="1", first_order="0"), AS_OF)
    assert "activated" not in classify_segments(_user(setup="0", first_customer="1"), AS_OF)


def test_passive_is_signed_in_but_not_activated():
    segs = classify_segments(_user(setup="0", first_customer="0", first_order="0"), AS_OF)
    assert "passive" in segs and "activated" not in segs


def test_qualified_needs_4_distinct_days_in_first_14():
    assert "qualified" in classify_segments(_user(distinct_days_first14="4"), AS_OF)
    assert "qualified" not in classify_segments(_user(distinct_days_first14="3"), AS_OF)
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ~/StitchPad\ Analytics\ Data/refresh && ./venv/bin/python -m pytest tests/test_people.py -v`
Expected: FAIL with `ModuleNotFoundError: No module named 'people'`.

- [ ] **Step 3: Write minimal implementation**

Create `refresh/people.py`:

```python
"""Pure user-level logic: segment classification, roster/journey shaping, and the
PUBLIC-output PII guard. No I/O. LOCAL-only data flows through here."""
from datetime import date

SEGMENTS = {
    "active_7d": "Active — last 7 days",
    "dormant": "Dormant — quiet 7+ days",
    "activated": "Activated — StitchPad bar",
    "qualified": "Qualified — StitchPad bar",
    "passive": "Signed in but passive",
}


def _flag(u, k):
    return str(u.get(k, "0")) == "1"


def classify_segments(u, as_of):
    segs = set()
    la = u.get("last_active_day")
    last = date.fromisoformat(la) if la else None
    segs.add("active_7d" if (last and (as_of - last).days <= 6) else "dormant")
    if _flag(u, "setup") and (_flag(u, "first_customer") or _flag(u, "first_order")):
        segs.add("activated")
    else:
        segs.add("passive")
    if int(u.get("distinct_days_first14") or 0) >= 4:
        segs.add("qualified")
    return segs
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd ~/StitchPad\ Analytics\ Data/refresh && ./venv/bin/python -m pytest tests/test_people.py -v`
Expected: `test_active_within_7_days`, `test_dormant_when_quiet_7_plus_days`, `test_activated_...`, `test_passive_...`, `test_qualified_...` all pass. (`build_people_dataset` tests come in Task 4 and will still error at import — that's fine; run only the named tests here with `-k "classify or active or dormant or activated or passive or qualified"` if you want green now.)

---

### Task 4: `build_people_dataset` — rosters, journeys, name merge

**Files:**
- Modify: `refresh/people.py`
- Modify: `refresh/tests/test_people.py` (add cases)

**Interfaces:**
- Consumes: `classify_segments`, `SEGMENTS` from Task 3.
- Produces: `build_people_dataset(users: list[dict], names: dict[str, dict], as_of: date) -> dict` returning `{"users": [rec...], "rosters": {seg_key: [summary...]}, "counts": {seg_key: int}}`. `names` maps `user_id -> {"name": str, "phone": str}`; a missing/blank name resolves to `"(name unavailable)"`. Each `rec` carries `user_id, name, phone, tier, platform, signup_day, last_active_day, distinct_days, orders, payments, segments (sorted list), timeline`.

- [ ] **Step 1: Add the failing test**

Append to `refresh/tests/test_people.py`:

```python
def test_build_dataset_merges_names_and_builds_rosters():
    users = [_user(user_id="u1", setup="0", first_customer="0", first_order="0"),  # passive
             _user(user_id="u2", setup="1", first_customer="1")]                    # activated
    names = {"u1": {"name": "Amaka Atelier", "phone": "+2348000000001"}}
    ds = build_people_dataset(users, names, AS_OF)
    u1 = next(u for u in ds["users"] if u["user_id"] == "u1")
    u2 = next(u for u in ds["users"] if u["user_id"] == "u2")
    assert u1["name"] == "Amaka Atelier"
    assert u2["name"] == "(name unavailable)"        # no Firestore doc
    assert [r["user_id"] for r in ds["rosters"]["passive"]] == ["u1"]
    assert [r["user_id"] for r in ds["rosters"]["activated"]] == ["u2"]
    assert ds["counts"]["passive"] == 1 and ds["counts"]["activated"] == 1
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ~/StitchPad\ Analytics\ Data/refresh && ./venv/bin/python -m pytest tests/test_people.py::test_build_dataset_merges_names_and_builds_rosters -v`
Expected: FAIL with `AttributeError`/`ImportError` (no `build_people_dataset`).

- [ ] **Step 3: Write minimal implementation**

Append to `refresh/people.py`:

```python
def build_people_dataset(users, names, as_of):
    recs = []
    rosters = {k: [] for k in SEGMENTS}
    for u in users:
        uid = u["user_id"]
        segs = classify_segments(u, as_of)
        info = names.get(uid) or {}
        name = info.get("name") or "(name unavailable)"
        phone = info.get("phone") or ""
        rec = {
            "user_id": uid, "name": name, "phone": phone,
            "tier": u.get("tier") or "free", "platform": u.get("platform") or "",
            "signup_day": u.get("signup_day") or "",
            "last_active_day": u.get("last_active_day") or "",
            "distinct_days": int(u.get("distinct_days") or 0),
            "orders": int(u.get("orders") or 0),
            "payments": int(u.get("payments") or 0),
            "segments": sorted(segs),
            "timeline": u.get("timeline") or [],
        }
        recs.append(rec)
        summary = {"user_id": uid, "name": name, "phone": phone,
                   "tier": rec["tier"], "last_active_day": rec["last_active_day"],
                   "distinct_days": rec["distinct_days"], "orders": rec["orders"]}
        for s in segs:
            rosters[s].append(summary)
    for s in rosters:
        rosters[s].sort(key=lambda r: (-r["distinct_days"], r["name"]))
    return {"users": recs, "rosters": rosters,
            "counts": {k: len(v) for k, v in rosters.items()}}
```

- [ ] **Step 4: Run the full people test file to verify it passes**

Run: `cd ~/StitchPad\ Analytics\ Data/refresh && ./venv/bin/python -m pytest tests/test_people.py -v`
Expected: all tests pass.

---

### Task 5: `assert_public_pii_free` guard

**Files:**
- Modify: `refresh/people.py`
- Create: `refresh/tests/test_pii_guard.py`

**Interfaces:**
- Produces: `assert_public_pii_free(text: str, names: dict[str, dict]) -> None` — raises `AssertionError` if any non-blank `name` or `phone` from `names` appears as a substring of `text`. Fail-closed.

- [ ] **Step 1: Write the failing test**

Create `refresh/tests/test_pii_guard.py`:

```python
import os, sys
import pytest
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from people import assert_public_pii_free

NAMES = {"u1": {"name": "Amaka Atelier", "phone": "+2348000000001"}}


def test_raises_when_name_present():
    with pytest.raises(AssertionError):
        assert_public_pii_free("...Amaka Atelier signed up...", NAMES)


def test_raises_when_phone_present():
    with pytest.raises(AssertionError):
        assert_public_pii_free("contact +2348000000001 now", NAMES)


def test_passes_when_clean():
    assert_public_pii_free("Signed in: 109 devices; 24 created an order", NAMES)
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ~/StitchPad\ Analytics\ Data/refresh && ./venv/bin/python -m pytest tests/test_pii_guard.py -v`
Expected: FAIL (`assert_public_pii_free` undefined).

- [ ] **Step 3: Write minimal implementation**

Append to `refresh/people.py`:

```python
def assert_public_pii_free(text, names):
    for uid, info in (names or {}).items():
        for key in ("name", "phone"):
            v = (info or {}).get(key) or ""
            v = v.strip()
            if v and v in text:
                raise AssertionError(f"PII leak: {key} for {uid} appears in a PUBLIC output")
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd ~/StitchPad\ Analytics\ Data/refresh && ./venv/bin/python -m pytest tests/test_pii_guard.py -v`
Expected: 3 passed.

---

### Task 6: `refresh.py` Part-B integration (query, Firestore, render, fail-soft)

**Files:**
- Modify: `refresh/refresh.py`
- Create: `refresh/people_template.html`
- Modify: `refresh/run.sh`

**Interfaces:**
- Consumes: `build_people_dataset`, `assert_public_pii_free` (Task 4/5); `pivot_cohort` already imported.
- Produces: `firestore_names(uids) -> dict[str,dict]`; `write_people_local(people_ds) -> None`; `d["people"]` in the launch window; `local/people.html` + `local/roster_<seg>.csv`.

- [ ] **Step 1: Add the per-user `people` query in `query_window()`**

In `refresh/refresh.py`, inside `query_window()`, add (day fields are cast to DATE so bq JSON emits ISO `YYYY-MM-DD`, which `classify_segments` parses):

```python
    d["people"] = q(""", ev AS (
        SELECT user_id, event_name, event_timestamp, event_date, platform,
          (SELECT value.string_value FROM UNNEST(user_properties)
             WHERE key='subscription_tier') tier
        FROM src WHERE user_id IS NOT NULL),
      per AS (
        SELECT user_id, ANY_VALUE(platform) platform, ANY_VALUE(tier) tier,
          MIN(IF(event_name='first_open', PARSE_DATE('%Y%m%d', event_date), NULL)) first_open_day,
          MIN(IF(event_name='sign_up',    PARSE_DATE('%Y%m%d', event_date), NULL)) signup_day,
          MAX(PARSE_DATE('%Y%m%d', event_date)) last_active_day,
          COUNT(DISTINCT event_date) distinct_days,
          MAX(IF(event_name='workshop_setup_completed',1,0)) setup,
          MAX(IF(event_name='customer_created',1,0)) first_customer,
          MAX(IF(event_name='order_created',1,0)) first_order,
          COUNTIF(event_name='order_created') orders,
          COUNTIF(event_name='payment_recorded') payments,
          MAX(IF(event_name='ai_feature_used',1,0)) ai_used,
          MAX(IF(event_name='referral_code_applied',1,0)) referral
        FROM ev GROUP BY user_id),
      d14 AS (
        SELECT e.user_id, COUNT(DISTINCT e.event_date) distinct_days_first14
        FROM ev e JOIN per p USING (user_id)
        WHERE p.first_open_day IS NOT NULL
          AND PARSE_DATE('%Y%m%d', e.event_date)
              BETWEEN p.first_open_day AND DATE_ADD(p.first_open_day, INTERVAL 13 DAY)
        GROUP BY e.user_id),
      tl AS (
        SELECT user_id, ARRAY_AGG(STRUCT(event_name AS event,
                 FORMAT_TIMESTAMP('%Y-%m-%d %H:%M',
                   TIMESTAMP_MICROS(event_timestamp), 'Africa/Lagos') AS ts)
                 ORDER BY event_timestamp LIMIT 500) timeline
        FROM ev
        WHERE event_name IN ('first_open','sign_up','login','workshop_setup_completed',
          'customer_created','measurement_added','order_created','order_status_advanced',
          'payment_recorded','receipt_sent','whatsapp_message_sent','ai_feature_used',
          'measurement_shared','referral_code_applied','upgrade_completed','celebration_shown')
        GROUP BY user_id)
      SELECT p.*, COALESCE(d14.distinct_days_first14, 0) distinct_days_first14,
             tl.timeline
      FROM per p LEFT JOIN d14 USING (user_id) LEFT JOIN tl USING (user_id)""")
```

Note: `distinct_days_first14` returns as int in bq JSON; `classify_segments`/`build_people_dataset` coerce with `int(... or 0)`, so string/int both work.

- [ ] **Step 2: Add `firestore_names()` (Option A, ADC, fail-soft)**

Add near the top-level helpers in `refresh/refresh.py`:

```python
def firestore_names(uids):
    """uid -> {name, phone} from Firestore users/{uid}. Reads ONLY businessName +
    phone. Fail-soft: any auth/import/read error returns {} (names unavailable)."""
    if not uids:
        return {}
    try:
        from google.cloud import firestore
        db = firestore.Client(project=PROJECT)
    except Exception as e:
        print(f"WARN Firestore unavailable ({e}) — people view will be pseudonymous")
        return {}
    out = {}
    for uid in uids:
        try:
            doc = db.collection("users").document(uid).get()
            if doc.exists:
                data = doc.to_dict() or {}
                out[uid] = {"name": (data.get("businessName") or "").strip(),
                            "phone": (data.get("phone") or "").strip()}
        except Exception:
            continue
    return out
```

- [ ] **Step 3: Create `refresh/people_template.html` (LOCAL-only)**

Create `refresh/people_template.html`. Reuse the `<head>`/`<style>` block from `template.html` (copy it) for brand consistency, then this body. The `__PEOPLE_JSON__` placeholder is replaced at render time:

```html
<body>
<div style="background:#B85A30;color:#fff;padding:14px 18px;font-weight:700;font-size:15px">
  LOCAL ONLY — contains tailor names &amp; phone numbers. Never publish, never share,
  never upload to the claude.ai artifact. This file stays on this Mac.
</div>
<main class="wrap">
  <h1>StitchPad — People (local)</h1>
  <p class="sub" id="stamp"></p>

  <section class="card"><h2>Segment rosters</h2>
    <div id="rosters"></div></section>

  <section class="card"><h2>Journey lookup</h2>
    <input id="q" placeholder="Search name or phone…" style="width:100%;padding:10px">
    <div id="results"></div>
    <div id="journey"></div></section>
</main>
<script>
const P = __PEOPLE_JSON__;
const SEG = P.segmentLabels;
document.getElementById('stamp').textContent = 'Generated ' + P.stamp + ' · ' + P.users.length + ' signed-in tailors';

// Rosters
document.getElementById('rosters').innerHTML = Object.keys(SEG).map(function(k){
  const rows = (P.rosters[k]||[]);
  return '<h3>'+SEG[k]+' — '+rows.length+'</h3><table><thead><tr>'
    +'<th>Name</th><th>Phone</th><th>Tier</th><th>Last active</th><th>Days</th><th>Orders</th></tr></thead><tbody>'
    + rows.map(r=>'<tr><td>'+r.name+'</td><td>'+r.phone+'</td><td>'+r.tier+'</td><td>'+r.last_active_day+'</td><td>'+r.distinct_days+'</td><td>'+r.orders+'</td></tr>').join('')
    + '</tbody></table>';
}).join('');

// Journey search
const byId = {}; P.users.forEach(u=>byId[u.user_id]=u);
function draw(u){
  document.getElementById('journey').innerHTML =
    '<h3>'+u.name+' <small>'+u.phone+'</small></h3>'
    +'<p>'+u.tier+' · '+u.platform+' · signed up '+u.signup_day+' · '+u.orders+' orders · '+u.payments+' payments · segments: '+u.segments.join(', ')+'</p>'
    +'<table><thead><tr><th>When</th><th>Event</th></tr></thead><tbody>'
    + (u.timeline||[]).map(e=>'<tr><td>'+e.ts+'</td><td>'+e.event+'</td></tr>').join('')
    + '</tbody></table>';
}
document.getElementById('q').addEventListener('input', function(e){
  const term = e.target.value.toLowerCase();
  const hits = P.users.filter(u => (u.name+' '+u.phone).toLowerCase().includes(term)).slice(0,25);
  document.getElementById('results').innerHTML = hits.map(u=>'<button data-id="'+u.user_id+'">'+u.name+' · '+u.phone+'</button>').join(' ');
  document.querySelectorAll('#results button').forEach(b=>b.onclick=()=>draw(byId[b.dataset.id]));
});
</script>
</body>
```

- [ ] **Step 4: Add `write_people_local()`**

Add to `refresh/refresh.py`:

```python
def write_people_local(people_ds):
    from people import SEGMENTS
    local_dir = os.path.join(BASE, "local")
    os.makedirs(local_dir, exist_ok=True)
    payload = {
        "stamp": TODAY.strftime("%b %-d, %Y"),
        "segmentLabels": SEGMENTS,
        "users": people_ds["users"],
        "rosters": people_ds["rosters"],
    }
    template = open(os.path.join(REFRESH_DIR, "people_template.html")).read()
    html = template.replace("__PEOPLE_JSON__", json.dumps(payload, ensure_ascii=False))
    open(os.path.join(local_dir, "people.html"), "w").write(html)
    for seg, rows in people_ds["rosters"].items():
        with open(os.path.join(local_dir, f"roster_{seg}.csv"), "w", newline="") as fh:
            w = csv.writer(fh)
            w.writerow(["name", "phone", "tier", "last_active_day", "distinct_days", "orders"])
            for r in rows:
                w.writerow([r["name"], r["phone"], r["tier"],
                            r["last_active_day"], r["distinct_days"], r["orders"]])
```

- [ ] **Step 5: Wire the fail-soft Part-B block into `main()`**

Replace `main()` in `refresh/refresh.py` with:

```python
def main():
    launch_raw = query_window(LAUNCH_START, production=True)
    all_raw = query_window(ALL_START, production=False)
    launch_ds = build_dataset(launch_raw, "launch")
    all_ds = build_dataset(all_raw, "all")
    write_outputs(launch_raw, all_raw, launch_ds, all_ds)   # PUBLIC — unconditional
    print(f"refreshed {TODAY}: dashboard.html + CSVs + xlsx in {BASE}")

    # PART B — LOCAL-only, fail-soft: never breaks the public refresh above.
    try:
        from people import build_people_dataset, assert_public_pii_free
        users = launch_raw["people"]
        names = firestore_names([u["user_id"] for u in users])

        # Guard: prove no name/phone leaked into any PUBLIC output before we go on.
        public_text = open(os.path.join(BASE, "dashboard.html")).read()
        for c in ("funnel", "activity", "cohort", "daily", "platform", "geo",
                  "brands", "features", "screens", "acquisition", "versions"):
            p = os.path.join(BASE, f"{c}.csv")
            if os.path.exists(p):
                public_text += open(p).read()
        assert_public_pii_free(public_text, names)

        people_ds = build_people_dataset(users, names, TODAY)
        write_people_local(people_ds)
        named = sum(1 for u in people_ds["users"] if u["name"] != "(name unavailable)")
        print(f"local/people.html: {len(people_ds['users'])} signed-in tailors, "
              f"{named} name-resolved")
    except Exception as e:
        print(f"WARN Part B (people/local) failed: {e} — public outputs are still fresh")
```

- [ ] **Step 6: Add the Firestore dependency to `run.sh`**

In `refresh/run.sh`, change the venv bootstrap pip line to:

```bash
  ./venv/bin/pip install --quiet openpyxl google-cloud-firestore
```

And (one-time, for the already-existing venv) run:
`cd ~/StitchPad\ Analytics\ Data/refresh && ./venv/bin/pip install --quiet google-cloud-firestore`

- [ ] **Step 7: Run end-to-end and verify**

Run: `cd ~/StitchPad\ Analytics\ Data/refresh && ./venv/bin/python refresh.py`
Then verify:
```bash
cd ~/StitchPad\ Analytics\ Data
ls local/                             # people.html + roster_*.csv present
open local/people.html                # orange LOCAL-ONLY banner; rosters populate; search works; clicking a tailor shows a timeline
head -3 local/roster_passive.csv      # named rows with phone/tier
# Reconcile roster counts against the aggregate activity segments:
grep -E "Signed in but passive|Activated|Qualified" activity.csv
wc -l local/roster_passive.csv local/roster_activated.csv local/roster_qualified.csv
```
Expected: roster row-counts ≈ matching `activity.csv` segment device counts (small differences from `user_id` vs `user_pseudo_id` grain are expected and documented). If ADC is missing, `people.html` still renders with "(name unavailable)" everywhere and the run prints the WARN — public outputs unaffected.

- [ ] **Step 8: Verify the PUBLIC surface is still name-free**

Run:
```bash
cd ~/StitchPad\ Analytics\ Data
# The guard already asserted this in-process; double-check no known name leaked:
./refresh/venv/bin/python - <<'PY'
import os, glob
# spot check: no local roster name should appear in dashboard.html
pub = open("dashboard.html").read()
import csv as _c
leaks = []
for f in glob.glob("local/roster_*.csv"):
    for row in _c.DictReader(open(f)):
        if row["name"] and row["name"] != "(name unavailable)" and row["name"] in pub:
            leaks.append(row["name"])
print("leaks:", leaks)   # expect []
PY
```
Expected: `leaks: []`.

---

### Task 7: README + containment documentation

**Files:**
- Modify: `README.txt`

- [ ] **Step 1: Document the PUBLIC/LOCAL split**

Add to `~/StitchPad Analytics Data/README.txt`:

```
PUBLIC vs LOCAL
- dashboard.html + *.csv + .xlsx are AGGREGATE and PII-FREE — safe to publish
  (this is what goes to the claude.ai artifact).
- local/ (people.html, roster_*.csv) contains TAILOR NAMES + PHONES. It is
  LOCAL-ONLY: never publish, never share, never upload. Regenerated each daily
  refresh alongside the public outputs. Names come from a read-only Firestore
  lookup (businessName + phone) via gcloud ADC; if ADC is absent the view falls
  back to pseudonymous ids and the public refresh is unaffected.
- Retention cohorts (public) are keyed on sign_up; early-July cohort sizes
  undercount due to the pre-1.1.0 SSO sign_up gap.
```

- [ ] **Step 2: Final full run + full test suite**

Run:
```bash
cd ~/StitchPad\ Analytics\ Data/refresh
./venv/bin/python -m pytest tests/ -v      # all green
./venv/bin/python refresh.py               # clean end-to-end, both public + local
```
Expected: all tests pass; `dashboard.html` has the cohort section; `local/people.html` has rosters + working journey search; no names in any public file.

---

## Self-Review

**Spec coverage:**
- Part A retention cohorts (public, sign_up basis, data-gap caveat) → Tasks 1–2. ✓
- Part B rosters (form a) → Tasks 3–4, 6 (`write_people_local` roster CSVs + rosters section). ✓
- Part B journey (form b) → Task 6 (`people_template.html` journey search + `timeline` in `people` query). ✓
- Firestore name join, Option A, ADC, only businessName+phone → Task 6 Step 2. ✓
- Local-only containment + banner + README → Tasks 6–7. ✓
- Fail-soft (public never breaks) → Task 6 Step 5 (`main` guarded block). ✓
- PII guard / reconciliation → Task 5 + Task 6 Steps 7–8. ✓
- Build order A-then-B → Part A (Tasks 1–2) precedes Part B (Tasks 3–7). ✓

**Type consistency:** `pivot_cohort` returns `{cohorts, max_week}` — consumed identically in Task 2 (`ds["cohort"]`) and the template. `classify_segments(u, as_of)` / `build_people_dataset(users, names, as_of)` signatures match between Tasks 3/4 and their call in Task 6 (`build_people_dataset(users, names, TODAY)`). `SEGMENTS` keys (`active_7d, dormant, activated, qualified, passive`) are identical across `people.py`, `write_people_local` (roster file names), and `people_template.html` (`P.segmentLabels`). `firestore_names` returns `{uid: {name, phone}}`, exactly what `build_people_dataset` and `assert_public_pii_free` expect.

**Placeholder scan:** No TBD/TODO; every code step carries real code. The one intentionally descriptive step is Task 2 Step 4's note to hook the cohort renderer into `template.html`'s existing tab-switch path — unavoidable without pasting the whole existing template, and it points to the exact mechanism to mirror.

## Notes / open verification points for the implementer

- **`template.html` tab-switch:** confirm how the existing page swaps `launch`/`all` datasets and render the cohort block through the same path (Task 2 Step 4).
- **ADC presence:** first Part-B run needs `gcloud auth application-default login` to have been done for `stitchpad-30607`; otherwise names are unavailable (fail-soft, not fatal).
- **Firestore field name:** assumes `users/{uid}.businessName` + `.phone` (per brand-onboarding + user_phone_vs_whatsapp). Verify against a real doc on first run; adjust the field names in `firestore_names` if the schema differs.

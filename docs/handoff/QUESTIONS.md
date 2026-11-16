# Questions for Daniel

## Customer acquisition / StitchPad Connect research — 4 November 2026

Research recommendations only; no feature implementation or rollout is approved.

- **Q9 — Initial buyer and fulfilment segment:** Should the first concierge pilot serve
  diaspora visitors ordering ahead for fitting/pickup in Nigeria, domestic Nigerian buyers,
  or customers requiring international delivery? Recommendation: test domestic and diaspora
  visitor requests in one Nigerian supply cluster first, and validate one export route separately.
- **Q10 — Product and operating scope:** Should StitchPad initially provide introductions
  and structured quotes, or operate managed transactions with support and remedies?
  Recommendation: manually supported matching with explicit responsibility boundaries before
  a public transactional marketplace. Payment splits are not escrow; Paystack's published
  unsupported categories include escrow services. Confirm any proposed funds flow with the
  provider and appropriate advisers before promising payment protection.
- **Q11 — Pilot commitment:** What budget and named support capacity can be allocated to
  designer vetting, buyer acquisition and exception handling? Proposed research target:
  10–15 designers, 30 qualified requests, and at least 10 completed paid orders, with
  separately reported acquisition channels and fulfilment routes. These are proposed
  learning targets, not demand forecasts or approved spending.
- **Q12 — Initial garment scope:** Which garment categories should be included based on
  the specialties and spare capacity of willing existing StitchPad designers? Recommendation:
  repeatable styles with clear specifications; defer complex bridal and large group orders.


## Africa expansion — 2 November 2026

### Q1. Mixed-currency dashboard and report totals — decided

Daniel approved **separate totals per currency** in chat on 2 November 2026.
Keep NGN, GHS and other currencies separate throughout aggregation and display; no
currency conversion or mandatory currency filter is authorized. Single-currency accounts
continue to show a single total. Currency implementation can proceed on that basis.

### Q2. Existing launch-free grants without an expiry — decided

Daniel approved ending existing indefinite launch-free grants **30 days after deployment,
with advance notice**, in chat on 2 November 2026. This refers to deployment of the complete
trial/read-only transition, not deployment of the country-foundation PR or a preparatory helper.

- Persist a rollout deployment timestamp and a fixed cutoff 30 days later. Rerunning the
  migration must not reset or extend that cutoff.
- Give affected users advance notice before their grant expires.
- After the cutoff, unpaid accounts retain read-only access to their existing records.
- Preserve active paid subscriptions and unrelated entitlements.
- No production migration or deployment has been performed; this is the approved policy.
- Older FREE accounts without a launch-free grant and Pro-purchase trial conversion remain
  separate questions under Q5.

### Q3. Staff notification time zone — future server-stage decision

Should staff notifications arrive at the workshop's local time or each staff member's
own local time? Server agent is documenting the implications; schedule wiring stays unchanged.
Not yet asked in chat to avoid interrupting the current smoke test with lower-priority choices.

### Q4. English while full French support is pending — decided

Daniel approved temporarily using English before full French support is ready, in chat on
2 November 2026. English fallback is allowed for untranslated engagement campaigns and other
not-yet-localized surfaces during this interim phase. Continue the planned full French app,
server-template, AI-draft and store-listing work; do not present English fallback as completed
French support. This language decision alone does not authorize opening new store territories
or bypassing the remaining billing, currency, QA and launch prerequisites.

### Q5. Trial conversion and older free accounts — decided

Daniel approved a 30-day notice period for older FREE accounts without grant/expiry
metadata on 4 November 2026. The period starts after **both the App Store and Google
Play approve the updated app**. Users receive notice explaining the change and the
need to update; unpaid accounts become read-only after the notice period. Existing
paid subscriptions remain protected. Store closure or removal was a verbal mistake
and is not requested.

Rollout implementation must persist a fixed notice start/cutoff, ensure the approved
update is available and notices can be delivered, and never silently restart the
30-day clock. Store approval alone does not authorize an agent deployment or migration.
Q2's existing launch-free grant policy remains recorded separately; reconcile the
coordinated rollout timing before activation rather than introducing separate clocks.

Daniel approved **switching to Pro immediately** when Pro is purchased during an
Atelier trial, in chat on 4 November 2026. Apply the verified paid Pro entitlement
immediately rather than retaining Atelier trial access until its original end.
The remaining trial must not override the purchase or be restored by a delayed
signup/expiry event. This decision concerns access precedence; store billing dates
remain governed by the verified purchase. No production activation has occurred.

### Q6. VIP spending threshold outside Nigeria — future currency-stage decision

The existing monetary VIP threshold is NGN 500,000 in lifetime spend. There is no approved
GHS/KES/CFA/GNF/CDF equivalent. Should each market have its own spending threshold, or should
foreign-currency accounts qualify through the existing repeat-order count rule until thresholds
are defined? Do not compare another currency's nominal amount to the Nigerian threshold or
invent exchange rates. Preserve existing Nigerian behavior; foreign monetary qualification
remains pending. Not yet asked in chat.

### Q7. Weekly goal after a country change — pending


Safety behavior implemented: existing weekly goals keep their stamped currency (legacy goals are
NGN), and progress only counts orders in that same currency. New goals use the hydrated account
country. The existing editor has no currency reset/change control, so moving countries does not
silently turn an NGN target into GHS. Product direction is needed for an explicit option to replace
an existing goal in the new account currency; no automatic conversion or target reset is planned.

## Q8 — duplicating an order across currencies (pending Daniel)

A duplicated order is newly created in the workshop's current currency. Copying an old order's
prices after a country/currency change would silently relabel money. Choose either copying details
but clearing money fields with notice (recommended), or explicitly blocking cross-currency duplication.
Until answered, the implementation blocks the cross-currency save with a clear message; same-currency
duplication is unchanged. No elapsed waiting time counts as approval.

## Q9 — customer acquisition and diaspora commerce research (4 November 2026)

Research discussion only; no marketplace implementation, launch, pricing, payment custody,
partner outreach, or change to the expansion build order is approved.

Decisions for Daniel after reviewing the research:

- Initial buyer journey: Nigeria-based customers, diaspora visitors collecting in Nigeria,
  or diaspora customers receiving international shipments? Recommendation: test the first
  two together in one supply city; validate one export corridor separately.
- Initial garment category and city: which have enough willing, dependable StitchPad
  workshops? Lagos and repeatable native outfits are research hypotheses, not decisions.
- Service scope: introductions and structured quotes, or managed orders with human
  support? Recommendation: manually supported matching before broad marketplace work.
- Who can own buyer recruitment, workshop vetting, follow-up and complaints, and what
  time/budget can the pilot use? Engineering alone cannot validate customer acquisition.
- Monetization and responsibility: what fee model and remedies should be tested, and
  who contracts with the buyer? Payment-provider acceptance, custody arrangements and
  destination obligations require validation before promises of escrow/protection.

Public profiles, portfolio publication and any use of existing customer records require
explicit opt-in and appropriate permissions; historical private app records are not
automatically public reviews or verified marketplace transactions.

## Remote body measurements — 4 November 2026

Daniel approved both journeys from the first release: a tailor requests measurements
from an existing customer, and a new customer finds/connects with a designer and
shares measurements. Accuracy takes priority over one-photo convenience; guided
multi-view photos/video may be evaluated. Explore a low-cost engine StitchPad owns;
expensive API dependence is undesirable. This is not approval of any paid provider,
budget, specific model, accuracy claim, training data reuse or marketplace payments.

Research and the offline validation harness are in draft PR381. No actual body
measurement accuracy is established by the harness's synthetic tests.

Pending questions presented through the async UI:

- Initial garments, essential dimensions and acceptable error per field/garment;
  availability of tailors/customers for blinded tape and garment-fit validation.
- Numeric pilot/monthly/per-usable-scan spending ceiling. Owned engine preferred,
  but its compute, data and maintenance still need measured economics.
- Adults-only pilot and customer-controlled measurement sharing/raw-photo deletion.
- French policy: device language (existing expansion plan), explicit language setting,
  or a country default with an override. Persisted locale alone does not translate UI.

Preserve unanswered choices; no preselected option counts as approval. Research
worktree `/private/tmp/stitchpad-remote-measurement-research` is separate from trial
implementation and the approved UI follow-up.

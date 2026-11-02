# Questions for Daniel

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

### Q5. Trial conversion and older free accounts — future trial-stage decisions

Trial agent found two additional transition cases for the PR3 design:

- How should older FREE accounts without grant/expiry metadata transition?
- When someone buys Pro during an Atelier trial, should purchased Pro apply immediately,
  or should they retain Atelier access for the remaining trial while billing begins on
  the store's schedule?

Details are in the trial worktree's PR3 plan. No production changes until resolved; these
have not yet been asked in chat, keeping current clarification focused on Q1 and Q2.

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

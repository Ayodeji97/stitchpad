# Parallel Africa expansion work — 2 November 2026

Daniel authorized parallel agents and branching from the current country-foundation work
while he continues smoke testing PR #374. No push, deployment or merge is part of this setup.

## Isolation and baseline

The smoke-test checkout stays on `feat/africa-expansion` with its local UI improvements.
Its HEAD was a1d1ae33, behind PR #374 head 2fa28d5c. An isolated checkpoint 503b2c1f captures
those local UI improvements without changing that checkout's index or branch. Integration
baseline `codex/africa-next-base` at bb90de1a merges the latest PR #374 changes into that checkpoint.
All worker branches must incorporate that integration baseline.

Worktrees are siblings at `../StitchPad-agent-worktrees/`:

| Worker | Branch | Worktree | Scope |
| --- | --- | --- | --- |
| Currency | codex/africa-pr2-currency | currency | Implement formatter, reusable amount input, historical-order currency; plan remaining migration |
| Trial | codex/africa-pr3-trial-plan | trial | Audit full client/server enforcement; implementation plan and safe inert policy slice |
| Server localization | codex/africa-server-localization-plan | server-localization | Time-zone utility/tests and staged schedules/templates/console dependency plan |

Workers use separate worktrees and local commits. They do not modify the smoke-test checkout.
Full Gradle runs need coordination to avoid competing builds. Review remains separate from implementation.

## Known dependencies

- Mixed-currency totals and indefinite legacy grants need Daniel's answers in QUESTIONS.md.
- Staff cannot read their owner's user document under current Firestore rules. Workshop
  market resolution needs a safe projection/callable; do not expose owner profile fields.
- The phone UI improvements are local and must be included in the reviewed PR before Daniel
  merges the version he is testing.
- Nothing has been pushed or deployed by this parallel-work setup.

## First checkpoints

- Currency: f2cc1f86, shared formatter/amount input and historical-order receipts/reminders;
  64 targeted tests and detekt pass. Agent continues nonblocked historical-order UI migration.
- Trial: 93d722bd, dormant signup-anchored policy and staged full enforcement plan;
  4 Jest tests and TypeScript check pass. No production access behavior changed.
- Server localization: c6c98160, additive local-calendar utility and staged readiness plan;
  30 targeted Jest tests, TypeScript and ESLint pass. No handlers/schedules changed.
- Trial worker performed a read-only review of the subsequent picker UX fix and found no
  concrete blocker; native iOS caret/keyboard testing remains owner verification.

## Decisions received

Daniel approved separate totals per currency and ending indefinite launch-free grants.
Currency aggregation work can resume. Legacy-grant cutoff/notice timing is still pending;
keep production access unchanged until specified.

Daniel confirmed legacy launch-free expiry **30 days after deployment with advance notice**.
The trial worker can implement the rollout policy using a fixed persisted cutoff; no production
activation is authorized by this planning/implementation update.

Daniel approved temporary English use while full French support is pending, including
untranslated engagement campaigns. Full French remains planned; territory launch decisions
and other readiness requirements remain separate.

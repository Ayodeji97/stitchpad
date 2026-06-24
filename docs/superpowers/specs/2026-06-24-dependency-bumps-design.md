# Dependency Bumps — Risk-Tiered Cleanup

**Date:** 2026-06-24
**Branch model:** worktree `feat/dep-bumps` (under `.claude/worktrees/dep-bumps`), one batch branch + PR per tier off `main`.
**Goal:** Clear the 6 open Dependabot PRs by applying them in risk-tiered batches, each gated by a full no-crash/no-regression test pass, then merge to `main`.

## Scope

Six open Dependabot PRs against `main`:

| PR | Bump | Tier |
|----|------|------|
| #208 | `actions/checkout` 6 → 7 | 1 — Low (CI only) |
| #209 | gradle-wrapper 9.5.1 → 9.6.0 | 1 — Low (stay <10.x per Gradle-10 backlog) |
| #137 | composeMultiplatform 1.11.0 → 1.11.1 | 2 — Medium |
| #170 | coil 3.4.0 → 3.5.0 | 2 — Medium (re-check AsyncImage/LoadingDots) |
| #138 | kotlin 2.3.21 → 2.4.0 | 3 — High (drags Compose compiler; `composeCompiler` refs `kotlin`) |
| #210 | compose material3 1.11.0-alpha07 → 1.12.0-alpha02 | 4 — High (alpha→alpha API churn) |

Out of scope: non-dependency open PRs (#134, #56, #41). Gradle 10.x (explicitly deferred). Any feature work.

## Approach

Apply bumps by editing `gradle/libs.versions.toml` directly per batch rather than merging the 6 one-bump Dependabot branches — manual edits batch cleanly and Dependabot auto-closes its PRs once the versions land on `main`. One PR per batch keeps a crash bisectable to a single tier.

### Batches (sequential — each merges before the next starts)

1. **Batch 1 — Low:** #208 + #209. CI/tooling only.
2. **Batch 2 — Medium:** #137 + #170. Re-verify image-loading paths.
3. **Batch 3 — Kotlin 2.4.0:** #138, solo. iOS Xcode build mandatory.
4. **Batch 4 — material3 alpha:** #210, solo. Walk M3 component surface.

## Per-batch merge gate

**Automated (run from the worktree):**
- `./gradlew detekt`
- `./gradlew :composeApp:testDebugUnitTest` (+ `allTests` where it adds coverage)
- `./gradlew :composeApp:assembleDebug`
- iOS framework link (`linkPodDebugFramework*` / `:composeApp:compileKotlinIos*`)
- crash-check `scanner.sh` on the batch diff
- `./gradlew :composeApp:assembleRelease` (R8 — watch for missing keep rules)

> Capture gradle exit codes directly — piping to `tail` reports the wrapper exit, not gradle's (known footgun).

**Manual (owner: Daniel — cannot be run from the agent host):**
- Clean iOS Xcode build + run of `iosApp`.
- Release-AAB install smoke: photo-upload path + offline path.

A batch merges only after both halves are green and Daniel confirms no crash/regression. Each PR body carries these smoke steps.

## Risks / watch-items
- **iOS link breaks that pass Android + build-ios CI** — Kotlin/Compose bumps have historically compiled on Android and JVM yet broken the iOS framework link or Swift target. The clean Xcode build is the real gate for Batches 3–4.
- **R8 release regressions** — new dep versions may need proguard keep rules; the offline-upload path is the historically untested surface.
- **material3 alpha churn** — alpha→alpha minor can move/rename component APIs; expect possible source edits, not just a version bump.

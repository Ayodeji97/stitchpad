# Founding Tailors In-App "Your Standing" Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a tailor see their own Founding Tailors standing in the app — points this month and a lifetime running total — by extending the read callable with `youAllTime` and adding a "Your standing" card to the screen.

**Architecture:** Extend `getFoundingTailorsLeaderboard` to also return the viewer's lifetime figure from the already-maintained `leaderboards/alltime` board. In the app, add a repository method that calls that callable with the user's own code, a `standing` field on the screen state populated best-effort after the link resolves, and a card that renders it. No new aggregation, no migration.

**Tech Stack:** TypeScript + firebase-functions v1 + firebase-admin (Jest, inline fake Firestore); Kotlin Multiplatform Compose + Koin + GitLive Firebase SDK (kotlin.test/Turbine).

## Global Constraints

- **Firebase region:** `europe-west1` (unchanged).
- **`youAllTime` semantics:** resolved from `leaderboards/alltime` the same way `you` is from the month board — `{rank: idx+1, points}` when the marketerId is in the board, `{rank:0, points:0}` when the code is valid but absent, `null` for no-code / unknown-code (never leak whether a code exists). The extra `alltime` read happens ONLY inside the existing `if (marketerId)` block.
- **App response DTO declares ALL fields.** GitLive's `.data<T>()` kotlinx deserialization trips on unknown keys, so the DTO must declare `updatedAt, monthId, top, you, youAllTime` (the other referral response DTOs already declare their full shape). Only `you`/`youAllTime` are read.
- **Standing fetch is best-effort:** a failure leaves `standing = null` (card hidden) and must NOT set `state.error` or block the link/share/leaderboard actions.
- **App conventions:** no hardcoded user-facing strings (`compose.resources`); no em dashes; `Result<T, E>` for expected failures; MVI (all state in the ViewModel); every `Screen` composable has a `@Preview`. `compose.resources` `stringResource` only substitutes positional args (`%1$d`, `%1$s`).
- **`CloudFunctionsReferralRepository` has no unit test** — GitLive `FirebaseFunctions` can't be faked in commonTest (the existing `recordAttribution`/`getOrCreateMyReferralLink` have none either). Its new method is compile-verified; behavior is covered by the ViewModel test (via `FakeReferralRepository`) and the backend tests.
- **Deploy:** redeploy ONLY `getFoundingTailorsLeaderboard` (aggregator unchanged); already in `index.ts` exports + `package.json` deploy allow-list; run `npm run lint`.

---

## Task 1: Extend the read callable with `youAllTime`

**Files:**
- Modify: `functions/src/referral/foundingTailorsLeaderboard.ts` (the `LeaderboardResponse` interface ~104-109 and `getFoundingTailorsLeaderboardHandler` ~116-150)
- Test: `functions/src/__tests__/referral/foundingTailorsLeaderboard.test.ts` (add cases in the read-callable section; leave existing tests untouched)

**Interfaces:**
- Consumes: `leaderboards/alltime` doc (`{ entries: LeaderEntry[] }`), the existing `marketerId` resolution.
- Produces: `LeaderboardResponse` gains `youAllTime: { rank: number; points: number } | null`. Consumed by App Task 2's DTO.

- [ ] **Step 1: Write failing tests**

Add to the read-callable section of `foundingTailorsLeaderboard.test.ts`:

```ts
test('resolves youAllTime rank+points from the alltime board', async () => {
  const { db } = makeFakeDb({
    'leaderboards/current': { monthId: '2026-08' },
    'leaderboards/2026-08': {
      monthId: '2026-08', updatedAt: ts('2026-08-25T00:00:00Z'),
      entries: [
        { marketerId: 'mA', name: 'Ada Styles', points: 3 },
        { marketerId: 'mB', name: 'Bola Wears', points: 1 },
      ],
    },
    'leaderboards/alltime': {
      updatedAt: ts('2026-08-25T00:00:00Z'),
      entries: [
        { marketerId: 'mB', name: 'Bola Wears', points: 20 },
        { marketerId: 'mA', name: 'Ada Styles', points: 12 },
      ],
    },
    'referralCodes/CODEA': { marketerId: 'mA' },
  });

  const res = await getFoundingTailorsLeaderboardHandler({ code: 'CODEA' }, { db });

  expect(res.you).toEqual({ rank: 1, points: 3 });         // current month
  expect(res.youAllTime).toEqual({ rank: 2, points: 12 }); // lifetime (mB is #1 all time)
});

test('youAllTime is {rank:0,points:0} for a valid code absent from the alltime board', async () => {
  const { db } = makeFakeDb({
    'leaderboards/current': { monthId: '2026-08' },
    'leaderboards/2026-08': { monthId: '2026-08', updatedAt: ts('2026-08-25T00:00:00Z'), entries: [] },
    'leaderboards/alltime': { updatedAt: ts('2026-08-25T00:00:00Z'), entries: [] },
    'referralCodes/CODEA': { marketerId: 'mA' },
  });

  const res = await getFoundingTailorsLeaderboardHandler({ code: 'CODEA' }, { db });

  expect(res.you).toEqual({ rank: 0, points: 0 });
  expect(res.youAllTime).toEqual({ rank: 0, points: 0 });
});

test('youAllTime is null for no code and for an unknown code', async () => {
  const { db } = makeFakeDb({
    'leaderboards/current': { monthId: '2026-08' },
    'leaderboards/2026-08': { monthId: '2026-08', updatedAt: ts('2026-08-25T00:00:00Z'), entries: [] },
    'leaderboards/alltime': { updatedAt: ts('2026-08-25T00:00:00Z'), entries: [] },
  });

  expect((await getFoundingTailorsLeaderboardHandler({}, { db })).youAllTime).toBeNull();
  expect((await getFoundingTailorsLeaderboardHandler({ code: 'NOPE' }, { db })).youAllTime).toBeNull();
});
```

- [ ] **Step 2: Run to verify they fail**

Run: `cd functions && npx jest src/__tests__/referral/foundingTailorsLeaderboard.test.ts -t youAllTime`
Expected: FAIL — `res.youAllTime` is `undefined` (property doesn't exist yet).

- [ ] **Step 3: Add `youAllTime` to the response interface**

In `foundingTailorsLeaderboard.ts`, add the field to `LeaderboardResponse`:

```ts
export interface LeaderboardResponse {
  updatedAt: number;
  monthId: string;
  top: PublicRow[];
  you: { rank: number; points: number } | null;
  youAllTime: { rank: number; points: number } | null;
}
```

- [ ] **Step 4: Resolve and return `youAllTime`**

In `getFoundingTailorsLeaderboardHandler`, declare `youAllTime` beside `you`, resolve it inside the existing `if (marketerId)` block right after `you` is set, and add it to the return:

```ts
  let you: { rank: number; points: number } | null = null;
  let youAllTime: { rank: number; points: number } | null = null;
  // ...existing code/pattern check that yields `const code = ...`...
  if (code) {
    const codeDoc = (await deps.db.doc(`${REFERRAL_CODES}/${code}`).get()).data() as { marketerId?: string } | undefined;
    const marketerId = codeDoc?.marketerId;
    if (marketerId) {
      const idx = entries.findIndex((e) => e.marketerId === marketerId);
      you = idx >= 0 ? { rank: idx + 1, points: entries[idx].points } : { rank: 0, points: 0 };

      // Lifetime running total from the pre-computed all-time board (same shape /
      // resolution as `you`). Read here — only when a code resolved a marketer — so
      // anonymous/web reads pay nothing new.
      const allTime = (await deps.db.doc('leaderboards/alltime').get()).data() as
        | { entries?: LeaderEntry[] }
        | undefined;
      const allEntries = allTime?.entries ?? [];
      const aIdx = allEntries.findIndex((e) => e.marketerId === marketerId);
      youAllTime = aIdx >= 0 ? { rank: aIdx + 1, points: allEntries[aIdx].points } : { rank: 0, points: 0 };
    }
    // Unknown code: `you`/`youAllTime` stay null — never leak whether the code exists.
  }

  return { updatedAt: board?.updatedAt?.toMillis?.() ?? 0, monthId, top, you, youAllTime };
```

- [ ] **Step 5: Run tests + lint + commit**

```bash
cd functions && npx jest src/__tests__/referral/foundingTailorsLeaderboard.test.ts && npm run lint && npx tsc --noEmit
git add src/referral/foundingTailorsLeaderboard.ts src/__tests__/referral/foundingTailorsLeaderboard.test.ts
git commit -m "feat(founding-tailors): return youAllTime (lifetime standing) from the read callable"
```
Expected: PASS (new + existing read-callable tests), lint + tsc clean.

---

## Task 2: App repository method + domain model

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/referral/domain/ReferralRepository.kt` (add `FoundingTailorsStanding` + interface method)
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/referral/data/CloudFunctionsReferralRepository.kt` (impl + DTOs)
- Modify: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/referral/data/FakeReferralRepository.kt` (implement the new method — required for commonTest to compile)

**Interfaces:**
- Consumes: the `getFoundingTailorsLeaderboard` callable (Task 1) via the app's `FirebaseFunctions`.
- Produces: `data class FoundingTailorsStanding(monthPoints, monthRank, allTimePoints, allTimeRank: Int)` and `suspend fun getFoundingTailorsStanding(code: String): Result<FoundingTailorsStanding, DataError.Network>`. Consumed by Task 3.

- [ ] **Step 1: Add the domain model + interface method**

In `ReferralRepository.kt`, add the data class next to `ReferralLink`:

```kotlin
/**
 * The signed-in tailor's own Founding Tailors standing. Rank 0 means the tailor
 * has a referral code but no points/rank yet (this month or lifetime).
 */
data class FoundingTailorsStanding(
    val monthPoints: Int,
    val monthRank: Int,
    val allTimePoints: Int,
    val allTimeRank: Int,
)
```

and add to the `ReferralRepository` interface:

```kotlin
    /**
     * The signed-in tailor's own month + lifetime Founding Tailors standing,
     * resolved from their [code] via the public leaderboard callable.
     */
    suspend fun getFoundingTailorsStanding(code: String): Result<FoundingTailorsStanding, DataError.Network>
```

- [ ] **Step 2: Implement it in `CloudFunctionsReferralRepository`**

Add the import `import com.danzucker.stitchpad.feature.referral.domain.FoundingTailorsStanding`, the method, and the DTOs (mirror the existing `getOrCreateMyReferralLink` try/catch → `Result.Error(DataError.Network.UNKNOWN)` shape):

```kotlin
    override suspend fun getFoundingTailorsStanding(
        code: String,
    ): Result<FoundingTailorsStanding, DataError.Network> =
        try {
            val data = functions
                .httpsCallable("getFoundingTailorsLeaderboard")
                .invoke(FoundingTailorsStandingRequestDto(code = code))
                .data<FoundingTailorsLeaderboardDto>()
            Result.Success(
                FoundingTailorsStanding(
                    monthPoints = data.you?.points ?: 0,
                    monthRank = data.you?.rank ?: 0,
                    allTimePoints = data.youAllTime?.points ?: 0,
                    allTimeRank = data.youAllTime?.rank ?: 0,
                ),
            )
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            AppLogger.e(tag = TAG, throwable = e) {
                "getFoundingTailorsStanding threw ${e::class.simpleName}: ${e.message}"
            }
            Result.Error(DataError.Network.UNKNOWN)
        }
```

DTOs at the bottom of the file (next to `MyReferralLinkDto`):

```kotlin
@Serializable
private data class FoundingTailorsStandingRequestDto(val code: String)

// Full response shape of getFoundingTailorsLeaderboard — EVERY field is declared so
// GitLive's kotlinx deserialization never trips on an unknown key (matches the other
// referral response DTOs). Only you/youAllTime are read.
@Serializable
private data class FoundingTailorsLeaderboardDto(
    val updatedAt: Long = 0,
    val monthId: String = "",
    val top: List<StandingRowDto> = emptyList(),
    val you: StandingEntryDto? = null,
    val youAllTime: StandingEntryDto? = null,
)

@Serializable
private data class StandingRowDto(val rank: Int = 0, val name: String = "", val points: Int = 0)

@Serializable
private data class StandingEntryDto(val rank: Int = 0, val points: Int = 0)
```

- [ ] **Step 3: Implement the method in the test fake**

In `FakeReferralRepository.kt`, add the import `import com.danzucker.stitchpad.feature.referral.domain.FoundingTailorsStanding`, a configurable result, and the override (mirror the `referralLinkResult` pattern):

```kotlin
    var standingResult: Result<FoundingTailorsStanding, DataError.Network> =
        Result.Success(FoundingTailorsStanding(monthPoints = 0, monthRank = 0, allTimePoints = 0, allTimeRank = 0))
    var standingCallCount: Int = 0
    var lastStandingCode: String? = null

    override suspend fun getFoundingTailorsStanding(
        code: String,
    ): Result<FoundingTailorsStanding, DataError.Network> {
        standingCallCount++
        lastStandingCode = code
        return standingResult
    }
```

- [ ] **Step 4: Compile-verify (no repo unit test — GitLive Functions can't be faked)**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileDebugUnitTestKotlinAndroid`
Expected: BUILD SUCCESSFUL (the new interface method compiles; the fake satisfies the interface so commonTest still compiles).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/referral/domain/ReferralRepository.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/referral/data/CloudFunctionsReferralRepository.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/referral/data/FakeReferralRepository.kt
git commit -m "feat(founding-tailors): repository method for the tailor's own standing"
```

---

## Task 3: State + ViewModel fetch

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/foundingtailors/presentation/FoundingTailorsContract.kt` (add `standing` to state)
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/foundingtailors/presentation/FoundingTailorsViewModel.kt` (fetch after code resolves)
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/foundingtailors/FoundingTailorsViewModelTest.kt`

**Interfaces:**
- Consumes: `ReferralRepository.getFoundingTailorsStanding` (Task 2), `FoundingTailorsStanding`.
- Produces: `FoundingTailorsState.standing: FoundingTailorsStanding?`. Consumed by Task 4.

- [ ] **Step 1: Add `standing` to the contract**

In `FoundingTailorsContract.kt`, add the import `import com.danzucker.stitchpad.feature.referral.domain.FoundingTailorsStanding` and the field:

```kotlin
data class FoundingTailorsState(
    val isLoading: Boolean = false,
    val referralUrl: String? = null,
    val standing: FoundingTailorsStanding? = null,
    val error: UiText? = null,
)
```

- [ ] **Step 2: Write the failing ViewModel tests**

Add to `FoundingTailorsViewModelTest.kt` (import `import com.danzucker.stitchpad.feature.referral.domain.FoundingTailorsStanding`):

```kotlin
    @Test
    fun `LoadLink populates standing from the repository`() = runTest {
        val repo = FakeReferralRepository()
        repo.standingResult = Result.Success(
            FoundingTailorsStanding(monthPoints = 2, monthRank = 1, allTimePoints = 9, allTimeRank = 3),
        )
        val vm = viewModel(referralRepository = repo, userRepository = userWith("CODE0"))

        vm.onAction(FoundingTailorsAction.LoadLink)

        assertEquals(1, repo.standingCallCount)
        assertEquals("CODE0", repo.lastStandingCode)
        assertEquals(
            FoundingTailorsStanding(monthPoints = 2, monthRank = 1, allTimePoints = 9, allTimeRank = 3),
            vm.state.value.standing,
        )
    }

    @Test
    fun `LoadLink leaves standing null and no error when the standing fetch fails`() = runTest {
        val repo = FakeReferralRepository()
        repo.standingResult = Result.Error(DataError.Network.UNKNOWN)
        val vm = viewModel(referralRepository = repo, userRepository = userWith("CODE0"))

        vm.onAction(FoundingTailorsAction.LoadLink)

        assertNull(vm.state.value.standing)
        assertNull(vm.state.value.error) // a failed standing fetch must NOT surface an error
        assertEquals("https://link.getstitchpad.com/r/CODE0", vm.state.value.referralUrl)
    }
```

- [ ] **Step 3: Run to verify they fail**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*FoundingTailorsViewModel*"`
Expected: FAIL — `standing` is always null (the VM never fetches it) and `standingCallCount` is 0.

- [ ] **Step 4: Fetch the standing after the code resolves**

In `FoundingTailorsViewModel.kt`, call a new `loadStanding(code)` after `code` is set in BOTH branches of `loadLink()`, and add the helper:

```kotlin
            val existingCode = userRepository.observeUser(uid).first()?.referralCode
            if (existingCode != null) {
                code = existingCode
                _state.update { it.copy(isLoading = false, referralUrl = LINK_BASE_URL + existingCode) }
                loadStanding(existingCode)
                return@launch
            }

            when (val result = referralRepository.getOrCreateMyReferralLink()) {
                is Result.Success -> {
                    code = result.data.code
                    _state.update { it.copy(isLoading = false, referralUrl = result.data.url) }
                    loadStanding(result.data.code)
                }
                is Result.Error -> {
                    _state.update { it.copy(isLoading = false, error = result.error.toFoundingTailorsUiText()) }
                }
            }
```

```kotlin
    /**
     * Best-effort secondary load of the tailor's own month + lifetime points. A
     * failure is swallowed (the card stays hidden) so it never blocks the link,
     * share, or leaderboard actions.
     */
    private suspend fun loadStanding(code: String) {
        when (val result = referralRepository.getFoundingTailorsStanding(code)) {
            is Result.Success -> _state.update { it.copy(standing = result.data) }
            is Result.Error -> Unit // leave standing null; never surface an error
        }
    }
```

- [ ] **Step 5: Run to verify they pass**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*FoundingTailorsViewModel*"`
Expected: PASS (new standing tests + all existing VM tests, which are unaffected because the default `FakeReferralRepository.standingResult` is a success with 0/0).

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/foundingtailors/presentation/FoundingTailorsContract.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/foundingtailors/presentation/FoundingTailorsViewModel.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/foundingtailors/FoundingTailorsViewModelTest.kt
git commit -m "feat(founding-tailors): load the tailor's own standing on the screen VM"
```

---

## Task 4: "Your standing" card + strings

**Files:**
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml` (add 6 standing strings)
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/foundingtailors/presentation/FoundingTailorsScreen.kt` (card + `StandingRow` + preview)

**Interfaces:**
- Consumes: `FoundingTailorsState.standing` (Task 3).
- Produces: the rendered card. No further consumers.

- [ ] **Step 1: Add strings**

In `strings.xml` (near the other `founding_tailors_*` strings; no em dashes; positional args only):

```xml
    <string name="founding_tailors_standing_title">Your standing</string>
    <string name="founding_tailors_standing_this_month">This month</string>
    <string name="founding_tailors_standing_all_time">All time</string>
    <string name="founding_tailors_standing_points">%1$d points</string>
    <string name="founding_tailors_standing_rank">#%1$d</string>
    <string name="founding_tailors_standing_empty">Share your link to start earning.</string>
```

- [ ] **Step 2: Render the card in the screen**

In `FoundingTailorsScreen.kt`, add imports (`androidx.compose.foundation.background`, `androidx.compose.foundation.shape.RoundedCornerShape`, `androidx.compose.ui.draw.clip`, and the 6 new string resources, each in alphabetical position), and insert the card in the main `Column` **right after the subtitle `Text` and before the error slot**:

```kotlin
            state.standing?.let { standing ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(DesignTokens.space3))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(DesignTokens.space4),
                    verticalArrangement = Arrangement.spacedBy(DesignTokens.space2),
                ) {
                    Text(
                        text = stringResource(Res.string.founding_tailors_standing_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    StandingRow(
                        label = stringResource(Res.string.founding_tailors_standing_this_month),
                        points = standing.monthPoints,
                        rank = standing.monthRank,
                    )
                    StandingRow(
                        label = stringResource(Res.string.founding_tailors_standing_all_time),
                        points = standing.allTimePoints,
                        rank = standing.allTimeRank,
                    )
                    if (standing.monthPoints == 0 && standing.allTimePoints == 0) {
                        Text(
                            text = stringResource(Res.string.founding_tailors_standing_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
```

Add the private row composable (near the previews):

```kotlin
@Composable
private fun StandingRow(label: String, points: Int, rank: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (rank > 0) {
                Text(
                    text = stringResource(Res.string.founding_tailors_standing_rank, rank),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = stringResource(Res.string.founding_tailors_standing_points, points),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
```

- [ ] **Step 3: Show it in the preview**

Update `PREVIEW_STATE` to include a populated standing so the card renders in `@Preview`:

```kotlin
private val PREVIEW_STATE = FoundingTailorsState(
    isLoading = false,
    referralUrl = "https://link.getstitchpad.com/r/CODE0",
    standing = com.danzucker.stitchpad.feature.referral.domain.FoundingTailorsStanding(
        monthPoints = 2, monthRank = 1, allTimePoints = 9, allTimeRank = 3,
    ),
    error = null,
)
```
(Or add a top-level import for `FoundingTailorsStanding` and use the short name.)

- [ ] **Step 4: Build + detekt**

Run: `./gradlew :composeApp:assembleDebug detekt`
Expected: BUILD SUCCESSFUL, detekt clean. If the added composable trips `TooManyFunctions` on this file, add `@file:Suppress("TooManyFunctions")` at the top (the project's established pattern for preview-heavy screen files). Optional: `:composeApp:installDebug` to eyeball the card.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/composeResources/values/strings.xml \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/foundingtailors/presentation/FoundingTailorsScreen.kt
git commit -m "feat(founding-tailors): Your standing card (month + lifetime points)"
```

---

## Deploy & rollout

1. Redeploy ONLY the read callable: `cd functions && npm run lint && firebase deploy --only functions:getFoundingTailorsLeaderboard --project stitchpad-30607`. The aggregator is unchanged; the web page ignores the new `youAllTime` field.
2. Ship the app change (Tasks 2-4) through the normal PR + store pipeline.
3. No data migration — `leaderboards/alltime` is already maintained by the aggregator.

## Self-review notes (coverage vs spec)

- `youAllTime` from the alltime board, null/0-0 semantics → Task 1 (+ 3 tests).
- Full response DTO (unknown-key safety) → Task 2 Step 2 (all fields declared).
- Domain model + repo method → Task 2; fake updated so commonTest compiles → Task 2 Step 3.
- Best-effort fetch (failure hides card, no error, link still works) → Task 3 Step 4 + the two VM tests.
- Card: month + all-time, rank when > 0, 0/0 empty hint, preview → Task 4.
- No repo unit test (GitLive not fakeable) → Global Constraints + Task 2 Step 4 (compile-verified; behavior covered by Task 1 backend tests + Task 3 VM test).
- Deploy only the read callable → rollout.

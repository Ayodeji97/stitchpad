## Second-pass review (`36f06168` → `d8cb7a0d`)

**Verdict: Approve with nits**

Checked the follow-up fixes against the prior review findings on head `d8cb7a0d`.

### Prior findings
| Item | Status |
|---|---|
| AppConfig `onStart` upstream of `retryWithFallback` | **Fixed** — `onStart` is now downstream; backoff can grow |
| Customer-cap cache race | **Partial** — `update {}` fixes torn RMW; stale pre-create snapshot overwrite remains (documented soft-cap residual) |
| Reports cache generation mismatch | **Fixed** — both caches publish after `withContext` |
| Share-path `CancellationException` | **Fixed** |
| `checkListenerRetryCoverage` | **Partial** — no longer treats terminating `.catch` as survival; still file-level heuristic |
| FCM `runBlocking` | **Fixed** — WorkManager + direct `PushTokenRepository` so retries aren’t dead-coded by the registrar |
| Tutorials URL resolve cancellation | **Fixed** |

### Nits / residuals (non-blocking)
- Optional: generation-aware cap cache **if** the client cap ever becomes hard enforcement
- Optional: `RetryingListener` test that `onStart { emit(fallback) }` upstream would pin backoff (guards against regression)
- Guardrail error text still mentions `.catch`; structural check still file-count based
- Push worker returns success when signed out (no auth wait) — relies on next authenticated `registerForUser`, same class of drop as the old timeout path

No new ≥P2 defects found in the fix commits themselves. The hardening work is in good shape to merge from a coroutine/ANR perspective, with the documented soft-cap residual accepted as intentional.

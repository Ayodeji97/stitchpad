---
description: Auto-format Kotlin sources using detekt's ktlint-based formatter, then verify detekt passes.
allowed-tools: Bash(./gradlew:*)
---

Run the project's Kotlin formatter and verify the result.

Steps:
1. Run `./gradlew detektFormat` to auto-correct formatting issues (import order, indentation, blank lines, trailing spaces, etc.) across `commonMain`, `androidMain`, and `iosMain`.
2. Run `./gradlew detekt` to confirm no remaining issues.

If `detekt` still reports issues after formatting, summarize them for the user — those require manual fixes (e.g. line-length violations, magic numbers, long methods).

Report a concise summary of what changed and whether detekt is clean.

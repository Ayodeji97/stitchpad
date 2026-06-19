# Crash Classes

Canonical catalog of the ways StitchPad crashes. This is the single source of
truth. `scripts/crash-check.sh` implements the `detection: regex` subset below;
the `crash-check` skill reasons about the `detection: ai-only` ones.

When a new crash class is discovered (audit or real-world incident), append a
section here and — if regex-detectable — add a rule to `scripts/crash-check.sh`.

Each entry: **id** · detection · severity · symptom · why · fix.

## serializer-any
- detection: regex · severity: block
- Symptom: iOS crashes on the first Firestore emit.
- Why: `snap.data<Map<String, Any?>>()` has no serializer for `Any?` on Kotlin/Native.
- Fix: read into a typed `@Serializable` DTO.

## arrayunion-dto
- detection: regex (warn) + ai-only · severity: warn
- Symptom: iOS crash "Unsupported type".
- Why: `arrayUnion(DTO)` cannot serialize a domain/DTO object on Native.
- Fix: pass a primitive `Map`.

## koin-platformcontext
- detection: regex · severity: block
- Symptom: crash at launch (stack overflow).
- Why: `single<PlatformContext> { androidContext() }` — PlatformContext is a typealias
  for Context on Android, so the definition resolves itself recursively.
- Fix: do not register PlatformContext via androidContext().

## jvm-only-api
- detection: regex · severity: block
- Symptom: iOS link failure.
- Why: JVM-only stdlib APIs (`String.format`, `import java.*`) compile on Android
  but have no Native target.
- Fix: use a multiplatform API.

## epoch-days-skew
- detection: regex (warn) · severity: warn
- Symptom: iOS-only wrong values / overflow.
- Why: `LocalDate.toEpochDays()` returns `Long` on iOS Native, `Int` on JVM.
- Fix: cast `.toLong()` up front.

## clock-system-ios
- detection: regex (warn) · severity: warn
- Symptom: iOS compile/resolve failure on pinned datetime versions.
- Why: `Clock.System` unresolved on iOS in some kotlinx.datetime versions.
- Fix: inject `() -> Long` instead.

## peekaboo-maxselection
- detection: regex (warn) · severity: warn
- Symptom: Android crash opening the picker.
- Why: `SelectionMode.Multiple(maxSelection <= 1)` — PickMultipleVisualMedia needs > 1.
- Fix: guard `max > 1`, else use `SelectionMode.Single`.

## strings-backslash-apos
- detection: regex · severity: block
- Symptom: iOS renders `\'` literally in UI text.
- Why: Compose Multiplatform iOS does not process the backslash escape in strings.xml.
- Fix: use `&apos;` (or a typographic apostrophe).

## native-callback-selector
- detection: ai-only
- Symptom: Swift call site fails / wrong label (`value_`).
- Why: two fun-interface callbacks with the same method+param name collapse to one
  Obj-C selector. CI builds the framework, not the Swift target, so it slips through.
- Fix: give the callbacks distinct param names; verify with a clean Xcode build.

## gitlive-nonnull-token
- detection: ai-only
- Symptom: iOS SSO crash.
- Why: gitlive's Kotlin nullable signatures lie on iOS; providers require both tokens non-null.
- Fix: assert both tokens non-null before calling; test on a real iOS device.

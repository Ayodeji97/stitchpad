# StitchPad — Claude Code Project Guide

## What is this?
KMP + Compose Multiplatform production app for Nigerian tailors.
Package: com.danzucker.stitchpad
Firebase project: stitchpad-30607 (Firestore in europe-west1)

## Architecture
- MVVM + Clean Architecture within a single :composeApp module
- Package-based separation (not multi-module yet)
- Layers: domain (models, interfaces, errors) -> data (implementations, DTOs, mappers) -> presentation (ViewModels, Compose screens)

## Package Structure
```
com.danzucker.stitchpad/
  core/
    domain/model/          — Domain models (User, Customer, Measurement, Order)
    domain/repository/     — Repository interfaces
    domain/error/          — Result<T,E>, DataError, Error interface
    data/repository/       — Repository implementations
    data/remote/           — Firebase data sources
    data/dto/              — Firestore DTOs
    data/mapper/           — DTO <-> domain mappers
    presentation/          — Shared UI utilities (UiText, ObserveAsEvents)
  feature/
    auth/domain/           — AuthError, auth-specific types
    auth/data/             — FirebaseAuthDataSource
    auth/presentation/     — AuthViewModel, LoginScreen, SignUpScreen
  ui/
    theme/                 — DesignTokens, Theme, Color, Type
    components/            — Shared design system composables
  di/                      — Koin modules
  navigation/              — Route objects, nav graph
```

## Patterns (MUST follow)
1. MVI: Every screen has State, Action, Event sealed classes + ViewModel
2. Root/Screen split: Root composable (has ViewModel) + Screen composable (stateless, previewable)
3. Result<T, E>: Never throw for expected failures. Always return Result.Error
4. Koin DI: singleOf/viewModelOf constructor refs. koinViewModel() in Root composables only
5. Navigation: @Serializable route objects. Feature nav graphs. Cross-feature via callbacks
6. DTOs separate from domain models. Mappers as extension functions in data layer
7. Name implementations descriptively (FirebaseAuthRepository, not AuthRepositoryImpl)
8. UiText for all user-facing error strings

## Error Handling
- Result<T, E> sealed interface in core.domain.error
- DataError.Network and DataError.Local enums
- Feature-specific errors implement Error interface
- Catch exceptions at the layer that owns them, convert to Result.Error
- toUiText() extensions in presentation layer

## Design System
- Primary: Deep Saffron #E8A800
- Fonts: Plus Jakarta Sans (body) + JetBrains Mono (measurements)
- Light + dark mode defined
- All tokens in DesignTokens.kt, theme in StitchPadTheme

## Build & Run
- Android: ./gradlew :composeApp:assembleDebug
- iOS: open iosApp/iosApp.xcodeproj in Xcode, build & run
- Tests: ./gradlew :composeApp:allTests
- Detekt: ./gradlew detekt

## Firebase
- google-services.json goes in composeApp/ (Android, gitignored)
- GoogleService-Info.plist goes in iosApp/iosApp/ (iOS, gitignored)
- Using GitLive firebase-kotlin-sdk for KMP
- Auth: email/password + Google Sign-In (Sprint 1+)
- Firestore: europe-west1 region

## Rules
- Never commit google-services.json or GoogleService-Info.plist
- Never hardcode strings — use compose.resources string resources
- All state in ViewModel, never in remember/rememberSaveable (except Compose-internal state like LazyListState)
- No business logic in composables
- Every Screen composable must have a @Preview

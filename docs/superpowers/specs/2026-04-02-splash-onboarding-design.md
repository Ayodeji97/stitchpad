# Splash Screen + Onboarding Design Spec

## Overview
First-launch experience: Splash → 3-screen onboarding carousel → Sign Up/Login. Returning users skip directly to Login (or Home if signed in).

## Logo
- **Style:** Bold "S" lettermark in white circle with scissors accent
- **Implementation:** Compose Canvas drawing (no external image assets)
- **Usage:** Splash screen center, app icon base

## Splash Screen
- **Background:** Primary saffron (#E8A800) full screen
- **Content:** White circle with "S" lettermark, "StitchPad" wordmark (Plus Jakarta Sans Bold 28sp), "Your tailor's notebook" subtitle (14sp, 60% opacity)
- **Duration:** 1.5 seconds, fade-in animation
- **Auto-advance:** To onboarding (first launch) or Login/Home (returning user)

## Onboarding (3 screens, HorizontalPager)

### Screen 1
- **Headline:** "Never lose a customer's measurements again"
- **Subtitle:** "Store chest, waist, hip and all body measurements safely in one place."
- **Illustration:** Measurement tape icon in saffron-tinted circle (Compose Canvas)
- **Button:** "Next"

### Screen 2
- **Headline:** "Track orders from cutting to delivery"
- **Subtitle:** "Know exactly which orders are pending, in progress, or ready for pickup."
- **Illustration:** Scissors + status badges in saffron-tinted circle (Compose Canvas)
- **Button:** "Next"

### Screen 3
- **Headline:** "Your tailor's notebook"
- **Subtitle:** "Simple, fast, and built for fashion designers who work on their phones."
- **Illustration:** Notebook icon in saffron-tinted circle (Compose Canvas)
- **Button:** "Get Started" → navigates to Sign Up

### Shared UI Elements
- **Dot indicator:** 3 dots, active dot is wider pill (24x8dp), inactive is circle (8x8dp)
- **Active color:** primary500 (#E8A800), inactive: neutral200 (#E5E3DF)
- **Navigation:** Swipe left/right + "Next"/"Get Started" button
- **No skip button, no back button**
- **Background:** neutral50 (#F9F9F8)
- **Illustration circles:** 180dp diameter, primary50 (#FFF8E7) background

## Persistence
- Store `hasSeenOnboarding: Boolean` in platform preferences (DataStore on Android, NSUserDefaults on iOS via expect/actual)
- First launch: splash → onboarding → auth
- Returning (not logged in): splash → login
- Returning (logged in): splash → home

## Navigation Flow
```
SplashRoute → (check hasSeenOnboarding)
  ├── false → OnboardingRoute → SignUpRoute
  └── true → (check isLoggedIn)
        ├── false → LoginRoute
        └── true → HomeRoute
```

## New Routes
- `SplashRoute` — splash screen
- `OnboardingRoute` — 3-screen pager

## Files to Create/Modify
- `feature/onboarding/presentation/SplashScreen.kt` — splash with logo
- `feature/onboarding/presentation/OnboardingScreen.kt` — pager with 3 pages
- `feature/onboarding/presentation/OnboardingViewModel.kt` — manages hasSeenOnboarding state
- `feature/onboarding/presentation/components/StitchPadLogo.kt` — Compose Canvas logo
- `feature/onboarding/presentation/components/OnboardingPage.kt` — single page composable
- `feature/onboarding/presentation/components/OnboardingIllustration.kt` — Canvas illustrations
- `feature/onboarding/data/OnboardingPreferences.kt` — expect/actual for hasSeenOnboarding
- `navigation/Routes.kt` — add SplashRoute, OnboardingRoute
- `navigation/NavGraph.kt` — wire new routes with conditional logic
- `di/OnboardingModule.kt` — Koin module for onboarding
- `values/strings.xml` — add onboarding strings

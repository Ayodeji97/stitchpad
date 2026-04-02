# Workshop Onboarding Screen — Design Spec

## Context

Nigerian tailors identify strongly with their brand name. The Firestore `users` doc already has `businessName: String?` and `phone: String?` fields, but neither is collected during sign-up. Adding them to the sign-up form was rejected as too many fields at once. Instead, a dedicated post-signup screen collects this data at the moment of highest engagement.

## Screen: "Set up your workshop"

### When it shows
- **After first sign-up only** — inserted between successful registration and the Home screen
- **Condition:** User doc has no `businessName` set (null or missing)
- **Never on subsequent logins** — splash screen checks `isLoggedIn` and goes straight to Home

### Layout
Follows the existing auth screen pattern:
- Saffron header (#E8A800) with StitchPad logo
- White card with rounded top corners overlapping the header
- Title: "Set up your workshop"
- Subtitle: "Personalise StitchPad for your brand"

### Fields
1. **Business name** (optional)
   - Label: "Business name"
   - Placeholder: "e.g. Ade Fashions"
   - Helper text: "Shown on your dashboard. You can change this later."
   - Keyboard: default text

2. **Phone number** (optional)
   - Label: "Phone number"
   - Placeholder: "+234 801 234 5678"
   - Helper text: "For your profile, not shared with customers."
   - Keyboard: phone number

### Actions
- **"Continue" button** — if at least one field has a value, creates the Firestore user doc with businessName + phone, then navigates to Home. If both fields are empty, behaves like Skip (no Firestore write).
- **"Skip for now" link** — navigates to Home without writing to Firestore. businessName stays null.

### Navigation flow
```
Sign Up (success)
  → WorkshopSetupRoute (NEW)
    → Continue / Skip
      → HomeRoute
```

### Route
- `WorkshopSetupRoute` — new `@Serializable` route object in `navigation/Routes.kt`
- Added to `NavGraph.kt` between SignUp success and Home

## Architecture

### MVI pattern (per project conventions)
- **WorkshopSetupState**: `businessName: String`, `phone: String`, `isLoading: Boolean`
- **WorkshopSetupAction**: `OnBusinessNameChange(name)`, `OnPhoneChange(phone)`, `OnContinueClick`, `OnSkipClick`
- **WorkshopSetupEvent**: `NavigateToHome`
- **WorkshopSetupViewModel**: handles actions, writes to Firestore on Continue

### Data flow (Continue)
1. If both fields empty, treat as Skip (no write, navigate to Home)
2. Otherwise, ViewModel calls a new `UserRepository.createUserProfile(businessName, phone)` method
3. This creates the `users/{userId}` document in Firestore (this is the first write — the doc doesn't exist yet after sign-up)
3. On success, emit `NavigateToHome`
4. On error, show Snackbar error, keep user on screen

### Data flow (Skip)
1. Emit `NavigateToHome` immediately, no Firestore write

### New files
- `feature/onboarding/presentation/workshop/WorkshopSetupScreen.kt` — Root + Screen composables
- `feature/onboarding/presentation/workshop/WorkshopSetupViewModel.kt`
- `feature/onboarding/presentation/workshop/WorkshopSetupState.kt`
- `feature/onboarding/presentation/workshop/WorkshopSetupAction.kt`
- `feature/onboarding/presentation/workshop/WorkshopSetupEvent.kt`

### Modified files
- `navigation/Routes.kt` — add `WorkshopSetupRoute`
- `navigation/NavGraph.kt` — add composable destination, wire SignUp success → WorkshopSetup → Home
- `di/AppModule.kt` — register `WorkshopSetupViewModel` with Koin
- `core/domain/repository/UserRepository.kt` — add `updateUserProfile()` interface method (or create if doesn't exist)
- `core/data/repository/` — implement Firestore write

### Reuse
- Same saffron header + white card layout as `SignUpScreen.kt` and `LoginScreen.kt`
- Same `OutlinedTextField` styling with `inputColors`
- Same `LabeledField` composable pattern
- Existing `StitchPadLogo` component

## Nudge (for users who skip)

After the first customer is created, if `businessName` is still null, show a one-time Snackbar:
- Text: "Customer saved! Personalise your workshop"
- Action button: "SETTINGS" (navigates to Settings screen)
- Trigger: in the customer creation success flow
- One-time: tracked via a flag (e.g., `hasSeenWorkshopNudge` in local preferences)

> Note: The nudge depends on the Customer CRUD feature (Sprint 1) and Settings screen existing. It will be implemented as part of those features, not this screen.

## Firestore write on Continue

```
users/{userId} {
  businessName: "Ade Fashions",  // or null if empty
  phone: "+2348012345678",       // or null if empty
  displayName: "...",            // already set from signup
  email: "...",                  // already set from signup
  subscriptionTier: "free",
  subscriptionStatus: "active",
  customerCount: 0,
  createdAt: serverTimestamp(),
  updatedAt: serverTimestamp()
}
```

> Note: This is the first Firestore user doc write. Currently, FirebaseAuthRepository only creates the Firebase Auth user. This screen (or the Continue action) is where the Firestore `users/{userId}` document gets created.

## Testing

### Unit tests (WorkshopSetupViewModelTest)
- Initial state is empty
- Business name change updates state
- Phone change updates state
- Continue with data writes to Firestore and emits NavigateToHome
- Continue with empty fields treats as skip (no Firestore write, emits NavigateToHome)
- Skip emits NavigateToHome without Firestore write
- Firestore error shows error event

### Manual verification
- Sign up new account → workshop screen appears
- Fill both fields + Continue → lands on Home, Firestore doc has values
- Skip → lands on Home, Firestore doc has null businessName
- Log out + log back in → goes straight to Home (no workshop screen again)

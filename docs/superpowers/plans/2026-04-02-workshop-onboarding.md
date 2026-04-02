# Workshop Onboarding Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a post-signup "Set up your workshop" screen that collects business name and phone number before the user reaches the Home screen.

**Architecture:** New `WorkshopSetupRoute` inserted between sign-up success and Home. Uses MVI pattern (State/Action/Event + ViewModel). Creates a `UserRepository` interface + Firestore implementation for writing the user profile document. TDD — tests first, then implementation.

**Tech Stack:** KMP, Compose Multiplatform, Koin DI, GitLive Firebase Firestore SDK, kotlin.test

---

## File Structure

### New files
| File | Responsibility |
|------|---------------|
| `core/domain/repository/UserRepository.kt` | Interface: `createUserProfile(userId, businessName, phone)` |
| `core/data/repository/FirebaseUserRepository.kt` | Firestore implementation: writes `users/{userId}` doc |
| `feature/onboarding/presentation/workshop/WorkshopSetupState.kt` | MVI state data class |
| `feature/onboarding/presentation/workshop/WorkshopSetupAction.kt` | MVI action sealed interface |
| `feature/onboarding/presentation/workshop/WorkshopSetupEvent.kt` | MVI event sealed interface |
| `feature/onboarding/presentation/workshop/WorkshopSetupViewModel.kt` | ViewModel: validates, calls repo, emits events |
| `feature/onboarding/presentation/workshop/WorkshopSetupScreen.kt` | Root + Screen composables |
| `commonTest/.../core/data/repository/FakeUserRepository.kt` | Test double for UserRepository |
| `commonTest/.../feature/onboarding/presentation/workshop/WorkshopSetupViewModelTest.kt` | ViewModel unit tests |

### Modified files
| File | Change |
|------|--------|
| `navigation/Routes.kt` | Add `WorkshopSetupRoute` |
| `navigation/NavGraph.kt` | Add composable destination, wire SignUp → Workshop → Home |
| `di/AuthModule.kt` | Register `UserRepository` + `WorkshopSetupViewModel` in Koin |
| `StitchPadApp.kt` | No change needed — modules already registered |

---

### Task 1: Domain Layer — UserRepository Interface

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/repository/UserRepository.kt`

- [ ] **Step 1: Create the UserRepository interface**

```kotlin
package com.danzucker.stitchpad.core.domain.repository

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult

interface UserRepository {
    suspend fun createUserProfile(
        userId: String,
        businessName: String?,
        phone: String?
    ): EmptyResult<DataError.Network>
}
```

- [ ] **Step 2: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/repository/UserRepository.kt
git commit -m "feat: add UserRepository interface for user profile creation"
```

---

### Task 2: Data Layer — FirebaseUserRepository

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/repository/FirebaseUserRepository.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/AuthModule.kt`

- [ ] **Step 1: Create FirebaseUserRepository**

```kotlin
package com.danzucker.stitchpad.core.data.repository

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.repository.UserRepository
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.FieldValue

class FirebaseUserRepository(
    private val firestore: FirebaseFirestore
) : UserRepository {

    override suspend fun createUserProfile(
        userId: String,
        businessName: String?,
        phone: String?
    ): EmptyResult<DataError.Network> {
        return try {
            firestore.collection("users").document(userId).set(
                mapOf(
                    "businessName" to businessName,
                    "phone" to phone,
                    "subscriptionTier" to "free",
                    "subscriptionStatus" to "active",
                    "customerCount" to 0,
                    "createdAt" to FieldValue.serverTimestamp,
                    "updatedAt" to FieldValue.serverTimestamp
                ),
                merge = true
            )
            Result.Success(Unit)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Result.Error(DataError.Network.UNKNOWN)
        }
    }
}
```

- [ ] **Step 2: Register in Koin — add to `AuthModule.kt`**

Add these imports at the top of `AuthModule.kt`:
```kotlin
import com.danzucker.stitchpad.core.data.repository.FirebaseUserRepository
import com.danzucker.stitchpad.core.domain.repository.UserRepository
```

Add this line inside `authDataModule`:
```kotlin
singleOf(::FirebaseUserRepository) bind UserRepository::class
```

- [ ] **Step 3: Verify build compiles**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/repository/FirebaseUserRepository.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/AuthModule.kt
git commit -m "feat: add FirebaseUserRepository for user profile creation"
```

---

### Task 3: MVI Contracts — State, Action, Event

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/onboarding/presentation/workshop/WorkshopSetupState.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/onboarding/presentation/workshop/WorkshopSetupAction.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/onboarding/presentation/workshop/WorkshopSetupEvent.kt`

- [ ] **Step 1: Create WorkshopSetupState**

```kotlin
package com.danzucker.stitchpad.feature.onboarding.presentation.workshop

data class WorkshopSetupState(
    val businessName: String = "",
    val phone: String = "",
    val isLoading: Boolean = false
)
```

- [ ] **Step 2: Create WorkshopSetupAction**

```kotlin
package com.danzucker.stitchpad.feature.onboarding.presentation.workshop

sealed interface WorkshopSetupAction {
    data class OnBusinessNameChange(val name: String) : WorkshopSetupAction
    data class OnPhoneChange(val phone: String) : WorkshopSetupAction
    data object OnContinueClick : WorkshopSetupAction
    data object OnSkipClick : WorkshopSetupAction
}
```

- [ ] **Step 3: Create WorkshopSetupEvent**

```kotlin
package com.danzucker.stitchpad.feature.onboarding.presentation.workshop

import com.danzucker.stitchpad.core.presentation.UiText

sealed interface WorkshopSetupEvent {
    data object NavigateToHome : WorkshopSetupEvent
    data class ShowError(val message: UiText) : WorkshopSetupEvent
}
```

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/onboarding/presentation/workshop/
git commit -m "feat: add WorkshopSetup MVI contracts (State, Action, Event)"
```

---

### Task 4: Test Double — FakeUserRepository

**Files:**
- Create: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/data/repository/FakeUserRepository.kt`

- [ ] **Step 1: Create FakeUserRepository**

```kotlin
package com.danzucker.stitchpad.core.data.repository

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.repository.UserRepository

class FakeUserRepository : UserRepository {
    var shouldReturnError: DataError.Network? = null
    var lastUserId: String? = null
    var lastBusinessName: String? = null
    var lastPhone: String? = null

    override suspend fun createUserProfile(
        userId: String,
        businessName: String?,
        phone: String?
    ): EmptyResult<DataError.Network> {
        shouldReturnError?.let { return Result.Error(it) }
        lastUserId = userId
        lastBusinessName = businessName
        lastPhone = phone
        return Result.Success(Unit)
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/data/repository/FakeUserRepository.kt
git commit -m "test: add FakeUserRepository test double"
```

---

### Task 5: ViewModel Tests — Write Failing Tests

**Files:**
- Create: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/onboarding/presentation/workshop/WorkshopSetupViewModelTest.kt`

- [ ] **Step 1: Write all ViewModel tests**

```kotlin
package com.danzucker.stitchpad.feature.onboarding.presentation.workshop

import com.danzucker.stitchpad.core.data.repository.FakeUserRepository
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class WorkshopSetupViewModelTest {

    private lateinit var viewModel: WorkshopSetupViewModel
    private lateinit var userRepository: FakeUserRepository
    private lateinit var authRepository: FakeAuthRepository

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        userRepository = FakeUserRepository()
        authRepository = FakeAuthRepository()
        // Pre-populate a signed-in user
        authRepository.shouldReturnError = null
        viewModel = WorkshopSetupViewModel(userRepository, authRepository)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialStateIsEmpty() {
        val state = viewModel.state.value
        assertEquals("", state.businessName)
        assertEquals("", state.phone)
        assertFalse(state.isLoading)
    }

    @Test
    fun onBusinessNameChangeUpdatesState() {
        viewModel.onAction(WorkshopSetupAction.OnBusinessNameChange("Ade Fashions"))
        assertEquals("Ade Fashions", viewModel.state.value.businessName)
    }

    @Test
    fun onPhoneChangeUpdatesState() {
        viewModel.onAction(WorkshopSetupAction.OnPhoneChange("+2348012345678"))
        assertEquals("+2348012345678", viewModel.state.value.phone)
    }

    @Test
    fun skipEmitsNavigateToHome() = runTest {
        viewModel.onAction(WorkshopSetupAction.OnSkipClick)

        val event = viewModel.events.first()
        assertIs<WorkshopSetupEvent.NavigateToHome>(event)
    }

    @Test
    fun skipDoesNotWriteToRepository() = runTest {
        viewModel.onAction(WorkshopSetupAction.OnSkipClick)
        viewModel.events.first()

        assertNull(userRepository.lastUserId)
    }

    @Test
    fun continueWithDataWritesToRepositoryAndNavigates() = runTest {
        // Sign up a user first so getCurrentUser returns something
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")

        viewModel = WorkshopSetupViewModel(userRepository, authRepository)
        viewModel.onAction(WorkshopSetupAction.OnBusinessNameChange("Ade Fashions"))
        viewModel.onAction(WorkshopSetupAction.OnPhoneChange("+234801"))
        viewModel.onAction(WorkshopSetupAction.OnContinueClick)

        val event = viewModel.events.first()
        assertIs<WorkshopSetupEvent.NavigateToHome>(event)
        assertEquals("Ade Fashions", userRepository.lastBusinessName)
        assertEquals("+234801", userRepository.lastPhone)
    }

    @Test
    fun continueWithEmptyFieldsBehavesLikeSkip() = runTest {
        viewModel.onAction(WorkshopSetupAction.OnContinueClick)

        val event = viewModel.events.first()
        assertIs<WorkshopSetupEvent.NavigateToHome>(event)
        assertNull(userRepository.lastUserId)
    }

    @Test
    fun continueWithOnlyBusinessNameWritesToRepository() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        viewModel = WorkshopSetupViewModel(userRepository, authRepository)

        viewModel.onAction(WorkshopSetupAction.OnBusinessNameChange("Ade Fashions"))
        viewModel.onAction(WorkshopSetupAction.OnContinueClick)

        val event = viewModel.events.first()
        assertIs<WorkshopSetupEvent.NavigateToHome>(event)
        assertEquals("Ade Fashions", userRepository.lastBusinessName)
    }

    @Test
    fun continueWithRepositoryErrorEmitsShowError() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        userRepository.shouldReturnError = DataError.Network.UNKNOWN
        viewModel = WorkshopSetupViewModel(userRepository, authRepository)

        viewModel.onAction(WorkshopSetupAction.OnBusinessNameChange("Ade"))
        viewModel.onAction(WorkshopSetupAction.OnContinueClick)

        val event = viewModel.events.first()
        assertIs<WorkshopSetupEvent.ShowError>(event)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :composeApp:testDebugUnitTest`
Expected: FAIL — `WorkshopSetupViewModel` does not exist yet

- [ ] **Step 3: Commit failing tests**

```bash
git add composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/onboarding/presentation/workshop/WorkshopSetupViewModelTest.kt
git commit -m "test: add WorkshopSetupViewModel tests (red)"
```

---

### Task 6: ViewModel Implementation — Make Tests Pass

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/onboarding/presentation/workshop/WorkshopSetupViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/AuthModule.kt`

- [ ] **Step 1: Create WorkshopSetupViewModel**

```kotlin
package com.danzucker.stitchpad.feature.onboarding.presentation.workshop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.repository.UserRepository
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WorkshopSetupViewModel(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(WorkshopSetupState())
    val state = _state.asStateFlow()

    private val _events = Channel<WorkshopSetupEvent>()
    val events = _events.receiveAsFlow()

    fun onAction(action: WorkshopSetupAction) {
        when (action) {
            is WorkshopSetupAction.OnBusinessNameChange -> {
                _state.update { it.copy(businessName = action.name) }
            }
            is WorkshopSetupAction.OnPhoneChange -> {
                _state.update { it.copy(phone = action.phone) }
            }
            WorkshopSetupAction.OnContinueClick -> onContinue()
            WorkshopSetupAction.OnSkipClick -> {
                viewModelScope.launch {
                    _events.send(WorkshopSetupEvent.NavigateToHome)
                }
            }
        }
    }

    private fun onContinue() {
        val currentState = _state.value
        val hasData = currentState.businessName.isNotBlank() || currentState.phone.isNotBlank()

        if (!hasData) {
            viewModelScope.launch {
                _events.send(WorkshopSetupEvent.NavigateToHome)
            }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            val user = authRepository.getCurrentUser()
            if (user == null) {
                _state.update { it.copy(isLoading = false) }
                _events.send(WorkshopSetupEvent.NavigateToHome)
                return@launch
            }

            val result = userRepository.createUserProfile(
                userId = user.id,
                businessName = currentState.businessName.ifBlank { null },
                phone = currentState.phone.ifBlank { null }
            )
            _state.update { it.copy(isLoading = false) }

            when (result) {
                is Result.Success -> _events.send(WorkshopSetupEvent.NavigateToHome)
                is Result.Error -> _events.send(
                    WorkshopSetupEvent.ShowError(
                        UiText.DynamicString("Something went wrong. Please try again.")
                    )
                )
            }
        }
    }
}
```

- [ ] **Step 2: Register ViewModel in Koin — add to `authPresentationModule` in `AuthModule.kt`**

Add import:
```kotlin
import com.danzucker.stitchpad.feature.onboarding.presentation.workshop.WorkshopSetupViewModel
```

Add inside `authPresentationModule`:
```kotlin
viewModelOf(::WorkshopSetupViewModel)
```

- [ ] **Step 3: Run tests to verify they pass**

Run: `./gradlew :composeApp:testDebugUnitTest`
Expected: ALL PASS

- [ ] **Step 4: Run detekt**

Run: `./gradlew detekt`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/onboarding/presentation/workshop/WorkshopSetupViewModel.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/AuthModule.kt
git commit -m "feat: implement WorkshopSetupViewModel (green)"
```

---

### Task 7: Navigation — Route + NavGraph Wiring

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/navigation/Routes.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/navigation/NavGraph.kt`

- [ ] **Step 1: Add WorkshopSetupRoute to Routes.kt**

Add after `SignUpRoute`:
```kotlin
@Serializable
data object WorkshopSetupRoute
```

- [ ] **Step 2: Update NavGraph.kt — change SignUp success navigation**

In the `composable<SignUpRoute>` block, change `onNavigateToHome` to navigate to `WorkshopSetupRoute` instead of `HomeRoute`:

```kotlin
composable<SignUpRoute> {
    SignUpRoot(
        onNavigateToLogin = { navController.navigateUp() },
        onNavigateToHome = {
            navController.navigate(WorkshopSetupRoute) {
                popUpTo(LoginRoute) { inclusive = true }
            }
        }
    )
}
```

- [ ] **Step 3: Add WorkshopSetup composable destination in NavGraph.kt**

Add this import at the top:
```kotlin
import com.danzucker.stitchpad.feature.onboarding.presentation.workshop.WorkshopSetupRoot
```

Add this composable block between `SignUpRoute` and `HomeRoute`:
```kotlin
composable<WorkshopSetupRoute> {
    WorkshopSetupRoot(
        onNavigateToHome = {
            navController.navigate(HomeRoute) {
                popUpTo(WorkshopSetupRoute) { inclusive = true }
            }
        }
    )
}
```

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/navigation/Routes.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/navigation/NavGraph.kt
git commit -m "feat: wire WorkshopSetupRoute into navigation graph"
```

---

### Task 8: UI — WorkshopSetupScreen Composable

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/onboarding/presentation/workshop/WorkshopSetupScreen.kt`

- [ ] **Step 1: Create Root + Screen composables**

```kotlin
package com.danzucker.stitchpad.feature.onboarding.presentation.workshop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.onboarding.presentation.components.StitchPadLogo
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import com.danzucker.stitchpad.util.ObserveAsEvents
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun WorkshopSetupRoot(
    onNavigateToHome: () -> Unit,
    viewModel: WorkshopSetupViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            WorkshopSetupEvent.NavigateToHome -> onNavigateToHome()
            is WorkshopSetupEvent.ShowError -> {
                val message = when (val text = event.message) {
                    is UiText.DynamicString -> text.value
                    is UiText.StringResourceText -> text.id.toString()
                }
                scope.launch { snackbarHostState.showSnackbar(message) }
            }
        }
    }

    WorkshopSetupScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction
    )
}

@Composable
fun WorkshopSetupScreen(
    state: WorkshopSetupState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onAction: (WorkshopSetupAction) -> Unit
) {
    val inputColors = OutlinedTextFieldDefaults.colors(
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        focusedContainerColor = MaterialTheme.colorScheme.surface
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Saffron header with logo
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(DesignTokens.primary500),
                contentAlignment = Alignment.Center
            ) {
                StitchPadLogo(size = 64.dp)
            }

            // White card overlapping header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-24).dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = DesignTokens.space4, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Set up your workshop",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Personalise StitchPad for your brand",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(28.dp))

                // Business name field
                LabeledField(label = "Business name") {
                    OutlinedTextField(
                        value = state.businessName,
                        onValueChange = { onAction(WorkshopSetupAction.OnBusinessNameChange(it)) },
                        placeholder = { Text("e.g. Ade Fashions") },
                        supportingText = {
                            Text(
                                text = "Shown on your dashboard. You can change this later.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = inputColors,
                        shape = RoundedCornerShape(DesignTokens.radiusMd),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(DesignTokens.space3))

                // Phone number field
                LabeledField(label = "Phone number") {
                    OutlinedTextField(
                        value = state.phone,
                        onValueChange = { onAction(WorkshopSetupAction.OnPhoneChange(it)) },
                        placeholder = { Text("+234 801 234 5678") },
                        supportingText = {
                            Text(
                                text = "For your profile, not shared with customers.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = inputColors,
                        shape = RoundedCornerShape(DesignTokens.radiusMd),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Phone,
                            imeAction = ImeAction.Done
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(28.dp))

                // Continue button
                Button(
                    onClick = { onAction(WorkshopSetupAction.OnContinueClick) },
                    enabled = !state.isLoading,
                    shape = RoundedCornerShape(DesignTokens.radiusMd),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Continue")
                    }
                }
                Spacer(modifier = Modifier.height(DesignTokens.space4))

                // Skip link
                Text(
                    text = "Skip for now",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clickable { onAction(WorkshopSetupAction.OnSkipClick) }
                        .padding(DesignTokens.space2)
                )
                Spacer(modifier = Modifier.height(DesignTokens.space10))
            }
        }
    }
}

@Composable
private fun LabeledField(
    label: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        content()
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun WorkshopSetupScreenPreview() {
    StitchPadTheme {
        WorkshopSetupScreen(state = WorkshopSetupState(), onAction = {})
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun WorkshopSetupScreenFilledPreview() {
    StitchPadTheme {
        WorkshopSetupScreen(
            state = WorkshopSetupState(
                businessName = "Ade Fashions",
                phone = "+2348012345678"
            ),
            onAction = {}
        )
    }
}
```

- [ ] **Step 2: Run detekt**

Run: `./gradlew detekt`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run all tests**

Run: `./gradlew :composeApp:testDebugUnitTest`
Expected: ALL PASS

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/onboarding/presentation/workshop/WorkshopSetupScreen.kt
git commit -m "feat: add WorkshopSetupScreen UI composable"
```

---

### Task 9: Final Verification

- [ ] **Step 1: Run full test suite**

Run: `./gradlew :composeApp:testDebugUnitTest`
Expected: ALL PASS (including all existing + new WorkshopSetupViewModel tests)

- [ ] **Step 2: Run detekt**

Run: `./gradlew detekt`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Build Android APK**

Run: `./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Manual smoke test**

1. Install debug APK on emulator/device
2. Create a new account (sign up)
3. Verify "Set up your workshop" screen appears after sign-up
4. Test: fill both fields + Continue → lands on Home
5. Sign out, create another account
6. Test: tap Skip → lands on Home
7. Sign out, log back in with first account → goes straight to Home (no workshop screen)

package com.danzucker.stitchpad.feature.customer.presentation.form

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danzucker.stitchpad.feature.freemium.presentation.cap.CustomerCapReachedSheet
import com.danzucker.stitchpad.ui.components.StitchPadButton
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import com.danzucker.stitchpad.util.ObserveAsEvents
import com.danzucker.stitchpad.util.dismissKeyboardOnScroll
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.customer_form_add_measurements_helper
import stitchpad.composeapp.generated.resources.customer_form_add_measurements_next
import stitchpad.composeapp.generated.resources.customer_form_address_label
import stitchpad.composeapp.generated.resources.customer_form_address_placeholder
import stitchpad.composeapp.generated.resources.customer_form_email_label
import stitchpad.composeapp.generated.resources.customer_form_email_placeholder
import stitchpad.composeapp.generated.resources.customer_form_name_label
import stitchpad.composeapp.generated.resources.customer_form_name_placeholder
import stitchpad.composeapp.generated.resources.customer_form_phone_label
import stitchpad.composeapp.generated.resources.customer_form_phone_placeholder
import stitchpad.composeapp.generated.resources.customer_form_save_and_measure_button
import stitchpad.composeapp.generated.resources.customer_form_save_button
import stitchpad.composeapp.generated.resources.customer_form_title_add
import stitchpad.composeapp.generated.resources.customer_form_title_edit

@Composable
fun CustomerFormRoot(
    onNavigateBack: () -> Unit,
    onNavigateToUpgrade: () -> Unit,
    onNavigateToCustomerWithMeasurement: (customerId: String) -> Unit,
) {
    val viewModel: CustomerFormViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // Sheet visibility is held local to the composable rather than in
    // ViewModel state — the sheet is purely a presentation concern that
    // doesn't need to survive process death (the underlying CAP_REACHED
    // result will fire again on retry).
    var capSheet by remember { mutableStateOf<CustomerFormEvent.ShowCapReachedSheet?>(null) }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            CustomerFormEvent.NavigateBack -> onNavigateBack()
            is CustomerFormEvent.ShowCapReachedSheet -> capSheet = event
            is CustomerFormEvent.NavigateToNewCustomerMeasurement ->
                onNavigateToCustomerWithMeasurement(event.customerId)
        }
    }

    val errorMessage = state.errorMessage?.asString()
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            viewModel.onAction(CustomerFormAction.OnErrorDismiss)
        }
    }

    CustomerFormScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction
    )

    val pending = capSheet
    if (pending != null) {
        CustomerCapReachedSheet(
            activeCount = pending.activeCount,
            customerCap = pending.customerCap,
            onUpgradeClick = {
                capSheet = null
                onNavigateToUpgrade()
            },
            onSwapClick = {
                // "Swap a customer" returns the tailor to the customer list,
                // where every locked row exposes a swap CTA via SwapSheet.
                // From there they can pick which active customer to demote.
                capSheet = null
                onNavigateBack()
            },
            onDismiss = { capSheet = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerFormScreen(
    state: CustomerFormState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onAction: (CustomerFormAction) -> Unit
) {
    val title = if (state.isEditMode) {
        stringResource(Res.string.customer_form_title_edit)
    } else {
        stringResource(Res.string.customer_form_title_add)
    }
    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onAction(CustomerFormAction.OnNavigateBack) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            verticalArrangement = Arrangement.spacedBy(DesignTokens.space4),
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                }
                .dismissKeyboardOnScroll()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(horizontal = DesignTokens.space4, vertical = DesignTokens.space4)
        ) {
            StitchPadField(
                value = state.name,
                onValueChange = { if (it.length <= 50) onAction(CustomerFormAction.OnNameChange(it)) },
                onBlur = { onAction(CustomerFormAction.OnNameBlur) },
                label = stringResource(Res.string.customer_form_name_label),
                placeholder = stringResource(Res.string.customer_form_name_placeholder),
                isError = state.nameError != null,
                errorText = state.nameError?.asString(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                )
            )

            StitchPadField(
                value = state.phone,
                onValueChange = { newValue ->
                    val filtered = newValue.filter { c -> c.isDigit() || c == '+' || c == ' ' || c == '-' }
                    if (filtered.length <= 20) onAction(CustomerFormAction.OnPhoneChange(filtered))
                },
                onBlur = { onAction(CustomerFormAction.OnPhoneBlur) },
                label = stringResource(Res.string.customer_form_phone_label),
                placeholder = stringResource(Res.string.customer_form_phone_placeholder),
                isError = state.phoneError != null,
                errorText = state.phoneError?.asString(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Next
                )
            )

            StitchPadField(
                value = state.email,
                onValueChange = { if (it.length <= 100) onAction(CustomerFormAction.OnEmailChange(it)) },
                onBlur = { onAction(CustomerFormAction.OnEmailBlur) },
                label = stringResource(Res.string.customer_form_email_label),
                placeholder = stringResource(Res.string.customer_form_email_placeholder),
                isError = state.emailError != null,
                errorText = state.emailError?.asString(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                )
            )

            StitchPadField(
                value = state.address,
                onValueChange = { if (it.length <= 150) onAction(CustomerFormAction.OnAddressChange(it)) },
                label = stringResource(Res.string.customer_form_address_label),
                placeholder = stringResource(Res.string.customer_form_address_placeholder),
                singleLine = false,
                minLines = 2,
                maxLines = 3,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    keyboardType = KeyboardType.Text,
                    // Address is now the last text input on both create + edit
                    // (delivery selector + notes field removed in PTSP-5).
                    imeAction = ImeAction.Done
                )
            )

            Spacer(Modifier.height(DesignTokens.space2))

            if (!state.isEditMode) {
                MeasurementsToggleCard(
                    checked = state.addMeasurementsNext,
                    onToggle = { onAction(CustomerFormAction.OnToggleAddMeasurementsNext) },
                    enabled = !state.isLoading,
                )
            }

            val showMeasureCta = !state.isEditMode && state.addMeasurementsNext
            StitchPadButton(
                text = if (showMeasureCta) {
                    stringResource(Res.string.customer_form_save_and_measure_button)
                } else {
                    stringResource(Res.string.customer_form_save_button)
                },
                onClick = { onAction(CustomerFormAction.OnSaveClick) },
                isLoading = state.isLoading,
                leadingIcon = if (showMeasureCta) {
                    Icons.AutoMirrored.Filled.ArrowForward
                } else {
                    Icons.Default.Check
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StitchPadField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    isError: Boolean = false,
    errorText: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onBlur: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val labelColor = if (isError) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        errorBorderColor = MaterialTheme.colorScheme.error,
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
    )
    val interactionSource = remember { MutableInteractionSource() }
    var hasFocused by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = labelColor,
            modifier = Modifier.padding(bottom = DesignTokens.space1)
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = keyboardOptions,
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        hasFocused = true
                    } else if (hasFocused) {
                        onBlur?.invoke()
                    }
                },
            decorationBox = { innerTextField ->
                OutlinedTextFieldDefaults.DecorationBox(
                    value = value,
                    innerTextField = innerTextField,
                    enabled = true,
                    singleLine = singleLine,
                    visualTransformation = VisualTransformation.None,
                    interactionSource = interactionSource,
                    isError = isError,
                    placeholder = {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    colors = colors,
                    container = {
                        OutlinedTextFieldDefaults.ContainerBox(
                            enabled = true,
                            isError = isError,
                            interactionSource = interactionSource,
                            colors = colors,
                            shape = RoundedCornerShape(DesignTokens.radiusMd),
                            focusedBorderThickness = 1.dp,
                            unfocusedBorderThickness = 1.dp
                        )
                    }
                )
            }
        )
        if (isError && errorText != null) {
            Text(
                text = errorText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = DesignTokens.space1, start = DesignTokens.space1)
            )
        }
    }
}

@Composable
private fun MeasurementsToggleCard(
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    // Border emphasises when checked (the default + recommended path) so the card
    // reads as an active, intentional step rather than ignorable fine print.
    val borderColor = if (checked) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DesignTokens.radiusLg))
            .border(
                width = if (checked) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(DesignTokens.radiusLg),
            )
            .toggleable(
                value = checked,
                enabled = enabled,
                onValueChange = { onToggle() },
                role = Role.Checkbox,
            )
            .padding(DesignTokens.space3),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(DesignTokens.radiusMd))
                .background(MaterialTheme.colorScheme.primaryContainer),
        ) {
            Icon(
                imageVector = Icons.Default.Straighten,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(DesignTokens.iconList),
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(DesignTokens.space1),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = stringResource(Res.string.customer_form_add_measurements_next),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(Res.string.customer_form_add_measurements_helper),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun CustomerFormScreenAddPreview() {
    StitchPadTheme {
        CustomerFormScreen(state = CustomerFormState(), onAction = {})
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun CustomerFormScreenEditPreview() {
    StitchPadTheme {
        CustomerFormScreen(
            state = CustomerFormState(
                isEditMode = true,
                name = "Amina Bello",
                phone = "+234 801 234 5678",
                email = "amina@gmail.com",
                address = "15 Adeola Odeku St, Lagos"
            ),
            onAction = {}
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun CustomerFormScreenAddMeasurementsCheckedPreview() {
    StitchPadTheme {
        CustomerFormScreen(
            state = CustomerFormState(
                name = "Amina Bello",
                phone = "+234 801 234 5678",
                addMeasurementsNext = true,
            ),
            onAction = {}
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun CustomerFormScreenAddMeasurementsUncheckedPreview() {
    StitchPadTheme {
        CustomerFormScreen(
            state = CustomerFormState(
                name = "Amina Bello",
                phone = "+234 801 234 5678",
                addMeasurementsNext = false,
            ),
            onAction = {}
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun CustomerFormScreenAddMeasurementsCheckedDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        CustomerFormScreen(
            state = CustomerFormState(
                name = "Amina Bello",
                phone = "+234 801 234 5678",
                addMeasurementsNext = true,
            ),
            onAction = {}
        )
    }
}

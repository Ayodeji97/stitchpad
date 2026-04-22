package com.danzucker.stitchpad.feature.customer.presentation.form

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danzucker.stitchpad.core.domain.model.DeliveryPreference
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import com.danzucker.stitchpad.util.ObserveAsEvents
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.customer_form_address_label
import stitchpad.composeapp.generated.resources.customer_form_address_placeholder
import stitchpad.composeapp.generated.resources.customer_form_delivery_label
import stitchpad.composeapp.generated.resources.customer_form_email_label
import stitchpad.composeapp.generated.resources.customer_form_email_placeholder
import stitchpad.composeapp.generated.resources.customer_form_name_label
import stitchpad.composeapp.generated.resources.customer_form_name_placeholder
import stitchpad.composeapp.generated.resources.customer_form_notes_label
import stitchpad.composeapp.generated.resources.customer_form_notes_placeholder
import stitchpad.composeapp.generated.resources.customer_form_phone_label
import stitchpad.composeapp.generated.resources.customer_form_phone_placeholder
import stitchpad.composeapp.generated.resources.customer_form_save_button
import stitchpad.composeapp.generated.resources.customer_form_title_add
import stitchpad.composeapp.generated.resources.customer_form_title_edit
import stitchpad.composeapp.generated.resources.delivery_delivery
import stitchpad.composeapp.generated.resources.delivery_either
import stitchpad.composeapp.generated.resources.delivery_pickup

@Composable
fun CustomerFormRoot(onNavigateBack: () -> Unit) {
    val viewModel: CustomerFormViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            CustomerFormEvent.NavigateBack -> onNavigateBack()
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
                    imeAction = ImeAction.Next
                )
            )

            DeliveryPreferenceSelector(
                selected = state.deliveryPreference,
                label = stringResource(Res.string.customer_form_delivery_label),
                onSelected = { onAction(CustomerFormAction.OnDeliveryPreferenceChange(it)) }
            )

            StitchPadField(
                value = state.notes,
                onValueChange = { if (it.length <= 300) onAction(CustomerFormAction.OnNotesChange(it)) },
                label = stringResource(Res.string.customer_form_notes_label),
                placeholder = stringResource(Res.string.customer_form_notes_placeholder),
                singleLine = false,
                minLines = 3,
                maxLines = 5,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    keyboardType = KeyboardType.Text
                )
            )

            Spacer(Modifier.height(DesignTokens.space2))

            SaveButton(
                isLoading = state.isLoading,
                label = stringResource(Res.string.customer_form_save_button),
                onClick = { onAction(CustomerFormAction.OnSaveClick) }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeliveryPreferenceSelector(
    selected: DeliveryPreference,
    label: String,
    onSelected: (DeliveryPreference) -> Unit
) {
    val pickupLabel = stringResource(Res.string.delivery_pickup)
    val deliveryLabel = stringResource(Res.string.delivery_delivery)
    val eitherLabel = stringResource(Res.string.delivery_either)

    fun DeliveryPreference.displayLabel() = when (this) {
        DeliveryPreference.PICKUP -> pickupLabel
        DeliveryPreference.DELIVERY -> deliveryLabel
        DeliveryPreference.EITHER -> eitherLabel
    }

    Column {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = DesignTokens.space1)
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            DeliveryPreference.entries.forEachIndexed { index, preference ->
                SegmentedButton(
                    selected = selected == preference,
                    onClick = { onSelected(preference) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = DeliveryPreference.entries.size
                    ),
                    label = {
                        Text(
                            text = preference.displayLabel(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected == preference) {
                                FontWeight.SemiBold
                            } else {
                                FontWeight.Normal
                            }
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun SaveButton(
    isLoading: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = !isLoading,
        shape = RoundedCornerShape(DesignTokens.radiusMd),
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(22.dp)
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
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

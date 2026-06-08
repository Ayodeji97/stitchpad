package com.danzucker.stitchpad.feature.onboarding.presentation.workshop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import com.danzucker.stitchpad.core.debug.isDebugBuild
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.core.sharing.WhatsAppLauncher
import com.danzucker.stitchpad.feature.auth.presentation.components.AuthCard
import com.danzucker.stitchpad.feature.auth.presentation.components.AuthHero
import com.danzucker.stitchpad.feature.auth.presentation.components.AuthTextField
import com.danzucker.stitchpad.feature.branding.presentation.LogoUploadState
import com.danzucker.stitchpad.ui.components.LoadingDots
import com.danzucker.stitchpad.ui.components.StitchPadButton
import com.danzucker.stitchpad.ui.components.WhatsAppConfirmRow
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.LocalStitchPadColors
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import com.danzucker.stitchpad.util.ObserveAsEvents
import com.danzucker.stitchpad.util.clearFocusOnTap
import com.preat.peekaboo.image.picker.SelectionMode
import com.preat.peekaboo.image.picker.rememberImagePickerLauncher
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.bank_details_account_name_label
import stitchpad.composeapp.generated.resources.bank_details_account_name_placeholder
import stitchpad.composeapp.generated.resources.bank_details_account_number_label
import stitchpad.composeapp.generated.resources.bank_details_account_number_placeholder
import stitchpad.composeapp.generated.resources.bank_details_bank_label
import stitchpad.composeapp.generated.resources.bank_details_bank_placeholder
import stitchpad.composeapp.generated.resources.bank_details_optional
import stitchpad.composeapp.generated.resources.bank_details_section_subtitle
import stitchpad.composeapp.generated.resources.bank_details_section_title
import stitchpad.composeapp.generated.resources.whatsapp_confirm_message
import stitchpad.composeapp.generated.resources.workshop_business_name_helper
import stitchpad.composeapp.generated.resources.workshop_business_name_label
import stitchpad.composeapp.generated.resources.workshop_business_name_placeholder
import stitchpad.composeapp.generated.resources.workshop_continue_button
import stitchpad.composeapp.generated.resources.workshop_logo_finishing
import stitchpad.composeapp.generated.resources.workshop_logo_label
import stitchpad.composeapp.generated.resources.workshop_logo_optional
import stitchpad.composeapp.generated.resources.workshop_logo_retry
import stitchpad.composeapp.generated.resources.workshop_logo_upload_failed
import stitchpad.composeapp.generated.resources.workshop_logo_upload_sub
import stitchpad.composeapp.generated.resources.workshop_logo_upload_title
import stitchpad.composeapp.generated.resources.workshop_skip
import stitchpad.composeapp.generated.resources.workshop_subtitle
import stitchpad.composeapp.generated.resources.workshop_title
import stitchpad.composeapp.generated.resources.workshop_whatsapp_helper
import stitchpad.composeapp.generated.resources.workshop_whatsapp_label
import stitchpad.composeapp.generated.resources.workshop_whatsapp_optional
import stitchpad.composeapp.generated.resources.workshop_whatsapp_placeholder

@Composable
fun WorkshopSetupRoot(
    onNavigateToHome: () -> Unit,
    onNavigateToLogin: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    viewModel: WorkshopSetupViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val whatsAppLauncher: WhatsAppLauncher = koinInject()
    val showMessage: (UiText) -> Unit = { text ->
        scope.launch {
            val message = when (text) {
                is UiText.DynamicString -> text.value
                is UiText.StringResourceText -> org.jetbrains.compose.resources.getString(text.id)
            }
            snackbarHostState.showSnackbar(message)
        }
    }
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            WorkshopSetupEvent.NavigateToHome -> onNavigateToHome()
            WorkshopSetupEvent.NavigateToLogin -> onNavigateToLogin()
            is WorkshopSetupEvent.ShowError -> showMessage(event.message)
            is WorkshopSetupEvent.ShowSnackbar -> showMessage(event.message)
            is WorkshopSetupEvent.LaunchWhatsAppConfirm -> scope.launch {
                val message = getString(Res.string.whatsapp_confirm_message, event.code)
                whatsAppLauncher.launch(event.phoneE164, message)
            }
        }
    }

    val pickerScope = rememberCoroutineScope()
    val logoPicker = rememberImagePickerLauncher(
        selectionMode = SelectionMode.Single,
        scope = pickerScope,
        onResult = { byteArrays ->
            byteArrays.firstOrNull()?.let {
                viewModel.onAction(WorkshopSetupAction.OnLogoPicked(it))
            }
        }
    )

    WorkshopSetupScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction,
        onLaunchPicker = { logoPicker.launch() },
    )
}

@Composable
fun WorkshopSetupScreen(
    state: WorkshopSetupState,
    snackbarHostState: SnackbarHostState,
    onAction: (WorkshopSetupAction) -> Unit,
    onLaunchPicker: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DesignTokens.neutral900)
            .clearFocusOnTap(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AuthHero()

            AuthCard {
                // 1. Title
                Text(
                    text = stringResource(Res.string.workshop_title),
                    style = TextStyle(
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFF5F2ED),
                        textAlign = TextAlign.Center,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )

                // 2. Subtitle
                Text(
                    text = stringResource(Res.string.workshop_subtitle),
                    style = TextStyle(
                        fontSize = 14.sp,
                        color = Color(0xFFA8A49D),
                        textAlign = TextAlign.Center,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )

                // 3. Business name field
                AuthTextField(
                    label = stringResource(Res.string.workshop_business_name_label),
                    value = state.businessName,
                    onValueChange = { onAction(WorkshopSetupAction.OnBusinessNameChange(it)) },
                    leadingIcon = Icons.Outlined.Storefront,
                    placeholder = stringResource(Res.string.workshop_business_name_placeholder),
                    helperText = stringResource(Res.string.workshop_business_name_helper),
                    errorText = state.businessNameError?.let { stringResource(it) },
                    onFocusLost = { onAction(WorkshopSetupAction.OnBusinessNameBlur) },
                    imeAction = ImeAction.Next,
                )

                // 4. WhatsApp number — bespoke composition
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // 4a. Custom label row: green WhatsApp circle icon + label + Required chip
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF25D366)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Chat,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp),
                            )
                        }
                        Text(
                            text = stringResource(Res.string.workshop_whatsapp_label),
                            style = TextStyle(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFF5F2ED),
                            ),
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = stringResource(Res.string.workshop_whatsapp_optional),
                                style = TextStyle(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    letterSpacing = 0.8.sp,
                                ),
                            )
                        }
                    }

                    // 4b. Country picker (+234 fixed) + number input row
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // +234 country block — non-interactive in V1 (Nigeria-only)
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF2A2825))
                                .border(1.5.dp, Color(0xFF3A3731), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            // Nigerian flag — 3-band rectangle (green-white-green)
                            Box(
                                modifier = Modifier
                                    .size(width = 24.dp, height = 18.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                            ) {
                                Row(modifier = Modifier.fillMaxSize()) {
                                    Box(Modifier.weight(1f).fillMaxHeight().background(Color(0xFF008751)))
                                    Box(Modifier.weight(1f).fillMaxHeight().background(Color.White))
                                    Box(Modifier.weight(1f).fillMaxHeight().background(Color(0xFF008751)))
                                }
                            }
                            Text(
                                text = "+234",
                                style = TextStyle(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFF5F2ED),
                                ),
                            )
                        }
                        // Number input — AuthTextField with empty label (skips label render)
                        AuthTextField(
                            label = "",
                            value = state.whatsappNumber,
                            onValueChange = { onAction(WorkshopSetupAction.OnWhatsAppNumberChange(it)) },
                            leadingIcon = Icons.Outlined.Phone,
                            keyboardType = KeyboardType.Phone,
                            imeAction = ImeAction.Done,
                            placeholder = stringResource(Res.string.workshop_whatsapp_placeholder),
                            errorText = state.whatsappError?.let { stringResource(it) },
                            onFocusLost = { onAction(WorkshopSetupAction.OnWhatsAppNumberBlur) },
                            modifier = Modifier.weight(1f),
                        )
                    }

                    // 4c. Helper microcopy — green WhatsApp icon + attestation text
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF25D366)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Chat,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(8.dp),
                            )
                        }
                        Text(
                            text = stringResource(Res.string.workshop_whatsapp_helper),
                            style = TextStyle(
                                fontSize = 12.5.sp,
                                color = Color(0xFFA8A49D),
                                lineHeight = 18.sp,
                            ),
                        )
                    }

                    // 4d. WhatsApp number confirm row
                    WhatsAppConfirmRow(
                        state = state.whatsappConfirm,
                        numberValid = state.whatsappError == null && state.whatsappNumber.isNotBlank(),
                        onConfirmClick = { onAction(WorkshopSetupAction.OnConfirmWhatsAppClick) },
                        onCodeChange = { onAction(WorkshopSetupAction.OnConfirmCodeChange(it)) },
                        onDismiss = { onAction(WorkshopSetupAction.OnDismissConfirm) },
                        debugCode = state.whatsappConfirm.code.takeIf { isDebugBuild },
                    )
                }

                // 4e. Payment details (PTSP-16) — collapsed group between WhatsApp and Logo.
                WorkshopPaymentDetailsSection(state = state, onAction = onAction)

                // 5. Logo upload tile
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stringResource(Res.string.workshop_logo_label),
                            style = TextStyle(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFF5F2ED)
                            ),
                        )
                        Text(
                            text = stringResource(Res.string.workshop_logo_optional),
                            style = TextStyle(fontSize = 13.sp, color = Color(0xFFA8A49D)),
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(108.dp)
                            .border(1.5.dp, Color(0xFF3A3731), RoundedCornerShape(10.dp))
                            .background(Color(0xFF1F1D1A), RoundedCornerShape(10.dp))
                            .clickable {
                                when (state.logo) {
                                    is LogoUploadState.Failed ->
                                        onAction(WorkshopSetupAction.OnLogoRetry)
                                    LogoUploadState.Empty,
                                    is LogoUploadState.Uploaded -> onLaunchPicker()
                                    is LogoUploadState.Uploading -> Unit
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        WorkshopLogoTileContent(state.logo)
                    }

                    if (state.isAwaitingLogo) {
                        Text(
                            text = stringResource(Res.string.workshop_logo_finishing),
                            style = TextStyle(fontSize = 12.sp, color = Color(0xFFA8A49D)),
                        )
                    }
                }

                // 6. Continue button
                StitchPadButton(
                    text = stringResource(Res.string.workshop_continue_button),
                    onClick = { onAction(WorkshopSetupAction.OnContinueClick) },
                    enabled = !state.isAwaitingLogo && state.businessName.isNotBlank(),
                    isLoading = state.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                )

                // 7. Skip button
                TextButton(
                    onClick = { onAction(WorkshopSetupAction.OnSkipClick) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(Res.string.workshop_skip),
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = LocalStitchPadColors.current.brandAccent,
                        ),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun WorkshopLogoTileContent(
    logo: LogoUploadState,
) {
    when (logo) {
        LogoUploadState.Empty -> {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(LocalStitchPadColors.current.brandAccent.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PhotoCamera,
                        contentDescription = null,
                        tint = LocalStitchPadColors.current.brandAccent,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    text = stringResource(Res.string.workshop_logo_upload_title),
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFF5F2ED)),
                )
                Text(
                    text = stringResource(Res.string.workshop_logo_upload_sub),
                    style = TextStyle(fontSize = 11.5.sp, color = Color(0xFFA8A49D)),
                )
            }
        }
        is LogoUploadState.Uploading -> {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                SubcomposeAsyncImage(
                    model = logo.previewBytes,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                    // Dim overlay + centered dots sit on top; Coil's loading slot stays empty so we don't show two spinners.
                    loading = { Box(Modifier.fillMaxSize()) },
                )
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) { LoadingDots(color = Color.White) }
            }
        }
        is LogoUploadState.Uploaded -> {
            SubcomposeAsyncImage(
                model = logo.url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                loading = { LoadingDots() },
            )
        }
        is LogoUploadState.Failed -> {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(Res.string.workshop_logo_upload_failed),
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFF5F2ED)),
                )
                Text(
                    text = stringResource(Res.string.workshop_logo_retry),
                    style = TextStyle(fontSize = 11.5.sp, color = LocalStitchPadColors.current.brandAccent),
                )
            }
        }
    }
}

@Composable
private fun WorkshopPaymentDetailsSection(
    state: WorkshopSetupState,
    onAction: (WorkshopSetupAction) -> Unit,
) {
    val expanded = state.isPaymentDetailsExpanded
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.5.dp, Color(0xFF3A3731), RoundedCornerShape(12.dp))
            .background(Color(0xFF1F1D1A)),
    ) {
        // Header row — tap to expand/collapse
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onAction(WorkshopSetupAction.OnTogglePaymentDetails) }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(LocalStitchPadColors.current.brandAccent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.AccountBalance,
                    contentDescription = null,
                    tint = LocalStitchPadColors.current.brandAccent,
                    modifier = Modifier.size(16.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.bank_details_section_title),
                        style = TextStyle(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFF5F2ED),
                        ),
                    )
                    Text(
                        text = stringResource(Res.string.bank_details_optional),
                        style = TextStyle(fontSize = 13.sp, color = Color(0xFFA8A49D)),
                    )
                }
                Text(
                    text = stringResource(Res.string.bank_details_section_subtitle),
                    style = TextStyle(
                        fontSize = 11.5.sp,
                        color = Color(0xFFA8A49D),
                        lineHeight = 16.sp,
                    ),
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = Color(0xFFA8A49D),
                modifier = Modifier.size(20.dp),
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AuthTextField(
                    label = stringResource(Res.string.bank_details_bank_label),
                    value = state.bankName,
                    onValueChange = { onAction(WorkshopSetupAction.OnBankNameChange(it)) },
                    leadingIcon = Icons.Outlined.AccountBalance,
                    placeholder = stringResource(Res.string.bank_details_bank_placeholder),
                    errorText = state.bankNameError?.let { stringResource(it) },
                    onFocusLost = { onAction(WorkshopSetupAction.OnBankNameBlur) },
                    imeAction = ImeAction.Next,
                )
                AuthTextField(
                    label = stringResource(Res.string.bank_details_account_name_label),
                    value = state.bankAccountName,
                    onValueChange = { onAction(WorkshopSetupAction.OnBankAccountNameChange(it)) },
                    leadingIcon = Icons.Outlined.Person,
                    placeholder = stringResource(Res.string.bank_details_account_name_placeholder),
                    errorText = state.bankAccountNameError?.let { stringResource(it) },
                    onFocusLost = { onAction(WorkshopSetupAction.OnBankAccountNameBlur) },
                    imeAction = ImeAction.Next,
                )
                AuthTextField(
                    label = stringResource(Res.string.bank_details_account_number_label),
                    value = state.bankAccountNumber,
                    onValueChange = { onAction(WorkshopSetupAction.OnBankAccountNumberChange(it)) },
                    leadingIcon = Icons.Outlined.Badge,
                    placeholder = stringResource(Res.string.bank_details_account_number_placeholder),
                    errorText = state.bankAccountNumberError?.let { stringResource(it) },
                    onFocusLost = { onAction(WorkshopSetupAction.OnBankAccountNumberBlur) },
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                )
            }
        }
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun WorkshopSetupScreenPreview() {
    StitchPadTheme {
        WorkshopSetupScreen(
            state = WorkshopSetupState(),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
            onLaunchPicker = {},
        )
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
                whatsappNumber = "0803 123 4567",
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
            onLaunchPicker = {},
        )
    }
}

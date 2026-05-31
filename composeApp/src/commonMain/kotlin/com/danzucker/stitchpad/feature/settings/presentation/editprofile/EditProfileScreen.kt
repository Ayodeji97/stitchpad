package com.danzucker.stitchpad.feature.settings.presentation.editprofile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.danzucker.stitchpad.core.debug.isDebugBuild
import com.danzucker.stitchpad.feature.branding.presentation.LogoUploadState
import com.danzucker.stitchpad.feature.settings.presentation.components.AvatarGradients
import com.danzucker.stitchpad.feature.settings.presentation.components.avatarBrush
import com.danzucker.stitchpad.ui.components.BrandLogo
import com.danzucker.stitchpad.ui.components.LoadingDots
import com.danzucker.stitchpad.ui.components.WhatsAppConfirmRow
import com.danzucker.stitchpad.ui.text.platformTextStyleNoFontPadding
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import com.danzucker.stitchpad.util.clearFocusOnTap
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.bank_details_account_name_label
import stitchpad.composeapp.generated.resources.bank_details_account_name_placeholder
import stitchpad.composeapp.generated.resources.bank_details_account_number_label
import stitchpad.composeapp.generated.resources.bank_details_account_number_placeholder
import stitchpad.composeapp.generated.resources.bank_details_bank_label
import stitchpad.composeapp.generated.resources.bank_details_bank_placeholder
import stitchpad.composeapp.generated.resources.bank_details_section_subtitle
import stitchpad.composeapp.generated.resources.bank_details_section_title
import stitchpad.composeapp.generated.resources.common_cancel
import stitchpad.composeapp.generated.resources.edit_profile_avatar_hint
import stitchpad.composeapp.generated.resources.edit_profile_back_cd
import stitchpad.composeapp.generated.resources.edit_profile_color_cd
import stitchpad.composeapp.generated.resources.edit_profile_helper_business_name
import stitchpad.composeapp.generated.resources.edit_profile_helper_email_readonly
import stitchpad.composeapp.generated.resources.edit_profile_helper_phone
import stitchpad.composeapp.generated.resources.edit_profile_helper_whatsapp
import stitchpad.composeapp.generated.resources.edit_profile_helper_your_name
import stitchpad.composeapp.generated.resources.edit_profile_label_business_name
import stitchpad.composeapp.generated.resources.edit_profile_label_email
import stitchpad.composeapp.generated.resources.edit_profile_label_phone
import stitchpad.composeapp.generated.resources.edit_profile_label_whatsapp
import stitchpad.composeapp.generated.resources.edit_profile_label_your_name
import stitchpad.composeapp.generated.resources.edit_profile_logo_change
import stitchpad.composeapp.generated.resources.edit_profile_logo_remove
import stitchpad.composeapp.generated.resources.edit_profile_logo_remove_confirm_body
import stitchpad.composeapp.generated.resources.edit_profile_logo_remove_confirm_cta
import stitchpad.composeapp.generated.resources.edit_profile_logo_remove_confirm_title
import stitchpad.composeapp.generated.resources.edit_profile_logo_title
import stitchpad.composeapp.generated.resources.edit_profile_save
import stitchpad.composeapp.generated.resources.edit_profile_title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    state: EditProfileState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onLaunchLogoPicker: () -> Unit = {},
    onAction: (EditProfileAction) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.edit_profile_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onAction(EditProfileAction.OnBackClick) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.edit_profile_back_cd),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding(),
            ) {
                Button(
                    onClick = { onAction(EditProfileAction.OnSaveClick) },
                    enabled = state.canSave,
                    shape = RoundedCornerShape(DesignTokens.radiusLg),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = DesignTokens.space4,
                            vertical = DesignTokens.space3,
                        )
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp),
                        )
                    } else {
                        Text(
                            text = stringResource(Res.string.edit_profile_save),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        },
    ) { padding ->
        if (state.showRemoveLogoDialog) {
            AlertDialog(
                onDismissRequest = { onAction(EditProfileAction.OnLogoRemoveDismiss) },
                title = { Text(stringResource(Res.string.edit_profile_logo_remove_confirm_title)) },
                text = { Text(stringResource(Res.string.edit_profile_logo_remove_confirm_body)) },
                confirmButton = {
                    TextButton(onClick = { onAction(EditProfileAction.OnLogoRemoveConfirm) }) {
                        Text(
                            text = stringResource(Res.string.edit_profile_logo_remove_confirm_cta),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { onAction(EditProfileAction.OnLogoRemoveDismiss) }) {
                        Text(stringResource(Res.string.common_cancel))
                    }
                },
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .clearFocusOnTap()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DesignTokens.space4),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(DesignTokens.space4))
            AvatarBlock(
                businessName = state.businessName.ifBlank { state.displayName.ifBlank { state.email } },
                avatarColorIndex = state.avatarColorIndex,
            )
            Spacer(Modifier.height(DesignTokens.space2))
            Text(
                text = stringResource(Res.string.edit_profile_avatar_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(DesignTokens.space3))
            ColorPickerRow(
                selectedIndex = state.avatarColorIndex,
                onSelect = { onAction(EditProfileAction.OnAvatarColorSelect(it)) },
            )
            Spacer(Modifier.height(DesignTokens.space5))

            BrandLogoSection(
                logo = state.logo,
                existingLogoUrl = state.originalLogoUrl,
                hasExistingLogo = state.originalLogoStoragePath != null,
                fallbackInitials = state.businessName,
                onChangeClick = onLaunchLogoPicker,
                onRemoveClick = { onAction(EditProfileAction.OnLogoRemoveClick) },
            )
            Spacer(Modifier.height(DesignTokens.space5))

            ProfileTextField(
                value = state.businessName,
                onValueChange = { onAction(EditProfileAction.OnBusinessNameChange(it)) },
                onBlur = { onAction(EditProfileAction.OnBusinessNameBlur) },
                label = stringResource(Res.string.edit_profile_label_business_name),
                helper = stringResource(
                    Res.string.edit_profile_helper_business_name,
                    state.businessNameCount,
                    state.maxBusinessNameLength,
                ),
                error = state.businessNameError?.let { stringResource(it) },
                imeAction = ImeAction.Next,
            )
            Spacer(Modifier.height(DesignTokens.space3))

            ProfileTextField(
                value = state.displayName,
                onValueChange = { onAction(EditProfileAction.OnDisplayNameChange(it)) },
                onBlur = null,
                label = stringResource(Res.string.edit_profile_label_your_name),
                helper = stringResource(Res.string.edit_profile_helper_your_name),
                error = null,
                imeAction = ImeAction.Next,
            )
            Spacer(Modifier.height(DesignTokens.space3))

            ProfileTextField(
                value = state.whatsappNumber,
                onValueChange = { onAction(EditProfileAction.OnWhatsappChange(it)) },
                onBlur = { onAction(EditProfileAction.OnWhatsappBlur) },
                label = stringResource(Res.string.edit_profile_label_whatsapp),
                helper = stringResource(Res.string.edit_profile_helper_whatsapp),
                error = state.whatsappError?.let { stringResource(it) },
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Next,
            )

            WhatsAppConfirmRow(
                state = state.whatsappConfirm,
                numberValid = state.whatsappError == null && state.whatsappNumber.isNotBlank(),
                onConfirmClick = { onAction(EditProfileAction.OnConfirmWhatsAppClick) },
                onCodeChange = { onAction(EditProfileAction.OnConfirmCodeChange(it)) },
                onDismiss = { onAction(EditProfileAction.OnDismissConfirm) },
                debugCode = state.whatsappConfirm.code.takeIf { isDebugBuild },
            )
            Spacer(Modifier.height(DesignTokens.space3))

            ProfileTextField(
                value = state.phoneNumber,
                onValueChange = { onAction(EditProfileAction.OnPhoneChange(it)) },
                onBlur = { onAction(EditProfileAction.OnPhoneBlur) },
                label = stringResource(Res.string.edit_profile_label_phone),
                helper = stringResource(Res.string.edit_profile_helper_phone),
                error = state.phoneError?.let { stringResource(it) },
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Next,
            )
            Spacer(Modifier.height(DesignTokens.space4))

            BankDetailsSection(state = state, onAction = onAction)
            Spacer(Modifier.height(DesignTokens.space4))

            EmailReadonlyField(email = state.email)
            Spacer(Modifier.height(DesignTokens.space5))
        }
    }
}

@Composable
private fun AvatarBlock(
    businessName: String,
    avatarColorIndex: Int,
) {
    val initials = businessName.trim()
        .split(" ")
        .filter { it.isNotEmpty() }
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifEmpty { "?" }
    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(CircleShape)
            .background(avatarBrush(avatarColorIndex)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            color = Color.White,
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            style = TextStyle(
                lineHeight = 36.sp,
                platformStyle = platformTextStyleNoFontPadding(),
            ),
        )
    }
}

@Composable
private fun ColorPickerRow(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val cd = stringResource(Res.string.edit_profile_color_cd)
    Row(
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        modifier = Modifier.semantics { contentDescription = cd },
    ) {
        AvatarGradients.indices.forEach { index ->
            val borderColor = if (index == selectedIndex) {
                MaterialTheme.colorScheme.onSurface
            } else {
                Color.Transparent
            }
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(avatarBrush(index))
                    .border(width = 2.dp, color = borderColor, shape = CircleShape)
                    .clickable { onSelect(index) },
            )
        }
    }
}

@Composable
private fun ProfileTextField(
    value: String,
    onValueChange: (String) -> Unit,
    onBlur: (() -> Unit)?,
    label: String,
    helper: String?,
    error: String?,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
) {
    // onBlur is currently unused; validation runs on Save. Kept on the API so the
    // ViewModel's blur actions stay reachable when we wire focus tracking later.
    val unusedHint = onBlur

    @Suppress("UNUSED_VARIABLE")
    val unused = unusedHint

    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
            onDone = { focusManager.clearFocus() },
        ),
        isError = error != null,
        singleLine = true,
        supportingText = {
            val text = error ?: helper
            if (text != null) {
                Text(
                    text = text,
                    color = if (error != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        shape = RoundedCornerShape(DesignTokens.radiusMd),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun BankDetailsSection(
    state: EditProfileState,
    onAction: (EditProfileAction) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space2),
    ) {
        Text(
            text = stringResource(Res.string.bank_details_section_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(Res.string.bank_details_section_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(DesignTokens.space2))
        ProfileTextField(
            value = state.bankName,
            onValueChange = { onAction(EditProfileAction.OnBankNameChange(it)) },
            onBlur = { onAction(EditProfileAction.OnBankNameBlur) },
            label = stringResource(Res.string.bank_details_bank_label),
            helper = stringResource(Res.string.bank_details_bank_placeholder),
            error = state.bankNameError?.let { stringResource(it) },
            imeAction = ImeAction.Next,
        )
        Spacer(Modifier.height(DesignTokens.space2))
        ProfileTextField(
            value = state.bankAccountName,
            onValueChange = { onAction(EditProfileAction.OnBankAccountNameChange(it)) },
            onBlur = { onAction(EditProfileAction.OnBankAccountNameBlur) },
            label = stringResource(Res.string.bank_details_account_name_label),
            helper = stringResource(Res.string.bank_details_account_name_placeholder),
            error = state.bankAccountNameError?.let { stringResource(it) },
            imeAction = ImeAction.Next,
        )
        Spacer(Modifier.height(DesignTokens.space2))
        ProfileTextField(
            value = state.bankAccountNumber,
            onValueChange = { onAction(EditProfileAction.OnBankAccountNumberChange(it)) },
            onBlur = { onAction(EditProfileAction.OnBankAccountNumberBlur) },
            label = stringResource(Res.string.bank_details_account_number_label),
            helper = stringResource(Res.string.bank_details_account_number_placeholder),
            error = state.bankAccountNumberError?.let { stringResource(it) },
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done,
        )
    }
}

@Composable
private fun EmailReadonlyField(email: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(DesignTokens.radiusMd),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = DesignTokens.space4,
                vertical = DesignTokens.space3,
            ),
        ) {
            Text(
                text = stringResource(Res.string.edit_profile_label_email).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = email,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(DesignTokens.space2))
            Text(
                text = stringResource(Res.string.edit_profile_helper_email_readonly),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BrandLogoSection(
    logo: LogoUploadState,
    existingLogoUrl: String?,
    hasExistingLogo: Boolean,
    fallbackInitials: String,
    onChangeClick: () -> Unit,
    onRemoveClick: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(Res.string.edit_profile_logo_title),
            style = MaterialTheme.typography.titleSmall,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            EditProfileLogoTile(
                logo = logo,
                existingLogoUrl = existingLogoUrl,
                fallbackInitials = fallbackInitials,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onChangeClick) {
                    Text(stringResource(Res.string.edit_profile_logo_change))
                }
                // Show Remove whenever a logo currently exists on Firestore — either
                // because the current upload state is Uploaded, OR because there was
                // an originally-loaded logo that's still active (e.g. the user tapped
                // Change, the upload failed -> state.logo = Failed, but Firestore
                // still points at the pre-existing logo). Hiding Remove in that
                // failure window would strand the user with a broken state.
                if (logo is LogoUploadState.Uploaded || hasExistingLogo) {
                    TextButton(onClick = onRemoveClick) {
                        Text(
                            text = stringResource(Res.string.edit_profile_logo_remove),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Renders the 64dp logo tile in BrandLogoSection. Mirrors WorkshopSetupScreen's
 * tile shape: each LogoUploadState gets its own treatment so a slow or failed
 * upload still surfaces the user's intent (preview bytes + LoadingDots overlay).
 * Without this, Uploading/Failed states would visually fall back to the existing
 * logo or initials, making the action appear inert on slow networks.
 */
@Composable
private fun EditProfileLogoTile(
    logo: LogoUploadState,
    existingLogoUrl: String?,
    fallbackInitials: String,
) {
    val tileShape = RoundedCornerShape(12.dp)
    when (logo) {
        is LogoUploadState.Uploading -> {
            Box(
                modifier = Modifier.size(64.dp).clip(tileShape),
                contentAlignment = Alignment.Center,
            ) {
                SubcomposeAsyncImage(
                    model = logo.previewBytes,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    // Overlay shows LoadingDots; Coil's loading slot stays empty to avoid duplicate spinners.
                    loading = { Box(Modifier.fillMaxSize()) },
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) { LoadingDots(color = androidx.compose.ui.graphics.Color.White) }
            }
        }
        is LogoUploadState.Failed -> {
            Box(
                modifier = Modifier.size(64.dp).clip(tileShape),
                contentAlignment = Alignment.Center,
            ) {
                SubcomposeAsyncImage(
                    model = logo.previewBytes,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = { LoadingDots() },
                )
                // Dim the failed preview so the Retry copy in the snackbar
                // is unmistakably about THIS tile.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f)),
                )
            }
        }
        else -> {
            // Empty or Uploaded — fall through to the existing URL-based render so
            // a confirmed Uploaded state shows the new URL, and Empty falls back to
            // the originally-persisted logo (or initials when neither exists).
            val logoUrl = (logo as? LogoUploadState.Uploaded)?.url ?: existingLogoUrl
            BrandLogo(logoUrl = logoUrl, fallbackInitials = fallbackInitials, size = 64.dp)
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun EditProfileScreenPreview() {
    StitchPadTheme {
        EditProfileScreen(
            state = EditProfileState(
                isLoading = false,
                email = "folake@stitchpad.app",
                businessName = "Folake's Atelier",
                originalBusinessName = "Folake's Atelier",
                displayName = "Folake Adeyemi",
                originalDisplayName = "Folake Adeyemi",
                phoneNumber = "+2348035550142",
                originalPhoneNumber = "+2348035550142",
                whatsappNumber = "+2348139994477",
                originalWhatsappNumber = "+2348139994477",
                avatarColorIndex = 0,
                originalAvatarColorIndex = 0,
            ),
            onAction = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun EditProfileScreenDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        EditProfileScreen(
            state = EditProfileState(
                isLoading = false,
                email = "folake@stitchpad.app",
                businessName = "Folake's Atelier & Co",
                originalBusinessName = "Folake's Atelier",
                phoneNumber = "",
                whatsappNumber = "+234803",
                avatarColorIndex = 3,
            ),
            onAction = {},
        )
    }
}

// Padding values constant kept here in case we need a non-zero global content padding.
@Suppress("unused")
private val EditProfileContentPaddingHint = PaddingValues(0.dp)

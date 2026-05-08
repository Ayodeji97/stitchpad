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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.danzucker.stitchpad.feature.settings.presentation.components.AvatarGradients
import com.danzucker.stitchpad.feature.settings.presentation.components.avatarBrush
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.edit_profile_avatar_hint
import stitchpad.composeapp.generated.resources.edit_profile_back_cd
import stitchpad.composeapp.generated.resources.edit_profile_color_cd
import stitchpad.composeapp.generated.resources.edit_profile_helper_business_name
import stitchpad.composeapp.generated.resources.edit_profile_helper_email_readonly
import stitchpad.composeapp.generated.resources.edit_profile_helper_whatsapp
import stitchpad.composeapp.generated.resources.edit_profile_helper_your_name
import stitchpad.composeapp.generated.resources.edit_profile_label_business_name
import stitchpad.composeapp.generated.resources.edit_profile_label_email
import stitchpad.composeapp.generated.resources.edit_profile_label_phone
import stitchpad.composeapp.generated.resources.edit_profile_label_whatsapp
import stitchpad.composeapp.generated.resources.edit_profile_label_your_name
import stitchpad.composeapp.generated.resources.edit_profile_phone_prefix
import stitchpad.composeapp.generated.resources.edit_profile_save
import stitchpad.composeapp.generated.resources.edit_profile_title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    state: EditProfileState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
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
                        containerColor = DesignTokens.primary500,
                        contentColor = Color(0xFF181615),
                    ),
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            color = Color(0xFF181615),
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
                value = state.phoneNumber,
                onValueChange = { onAction(EditProfileAction.OnPhoneChange(it)) },
                onBlur = { onAction(EditProfileAction.OnPhoneBlur) },
                label = stringResource(Res.string.edit_profile_label_phone),
                helper = stringResource(Res.string.edit_profile_phone_prefix),
                error = state.phoneError?.let { stringResource(it) },
                keyboardType = KeyboardType.Phone,
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
                imeAction = ImeAction.Done,
            )
            Spacer(Modifier.height(DesignTokens.space3))

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
    val initial = businessName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(CircleShape)
            .background(avatarBrush(avatarColorIndex)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            color = Color.White,
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
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
    @Suppress("UNUSED_VARIABLE") val unused = unusedHint

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
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
                whatsappNumber = "",
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
                phoneNumber = "+234803",
                avatarColorIndex = 3,
            ),
            onAction = {},
        )
    }
}

// Padding values constant kept here in case we need a non-zero global content padding.
@Suppress("unused")
private val EditProfileContentPaddingHint = PaddingValues(0.dp)

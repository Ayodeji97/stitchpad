package com.danzucker.stitchpad.feature.onboarding.presentation.workshop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.auth.presentation.components.AuthCard
import com.danzucker.stitchpad.feature.auth.presentation.components.AuthHero
import com.danzucker.stitchpad.feature.auth.presentation.components.AuthTextField
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import com.danzucker.stitchpad.util.ObserveAsEvents
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.workshop_business_name_hint
import stitchpad.composeapp.generated.resources.workshop_business_name_label
import stitchpad.composeapp.generated.resources.workshop_business_name_placeholder
import stitchpad.composeapp.generated.resources.workshop_continue_button
import stitchpad.composeapp.generated.resources.workshop_logo_coming_soon
import stitchpad.composeapp.generated.resources.workshop_logo_label
import stitchpad.composeapp.generated.resources.workshop_logo_optional
import stitchpad.composeapp.generated.resources.workshop_logo_upload_sub
import stitchpad.composeapp.generated.resources.workshop_logo_upload_title
import stitchpad.composeapp.generated.resources.workshop_phone_hint
import stitchpad.composeapp.generated.resources.workshop_phone_label
import stitchpad.composeapp.generated.resources.workshop_phone_placeholder
import stitchpad.composeapp.generated.resources.workshop_skip
import stitchpad.composeapp.generated.resources.workshop_subtitle
import stitchpad.composeapp.generated.resources.workshop_title

@Composable
fun WorkshopSetupRoot(
    onNavigateToHome: () -> Unit,
    onNavigateToLogin: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    viewModel: WorkshopSetupViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val comingSoon = stringResource(Res.string.workshop_logo_coming_soon)

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            WorkshopSetupEvent.NavigateToHome -> onNavigateToHome()
            WorkshopSetupEvent.NavigateToLogin -> onNavigateToLogin()
            is WorkshopSetupEvent.ShowError -> {
                scope.launch {
                    val message = when (val text = event.message) {
                        is UiText.DynamicString -> text.value
                        is UiText.StringResourceText -> org.jetbrains.compose.resources.getString(text.id)
                    }
                    snackbarHostState.showSnackbar(message)
                }
            }
            WorkshopSetupEvent.ShowComingSoon -> {
                scope.launch { snackbarHostState.showSnackbar(comingSoon) }
            }
        }
    }

    WorkshopSetupScreen(
        state = state,
        onAction = viewModel::onAction,
    )
}

@Composable
fun WorkshopSetupScreen(
    state: WorkshopSetupState,
    onAction: (WorkshopSetupAction) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DesignTokens.neutral900),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AuthHero(height = 220.dp, logoDiameter = 68.dp, showTagline = true)

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
                    helperText = stringResource(Res.string.workshop_business_name_hint),
                    errorText = state.businessNameError?.let { stringResource(it) },
                )

                // 4. Phone field
                AuthTextField(
                    label = stringResource(Res.string.workshop_phone_label),
                    value = state.phone,
                    onValueChange = { onAction(WorkshopSetupAction.OnPhoneChange(it)) },
                    leadingIcon = Icons.Outlined.Phone,
                    placeholder = stringResource(Res.string.workshop_phone_placeholder),
                    keyboardType = KeyboardType.Phone,
                    helperText = stringResource(Res.string.workshop_phone_hint),
                    errorText = state.phoneError?.let { stringResource(it) },
                )

                // 5. Logo upload tile
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = stringResource(Res.string.workshop_logo_label),
                            style = TextStyle(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFF5F2ED),
                            ),
                        )
                        Text(
                            text = stringResource(Res.string.workshop_logo_optional),
                            style = TextStyle(
                                fontSize = 13.sp,
                                color = Color(0xFFA8A49D),
                            ),
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(108.dp)
                            .border(1.5.dp, Color(0xFF3A3731), RoundedCornerShape(10.dp))
                            .background(Color(0xFF1F1D1A), RoundedCornerShape(10.dp))
                            .clickable { onAction(WorkshopSetupAction.OnLogoUploadClick) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        DesignTokens.primary500.copy(alpha = 0.15f),
                                        CircleShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.PhotoCamera,
                                    contentDescription = null,
                                    tint = DesignTokens.primary400,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Text(
                                text = stringResource(Res.string.workshop_logo_upload_title),
                                style = TextStyle(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFF5F2ED),
                                ),
                            )
                            Text(
                                text = stringResource(Res.string.workshop_logo_upload_sub),
                                style = TextStyle(
                                    fontSize = 11.5.sp,
                                    color = Color(0xFFA8A49D),
                                ),
                            )
                        }
                    }
                }

                // 6. Continue button
                Button(
                    onClick = { onAction(WorkshopSetupAction.OnContinueClick) },
                    enabled = !state.isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DesignTokens.primary500,
                        contentColor = DesignTokens.neutral900,
                        disabledContainerColor = DesignTokens.primary500.copy(alpha = 0.5f),
                        disabledContentColor = DesignTokens.neutral900.copy(alpha = 0.5f),
                    ),
                ) {
                    Text(
                        text = stringResource(Res.string.workshop_continue_button),
                        style = TextStyle(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }

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
                            color = DesignTokens.primary400,
                        ),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
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
                phone = "+2348012345678",
            ),
            onAction = {},
        )
    }
}

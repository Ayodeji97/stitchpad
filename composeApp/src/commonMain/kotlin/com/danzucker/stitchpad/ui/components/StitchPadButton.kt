@file:Suppress("TooManyFunctions")

package com.danzucker.stitchpad.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadMotion
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.cd_button_loading

private val BUTTON_HEIGHT: Dp = 54.dp
private val SPINNER_SIZE: Dp = 20.dp
private val SPINNER_STROKE: Dp = 2.dp

/**
 * The app's unified primary/secondary action button.
 *
 * Calm & professional motion:
 *  - a subtle press scale (to [StitchPadMotion.PRESSED_SCALE]) for tactile feedback, and
 *  - an in-place [Crossfade] between the label and a loading spinner.
 *
 * The button keeps a stable size while [isLoading] toggles (fixed height +
 * caller-supplied `fillMaxWidth()`), so the content fades without the button
 * resizing. Callers pass their domain [enabled] and [isLoading] separately —
 * the button itself blocks interaction while loading.
 *
 * Disabled styling comes from [DesignTokens] so every CTA greys out identically.
 *
 * This overload renders [text] (with an optional [leadingIcon]); use the slot
 * overload for custom content.
 */
@Composable
fun StitchPadButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    variant: StitchPadButtonVariant = StitchPadButtonVariant.Primary,
    leadingIcon: ImageVector? = null,
    shape: RoundedCornerShape = RoundedCornerShape(DesignTokens.radiusLg),
    contentDescriptionWhenLoading: String? = stringResource(Res.string.cd_button_loading),
) {
    StitchPadButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        isLoading = isLoading,
        variant = variant,
        shape = shape,
        contentDescriptionWhenLoading = contentDescriptionWhenLoading,
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(DesignTokens.iconInline),
            )
        }
        Text(
            text = text,
            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold),
        )
    }
}

/**
 * Slot overload of [StitchPadButton]. [content] is laid out in a centered [Row]
 * while the button is at rest, and cross-fades to a spinner while [isLoading].
 */
@Composable
fun StitchPadButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    variant: StitchPadButtonVariant = StitchPadButtonVariant.Primary,
    shape: RoundedCornerShape = RoundedCornerShape(DesignTokens.radiusLg),
    contentDescriptionWhenLoading: String? = stringResource(Res.string.cd_button_loading),
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) StitchPadMotion.PRESSED_SCALE else StitchPadMotion.RESTING_SCALE,
        animationSpec = StitchPadMotion.press(),
        label = "stitchpad-button-scale",
    )

    val buttonModifier = modifier
        .height(BUTTON_HEIGHT)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .then(
            if (isLoading && contentDescriptionWhenLoading != null) {
                Modifier.semantics { contentDescription = contentDescriptionWhenLoading }
            } else {
                Modifier
            },
        )

    val buttonContent: @Composable RowScope.() -> Unit = {
        Crossfade(
            targetState = isLoading,
            animationSpec = StitchPadMotion.contentFade(),
            label = "stitchpad-button-content",
        ) { loading ->
            if (loading) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(SPINNER_SIZE),
                        strokeWidth = SPINNER_STROKE,
                        color = LocalContentColor.current,
                    )
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
                    verticalAlignment = Alignment.CenterVertically,
                    content = content,
                )
            }
        }
    }

    when (variant) {
        StitchPadButtonVariant.Primary -> Button(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled && !isLoading,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                disabledContainerColor = DesignTokens.neutral700,
                disabledContentColor = DesignTokens.neutral500,
            ),
            interactionSource = interactionSource,
            content = buttonContent,
        )

        StitchPadButtonVariant.Secondary -> OutlinedButton(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled && !isLoading,
            shape = shape,
            colors = ButtonDefaults.outlinedButtonColors(
                disabledContentColor = DesignTokens.neutral500,
            ),
            interactionSource = interactionSource,
            content = buttonContent,
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun StitchPadButtonPrimaryPreview() {
    StitchPadTheme {
        StitchPadButton(
            text = "Sign In",
            onClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .padding(DesignTokens.space4),
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun StitchPadButtonLeadingIconPreview() {
    StitchPadTheme {
        StitchPadButton(
            text = "Save customer",
            onClick = {},
            leadingIcon = Icons.Default.Check,
            modifier = Modifier
                .fillMaxWidth()
                .padding(DesignTokens.space4),
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun StitchPadButtonLoadingPreview() {
    StitchPadTheme {
        StitchPadButton(
            text = "Sign In",
            onClick = {},
            isLoading = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(DesignTokens.space4),
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun StitchPadButtonDisabledPreview() {
    StitchPadTheme {
        StitchPadButton(
            text = "Sign In",
            onClick = {},
            enabled = false,
            modifier = Modifier
                .fillMaxWidth()
                .padding(DesignTokens.space4),
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun StitchPadButtonSecondaryPreview() {
    StitchPadTheme {
        StitchPadButton(
            text = "Continue with Google",
            onClick = {},
            variant = StitchPadButtonVariant.Secondary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(DesignTokens.space4),
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun StitchPadButtonPrimaryDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        StitchPadButton(
            text = "Create account",
            onClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .padding(DesignTokens.space4),
        )
    }
}

package com.danzucker.stitchpad.feature.auth.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.FrauncesFamily
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.auth_background
import stitchpad.composeapp.generated.resources.auth_background_alt
import stitchpad.composeapp.generated.resources.brand_name
import stitchpad.composeapp.generated.resources.workshop_brand_tagline

/** Photo-background hero with logo + brand mark + tagline. */
@Composable
fun AuthHero(
    modifier: Modifier = Modifier,
    variant: AuthHeroVariant = AuthHeroVariant.Utility,
    height: Dp = 280.dp,
    logoDiameter: Dp = 80.dp,
    showTagline: Boolean = true,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(DesignTokens.indigo900),
    ) {
        Image(
            painter = painterResource(variant.drawable),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().height(height),
            contentScale = ContentScale.Crop,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StitchPadLogo(diameter = logoDiameter)
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(Res.string.brand_name),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = FrauncesFamily(),
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = 2.2.sp,
                ),
            )
            if (showTagline) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(Res.string.workshop_brand_tagline),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.85f),
                        letterSpacing = 3.2.sp,
                    ),
                )
            }
        }
    }
}

/**
 * Picks which adire-themed hero photo is used. Welcome (default) shows the
 * richer dress-form + measuring-tape + adire-pattern composition — used on
 * Login, SignUp, and Workshop Setup. Utility shows a calmer measuring-tape-
 * on-indigo composition — used on ForgotPassword where a softer, less
 * distracting backdrop fits the task.
 */
enum class AuthHeroVariant {
    Welcome,
    Utility,
}

private val AuthHeroVariant.drawable: DrawableResource
    get() = when (this) {
        AuthHeroVariant.Welcome -> Res.drawable.auth_background
        AuthHeroVariant.Utility -> Res.drawable.auth_background_alt
    }

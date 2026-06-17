package com.danzucker.stitchpad.feature.onboarding.presentation.welcome

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danzucker.stitchpad.ui.components.StitchPadButton
import com.danzucker.stitchpad.ui.components.StitchPadMark
import com.danzucker.stitchpad.ui.components.VideoBackground
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.FrauncesFamily
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.brand_name
import stitchpad.composeapp.generated.resources.welcome_get_started
import stitchpad.composeapp.generated.resources.welcome_headline
import stitchpad.composeapp.generated.resources.welcome_poster
import stitchpad.composeapp.generated.resources.welcome_sign_in
import stitchpad.composeapp.generated.resources.workshop_brand_tagline

private val secondaryButtonHeight = 54.dp

// Darkens the lower half of the video so the headline + CTAs stay legible while
// leaving the upper footage (the atelier scene) clear. Mirrors the onboarding
// photo overlay treatment.
private val welcomeScrim: Brush = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = 0.55f),
    0.20f to Color.Black.copy(alpha = 0.30f),
    0.42f to Color.Transparent,
    0.62f to Color.Black.copy(alpha = 0.50f),
    1f to Color.Black.copy(alpha = 0.88f),
)

// White brand text can vanish against bright frames of the video (a sunlit window,
// pale fabric). A soft drop shadow keeps it legible over anything underneath.
private val brandTextShadow = Shadow(
    color = Color.Black.copy(alpha = 0.65f),
    offset = Offset(0f, 2f),
    blurRadius = 10f,
)

/**
 * Logged-out video landing. Plays a muted, looping atelier clip behind the brand
 * lockup and two CTAs — Create account (primary) and Sign in (secondary). Has no
 * ViewModel; it is pure navigation, like [com.danzucker.stitchpad.feature.onboarding.presentation.SplashRoot].
 */
@Composable
fun WelcomeRoot(
    onSignIn: () -> Unit,
    onSignUp: () -> Unit,
) {
    WelcomeScreen(onSignIn = onSignIn, onSignUp = onSignUp)
}

@Composable
fun WelcomeScreen(
    onSignIn: () -> Unit,
    onSignUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val videoUri = remember { Res.getUri("files/welcome_video.mp4") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DesignTokens.inkDark),
    ) {
        // Poster first-frame, always present so nothing flashes black before the
        // video's first frame renders (and it's all the preview/tooling shows).
        Image(
            painter = painterResource(Res.drawable.welcome_poster),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // Live video sits on top of the poster. Skipped in @Preview / inspection
        // mode, where a platform player can't run.
        if (!LocalInspectionMode.current) {
            VideoBackground(uri = videoUri, modifier = Modifier.fillMaxSize())
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .background(welcomeScrim),
        )

        WelcomeBrandLockup(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = DesignTokens.space10),
        )

        WelcomeActions(
            onSignUp = onSignUp,
            onSignIn = onSignIn,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = DesignTokens.space6)
                .padding(bottom = DesignTokens.space10),
        )
    }
}

@Composable
private fun WelcomeBrandLockup(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StitchPadMark(
            size = 72.dp,
            coverColor = Color.White,
            coverDepthColor = DesignTokens.neutral200,
            detailColor = DesignTokens.indigo700,
        )
        Spacer(Modifier.height(DesignTokens.space3))
        Text(
            text = stringResource(Res.string.brand_name),
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = FrauncesFamily(),
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = 2.2.sp,
                shadow = brandTextShadow,
            ),
        )
        Spacer(Modifier.height(DesignTokens.space1))
        Text(
            text = stringResource(Res.string.workshop_brand_tagline),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.85f),
                letterSpacing = 3.2.sp,
                shadow = brandTextShadow,
            ),
        )
    }
}

@Composable
private fun WelcomeActions(
    onSignUp: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.welcome_headline),
            style = MaterialTheme.typography.headlineSmall.copy(
                fontFamily = FrauncesFamily(),
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                shadow = brandTextShadow,
            ),
        )
        Spacer(Modifier.height(DesignTokens.space6))
        StitchPadButton(
            text = stringResource(Res.string.welcome_get_started),
            onClick = onSignUp,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(DesignTokens.space3))
        // Explicit white outline rather than the design-system Secondary variant:
        // its theme-coloured content can't be guaranteed legible over the video.
        OutlinedButton(
            onClick = onSignIn,
            modifier = Modifier
                .fillMaxWidth()
                .height(secondaryButtonHeight),
            shape = RoundedCornerShape(DesignTokens.radiusLg),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
        ) {
            Text(
                text = stringResource(Res.string.welcome_sign_in),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun WelcomeScreenPreview() {
    StitchPadTheme {
        WelcomeScreen(onSignIn = {}, onSignUp = {})
    }
}

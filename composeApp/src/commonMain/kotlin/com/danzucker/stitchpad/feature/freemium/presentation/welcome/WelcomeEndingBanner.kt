package com.danzucker.stitchpad.feature.freemium.presentation.welcome

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.welcome_ending_banner_body
import stitchpad.composeapp.generated.resources.welcome_ending_banner_cta
import stitchpad.composeapp.generated.resources.welcome_ending_banner_title_one
import stitchpad.composeapp.generated.resources.welcome_ending_banner_title_other

@Composable
fun WelcomeEndingBanner(
    daysLeft: Int,
    onSeeUpgrade: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(DesignTokens.radiusLg),
    ) {
        Column(modifier = Modifier.padding(DesignTokens.space4)) {
            val titleRes = if (daysLeft == 1) {
                Res.string.welcome_ending_banner_title_one
            } else {
                Res.string.welcome_ending_banner_title_other
            }
            Text(
                text = stringResource(titleRes, daysLeft),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Text(
                text = stringResource(Res.string.welcome_ending_banner_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            TextButton(onClick = onSeeUpgrade) {
                Text(stringResource(Res.string.welcome_ending_banner_cta))
            }
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun WelcomeEndingBannerPreview() {
    StitchPadTheme {
        WelcomeEndingBanner(
            daysLeft = 2,
            onSeeUpgrade = {},
        )
    }
}

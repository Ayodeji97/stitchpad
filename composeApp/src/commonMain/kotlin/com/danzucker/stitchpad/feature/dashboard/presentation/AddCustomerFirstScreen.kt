package com.danzucker.stitchpad.feature.dashboard.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.add_customer_first_back_cd
import stitchpad.composeapp.generated.resources.add_customer_first_cta
import stitchpad.composeapp.generated.resources.add_customer_first_supporting
import stitchpad.composeapp.generated.resources.add_customer_first_title
import stitchpad.composeapp.generated.resources.dashboard_empty_customers

private val ILLUSTRATION_SIZE = 200.dp
private val SPARKLE_BADGE_SIZE = 44.dp

/**
 * Stateless gate screen rendered when a BrandNew user taps an action that
 * requires an existing customer (hero "Create first order", "Create order"
 * tile, "Measurement" tile). The single brand-primary CTA routes onward to
 * the customer form. Top-bar back exits to the dashboard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCustomerFirstScreen(
    onAddCustomerClick: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.add_customer_first_back_cd),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = DesignTokens.space5)
                .padding(bottom = DesignTokens.space5),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            IllustrationWithBadge()
            Spacer(Modifier.height(DesignTokens.space6))
            Text(
                text = stringResource(Res.string.add_customer_first_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(DesignTokens.space3))
            Text(
                text = stringResource(Res.string.add_customer_first_supporting),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(DesignTokens.space6))
            Button(
                onClick = onAddCustomerClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(DesignTokens.radiusLg),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text(
                    text = stringResource(Res.string.add_customer_first_cta),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = DesignTokens.space2),
                )
                Spacer(Modifier.size(DesignTokens.space2))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(DesignTokens.iconInline),
                )
            }
        }
    }
}

@Composable
private fun IllustrationWithBadge() {
    val primary = MaterialTheme.colorScheme.primary
    Box(contentAlignment = Alignment.TopEnd) {
        Image(
            painter = painterResource(Res.drawable.dashboard_empty_customers),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(ILLUSTRATION_SIZE),
        )
        Box(
            modifier = Modifier
                .size(SPARKLE_BADGE_SIZE)
                .background(color = primary.copy(alpha = 0.18f), shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "+",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = primary,
            )
        }
    }
}

@Preview
@Composable
@Suppress("UnusedPrivateMember")
private fun AddCustomerFirstScreenLightPreview() {
    StitchPadTheme {
        AddCustomerFirstScreen(onAddCustomerClick = {}, onBack = {})
    }
}

@Preview
@Composable
@Suppress("UnusedPrivateMember")
private fun AddCustomerFirstScreenDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        AddCustomerFirstScreen(onAddCustomerClick = {}, onBack = {})
    }
}

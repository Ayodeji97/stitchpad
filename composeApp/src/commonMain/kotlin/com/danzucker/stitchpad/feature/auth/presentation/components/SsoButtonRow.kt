package com.danzucker.stitchpad.feature.auth.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danzucker.stitchpad.feature.auth.presentation.components.icons.AppleLogo
import com.danzucker.stitchpad.feature.auth.presentation.components.icons.GoogleLogo
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.auth_continue_with_apple
import stitchpad.composeapp.generated.resources.auth_continue_with_google
import stitchpad.composeapp.generated.resources.auth_or_continue_with

@Composable
fun SsoButtonRow(
    onGoogleClick: () -> Unit,
    onAppleClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            HorizontalDivider(color = Color(0xFF3A3731), modifier = Modifier.weight(1f))
            Text(
                text = stringResource(Res.string.auth_or_continue_with),
                style = TextStyle(fontSize = 12.sp, color = Color(0xFF7D7970)),
            )
            HorizontalDivider(color = Color(0xFF3A3731), modifier = Modifier.weight(1f))
        }

        SsoButton(
            text = stringResource(Res.string.auth_continue_with_google),
            icon = { GoogleLogo(modifier = Modifier.size(20.dp)) },
            onClick = onGoogleClick,
            enabled = enabled,
        )
        SsoButton(
            text = stringResource(Res.string.auth_continue_with_apple),
            icon = { AppleLogo(modifier = Modifier.size(20.dp), tint = Color.White) },
            onClick = onAppleClick,
            enabled = enabled,
        )
    }
}

@Composable
private fun SsoButton(
    text: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.5.dp, Color(0xFF3A3731)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color(0xFF2A2825),
            contentColor = Color(0xFFF5F2ED),
        ),
    ) {
        icon()
        Spacer(Modifier.size(10.dp))
        Text(
            text = text,
            style = TextStyle(fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold),
        )
    }
}

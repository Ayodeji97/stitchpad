package com.danzucker.stitchpad.feature.auth.presentation.deleteaccount

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danzucker.stitchpad.ui.theme.DesignTokens
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.settings_delete_account_cancel
import stitchpad.composeapp.generated.resources.settings_delete_account_confirm
import stitchpad.composeapp.generated.resources.settings_delete_account_data_sheet_body
import stitchpad.composeapp.generated.resources.settings_delete_account_data_sheet_dismiss
import stitchpad.composeapp.generated.resources.settings_delete_account_data_sheet_title
import stitchpad.composeapp.generated.resources.settings_delete_account_dialog_body
import stitchpad.composeapp.generated.resources.settings_delete_account_dialog_data_link
import stitchpad.composeapp.generated.resources.settings_delete_account_dialog_title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteAccountDialog(
    isLoading: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showDataSheet by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_delete_account_dialog_title)) },
        text = {
            Column {
                Text(stringResource(Res.string.settings_delete_account_dialog_body))
                Text(
                    text = stringResource(Res.string.settings_delete_account_dialog_data_link),
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DesignTokens.primary400,
                    ),
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clickable { showDataSheet = true },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = DesignTokens.error500),
            ) {
                Text(stringResource(Res.string.settings_delete_account_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text(stringResource(Res.string.settings_delete_account_cancel))
            }
        },
    )

    if (showDataSheet) {
        ModalBottomSheet(
            onDismissRequest = { showDataSheet = false },
            sheetState = rememberModalBottomSheetState(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(Res.string.settings_delete_account_data_sheet_title),
                    style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                )
                Text(stringResource(Res.string.settings_delete_account_data_sheet_body))
                Button(
                    onClick = { showDataSheet = false },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(Res.string.settings_delete_account_data_sheet_dismiss))
                }
            }
        }
    }
}

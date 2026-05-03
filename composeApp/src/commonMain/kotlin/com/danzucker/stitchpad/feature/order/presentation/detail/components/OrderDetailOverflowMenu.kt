package com.danzucker.stitchpad.feature.order.presentation.detail.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.order_detail_overflow_archive
import stitchpad.composeapp.generated.resources.order_detail_overflow_delete
import stitchpad.composeapp.generated.resources.order_detail_overflow_duplicate

@Composable
fun OrderDetailOverflowMenu(
    expanded: Boolean,
    showArchive: Boolean,
    onDismiss: () -> Unit,
    onDuplicateClick: () -> Unit,
    onArchiveClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        // Duplicate order — always visible
        DropdownMenuItem(
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                )
            },
            text = { Text(stringResource(Res.string.order_detail_overflow_duplicate)) },
            onClick = {
                onDismiss()
                onDuplicateClick()
            },
        )

        // Archive order — only shown when showArchive == true
        if (showArchive) {
            DropdownMenuItem(
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Archive,
                        contentDescription = null,
                    )
                },
                text = { Text(stringResource(Res.string.order_detail_overflow_archive)) },
                onClick = {
                    onDismiss()
                    onArchiveClick()
                },
            )
        }

        // Divider above Delete — always shown since Delete is always present
        HorizontalDivider()

        // Delete order — error-tinted, always visible
        DropdownMenuItem(
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            text = {
                Text(
                    text = stringResource(Res.string.order_detail_overflow_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            },
            onClick = {
                onDismiss()
                onDeleteClick()
            },
        )
    }
}

// region Previews

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderDetailOverflowMenuWithArchivePreview() {
    StitchPadTheme {
        OrderDetailOverflowMenu(
            expanded = true,
            showArchive = true,
            onDismiss = {},
            onDuplicateClick = {},
            onArchiveClick = {},
            onDeleteClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderDetailOverflowMenuDeliveredDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        OrderDetailOverflowMenu(
            expanded = true,
            showArchive = false,
            onDismiss = {},
            onDuplicateClick = {},
            onArchiveClick = {},
            onDeleteClick = {},
        )
    }
}

// endregion

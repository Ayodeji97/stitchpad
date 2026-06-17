@file:Suppress("MatchingDeclarationName")

package com.danzucker.stitchpad.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.order_form_photo_pick
import stitchpad.composeapp.generated.resources.order_form_photo_pick_support
import stitchpad.composeapp.generated.resources.order_form_photo_take
import stitchpad.composeapp.generated.resources.order_form_photo_take_support

/** Where a photo comes from. Shared so the detail screen and order form agree. */
enum class PhotoSource { Camera, Gallery }

/**
 * A small bottom sheet offering "Take photo" / "Choose from gallery". The caller is
 * responsible for dismissing the sheet and launching the matching picker after dismiss
 * (the launch-after-dismiss pattern that sidesteps the iOS present-after-dismiss timing bug).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoSourceSheet(
    onPick: (PhotoSource) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        ListItem(
            headlineContent = { Text(stringResource(Res.string.order_form_photo_take)) },
            supportingContent = { Text(stringResource(Res.string.order_form_photo_take_support)) },
            leadingContent = { Icon(Icons.Default.PhotoCamera, contentDescription = null) },
            modifier = Modifier.clickable { onPick(PhotoSource.Camera) },
        )
        ListItem(
            headlineContent = { Text(stringResource(Res.string.order_form_photo_pick)) },
            supportingContent = { Text(stringResource(Res.string.order_form_photo_pick_support)) },
            leadingContent = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
            modifier = Modifier.clickable { onPick(PhotoSource.Gallery) },
        )
    }
}

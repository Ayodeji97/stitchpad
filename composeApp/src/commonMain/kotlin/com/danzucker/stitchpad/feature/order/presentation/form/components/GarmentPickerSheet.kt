package com.danzucker.stitchpad.feature.order.presentation.form.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.core.domain.model.CustomGarmentType
import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.feature.order.domain.filterGarmentOptions
import com.danzucker.stitchpad.feature.order.presentation.garmentDisplayName
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.garment_picker_add_custom_format
import stitchpad.composeapp.generated.resources.garment_picker_add_custom_subtext
import stitchpad.composeapp.generated.resources.garment_picker_no_matches_format
import stitchpad.composeapp.generated.resources.garment_picker_search_placeholder
import stitchpad.composeapp.generated.resources.garment_picker_section_my_types
import stitchpad.composeapp.generated.resources.garment_picker_section_presets
import stitchpad.composeapp.generated.resources.garment_picker_subtitle
import stitchpad.composeapp.generated.resources.garment_picker_title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GarmentPickerSheet(
    customs: List<CustomGarmentType>,
    presets: List<GarmentType>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onPickPreset: (GarmentType) -> Unit,
    onPickCustom: (CustomGarmentType) -> Unit,
    onAddCustom: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        GarmentPickerSheetContent(
            customs = customs,
            presets = presets,
            searchQuery = searchQuery,
            onSearchChange = onSearchChange,
            onPickPreset = onPickPreset,
            onPickCustom = onPickCustom,
            onAddCustom = onAddCustom,
        )
    }
}

@Composable
private fun GarmentPickerSheetContent(
    customs: List<CustomGarmentType>,
    presets: List<GarmentType>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onPickPreset: (GarmentType) -> Unit,
    onPickCustom: (CustomGarmentType) -> Unit,
    onAddCustom: (String) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(Res.string.garment_picker_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(Res.string.garment_picker_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text(stringResource(Res.string.garment_picker_search_placeholder)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        )

        // Dedupe needs every preset (including ones hidden by the gender chip),
        // so the user can't type the name of a UNISEX preset (Trouser, Shirt, etc.)
        // while MALE/FEMALE is active and accidentally create a duplicate custom.
        // Build labels for all non-OTHER presets, not just the gender-filtered subset.
        val allPresets: List<GarmentType> = GarmentType.entries.filter { it != GarmentType.OTHER }
        val presetLabels: Map<GarmentType, String> = allPresets.associateWith { garmentDisplayName(it) }
        val filterResult = filterGarmentOptions(
            query = searchQuery,
            customs = customs,
            presets = presets,
            allPresets = allPresets,
            resolvePresetLabel = { presetLabels[it] ?: it.name },
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        ) {
            if (filterResult.showAddCustomCta) {
                item {
                    AddCustomRow(
                        typedText = searchQuery.trim(),
                        onClick = { onAddCustom(searchQuery.trim()) },
                    )
                }
            }
            if (filterResult.matchingCustoms.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = stringResource(Res.string.garment_picker_section_my_types),
                        count = filterResult.matchingCustoms.size,
                    )
                }
                items(filterResult.matchingCustoms, key = { it.id }) { custom ->
                    PickerRow(
                        label = custom.name,
                        onClick = { onPickCustom(custom) },
                    )
                }
            }
            item {
                SectionHeader(
                    title = stringResource(Res.string.garment_picker_section_presets),
                    count = filterResult.matchingPresets.size,
                )
            }
            if (filterResult.matchingPresets.isEmpty() && searchQuery.trim().isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(
                            Res.string.garment_picker_no_matches_format,
                            searchQuery.trim()
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                    )
                }
            } else {
                items(filterResult.matchingPresets, key = { it.name }) { preset ->
                    PickerRow(
                        label = presetLabels[preset] ?: preset.name,
                        onClick = { onPickPreset(preset) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PickerRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AddCustomRow(typedText: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 12.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.garment_picker_add_custom_format, typedText),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(Res.string.garment_picker_add_custom_subtext),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

package com.danzucker.stitchpad.feature.order.presentation.detail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import com.danzucker.stitchpad.core.domain.model.CostCategory
import com.danzucker.stitchpad.feature.order.presentation.detail.costCategoryOrder
import com.danzucker.stitchpad.feature.order.presentation.detail.hint
import com.danzucker.stitchpad.feature.order.presentation.detail.label
import com.danzucker.stitchpad.ui.components.ThousandsSeparatorTransformation
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.costs_editor_save
import stitchpad.composeapp.generated.resources.costs_editor_subtitle
import stitchpad.composeapp.generated.resources.costs_editor_title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CostsEditorSheet(
    draft: Map<CostCategory, String>,
    onAmountChange: (CostCategory, String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        CostsEditorSheetContent(
            draft = draft,
            onAmountChange = onAmountChange,
            onSave = onSave,
        )
    }
}

@Composable
private fun CostsEditorSheetContent(
    draft: Map<CostCategory, String>,
    onAmountChange: (CostCategory, String) -> Unit,
    onSave: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Scrollable + IME-aware: on small phones (or whenever the numeric keyboard is
            // open) the 6 amount fields + hints + Save button can exceed the viewport.
            // verticalScroll lets the lower categories scroll into view; imePadding keeps
            // the Save button clear of the keyboard instead of letting it get covered.
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = DesignTokens.space4)
            .padding(bottom = DesignTokens.space4),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space4),
    ) {
        Text(
            text = stringResource(Res.string.costs_editor_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(Res.string.costs_editor_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        ) {
            costCategoryOrder.forEach { category ->
                CostAmountRow(
                    category = category,
                    digits = draft[category].orEmpty(),
                    onAmountChange = onAmountChange,
                )
            }
        }

        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(DesignTokens.radiusMd),
        ) {
            Text(
                text = stringResource(Res.string.costs_editor_save),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = DesignTokens.space1),
            )
        }
    }
}

/**
 * Bound to a local [TextFieldValue] rather than the raw VM-owned digit string directly —
 * binding a VM String straight to a TextField's `value` desyncs the cursor position on
 * every recomposition (see feedback_compose_textfieldvalue_cursor). The field only pushes
 * the digits-only text back up via [onAmountChange]; the VM never dictates cursor position.
 */
@Composable
private fun CostAmountRow(
    category: CostCategory,
    digits: String,
    onAmountChange: (CostCategory, String) -> Unit,
) {
    var fieldValue by remember(category) {
        mutableStateOf(TextFieldValue(digits, TextRange(digits.length)))
    }
    LaunchedEffect(category, digits) {
        if (fieldValue.text != digits) {
            fieldValue = TextFieldValue(digits, TextRange(digits.length))
        }
    }

    val hint = category.hint()

    Column(
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space1),
    ) {
        Text(
            text = category.label(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedTextField(
            value = fieldValue,
            onValueChange = { newValue ->
                val filtered = newValue.text.filter(Char::isDigit)
                fieldValue = newValue.copy(text = filtered)
                if (filtered != digits) {
                    onAmountChange(category, filtered)
                }
            },
            prefix = { Text(text = "₦") },
            visualTransformation = ThousandsSeparatorTransformation,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(DesignTokens.radiusMd),
        )
    }
}

// region — Previews (render content only — ModalBottomSheet needs an Activity for proper preview)

private val PREVIEW_DRAFT = mapOf(
    CostCategory.FABRIC to "18000",
    CostCategory.MATERIALS_TRIMS to "4500",
    CostCategory.LABOUR to "",
    CostCategory.LOGISTICS to "",
    CostCategory.EMBELLISHMENT to "",
    CostCategory.OTHER to "",
)

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun CostsEditorSheetContentLightPreview() {
    StitchPadTheme {
        Surface {
            CostsEditorSheetContent(
                draft = PREVIEW_DRAFT,
                onAmountChange = { _, _ -> },
                onSave = {},
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun CostsEditorSheetContentDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        Surface {
            CostsEditorSheetContent(
                draft = PREVIEW_DRAFT,
                onAmountChange = { _, _ -> },
                onSave = {},
            )
        }
    }
}

// endregion

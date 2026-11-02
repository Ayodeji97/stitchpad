package com.danzucker.stitchpad.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.core.domain.model.Country
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.country_picker_chip_content_description

/** Shared, stateless phone control. A null picker callback shows a fixed country prefix. */
@Composable
fun CountryPhoneField(
    country: Country,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    helper: String? = null,
    error: String? = null,
    onCountryClick: (() -> Unit)? = null,
    onBlur: (() -> Unit)? = null,
    imeAction: ImeAction = ImeAction.Next,
    colors: CountryFieldColors = CountryFieldColors(
        container = MaterialTheme.colorScheme.surface,
        border = MaterialTheme.colorScheme.outline,
        primaryText = MaterialTheme.colorScheme.onSurface,
        secondaryText = MaterialTheme.colorScheme.onSurfaceVariant,
    ),
) {
    val description = stringResource(
        Res.string.country_picker_chip_content_description,
        countryDisplayName(country),
        country.dialCode,
    )
    val input = rememberSanitizedTextFieldValue(value, onValueChange)
    var hadFocus by remember { mutableStateOf(false) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(DesignTokens.space2)) {
        if (label.isNotEmpty()) Text(label, style = MaterialTheme.typography.labelLarge, color = colors.primaryText)
        OutlinedTextField(
            value = input.value,
            onValueChange = input.onValueChange,
            modifier = Modifier.fillMaxWidth().semantics {
                if (label.isNotEmpty()) contentDescription = label
            }.onFocusChanged {
                if (hadFocus && !it.isFocused) onBlur?.invoke()
                hadFocus = it.isFocused
            },
            singleLine = true,
            isError = error != null,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.primaryText),
            placeholder = { Text(country.exampleNational, color = colors.secondaryText) },
            leadingIcon = {
                Row(
                    modifier = Modifier
                        .then(
                            if (onCountryClick != null) {
                                Modifier.clickable(
                                    role = Role.Button,
                                    onClick = onCountryClick
                                )
                            } else {
                                Modifier
                            }
                        )
                        .semantics(mergeDescendants = true) { contentDescription = description }
                        .padding(horizontal = DesignTokens.space3, vertical = DesignTokens.space4),
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(country.flagEmoji)
                    Text("+${country.dialCode}", color = colors.primaryText, style = MaterialTheme.typography.bodyLarge)
                    if (onCountryClick != null) {
                        Icon(
                            Icons.Outlined.ExpandMore,
                            null,
                            tint = colors.secondaryText,
                            modifier = Modifier.size(DesignTokens.iconInline)
                        )
                    }
                    Spacer(
                        Modifier.width(1.dp).height(DesignTokens.iconList).background(colors.border),
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = imeAction),
            visualTransformation = PhonePrefixTransformation(country.dialCode),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(DesignTokens.radiusMd),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = colors.container,
                focusedContainerColor = colors.container,
                unfocusedBorderColor = colors.border,
            ),
        )
        val supporting = error ?: helper
        if (!supporting.isNullOrEmpty()) {
            Text(
                supporting,
                style = MaterialTheme.typography.bodySmall,
                color = if (error != null) MaterialTheme.colorScheme.error else colors.secondaryText,
            )
        }
    }
}

/** Hide only the repeated explicit prefix; preserve stored text and IME cursor offsets. */
internal class PhonePrefixTransformation(private val dialCode: String) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val prefix = "+$dialCode"
        val hidden = if (text.text.startsWith(prefix)) prefix.length else 0
        return TransformedText(
            AnnotatedString(text.text.drop(hidden)),
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int = (offset - hidden).coerceAtLeast(0)
                override fun transformedToOriginal(offset: Int): Int = offset + hidden
            },
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun CountryPhoneFieldPreview() {
    StitchPadTheme {
        CountryPhoneField(
            Country.GH,
            "+233244123456",
            {},
            label = countryDisplayName(Country.GH),
            onCountryClick = {}
        )
    }
}

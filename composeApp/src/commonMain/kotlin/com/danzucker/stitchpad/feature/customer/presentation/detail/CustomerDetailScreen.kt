package com.danzucker.stitchpad.feature.customer.presentation.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.domain.model.Measurement
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.ui.components.CustomerAvatar
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import com.danzucker.stitchpad.util.ObserveAsEvents
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.cd_edit_customer
import stitchpad.composeapp.generated.resources.customer_delete_cancel
import stitchpad.composeapp.generated.resources.customer_delete_confirm
import stitchpad.composeapp.generated.resources.customer_detail_measurements_section
import stitchpad.composeapp.generated.resources.customer_detail_no_measurements
import stitchpad.composeapp.generated.resources.fab_add_measurement
import stitchpad.composeapp.generated.resources.measurement_delete_message
import stitchpad.composeapp.generated.resources.measurement_delete_title
import stitchpad.composeapp.generated.resources.measurement_female_profile
import stitchpad.composeapp.generated.resources.measurement_male_profile
import stitchpad.composeapp.generated.resources.measurement_unit_cm
import stitchpad.composeapp.generated.resources.measurement_unit_inches

@Composable
fun CustomerDetailRoot(
    onNavigateBack: () -> Unit,
    onNavigateToEditCustomer: (String) -> Unit,
    onNavigateToAddMeasurement: (String) -> Unit,
    onNavigateToEditMeasurement: (String, String) -> Unit
) {
    val viewModel: CustomerDetailViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            CustomerDetailEvent.NavigateBack -> onNavigateBack()
            is CustomerDetailEvent.NavigateToEditCustomer -> onNavigateToEditCustomer(event.customerId)
            is CustomerDetailEvent.NavigateToAddMeasurement -> onNavigateToAddMeasurement(event.customerId)
            is CustomerDetailEvent.NavigateToEditMeasurement -> onNavigateToEditMeasurement(event.customerId, event.measurementId)
        }
    }

    val errorMessage = state.errorMessage?.asString()
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            viewModel.onAction(CustomerDetailAction.OnErrorDismiss)
        }
    }

    CustomerDetailScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailScreen(
    state: CustomerDetailState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onAction: (CustomerDetailAction) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.customer?.name ?: "",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onAction(CustomerDetailAction.OnNavigateBack) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onAction(CustomerDetailAction.OnEditCustomerClick) }) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(Res.string.cd_edit_customer),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onAction(CustomerDetailAction.OnAddMeasurementClick) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(16.dp),
                    spotColor = DesignTokens.primary500
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(Res.string.fab_add_measurement)
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        when {
            state.isLoading -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 80.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    item {
                        CustomerHeaderSection(customer = state.customer)
                    }
                    item {
                        MeasurementsSectionHeader()
                    }
                    if (state.measurements.isEmpty()) {
                        item {
                            MeasurementsEmptyState(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(DesignTokens.space8)
                            )
                        }
                    } else {
                        items(items = state.measurements, key = { it.id }) { measurement ->
                            SwipeableMeasurementItem(
                                measurement = measurement,
                                onClick = { onAction(CustomerDetailAction.OnMeasurementClick(measurement)) },
                                onDelete = { onAction(CustomerDetailAction.OnDeleteMeasurementClick(measurement)) }
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.padding(start = DesignTokens.space4)
                            )
                        }
                    }
                }
            }
        }
    }

    if (state.showDeleteDialog && state.measurementToDelete != null) {
        AlertDialog(
            onDismissRequest = { onAction(CustomerDetailAction.OnDismissDeleteDialog) },
            title = {
                Text(
                    text = stringResource(Res.string.measurement_delete_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(Res.string.measurement_delete_message),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = { onAction(CustomerDetailAction.OnConfirmDelete) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(DesignTokens.radiusMd)
                ) {
                    Text(
                        text = stringResource(Res.string.customer_delete_confirm),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(CustomerDetailAction.OnDismissDeleteDialog) }) {
                    Text(
                        text = stringResource(Res.string.customer_delete_cancel),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            shape = RoundedCornerShape(DesignTokens.radiusXl),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
private fun CustomerHeaderSection(customer: Customer?) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = DesignTokens.space6, horizontal = DesignTokens.space4)
    ) {
        CustomerAvatar(name = customer?.name ?: "", size = 72.dp)
        Spacer(Modifier.height(DesignTokens.space3))
        Text(
            text = customer?.name ?: "",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (customer?.phone?.isNotBlank() == true) {
            Spacer(Modifier.height(DesignTokens.space1))
            Text(
                text = customer.phone,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MeasurementsSectionHeader() {
    Text(
        text = stringResource(Res.string.customer_detail_measurements_section).uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            start = DesignTokens.space4,
            end = DesignTokens.space4,
            top = DesignTokens.space2,
            bottom = DesignTokens.space2
        )
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun MeasurementsEmptyState(modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(64.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(DesignTokens.radiusXl)
                )
        ) {
            Icon(
                imageVector = Icons.Default.Straighten,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(Modifier.height(DesignTokens.space3))
        Text(
            text = stringResource(Res.string.customer_detail_no_measurements),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableMeasurementItem(
    measurement: Measurement,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                false
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                contentAlignment = Alignment.CenterEnd,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(end = DesignTokens.space5)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            MeasurementListItem(measurement = measurement, onClick = onClick)
        }
    }
}

@Composable
private fun MeasurementListItem(measurement: Measurement, onClick: () -> Unit) {
    val profileTitle = if (measurement.gender == CustomerGender.FEMALE) {
        stringResource(Res.string.measurement_female_profile)
    } else {
        stringResource(Res.string.measurement_male_profile)
    }
    val unitLabel = if (measurement.unit == MeasurementUnit.INCHES) {
        stringResource(Res.string.measurement_unit_inches)
    } else {
        stringResource(Res.string.measurement_unit_cm)
    }
    val dateText = remember(measurement.dateTaken) {
        epochToDateString(measurement.dateTaken)
    }
    val subtitleParts = listOfNotNull(dateText.ifBlank { null }, unitLabel)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = DesignTokens.space4, vertical = DesignTokens.space3)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = profileTitle,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitleParts.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (measurement.fields.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                val previewKeys = if (measurement.gender == CustomerGender.FEMALE) {
                    listOf("bust_circumference" to "Bust", "waist" to "Waist", "hip_circumference" to "Hip")
                } else {
                    listOf("chest" to "Chest", "trouser_waist" to "Waist")
                }
                val preview = previewKeys
                    .mapNotNull { (key, label) ->
                        measurement.fields[key]?.let { "$label: ${formatMeasurementValue(it)}" }
                    }
                    .joinToString("  ")
                if (preview.isNotBlank()) {
                    Text(
                        text = preview,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun epochToDateString(epochMs: Long): String {
    if (epochMs == 0L) return ""
    val totalSeconds = epochMs / 1000
    val totalMinutes = totalSeconds / 60
    val totalHours = totalMinutes / 60
    val totalDays = totalHours / 24
    var z = totalDays + 719468
    val era = (if (z >= 0) z else z - 146096) / 146097
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = if (mp < 10) mp + 3 else mp - 9
    val year = y + (if (m <= 2) 1L else 0L)
    val monthNames = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )
    val monthName = monthNames.getOrElse((m - 1).toInt()) { "" }
    return "$d $monthName $year"
}

private fun formatMeasurementValue(value: Double): String {
    return if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        value.toString()
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun CustomerDetailScreenLoadingPreview() {
    StitchPadTheme {
        CustomerDetailScreen(state = CustomerDetailState(isLoading = true), onAction = {})
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun CustomerDetailScreenEmptyPreview() {
    StitchPadTheme {
        CustomerDetailScreen(
            state = CustomerDetailState(
                isLoading = false,
                customer = Customer(
                    id = "1",
                    userId = "u1",
                    name = "Amina Bello",
                    phone = "+234 801 234 5678"
                ),
                measurements = emptyList()
            ),
            onAction = {}
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun CustomerDetailScreenFilledPreview() {
    StitchPadTheme {
        CustomerDetailScreen(
            state = CustomerDetailState(
                isLoading = false,
                customer = Customer(
                    id = "1",
                    userId = "u1",
                    name = "Amina Bello",
                    phone = "+234 801 234 5678"
                ),
                measurements = listOf(
                    Measurement(
                        id = "m1",
                        customerId = "1",
                        gender = CustomerGender.FEMALE,
                        fields = mapOf(
                            "bust_circumference" to 36.0,
                            "waist" to 28.0,
                            "hip_circumference" to 38.0
                        ),
                        unit = MeasurementUnit.INCHES,
                        notes = null,
                        dateTaken = 1700000000000L,
                        createdAt = 1700000000000L
                    ),
                    Measurement(
                        id = "m2",
                        customerId = "1",
                        gender = CustomerGender.MALE,
                        fields = mapOf(
                            "chest" to 40.0,
                            "trouser_waist" to 32.0
                        ),
                        unit = MeasurementUnit.INCHES,
                        notes = null,
                        dateTaken = 1700000000000L,
                        createdAt = 1700000000000L
                    )
                )
            ),
            onAction = {}
        )
    }
}

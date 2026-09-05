package com.example.weathergpt.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.weathergpt.data.LocationResult
import com.example.weathergpt.viewmodel.LocationSearchState
import com.example.weathergpt.viewmodel.LocationViewModel
import kotlinx.coroutines.delay

@Composable
fun LocationSearchDialog(
    currentLocation: String,
    onDismiss: () -> Unit,
    onLocationSelected: (LocationResult) -> Unit,
    onUseCurrentLocation: (() -> Unit)? = null,
    isManualMode: Boolean = true,
    viewModel: LocationViewModel = viewModel()
) {
    var query by remember {
        mutableStateOf("")
    }

    val state by viewModel.state.collectAsState()

    LaunchedEffect(query) {
        delay(350)

        if (query.trim().length >= 2) {
            viewModel.search(query)
        } else {
            viewModel.clear()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Select Weather Location")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                if (onUseCurrentLocation != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onUseCurrentLocation()
                                onDismiss()
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = if (!isManualMode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        border = if (!isManualMode) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = null,
                                tint = if (!isManualMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Use Current Location (Auto GPS)",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (!isManualMode) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "Auto-detect live weather via GPS",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (!isManualMode) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Active",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f))
                        Text(
                            "  OR SEARCH ANY CITY  ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = {
                        Text("Search city or place")
                    },
                    placeholder = {
                        Text("e.g. Mumbai, Delhi, London...")
                    }
                )

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                when (val current = state) {

                    LocationSearchState.Idle -> {
                        Text(
                            "Type a city or place name.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    LocationSearchState.Loading -> {
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    is LocationSearchState.Error -> {
                        Text(
                            current.message,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    is LocationSearchState.Success -> {

                        if (current.results.isEmpty()) {

                            Text(
                                "No locations found.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                        } else {

                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {

                                current.results
                                    .take(5)
                                    .forEach { result ->

                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    onLocationSelected(
                                                        result
                                                    )
                                                }
                                                .padding(
                                                    vertical = 12.dp
                                                )
                                        ) {

                                            Text(
                                                result.name
                                                    ?: "Unknown location",
                                                style =
                                                    MaterialTheme.typography
                                                        .titleMedium
                                            )

                                            Text(
                                                listOfNotNull(
                                                    result.admin1,
                                                    result.country
                                                ).joinToString(", "),
                                                color =
                                                    MaterialTheme.colorScheme
                                                        .onSurfaceVariant,
                                                style =
                                                    MaterialTheme.typography
                                                        .bodySmall
                                            )

                                            if (
                                                result.latitude != null &&
                                                result.longitude != null
                                            ) {
                                                Text(
                                                    "${result.latitude}, ${result.longitude}",
                                                    color =
                                                        MaterialTheme.colorScheme
                                                            .onSurfaceVariant,
                                                    style =
                                                        MaterialTheme.typography
                                                            .labelSmall
                                                )
                                            }
                                        }
                                    }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Cancel")
            }
        }
    )
}

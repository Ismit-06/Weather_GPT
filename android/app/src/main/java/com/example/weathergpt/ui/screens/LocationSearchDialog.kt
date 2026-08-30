package com.example.weathergpt.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    viewModel: LocationViewModel = viewModel()
) {
    var query by remember {
        mutableStateOf(currentLocation)
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
            Text("Choose location")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = {
                        Text("Search location")
                    },
                    placeholder = {
                        Text("Delhi, Hyderabad...")
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

package com.example.ble_uuid_scanner

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.ble_uuid_scanner.ui.theme.BLE_UUID_SCANNERTheme

/**
 * The main and only activity in this application. It sets up the UI and serves as the
 * entry point for user interaction.
 */
class MainActivity : ComponentActivity() {
    // Lazily initializes the MainViewModel, which will be scoped to this Activity.
    private val viewModel: MainViewModel by viewModels()

    // Define a companion object for the logging tag.
    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Log the creation of the Activity for debugging purposes.
        Log.d(TAG, "onCreate: Activity is being created.")

        super.onCreate(savedInstanceState)
        // Set the content of the Activity to be the Jetpack Compose UI.
        setContent {
            BLE_UUID_SCANNERTheme {
                // A Surface container using the 'background' color from the theme.
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    // The main composable that holds the entire screen's UI.
                    MainScreen(viewModel)
                }
            }
        }
    }
}

/**
 * The main screen composable, which observes the ViewModel's state and coordinates
 * permission requests and UI updates.
 */
@Composable
fun MainScreen(viewModel: MainViewModel) {
    // Collect the UI state from the ViewModel as a Compose state.
    // This ensures that the UI recomposes whenever the state changes.
    val uiState by viewModel.uiState.collectAsState()

    // Define the list of required Bluetooth permissions based on the Android API level.
    val bluetoothPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // For Android 12 (S) and above, new permissions are required.
        remember { listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT) }
    } else {
        // For older versions, different permissions are needed.
        remember {
            listOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    // Create a launcher for the permission request.
    // The result of the request is passed to the ViewModel.
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            // Inform the ViewModel whether all permissions were granted.
            viewModel.onPermissionsResult(permissions.values.all { it })
        }
    )

    // A LaunchedEffect that runs once when the composable enters the composition.
    // It triggers the permission request.
    LaunchedEffect(Unit) {
        Log.d("MainActivity", "LaunchedEffect: Requesting permissions.")
        permissionLauncher.launch(bluetoothPermissions.toTypedArray())
    }

    // The main layout composable that arranges the UI elements.
    MainScreenLayout(
        hasPermissions = uiState.hasPermissions,
        isScanning = uiState.isScanning,
        statusMessage = uiState.statusMessage,
        statusType = uiState.statusType,
        availableDevices = uiState.availableDevices,
        filterConnectable = uiState.filterConnectable,
        onFilterConnectableChanged = { viewModel.onFilterConnectableChanged(it) },
        onScanClick = { viewModel.onScanClick() }
    )
}

/**
 * A stateless composable that displays the main UI of the app.
 * It receives all the necessary data and callbacks as parameters.
 */
// Suppress the MissingPermission lint warning. The linter doesn't know that we only call
// this composable after ensuring permissions are granted.
@SuppressLint("MissingPermission")
@Composable
fun MainScreenLayout(
    hasPermissions: Boolean,
    isScanning: Boolean,
    statusMessage: String,
    statusType: StatusType?,
    availableDevices: List<ScanResult>,
    filterConnectable: Boolean,
    onFilterConnectableChanged: (Boolean) -> Unit,
    onScanClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // A LazyColumn is used to efficiently display a potentially long list of devices.
        LazyColumn(
            modifier = Modifier
                .padding(top = 30.dp)
                .weight(1f) // This makes the list take up all available vertical space.
        ) {
            // If no devices are found and not currently scanning, show a message.
            if (availableDevices.isEmpty() && !isScanning) {
                item {
                    val message = if (filterConnectable) "No connectable devices found" else "No devices found"
                    Text(
                        text = message,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                // Display each discovered device as an item in the list.
                items(availableDevices) { result ->
                    // Determine if the device is connectable (only available on Android O and above).
                    val isConnectable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        result.isConnectable
                    } else {
                        true // Assume connectable on older APIs.
                    }

                    // Extract the 16-bit service IDs from the full UUIDs and look up their names.
                    val serviceUuidsString = result.scanRecord?.serviceUuids?.joinToString(", ") { parcelUuid ->
                        val uuidString = parcelUuid.uuid.toString().uppercase()
                        // Standard Bluetooth Base UUID format is 0000XXXX-0000-1000-8000-00805F9B34FB
                        if (uuidString.endsWith("-0000-1000-8000-00805F9B34FB")) {
                            val serviceId = uuidString.substring(4, 8)
                            BleServiceUuids.getServiceName(serviceId) // Look up the human-readable name.
                        } else {
                            uuidString // Show the full UUID for non-standard services.
                        }
                    } ?: "N/A" // Display "N/A" if no service UUIDs are present.

                    // Display the device information in a Text composable.
                    Text(
                        // Using a multiline string for readability.
                        text = """
                            Address: ${result.device.address}
                            Name: ${result.device.name ?: "N/A"}
                            RSSI: ${result.rssi}
                            Service UUIDs: $serviceUuidsString
                            Connectable: $isConnectable
                        """.trimIndent(),
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // A Row to layout the Scan button and the filter checkbox horizontally.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Change the button color to red when scanning is in progress.
            val buttonColors = if (isScanning) {
                ButtonDefaults.buttonColors(containerColor = Color.Red)
            } else {
                ButtonDefaults.buttonColors()
            }

            // The main button to start or stop the scan.
            Button(
                onClick = onScanClick,
                modifier = Modifier.weight(1f), // The button takes up proportional space.
                enabled = hasPermissions, // The button is disabled if permissions are not granted.
                colors = buttonColors
            ) {
                Text(if (isScanning) "Stop Scanning" else "Scan")
            }

            Spacer(modifier = Modifier.width(16.dp))

            // A Row for the "Filter Connectable" checkbox and its label.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Checkbox(
                    checked = filterConnectable,
                    onCheckedChange = onFilterConnectableChanged,
                    enabled = !isScanning // Disable the checkbox while scanning.
                )
                Text("Filter Connectable")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Determine the color for the status message based on its type.
        val statusColor = when (statusType) {
            StatusType.ERROR -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onBackground
        }

        // Display the current status message at the bottom of the screen.
        Text(
            text = statusMessage,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
            color = statusColor
        )
    }
}

// --- Previews for Jetpack Compose --- //

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    BLE_UUID_SCANNERTheme {
        MainScreenLayout(
            hasPermissions = true,
            isScanning = false,
            statusMessage = "Ready to Scan",
            statusType = null,
            availableDevices = emptyList(),
            filterConnectable = true,
            onFilterConnectableChanged = {},
            onScanClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenScanningPreview() {
    BLE_UUID_SCANNERTheme {
        MainScreenLayout(
            hasPermissions = true,
            isScanning = true,
            statusMessage = "Scanning...",
            statusType = null,
            availableDevices = emptyList(),
            filterConnectable = true,
            onFilterConnectableChanged = {},
            onScanClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenNoPermissionPreview() {
    BLE_UUID_SCANNERTheme {
        MainScreenLayout(
            hasPermissions = false,
            isScanning = false,
            statusMessage = "Permissions Denied. Cannot scan.",
            statusType = StatusType.ERROR,
            availableDevices = emptyList(),
            filterConnectable = true,
            onFilterConnectableChanged = {},
            onScanClick = {}
        )
    }
}

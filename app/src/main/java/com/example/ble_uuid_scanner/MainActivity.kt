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

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    // Define a companion object for the logging tag.
    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Add a log message at the absolute start of the Activity's lifecycle.
        // If this message doesn't appear, there is a fundamental issue with the app launching or Logcat settings.
        Log.d(TAG, "onCreate: Activity is being created.")

        super.onCreate(savedInstanceState)
        setContent {
            BLE_UUID_SCANNERTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainScreen(viewModel)
                }
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    // Define the permissions required for Bluetooth functionality based on the Android version.
    val bluetoothPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        remember { listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT) }
    } else {
        remember {
            listOf(
                Manifest.permission.BLUETOOTH,
                // BLUETOOTH_ADMIN is no longer required for LE scanning and can be removed.
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            viewModel.onPermissionsResult(permissions.values.all { it })
        }
    )

    LaunchedEffect(Unit) {
        // This log helps confirm if the composable's LaunchedEffect is being executed.
        Log.d("MainActivity", "LaunchedEffect: Requesting permissions.")
        permissionLauncher.launch(bluetoothPermissions.toTypedArray())
    }

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

// Suppress the MissingPermission lint warning.
// The linter doesn't know that we only call this composable with scan results
// after ensuring that the necessary permissions have been granted by the user.
// The call to `result.device.name` requires the BLUETOOTH_CONNECT permission on API 31+.
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
        LazyColumn(
            modifier = Modifier
                .padding(top = 30.dp)
                .weight(1f) // Takes up all available space, pushing the button to the bottom
        ) {
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
                items(availableDevices) { result ->
                    val isConnectable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        result.isConnectable
                    } else {
                        // Assume connectable if it's in the list (ViewModel has already filtered)
                        true
                    }

                    // Extract the 16-bit service IDs and look up their names.
                    val serviceUuidsString = result.scanRecord?.serviceUuids?.joinToString(", ") { parcelUuid ->
                        val uuidString = parcelUuid.uuid.toString().uppercase()
                        if (uuidString.endsWith("-0000-1000-8000-00805F9B34FB")) {
                            val serviceId = uuidString.substring(4, 8)
                            BleServiceUuids.getServiceName(serviceId)
                        } else {
                            uuidString
                        }
                    } ?: "N/A"

                    Text(
                        text = """
                            Address: ${result.device.address}
                            Name: ${result.device.name ?: "N/A"}
                            Service UUIDs: $serviceUuidsString
                            Connectable: $isConnectable
                        """.trimIndent(),
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Create a Row for the button and checkbox
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val buttonColors = if (isScanning) {
                ButtonDefaults.buttonColors(containerColor = Color.Red)
            } else {
                ButtonDefaults.buttonColors()
            }

            Button(
                onClick = onScanClick,
                modifier = Modifier.weight(1f), // Button takes up proportional space
                enabled = hasPermissions,
                colors = buttonColors
            ) {
                Text(if (isScanning) "Stop Scanning" else "Scan")
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Checkbox for filtering connectable devices
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f) // Checkbox part takes up proportional space
            ) {
                Checkbox(
                    checked = filterConnectable,
                    onCheckedChange = onFilterConnectableChanged,
                    enabled = !isScanning // Disable checkbox while scanning
                )
                Text("Filter Connectable")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val statusColor = when (statusType) {
            StatusType.ERROR -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onBackground
        }

        Text(
            text = statusMessage,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
            color = statusColor // Apply the color
        )
    }
}

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

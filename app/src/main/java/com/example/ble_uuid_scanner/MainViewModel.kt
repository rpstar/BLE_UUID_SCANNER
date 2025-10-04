package com.example.ble_uuid_scanner

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanCallback.SCAN_FAILED_ALREADY_STARTED
import android.bluetooth.le.ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED
import android.bluetooth.le.ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED
import android.bluetooth.le.ScanCallback.SCAN_FAILED_INTERNAL_ERROR
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

// TAG for logging, to easily filter logs in Logcat.
private const val TAG = "MainViewModel"
// Defines the duration for each BLE scan in milliseconds.
private const val SCAN_PERIOD = 4000L
// These UUIDs are not used in a scan-only scenario but are kept for reference.
val HEART_RATE_SERVICE_UUID: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
val HEART_RATE_MEASUREMENT_UUID: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

/**
 * Enum to represent the type of status message, allowing the UI to color-code messages
 * (e.g., show errors in red).
 */
enum class StatusType {
    SUCCESS, ERROR
}

/**
 * The ViewModel for the main screen. It handles the business logic of BLE scanning,
 * permission management, and holding the UI state.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    // Private mutable state flow to hold the UI state. This is internal to the ViewModel.
    private val _uiState = MutableStateFlow(MainUiState())
    // Publicly exposed, read-only state flow for the UI to observe.
    val uiState = _uiState.asStateFlow()

    // Lazily initializes the BluetoothAdapter. This is the entry point for all Bluetooth interaction.
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = getApplication<Application>().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    // Lazily initializes the BluetoothLeScanner, used to perform scan operations.
    private val bluetoothLeScanner by lazy { bluetoothAdapter?.bluetoothLeScanner }

    // Holds the currently active scanning job, allowing it to be cancelled.
    private var scanJob: Job? = null

    // A map to hold discovered devices. Using a map with the device address as the key
    // prevents duplicate entries in the list and allows for updating existing entries.
    private val discoveredDevices = mutableMapOf<String, ScanResult>()

    /**
     * Updates the UI state with the result of the permission request.
     * @param hasPermissions True if all required permissions were granted, false otherwise.
     */
    fun onPermissionsResult(hasPermissions: Boolean) {
        _uiState.value = _uiState.value.copy(hasPermissions = hasPermissions)
    }

    /**
     * Handles the change in the 'Filter Connectable' checkbox state.
     * This updates the UI state, which will be used to filter devices in the scan callback.
     */
    fun onFilterConnectableChanged(isChecked: Boolean) {
        _uiState.value = _uiState.value.copy(filterConnectable = isChecked)
    }

    // Callback object for BLE scan results.
    @SuppressLint("MissingPermission") // Permissions are checked in startScanning() before this is used.
    private val scanCallback = object : ScanCallback() {
        /**
         * Called when a new BLE device is found.
         * @param callbackType Determines how the result was found (e.g., as a batch or individually).
         * @param result The scan result, containing the device, RSSI, and advertising data.
         */
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            Log.d(TAG, "onScanResult received. Result is ${if (result == null) "null" else "not null"}")
            result?.let { scanResult ->
                // Check if the connectable filter is enabled.
                if (!_uiState.value.filterConnectable) {
                    // If the filter is off, add all devices to the map.
                    Log.i(TAG, "Device added (filter off): Name: ${scanResult.device.name ?: "N/A"}, Address: ${scanResult.device.address}")
                    discoveredDevices[scanResult.device.address] = scanResult
                } else {
                    // If the filter is on, check if the device is connectable.
                    val isDeviceConnectable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        scanResult.isConnectable
                    } else {
                        // On older APIs (before Oreo), we can't reliably know if a device is connectable
                        // from the advertising packet alone. We assume it is to be safe.
                        true
                    }

                    if (isDeviceConnectable) {
                        Log.i(TAG, "Connectable device added: Name: ${scanResult.device.name ?: "N/A"}, Address: ${scanResult.device.address}")
                        discoveredDevices[scanResult.device.address] = scanResult
                    } else {
                        Log.d(TAG, "Ignoring non-connectable device due to filter: Address: ${scanResult.device.address}")
                    }
                }
            }
        }

        /**
         * Called when the scan fails.
         * @param errorCode The error code indicating the reason for the failure.
         */
        override fun onScanFailed(errorCode: Int) {
            val errorMessage = when(errorCode) {
                SCAN_FAILED_ALREADY_STARTED -> "Scan already started."
                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "App registration failed."
                SCAN_FAILED_INTERNAL_ERROR -> "Internal error."
                SCAN_FAILED_FEATURE_UNSUPPORTED -> "Feature unsupported."
                else -> "Unknown scan error: $errorCode"
            }
            Log.e(TAG, "onScanFailed: Code: $errorCode, Message: $errorMessage")
            // Update the UI to show the error and stop the scanning indicator.
            _uiState.value = _uiState.value.copy(
                statusMessage = "Scan failed: $errorMessage",
                statusType = StatusType.ERROR,
                isScanning = false
            )
            stopScanning()
        }
    }

    /**
     * Starts a BLE scan if all preconditions are met (permissions, Bluetooth enabled, etc.).
     */
    @SuppressLint("MissingPermission") // Permissions are checked explicitly within this method.
    fun startScanning() {
        Log.d(TAG, "Attempting to start scan...")
        // Early exit if the device does not support BLE.
        if (bluetoothLeScanner == null) {
            Log.e(TAG, "Device does not support BLE scan (bluetoothLeScanner is null).")
            _uiState.value = _uiState.value.copy(statusMessage = "Device does not support BLE scan.", statusType = StatusType.ERROR)
            return
        }

        // Prevent starting a new scan if one is already running.
        if (scanJob?.isActive == true) {
            Log.d(TAG, "Scan is already active.")
            return
        }

        // Check if Bluetooth is enabled.
        if (bluetoothAdapter?.isEnabled == false) {
            Log.w(TAG, "Bluetooth is not enabled.")
            _uiState.value = _uiState.value.copy(statusMessage = "Please turn on Bluetooth to scan for devices.", statusType = StatusType.ERROR)
            return
        }

        // Determine the required permissions based on the Android version.
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12 (API 31) and higher require new Bluetooth permissions.
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            // Older versions require location permission for BLE scanning.
            listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH)
        }

        // Check if all required permissions have been granted.
        if (requiredPermissions.any { ContextCompat.checkSelfPermission(getApplication(), it) != PackageManager.PERMISSION_GRANTED }) {
            Log.w(TAG, "One or more required permissions are not granted.")
            _uiState.value = _uiState.value.copy(statusMessage = "Scan permissions not granted.", statusType = StatusType.ERROR)
            return
        }

        // Location services must be enabled for BLE scanning on many Android versions.
        val locationManager = getApplication<Application>().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!locationManager.isLocationEnabled) {
            Log.w(TAG, "Location services are not enabled.")
            _uiState.value = _uiState.value.copy(statusMessage = "Please enable Location Services to scan for devices.", statusType = StatusType.ERROR)
            return
        }

        // Start a new coroutine for the scanning process.
        scanJob = viewModelScope.launch {
            try {
                // Clear the list of previously discovered devices.
                discoveredDevices.clear()
                Log.d(TAG, "Starting BLE scan.")
                // Update the UI to indicate that a scan is in progress.
                _uiState.value = _uiState.value.copy(isScanning = true, availableDevices = emptyList(), statusMessage = "Scanning...")

                // Use low latency scan mode for faster discovery.
                val scanSettings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
                // Start the scan.
                bluetoothLeScanner?.startScan(null, scanSettings, scanCallback)
                Log.d(TAG, "bluetoothLeScanner.startScan() called.")

                // This loop runs during the scan period to periodically update the UI.
                val scanDurationMillis = SCAN_PERIOD
                var elapsedTime = 0L
                while (elapsedTime < scanDurationMillis) {
                    delay(1000L) // Wait for a second.
                    elapsedTime += 1000L
                    // Update the UI with the current list of devices, sorted by signal strength (RSSI).
                    _uiState.value = _uiState.value.copy(availableDevices = discoveredDevices.values.sortedByDescending { it.rssi })
                }

            } finally {
                // This block is guaranteed to run when the coroutine is cancelled or completes.
                Log.d(TAG, "Scan job 'finally' block reached. Stopping scan.")
                // Stop the BLE scan.
                bluetoothLeScanner?.stopScan(scanCallback)

                // Determine the final status message to display.
                val currentState = _uiState.value
                val finalMessage = when {
                    currentState.statusType == StatusType.ERROR -> currentState.statusMessage // Keep error message if one occurred.
                    scanJob?.isCancelled == true -> "Scan stopped."
                    discoveredDevices.isEmpty() -> "Scan complete. No devices found."
                    else -> "Scan complete."
                }
                Log.d(TAG, "Final status: $finalMessage")
                // Update the UI to show that the scan has finished, along with the final list of devices.
                _uiState.value = currentState.copy(
                    isScanning = false,
                    statusMessage = finalMessage,
                    // Sort the final list by RSSI as well.
                    availableDevices = discoveredDevices.values.sortedByDescending { it.rssi }
                )
            }
        }
    }

    /**
     * Stops the currently active BLE scan.
     */
    fun stopScanning() {
        if (scanJob?.isActive == true) {
            Log.d(TAG, "Stopping scan via stopScanning().")
            // Cancel the scanning coroutine, which will trigger the 'finally' block to stop the hardware scan.
            scanJob?.cancel()
        }
    }

    /**
     * Toggles the scan state when the user clicks the main button.
     */
    fun onScanClick() {
        Log.d(TAG, "onScanClick called. isScanning: ${_uiState.value.isScanning}")
        // Do not scan if permissions are not granted.
        if (!_uiState.value.hasPermissions) {
            _uiState.value = _uiState.value.copy(statusMessage = "Cannot scan: Permissions not granted.")
            return
        }
        // If currently scanning, stop the scan. Otherwise, start a new one.
        if (_uiState.value.isScanning) {
            stopScanning()
        } else {
            startScanning()
        }
    }
}

/**
 * Data class representing the state of the main UI. It's immutable, so a new instance
 * is created for each state change, which works well with Compose.
 */
data class MainUiState(
    // True if all necessary permissions have been granted.
    val hasPermissions: Boolean = false,
    // The message to display in the status bar at the bottom of the screen.
    val statusMessage: String = "Permissions not yet checked.",
    // The type of status message (e.g., SUCCESS, ERROR), for UI styling.
    val statusType: StatusType? = null,
    // True if a BLE scan is currently in progress.
    val isScanning: Boolean = false,
    // The list of discovered BLE devices to display.
    val availableDevices: List<ScanResult> = emptyList(),
    // Flag to control whether to filter for connectable devices only.
    val filterConnectable: Boolean = true
)

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

private const val TAG = "MainViewModel"
private const val SCAN_PERIOD = 4000L
// These UUIDs are not used in a scan-only scenario but are kept for reference.
val HEART_RATE_SERVICE_UUID: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
val HEART_RATE_MEASUREMENT_UUID: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

enum class StatusType {
    SUCCESS, ERROR
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState = _uiState.asStateFlow()

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = getApplication<Application>().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val bluetoothLeScanner by lazy { bluetoothAdapter?.bluetoothLeScanner }

    private var scanJob: Job? = null

    // A map to hold scan results, using the device address as a key to avoid duplicates.
    private val discoveredDevices = mutableMapOf<String, ScanResult>()

    fun onPermissionsResult(hasPermissions: Boolean) {
        _uiState.value = _uiState.value.copy(hasPermissions = hasPermissions)
    }

    /**
     * Handles the change in the 'Filter Connectable' checkbox state.
     */
    fun onFilterConnectableChanged(isChecked: Boolean) {
        _uiState.value = _uiState.value.copy(filterConnectable = isChecked)
    }

    // Callback for scan results.
    @SuppressLint("MissingPermission") // Permissions are checked in startScanning() before this is used.
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            Log.d(TAG, "onScanResult received. Result is ${if (result == null) "null" else "not null"}")
            result?.let { scanResult ->
                // If the filter is off, add all devices. If it's on, only add connectable devices.
                if (!_uiState.value.filterConnectable) {
                    Log.i(TAG, "Device added (filter off): Name: ${scanResult.device.name ?: "N/A"}, Address: ${scanResult.device.address}")
                    discoveredDevices[scanResult.device.address] = scanResult
                } else {
                    val isDeviceConnectable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        scanResult.isConnectable
                    } else {
                        // On older APIs, we can't reliably know. Assume connectable.
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

        override fun onScanFailed(errorCode: Int) {
            val errorMessage = when(errorCode) {
                SCAN_FAILED_ALREADY_STARTED -> "Scan already started."
                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "App registration failed."
                SCAN_FAILED_INTERNAL_ERROR -> "Internal error."
                SCAN_FAILED_FEATURE_UNSUPPORTED -> "Feature unsupported."
                else -> "Unknown scan error: $errorCode"
            }
            Log.e(TAG, "onScanFailed: Code: $errorCode, Message: $errorMessage")
            _uiState.value = _uiState.value.copy(
                statusMessage = "Scan failed: $errorMessage",
                statusType = StatusType.ERROR,
                isScanning = false
            )
            stopScanning()
        }
    }

    @SuppressLint("MissingPermission") // Permissions are checked before this is called
    fun startScanning() {
        Log.d(TAG, "Attempting to start scan...")
        if (bluetoothLeScanner == null) {
            Log.e(TAG, "Device does not support BLE scan (bluetoothLeScanner is null).")
            _uiState.value = _uiState.value.copy(statusMessage = "Device does not support BLE scan.", statusType = StatusType.ERROR)
            return
        }

        if (scanJob?.isActive == true) {
            Log.d(TAG, "Scan is already active.")
            return
        }

        if (bluetoothAdapter?.isEnabled == false) {
            Log.w(TAG, "Bluetooth is not enabled.")
            _uiState.value = _uiState.value.copy(statusMessage = "Please turn on Bluetooth to scan for devices.", statusType = StatusType.ERROR)
            return
        }

        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH)
        }

        if (requiredPermissions.any { ContextCompat.checkSelfPermission(getApplication(), it) != PackageManager.PERMISSION_GRANTED }) {
            Log.w(TAG, "One or more required permissions are not granted.")
            _uiState.value = _uiState.value.copy(statusMessage = "Scan permissions not granted.", statusType = StatusType.ERROR)
            return
        }

        val locationManager = getApplication<Application>().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!locationManager.isLocationEnabled) {
            Log.w(TAG, "Location services are not enabled.")
            _uiState.value = _uiState.value.copy(statusMessage = "Please enable Location Services to scan for devices.", statusType = StatusType.ERROR)
            return
        }

        scanJob = viewModelScope.launch {
            try {
                discoveredDevices.clear()
                Log.d(TAG, "Starting BLE scan.")
                _uiState.value = _uiState.value.copy(isScanning = true, availableDevices = emptyList(), statusMessage = "Scanning...")

                val scanSettings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
                bluetoothLeScanner?.startScan(null, scanSettings, scanCallback)
                Log.d(TAG, "bluetoothLeScanner.startScan() called.")

                val scanDurationMillis = SCAN_PERIOD
                var elapsedTime = 0L
                while (elapsedTime < scanDurationMillis) {
                    delay(1000L)
                    elapsedTime += 1000L
                    _uiState.value = _uiState.value.copy(availableDevices = discoveredDevices.values.toList())
                }

            } finally {
                Log.d(TAG, "Scan job 'finally' block reached. Stopping scan.")
                bluetoothLeScanner?.stopScan(scanCallback)

                val currentState = _uiState.value
                val finalMessage = when {
                    currentState.statusType == StatusType.ERROR -> currentState.statusMessage
                    scanJob?.isCancelled == true -> "Scan stopped."
                    discoveredDevices.isEmpty() -> "Scan complete. No devices found."
                    else -> "Scan complete."
                }
                Log.d(TAG, "Final status: $finalMessage")
                _uiState.value = currentState.copy(
                    isScanning = false,
                    statusMessage = finalMessage,
                    availableDevices = discoveredDevices.values.toList()
                )
            }
        }
    }

    fun stopScanning() {
        if (scanJob?.isActive == true) {
            Log.d(TAG, "Stopping scan via stopScanning().")
            scanJob?.cancel()
        }
    }

    fun onScanClick() {
        Log.d(TAG, "onScanClick called. isScanning: ${_uiState.value.isScanning}")
        if (!_uiState.value.hasPermissions) {
            _uiState.value = _uiState.value.copy(statusMessage = "Cannot scan: Permissions not granted.")
            return
        }
        if (_uiState.value.isScanning) {
            stopScanning()
        } else {
            startScanning()
        }
    }
}

/**
 * A simplified UI state for a scan-only application.
 */
data class MainUiState(
    val hasPermissions: Boolean = false,
    val statusMessage: String = "Permissions not yet checked.",
    val statusType: StatusType? = null,
    val isScanning: Boolean = false,
    val availableDevices: List<ScanResult> = emptyList(),
    // Add a flag to control the connectable filter, defaulting to true.
    val filterConnectable: Boolean = true
)

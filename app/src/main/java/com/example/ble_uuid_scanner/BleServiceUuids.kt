package com.example.ble_uuid_scanner

/**
 * A helper object to convert Bluetooth Service UUIDs into human-readable strings.
 * The values are based on the official Bluetooth SIG assigned numbers for services.
 * https://www.bluetooth.com/specifications/assigned-numbers/services/
 */
object BleServiceUuids {
    private val serviceUuidMap = mapOf(
        "1800" to "Generic Access",
        "1801" to "Generic Attribute",
        "1802" to "Immediate Alert",
        "1803" to "Link Loss",
        "1804" to "Tx Power",
        "1805" to "Current Time Service",
        "1806" to "Reference Time Update Service",
        "1807" to "Next DST Change Service",
        "1808" to "Glucose",
        "1809" to "Health Thermometer",
        "180A" to "Device Information",
        "180D" to "Heart Rate",
        "180F" to "Battery Service",
        "1810" to "Blood Pressure",
        "1811" to "Alert Notification Service",
        "1812" to "Human Interface Device",
        "1813" to "Scan Parameters",
        "1814" to "Running Speed and Cadence",
        "1815" to "Automation IO",
        "1816" to "Cycling Speed and Cadence",
        "1818" to "Cycling Power",
        "1819" to "Location and Navigation",
        "181A" to "Environmental Sensing",
        "181B" to "Body Composition",
        "181C" to "User Data",
        "181D" to "Weight Scale",
        "181E" to "Bond Management Service",
        "181F" to "Continuous Glucose Monitoring",
        "1820" to "Internet Protocol Support Service",
        "1821" to "Indoor Positioning",
        "1822" to "Pulse Oximeter Service",
        "1823" to "HTTP Proxy",
        "1824" to "Transport Discovery",
        "1825" to "Object Transfer Service",
        "1826" to "Fitness Machine",
        "1827" to "Mesh Provisioning Service",
        "1828" to "Mesh Proxy Service"
    )

    /**
     * Returns the service name for a given 16-bit service ID.
     * @param serviceId The 4-character hex string for the service ID.
     * @return The human-readable service name, or the original hex ID if not found.
     */
    fun getServiceName(serviceId: String): String {
        return serviceUuidMap[serviceId.uppercase()] ?: "0x$serviceId"
    }
}

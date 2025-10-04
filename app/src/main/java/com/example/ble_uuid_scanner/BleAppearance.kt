package com.example.ble_uuid_scanner

/**
 * A helper object to convert Bluetooth Appearance values into human-readable strings.
 * The values are based on the official Bluetooth SIG assigned numbers.
 * https://www.bluetooth.com/specifications/assigned-numbers/generic-access-profile/
 */
object BleAppearance {
    private val appearanceMap = mapOf(
        0 to "Unknown",
        64 to "Generic Phone",
        128 to "Generic Computer",
        192 to "Generic Watch",
        193 to "Watch: Sports Watch",
        256 to "Generic Clock",
        320 to "Generic Display",
        384 to "Generic Remote Control",
        448 to "Generic Eye-glasses",
        512 to "Generic Tag",
        576 to "Generic Keyring",
        640 to "Generic Media Player",
        704 to "Generic Barcode Scanner",
        768 to "Generic Thermometer",
        769 to "Thermometer: Ear",
        832 to "Generic Heart Rate Sensor",
        833 to "Heart Rate Sensor: Heart Rate Belt",
        896 to "Generic Blood Pressure",
        897 to "Blood Pressure: Arm",
        898 to "Blood Pressure: Wrist",
        960 to "Human Interface Device (HID)",
        961 to "Keyboard",
        962 to "Mouse",
        963 to "Joystick",
        964 to "Gamepad",
        965 to "Digitizer Tablet",
        966 to "Card Reader",
        967 to "Digital Pen",
        968 to "Barcode Scanner",
        1024 to "Generic Glucose Meter",
        1088 to "Generic: Running Walking Sensor",
        1089 to "Running Walking Sensor: In-Shoe",
        1090 to "Running Walking Sensor: On-Shoe",
        1091 to "Running Walking Sensor: On-Hip",
        1152 to "Generic: Cycling",
        1153 to "Cycling: Cycling Computer",
        1154 to "Cycling: Speed Sensor",
        1155 to "Cycling: Cadence Sensor",
        1156 to "Cycling: Power Sensor",
        1157 to "Cycling: Speed and Cadence Sensor",
        3136 to "Generic: Pulse Oximeter",
        3200 to "Generic: Weight Scale",
        3264 to "Generic: Personal Mobility Device",
        3328 to "Generic: Continuous Glucose Monitor",
        3392 to "Generic: Insulin Pump",
        5184 to "Generic Outdoor Sports Activity",
        5185 to "Location Display Device",
        5186 to "Location and Navigation Display Device",
        5187 to "Location Pod",
        5188 to "Location and Navigation Pod"
    )

    fun getAppearanceString(appearance: Int): String {
        return appearanceMap[appearance] ?: "Unknown or Reserved"
    }
}

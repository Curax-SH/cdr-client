package com.swisscom.health.des.cdr.client

/**
 * Provides OS information for the current platform.
 * Returns a string like "Windows Server 2019", "Windows 11", "macOS 14.2.1", "Linux (Ubuntu)", etc.
 */
internal fun getOsInfo(): String {
    val osName = System.getProperty("os.name") ?: "Unknown"
    val osVersion = System.getProperty("os.version") ?: "Unknown"
    val osArch = System.getProperty("os.arch") ?: "Unknown"

    return buildString {
        append(osName)
        if (osVersion.isNotBlank() && osVersion != "Unknown") {
            append(" ")
            append(osVersion)
        }
        if (osArch.isNotBlank() && osArch != "Unknown") {
            append(" (")
            append(osArch)
            append(")")
        }
    }
}


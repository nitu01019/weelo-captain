package com.weelo.logistics.core.utils

import java.text.SimpleDateFormat
import java.util.*

/**
 * Extension functions for common operations
 */

// String extensions
fun String.isValidEmail(): Boolean {
    return android.util.Patterns.EMAIL_ADDRESS.matcher(this).matches()
}

fun String.isValidPhone(): Boolean {
    val phonePattern = "^[+]?[0-9]{10,13}$"
    return this.matches(phonePattern.toRegex())
}

fun String.isValidLicenseNumber(): Boolean {
    // Indian driving license pattern
    val licensePattern = "^[A-Z]{2}[0-9]{2}[0-9]{11}$"
    return this.matches(licensePattern.toRegex())
}

// Date extensions
fun Long.toFormattedDate(pattern: String = "dd MMM yyyy"): String {
    val formatter = SimpleDateFormat(pattern, Locale.getDefault())
    return formatter.format(Date(this))
}

fun Long.toFormattedDateTime(pattern: String = "dd MMM yyyy, hh:mm a"): String {
    val formatter = SimpleDateFormat(pattern, Locale.getDefault())
    return formatter.format(Date(this))
}

// Double extensions
fun Double.toFormattedCurrency(): String {
    return "₹${String.format("%.2f", this)}"
}

fun Double.toFormattedDistance(): String {
    return "${String.format("%.2f", this)} km"
}

// List extensions
fun <T> List<T>.chunkedSafely(size: Int): List<List<T>> {
    return if (isEmpty()) emptyList() else chunked(size)
}

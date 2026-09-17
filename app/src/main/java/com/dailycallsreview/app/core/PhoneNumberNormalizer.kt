package com.dailycallsreview.app.core

object PhoneNumberNormalizer {
    fun last10Digits(rawNumber: String): String {
        val digitsOnly = rawNumber.filter { it.isDigit() }
        return if (digitsOnly.length <= 10) digitsOnly else digitsOnly.takeLast(10)
    }
}

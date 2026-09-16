package com.georgeapp.bulksmsreply

/**
 * Very small helper for comparing phone numbers that may be formatted
 * differently in different places ("(555) 123-4567" vs "+15551234567"
 * vs "5551234567"). This is NOT a full phone-number library - it just
 * strips everything but digits and compares the last 10, which is good
 * enough for matching US numbers against our own block list. If this
 * app grows beyond personal/US use, swap this for Google's libphonenumber.
 */
object PhoneNumbers {

    fun normalize(rawNumber: String): String {
        val digitsOnly = rawNumber.filter { it.isDigit() }
        return if (digitsOnly.length > 10) digitsOnly.takeLast(10) else digitsOnly
    }

    fun sameNumber(a: String, b: String): Boolean {
        val na = normalize(a)
        val nb = normalize(b)
        return na.isNotEmpty() && na == nb
    }
}

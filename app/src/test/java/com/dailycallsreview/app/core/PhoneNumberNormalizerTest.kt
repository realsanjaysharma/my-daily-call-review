package com.dailycallsreview.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneNumberNormalizerTest {

    @Test
    fun `strips plus and country code to last 10 digits`() {
        assertEquals("9876543210", PhoneNumberNormalizer.last10Digits("+91 98765 43210"))
    }

    @Test
    fun `strips dashes and spaces`() {
        assertEquals("9876543210", PhoneNumberNormalizer.last10Digits("987-654-3210"))
    }

    @Test
    fun `number shorter than 10 digits is returned as is`() {
        assertEquals("12345", PhoneNumberNormalizer.last10Digits("12345"))
    }

    @Test
    fun `already normalized number is unchanged`() {
        assertEquals("9876543210", PhoneNumberNormalizer.last10Digits("9876543210"))
    }
}

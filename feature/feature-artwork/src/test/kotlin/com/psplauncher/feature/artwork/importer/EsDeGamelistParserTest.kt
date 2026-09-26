package com.psplauncher.feature.artwork.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EsDeGamelistParserTest {
    @Test
    fun `ES releasedate format maps to a year`() {
        assertEquals(2001, EsDeGamelistParser.yearOf("20011217T000000"))
        assertEquals(1996, EsDeGamelistParser.yearOf("19960923T000000"))
        assertEquals(2001, EsDeGamelistParser.yearOf(" 20011217T000000 "))
    }

    @Test
    fun `garbage release dates are rejected`() {
        assertNull(EsDeGamelistParser.yearOf(null))
        assertNull(EsDeGamelistParser.yearOf(""))
        assertNull(EsDeGamelistParser.yearOf("unknown"))
        assertNull(EsDeGamelistParser.yearOf("0000"))
        assertNull(EsDeGamelistParser.yearOf("9999"))
    }
}

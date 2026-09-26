package com.psplauncher.feature.xmb.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class QuickSearchTest {
    @Test
    fun `plain words are a search`() {
        assertEquals(QuickSearchAction.Search("cave story"), quickSearchActionFor("cave story"))
        assertEquals(QuickSearchAction.Search("psp homebrew"), quickSearchActionFor("  psp homebrew  "))
    }

    @Test
    fun `a bare domain is opened, with a scheme added`() {
        assertEquals(QuickSearchAction.Open("https://news.bbc.co.uk"), quickSearchActionFor("news.bbc.co.uk"))
        assertEquals(QuickSearchAction.Open("https://bbc.co.uk/news"), quickSearchActionFor("bbc.co.uk/news"))
    }

    @Test
    fun `a scheme is taken at its word and never rewritten`() {
        assertEquals(QuickSearchAction.Open("http://192.168.1.4:8080"), quickSearchActionFor("http://192.168.1.4:8080"))
        assertEquals(QuickSearchAction.Open("https://example.com"), quickSearchActionFor("https://example.com"))
    }

    @Test
    fun `a dotted thing that is not a host stays a search`() {
        assertEquals(QuickSearchAction.Search("3.5"), quickSearchActionFor("3.5"))
        assertEquals(QuickSearchAction.Search("v1.2.3"), quickSearchActionFor("v1.2.3"))
        assertEquals(QuickSearchAction.Search("mario64.z64"), quickSearchActionFor("mario64.z64"))
        assertEquals(QuickSearchAction.Search("half-life 2.exe"), quickSearchActionFor("half-life 2.exe"))
        assertEquals(QuickSearchAction.Search("example."), quickSearchActionFor("example."))
        assertEquals(QuickSearchAction.Search(".com"), quickSearchActionFor(".com"))
    }

    @Test
    fun `an empty box does nothing at all`() {
        assertEquals(QuickSearchAction.None, quickSearchActionFor(""))
        assertEquals(QuickSearchAction.None, quickSearchActionFor("   "))
        assertEquals(QuickSearchAction.None, quickSearchActionFor("\t\n"))
    }
}

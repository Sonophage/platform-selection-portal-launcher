package com.psplauncher.feature.xmb.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Whether Quick Search searches or opens.
 *
 * The rule is a guess, and the test's job is to pin which way it guesses in the cases that
 * actually turn up on a handheld: a game name with numbers in it, a bare domain, a domain with a
 * path, something already carrying a scheme, and an empty box. Getting it wrong is not fatal --
 * a wrong guess costs one search result page -- but getting it wrong SILENTLY and differently
 * after an edit is how a box that used to open your wiki starts searching for its name.
 */
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
        // The cases this rule exists for. Each one has a dot and no spaces, which is most of the
        // way to looking like an address, and each one is plainly something you meant to look up.
        assertEquals(QuickSearchAction.Search("3.5"), quickSearchActionFor("3.5"))
        assertEquals(QuickSearchAction.Search("v1.2.3"), quickSearchActionFor("v1.2.3"))
        assertEquals(QuickSearchAction.Search("mario64.z64"), quickSearchActionFor("mario64.z64"))
        assertEquals(QuickSearchAction.Search("half-life 2.exe"), quickSearchActionFor("half-life 2.exe"))
        assertEquals(QuickSearchAction.Search("example."), quickSearchActionFor("example."))
        assertEquals(QuickSearchAction.Search(".com"), quickSearchActionFor(".com"))
    }

    @Test
    fun `an empty box does nothing at all`() {
        // Not a search for "": firing a web-search intent with no query opens a browser on a
        // blank results page, which looks exactly like the app doing something stupid.
        assertEquals(QuickSearchAction.None, quickSearchActionFor(""))
        assertEquals(QuickSearchAction.None, quickSearchActionFor("   "))
        assertEquals(QuickSearchAction.None, quickSearchActionFor("\t\n"))
    }
}

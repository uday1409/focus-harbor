package dev.focusharbor.app.focus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExitTextTest {
    private val text = "Any ten words I write should let me leave now"

    @Test
    fun acceptsTenOrMoreWordsButRejectsNine() {
        assertTrue(ExitText.isValid(text))
        assertTrue(ExitText.isValid("$text please"))
        assertFalse(ExitText.isValid("one two three four five six seven eight nine"))
        assertFalse(ExitText.isValid(""))
        assertEquals(0, ExitText.wordCount(" \t\n"))
    }

    @Test
    fun countsAcrossSpacesTabsNewlinesAndUnicodeSeparators() {
        val entered = "\u00A0Any\u2003ten\twords\nI  write should let me leave now\u00A0"
        assertEquals(10, ExitText.wordCount(entered))
        assertTrue(ExitText.isValid(entered))
    }

    @Test
    fun acceptsAnyWordingCaseAndPunctuationWithoutMatching() {
        assertTrue(ExitText.isValid("Different words do not need to match any preset anymore"))
        assertTrue(ExitText.isValid(text.uppercase()))
        assertTrue(ExitText.isValid("$text!"))
    }

    @Test
    fun punctuationAndEmojiAloneDoNotCountAsWords() {
        assertEquals(0, ExitText.wordCount("!!! ... -- \uD83C\uDF3F"))
        assertFalse(ExitText.isValid("one two three four five six seven eight nine !!!"))
        assertTrue(ExitText.isValid("$text !!! \uD83C\uDF3F"))
    }

    @Test
    fun supportsUnicodeWordsAndHasNoUpperWordLimit() {
        val unicodePhrase = "\u65e5\u672c \u8a9e attention and return to what matters right now"
        assertTrue(ExitText.isValid(unicodePhrase))
        assertTrue(ExitText.isValid("\uD801\uDC00 two three four five six seven eight nine ten"))
        val longText = "$text ${"extra ".repeat(1_000)}"
        assertTrue(ExitText.isValid(longText))
        assertEquals(1_010, ExitText.wordCount(longText))
    }
}

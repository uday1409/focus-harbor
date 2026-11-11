package dev.focusharbor.app.focus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExitPhraseTest {
    private val phrase = "I choose my attention and return to what matters now"

    @Test
    fun requiresExactlyTenWords() {
        assertTrue(ExitPhrase.isValid(phrase))
        assertFalse(ExitPhrase.isValid("choose my attention and return to what matters now"))
        assertFalse(ExitPhrase.isValid("$phrase please"))
        assertFalse(ExitPhrase.isValid(""))
        assertEquals(0, ExitPhrase.wordCount(" \t\n"))
    }

    @Test
    fun normalizesSpacesTabsNewlinesAndUnicodeSeparators() {
        val entered = "\u00A0I\u2003choose\tmy\nattention  and return to what matters now\u00A0"
        assertEquals(phrase, ExitPhrase.normalize(entered))
        assertEquals(10, ExitPhrase.wordCount(entered))
        assertTrue(ExitPhrase.matches(phrase, entered))
    }

    @Test
    fun casePunctuationAndWordOrderMustMatch() {
        assertFalse(ExitPhrase.matches(phrase, phrase.lowercase()))
        assertFalse(ExitPhrase.matches(phrase, "$phrase."))
        assertFalse(
            ExitPhrase.matches(phrase, "I choose my attention and return to what now matters"),
        )
    }

    @Test
    fun rejectsPunctuationOnlyTokensAndOversizedInput() {
        assertFalse(ExitPhrase.isValid("I choose my attention and return to what matters !!!"))
        assertFalse(ExitPhrase.isValid("${"x".repeat(300)} a b c d e f g h i"))
        assertFalse(ExitPhrase.matches("", ""))
    }

    @Test
    fun supportsNonEnglishWordsWithoutFoldingCharacters() {
        val unicodePhrase = "\u65e5\u672c \u8a9e attention and return to what matters right now"
        assertTrue(ExitPhrase.isValid(unicodePhrase))
        assertTrue(ExitPhrase.matches(unicodePhrase, unicodePhrase))
        assertTrue(ExitPhrase.isValid("\uD801\uDC00 two three four five six seven eight nine ten"))
        assertFalse(ExitPhrase.matches(phrase, phrase.replace("my", "m\u0443")))
    }
}

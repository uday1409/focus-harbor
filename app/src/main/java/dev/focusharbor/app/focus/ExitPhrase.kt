package dev.focusharbor.app.focus

object ExitPhrase {
    const val WORDS = 10
    const val MAX_LENGTH = 300

    private val whitespace = Regex("[\\p{Z}\\s]+")

    fun normalize(text: String): String =
        text.trim { it.isWhitespace() || Character.isSpaceChar(it) }
            .split(whitespace)
            .filter(String::isNotEmpty)
            .joinToString(" ")

    fun wordCount(text: String): Int =
        normalize(text).split(' ').count(String::isNotEmpty)

    fun isValid(text: String): Boolean {
        if (text.length > MAX_LENGTH) return false
        val words = normalize(text).split(' ').filter(String::isNotEmpty)
        return words.size == WORDS &&
            words.all { word -> word.codePoints().anyMatch(Character::isLetterOrDigit) }
    }

    fun matches(expected: String, entered: String): Boolean =
        isValid(expected) && isValid(entered) && normalize(expected) == normalize(entered)
}

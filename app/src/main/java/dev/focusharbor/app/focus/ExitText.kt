package dev.focusharbor.app.focus

object ExitText {
    const val MIN_WORDS = 10

    private val tokens = Regex("[^\\p{Z}\\s]+")

    private fun words(text: String) = tokens.findAll(text).filter {
        it.value.codePoints().anyMatch(Character::isLetterOrDigit)
    }

    fun wordCount(text: String): Int = words(text).count()

    fun isValid(text: String): Boolean = words(text).take(MIN_WORDS).count() == MIN_WORDS
}

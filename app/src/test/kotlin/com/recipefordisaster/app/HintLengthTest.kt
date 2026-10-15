package com.recipefordisaster.app

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** The hint line under the restaurant is fixed at two lines, so a longer one gets cut off with "…". */
class HintLengthTest {

    @Test
    fun `every hint fits on two lines`() {
        val strings = File("src/main/res/values/strings.xml").readText()
        val hints = Regex("""<string name="((?:hint|morning_hint)_[a-z_0-9]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(strings)
            .map { it.groupValues[1] to it.groupValues[2] }
            .toList()
        assertTrue("no hints found", hints.isNotEmpty())

        val tooLong = hints.filter { (_, text) ->
            // A name or number fills in for each placeholder: allow a long name.
            text.replace(Regex("%\\d\\\$s"), "Montgomery").replace(Regex("%\\d\\\$d"), "8").replace("\\'", "'").length > MAX_HINT_CHARS
        }
        assertTrue("These hints are too long for two lines: ${tooLong.map { it.first }}", tooLong.isEmpty())
    }

    private companion object {
        /** About what fits on two lines of the hint text on a narrow phone. */
        const val MAX_HINT_CHARS = 85
    }
}

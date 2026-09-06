package dev.wuxie233.codecarry.ui.screens.chat

import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.SyntaxThemes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SafeMarkdownHighlightingTest {
    @Test
    fun buildSafeHighlightedAnnotatedString_handlesReversedHighlightRanges() {
        val code = "key: maven-${'$'}{{ runner.os }}-${'$'}{{ hashFiles('**/pom.xml') }}-${'$'}{{ hashFiles('**/*.java') }}"
        val builder = Highlights.Builder()
            .theme(SyntaxThemes.default(darkMode = false))

        val annotated = buildSafeHighlightedAnnotatedString(
            code = code,
            language = null,
            highlightsBuilder = builder,
        )

        assertEquals(code, annotated.text)
    }

    @Test
    fun markdownCodeCopyPayload_usesExactInnerFenceTextWithoutMarkers() {
        val markdown = """
            ```kotlin
            val answer = 42
            println(answer)
            ```
        """.trimIndent()

        val inner = extractMarkdownFenceInnerCode(markdown)

        assertEquals("val answer = 42\nprintln(answer)", inner)
        assertFalse(inner.contains("`"))
        assertEquals(inner, markdownCodeCopyPayload(inner))
    }

    @Test
    fun markdownCodeCopyPayload_stripsTildeFenceAndLanguage() {
        val markdown = """
            ~~~python
            print("hi")
            ~~~
        """.trimIndent()

        val inner = extractMarkdownFenceInnerCode(markdown)

        assertEquals("print(\"hi\")", inner)
        assertFalse(inner.contains("~~~"))
        assertEquals(inner, markdownCodeCopyPayload(inner))
    }

    @Test
    fun markdownCodeCopyPayload_keepsMermaidFallbackSourceWithoutFences() {
        val markdown = """
            ```mermaid
            this is not a mermaid diagram
            ```
        """.trimIndent()

        val inner = extractMarkdownFenceInnerCode(markdown)

        assertEquals("this is not a mermaid diagram", inner)
        assertEquals(inner, markdownCodeCopyPayload(inner))
    }

    @Test
    fun markdownCodeCopyPayload_preservesIndentedInnerBodyAsGiven() {
        val inner = "fun main() {\n    println(\"ok\")\n}"

        assertEquals(inner, markdownCodeCopyPayload(inner))
    }
}

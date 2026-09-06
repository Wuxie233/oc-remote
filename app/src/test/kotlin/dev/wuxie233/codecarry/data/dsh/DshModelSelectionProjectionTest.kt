package dev.wuxie233.codecarry.data.dsh

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DshModelSelectionProjectionTest {
    private val default = DshModelSelection("default-provider", "default-model", "low")

    @Test
    fun `session next selection wins over both last used and host default`() {
        val projection = Json.parseToJsonElement("""{"lastUsed":{"provider":"old","model":"old"},"next":{"provider":"preset-provider","model":"preset-model","reasoningEffort":"high"}}""")
        assertEquals(DshModelSelection("preset-provider", "preset-model", "high"), dshProjectedModelSelection(projection, default))
    }

    @Test
    fun `only an explicit null next allows host default`() {
        assertEquals(default, dshProjectedModelSelection(Json.parseToJsonElement("""{"next":null}"""), default))
        assertNull(dshProjectedModelSelection(null, default))
        assertNull(dshProjectedModelSelection(Json.parseToJsonElement("""{}"""), default))
        assertNull(dshProjectedModelSelection(Json.parseToJsonElement("""{"next":null}"""), null))
    }

    @Test
    fun `malformed selection cannot silently switch to host default`() {
        assertNull(dshProjectedModelSelection(Json.parseToJsonElement("""{"next":{"provider":"p"}}"""), default))
        assertNull(dshProjectedModelSelection(Json.parseToJsonElement("""{"next":{"provider":"","model":"m"}}"""), default))
    }

    @Test
    fun `model without effort clears the earlier effort`() {
        assertEquals(DshModelSelection("p", "m"), dshProjectedModelSelection(Json.parseToJsonElement("""{"next":{"provider":"p","model":"m"}}"""), default))
    }

    @Test
    fun `late model receipt cannot override newer user choice or reconnect`() {
        assertTrue(isCurrentDshModelReceipt(2, 4, 2, 4, true))
        assertFalse(isCurrentDshModelReceipt(2, 3, 2, 4, true))
        assertFalse(isCurrentDshModelReceipt(1, 4, 2, 4, true))
        assertFalse(isCurrentDshModelReceipt(2, 4, 2, 4, false))
    }

    @Test
    fun `keep a still advertised effort`() {
        assertEquals("high", compatibleDshReasoningEffort("high", listOf("low", "high"), "low"))
    }

    @Test
    fun `fallback to default when current effort is not advertised`() {
        assertEquals("low", compatibleDshReasoningEffort("high", listOf("low", "medium"), "low"))
    }

    @Test
    fun `omit effort when the model advertises none`() {
        assertNull(compatibleDshReasoningEffort("high", emptyList(), "low"))
        assertNull(compatibleDshReasoningEffort("high", emptyList(), null))
    }

    @Test
    fun `empty current uses default when advertised`() {
        assertEquals("low", compatibleDshReasoningEffort(null, listOf("low", "high"), "low"))
        assertEquals("low", compatibleDshReasoningEffort("", listOf("low", "high"), "low"))
    }

    @Test
    fun `unlisted default or first advertised when neither current nor default is advertised`() {
        assertEquals("medium", compatibleDshReasoningEffort("high", listOf("x", "y"), "medium"))
        assertEquals("x", compatibleDshReasoningEffort("high", listOf("x", "y"), null))
    }
}

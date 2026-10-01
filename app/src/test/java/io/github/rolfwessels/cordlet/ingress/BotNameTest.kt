package io.github.rolfwessels.cordlet.ingress

import org.junit.Assert.*
import org.junit.Test

class BotNameTest {
    private val config = IngressConfig(DEFAULT_ENDPOINT, "test-device-token")
    private fun rejects(name: String) {
        try { validateConfig(config.copy(botName = name)); fail("Expected invalid bot name rejection") }
        catch (_: IllegalArgumentException) { }
    }
    @Test fun oldConfigurationDefaultsToHermes() { assertEquals("Hermes", config.botName) }
    @Test fun acceptsCustomUnicodeNameWithoutNormalizing() {
        val named = config.copy(botName = "Wren · 鳥")
        validateConfig(named)
        assertEquals("Wren · 鳥", named.botName)
    }
    @Test fun rejectsBlankNames() { for (name in listOf("", " ", "\t")) rejects(name) }
    @Test fun enforcesSixtyFourCharacterBound() {
        validateConfig(config.copy(botName = "x".repeat(64)))
        rejects("x".repeat(65))
    }
    @Test fun rejectsControlCharacters() {
        for (control in listOf('\u0000', '\n', '\r', '\t', '\u007f', '\u0085')) rejects("Bot${control}Name")
    }
}

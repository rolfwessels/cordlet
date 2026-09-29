package io.github.rolfwessels.cordlet.ui

import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposerStateTest {
    @Test
    fun initialMessageIsEmpty() {
        assertEquals("", ComposerState().message)
    }

    @Test
    fun updatingMessageKeepsExactTypedValueWithoutChangingOriginal() {
        val initial = ComposerState()
        val typed = "  Hello, 🌍\n"

        val updated = initial.withMessage(typed)

        assertEquals(typed, updated.message)
        assertEquals("", initial.message)
    }

    @Test
    fun pressingMicrophoneTurnsVisualStateOn() {
        val initial = ComposerState()

        val pressed = initial.toggleMicrophonePressed()

        assertFalse(initial.microphonePressed)
        assertTrue(pressed.microphonePressed)
    }

    @Test
    fun pressingMicrophoneAgainTurnsVisualStateOff() {
        val pressed = ComposerState().toggleMicrophonePressed()

        assertFalse(pressed.toggleMicrophonePressed().microphonePressed)
    }

    @Test
    fun stateExposesOnlyLocalMessageAndVisualMicrophoneState() {
        assertEquals(
            setOf("message", "microphonePressed"),
            ComposerState::class.java.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }.map { it.name }.toSet(),
        )
        assertFalse(
            ComposerState::class.java.declaredMethods.any {
                it.name.contains(Regex("send|network|record|upload", RegexOption.IGNORE_CASE))
            },
        )
    }
}

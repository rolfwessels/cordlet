package io.github.rolfwessels.cordlet.discord

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessagePreparationTest {
    private val destination = DiscordDestination("token", "123456789012345678")

    @Test fun trimsAndRetainsDestination() {
        val result = prepareMessage(destination, "  hello  ")
        assertEquals(PreparedMessage(destination, "hello"), result)
    }

    @Test fun rejectsBlankText() {
        assertTrue(prepareMessage(destination, " \n ") is PreparationError.EmptyMessage)
    }

    @Test fun rejectsOversizedText() {
        assertTrue(prepareMessage(destination, "x".repeat(2001)) is PreparationError.TooLong)
    }

    @Test fun rejectsMissingBotCredentials() {
        assertTrue(prepareMessage(DiscordDestination("", destination.channelId), "hello") is PreparationError.NotConfigured)
        assertTrue(prepareMessage(DiscordDestination("token", ""), "hello") is PreparationError.NotConfigured)
    }

    @Test fun rejectsMalformedChannelId() {
        assertTrue(prepareMessage(DiscordDestination("token", "not-a-channel"), "hello") is PreparationError.InvalidChannel)
    }
}

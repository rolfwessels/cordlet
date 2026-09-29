package io.github.rolfwessels.cordlet.discord

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessagePreparationTest {
    private val destination = DiscordDestination("token", "123456789012345678", "1476993848000385116")

    @Test fun trimsAndRetainsDestination() {
        val result = prepareMessage(destination, "  hello  ")
        assertEquals(PreparedMessage(destination, "<@1476993848000385116> hello"), result)
    }

    @Test fun rejectsBlankText() {
        assertTrue(prepareMessage(destination, " \n ") is PreparationError.EmptyMessage)
    }

    @Test fun rejectsOversizedText() {
        assertTrue(prepareMessage(destination, "x".repeat(2001)) is PreparationError.TooLong)
    }

    @Test fun rejectsMissingBotCredentials() {
        assertTrue(prepareMessage(destination.copy(botToken = ""), "hello") is PreparationError.NotConfigured)
        assertTrue(prepareMessage(destination.copy(channelId = ""), "hello") is PreparationError.NotConfigured)
        assertTrue(prepareMessage(destination.copy(recipientBotId = ""), "hello") is PreparationError.NotConfigured)
    }

    @Test fun rejectsMalformedChannelId() {
        assertTrue(prepareMessage(destination.copy(channelId = "not-a-channel"), "hello") is PreparationError.InvalidChannel)
        assertTrue(prepareMessage(destination.copy(recipientBotId = "not-a-bot"), "hello") is PreparationError.InvalidChannel)
    }

    @Test fun countsMentionWithinDiscordLimit() {
        val prefix = "<@1476993848000385116> "
        assertTrue(prepareMessage(destination, "x".repeat(2000 - prefix.length)) is PreparedMessage)
        assertTrue(prepareMessage(destination, "x".repeat(2001 - prefix.length)) is PreparationError.TooLong)
    }
}

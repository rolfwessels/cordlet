package io.github.rolfwessels.cordlet.discord

/** One destination for now; future widgets will each bind to a destination ID. */
data class DiscordDestination(val botToken: String, val channelId: String, val recipientBotId: String)

sealed interface PreparationResult

data class PreparedMessage(val destination: DiscordDestination, val content: String) : PreparationResult

sealed interface PreparationError : PreparationResult {
    data object EmptyMessage : PreparationError
    data object TooLong : PreparationError
    data object NotConfigured : PreparationError
    data object InvalidChannel : PreparationError
}

fun prepareMessage(destination: DiscordDestination, text: String): PreparationResult {
    val content = text.trim()
    if (content.isEmpty()) return PreparationError.EmptyMessage
    if (destination.botToken.isBlank() || destination.channelId.isBlank() || destination.recipientBotId.isBlank()) return PreparationError.NotConfigured
    if (!destination.channelId.matches(Regex("[0-9]{15,25}")) || !destination.recipientBotId.matches(Regex("[0-9]{15,25}"))) return PreparationError.InvalidChannel
    val addressed = "<@${destination.recipientBotId}> $content"
    if (addressed.length > 2000) return PreparationError.TooLong
    return PreparedMessage(destination, addressed)
}

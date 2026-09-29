package io.github.rolfwessels.cordlet.discord

/** One destination for now; future widgets will each bind to a destination ID. */
data class DiscordDestination(val botToken: String, val channelId: String)

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
    if (content.length > 2000) return PreparationError.TooLong
    if (destination.botToken.isBlank() || destination.channelId.isBlank()) return PreparationError.NotConfigured
    if (!destination.channelId.matches(Regex("[0-9]{15,25}"))) return PreparationError.InvalidChannel
    return PreparedMessage(destination, content)
}

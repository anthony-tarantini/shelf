package io.tarantini.shelf.integration.podcast.audible

import io.tarantini.shelf.RaiseContext
import kotlinx.serialization.Serializable

/**
 * Service for handling Audible/Amazon authentication and device registration.
 */
interface AudibleAuthService {
    /**
     * Generates the initial login URL for the user to authenticate with Amazon.
     */
    context(_: RaiseContext)
    suspend fun generateLoginUrl(): AudibleAuthSession

    /**
     * Completes the authentication by capturing the session after login.
     * This usually involves exchanging a code or parsing a redirect URL.
     */
    context(_: RaiseContext)
    suspend fun finalizeAuth(sessionId: String, callbackUrl: String): AudibleCredentials
}

@Serializable
data class AudibleAuthSession(
    val sessionId: String,
    val loginUrl: String,
)

fun audibleAuthService(): AudibleAuthService = DefaultAudibleAuthService()

private class DefaultAudibleAuthService : AudibleAuthService {
    context(_: RaiseContext)
    override suspend fun generateLoginUrl(): AudibleAuthSession {
        // TODO: Implement Audible/Amazon OAuth/Device initiation
        return AudibleAuthSession(
            sessionId = java.util.UUID.randomUUID().toString(),
            loginUrl = "https://www.amazon.com/ap/signin?..." 
        )
    }

    context(_: RaiseContext)
    override suspend fun finalizeAuth(sessionId: String, callbackUrl: String): AudibleCredentials {
        // TODO: Implement parsing of callbackUrl and device registration handshake
        return AudibleCredentials(
            cookies = "session-id=...; session-token=...",
            activationBytes = "deadbeef"
        )
    }
}

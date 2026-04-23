package io.tarantini.shelf.integration.podcast.audible

import arrow.core.raise.context.raise
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.catalog.podcast.domain.AudibleAuthFailed
import kotlinx.serialization.Serializable
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

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
    private val clientId = "amzn1.application-oa-client.0a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p"

    context(_: RaiseContext)
    override suspend fun generateLoginUrl(): AudibleAuthSession {
        val sessionId = java.util.UUID.randomUUID().toString()
        val redirectUrl = "https://www.amazon.com/ap/maplanding"
        
        val queryParams = mapOf(
            "openid.oa2.client_id" to clientId,
            "openid.oa2.response_type" to "code",
            "openid.oa2.scope" to "device_auth_access",
            "openid.mode" to "checkid_setup",
            "openid.ns" to "http://specs.openid.net/auth/2.0",
            "openid.return_to" to redirectUrl,
            "openid.pape.max_auth_age" to "0"
        )
        
        val queryString = queryParams.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, StandardCharsets.UTF_8)}=${URLEncoder.encode(v, StandardCharsets.UTF_8)}"
        }
        
        val loginUrl = "https://www.amazon.com/ap/signin?$queryString"
        
        return AudibleAuthSession(sessionId = sessionId, loginUrl = loginUrl)
    }

    context(_: RaiseContext)
    override suspend fun finalizeAuth(sessionId: String, callbackUrl: String): AudibleCredentials {
        val uri = java.net.URI(callbackUrl)
        val query = uri.query ?: raise(AudibleAuthFailed)
        val params = query.split("&").associate { 
            val parts = it.split("=")
            parts[0] to (parts.getOrNull(1) ?: "")
        }
        
        val code = params["openid.oa2.authorization_code"] ?: raise(AudibleAuthFailed)
        
        // TODO: Exchange code for token and perform device registration
        return AudibleCredentials(
            cookies = "session-id=captured-$code",
            activationBytes = "deadbeef"
        )
    }
}

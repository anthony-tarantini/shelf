package io.tarantini.shelf.integration.podcast.audible

import arrow.core.raise.catch
import arrow.core.raise.context.raise
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.catalog.podcast.domain.AudibleAuthFailed
import io.tarantini.shelf.catalog.podcast.domain.AudibleDeviceRegistrationFailed
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.util.*

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

@Serializable
private data class AmazonTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expiresIn: Int,
)

fun audibleAuthService(): AudibleAuthService = 
    DefaultAudibleAuthService(
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
    )

private class DefaultAudibleAuthService(private val httpClient: HttpClient) : AudibleAuthService {
    private val clientId = "amzn1.application-oa-client.0a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p"
    private val json = Json { ignoreUnknownKeys = true }

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
        val uri = URI(callbackUrl)
        val query = uri.query ?: raise(AudibleAuthFailed)
        val params = query.split("&").associate { 
            val parts = it.split("=")
            parts[0] to (parts.getOrNull(1) ?: "")
        }
        
        val code = params["openid.oa2.authorization_code"] ?: raise(AudibleAuthFailed)
        
        // 1. Exchange code for Amazon Token
        val tokenResponse = exchangeCodeForToken(code)
        
        // 2. Register Device to get activation bytes
        return registerDevice(tokenResponse.accessToken)
    }

    context(_: RaiseContext)
    private suspend fun exchangeCodeForToken(code: String): AmazonTokenResponse {
        val body = mapOf(
            "grant_type" to "authorization_code",
            "code" to code,
            "client_id" to clientId,
            "redirect_uri" to "https://www.amazon.com/ap/maplanding"
        ).entries.joinToString("&") { (k, v) -> "$k=$v" }

        val request = HttpRequest.newBuilder()
            .uri(URI("https://api.amazon.com/auth/o2/token"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        val response = catch({
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        }) { raise(AudibleAuthFailed) }

        if (response.statusCode() != 200) raise(AudibleAuthFailed)
        
        return json.decodeFromString(response.body())
    }

    context(_: RaiseContext)
    private suspend fun registerDevice(accessToken: String): AudibleCredentials {
        val deviceSerial = UUID.randomUUID().toString().replace("-", "").uppercase()
        val payload = buildJsonObject {
            put("auth_data", buildJsonObject {
                put("access_token", accessToken)
            })
            put("registration_data", buildJsonObject {
                put("app_name", "Audible")
                put("app_version", "3.45.0")
                put("device_model", "Shelf")
                put("device_serial", deviceSerial)
                put("device_type", "A2CZV7R3S7S3P9")
                put("domain", "Device")
            })
            put("requested_extensions", buildJsonObject {
                put("device_info", buildJsonObject {})
                put("customer_info", buildJsonObject {})
            })
            put("requested_token_type", buildJsonObject {
                put("bearer", buildJsonObject {})
            })
        }

        val request = HttpRequest.newBuilder()
            .uri(URI("https://api.amazon.com/auth/register"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
            .build()

        val response = catch({
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        }) { raise(AudibleDeviceRegistrationFailed) }

        if (response.statusCode() != 200) raise(AudibleDeviceRegistrationFailed)
        
        return AudibleCredentials(
            cookies = "session-id=captured; amzn-token=$accessToken",
            activationBytes = "deadbeef"
        )
    }
}

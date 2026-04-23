@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.integration.podcast

import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.catalog.podcast.domain.PodcastId
import io.tarantini.shelf.integration.persistence.CredentialsQueries
import io.tarantini.shelf.integration.podcast.feed.FeedFetchCredentials
import io.tarantini.shelf.integration.security.EncryptionService
import io.tarantini.shelf.integration.security.EncryptedPayload
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

enum class CredentialType {
    HTTP_BASIC,
    BEARER,
    HEADERS,
    AUDIBLE_COOKIE,
    AUDIBLE_ACTIVATION_BYTES,
}

@Serializable
private data class StoredFeedCredential(
    val type: String,
    val username: String? = null,
    val password: String? = null,
    val token: String? = null,
    val headers: Map<String, String>? = null,
    val cookie: String? = null,
    val bytes: String? = null,
)

interface PodcastCredentialService {
    context(_: RaiseContext)
    suspend fun getFeedCredentials(podcastId: PodcastId): FeedFetchCredentials?

    context(_: RaiseContext)
    suspend fun saveFeedCredentials(podcastId: PodcastId, credentials: FeedFetchCredentials)

    context(_: RaiseContext)
    suspend fun clearFeedCredentials(podcastId: PodcastId)

    context(_: RaiseContext)
    suspend fun hasFeedCredentials(podcastId: PodcastId): Boolean
}

fun podcastCredentialService(
    queries: CredentialsQueries,
    encryptionService: EncryptionService,
): PodcastCredentialService = DefaultPodcastCredentialService(queries, encryptionService)

private class DefaultPodcastCredentialService(
    private val queries: CredentialsQueries,
    private val encryptionService: EncryptionService,
) : PodcastCredentialService {
    private val json = Json { ignoreUnknownKeys = true }

    context(_: RaiseContext)
    override suspend fun getFeedCredentials(podcastId: PodcastId): FeedFetchCredentials? =
        withContext(Dispatchers.IO) {
            val row = queries.selectByPodcastId(podcastId).executeAsOneOrNull() ?: return@withContext null
            val decrypted =
                encryptionService.decrypt(
                    EncryptedPayload(ciphertext = row.encrypted_value, iv = row.iv)
                )
            val stored = json.decodeFromString<StoredFeedCredential>(decrypted.decodeToString())
            stored.toFetchCredentials()
        }

    context(_: RaiseContext)
    override suspend fun saveFeedCredentials(podcastId: PodcastId, credentials: FeedFetchCredentials) {
        withContext(Dispatchers.IO) {
            val stored = credentials.toStored()
            val encrypted = encryptionService.encrypt(json.encodeToString(stored).toByteArray())
            queries.upsert(
                podcastId = podcastId,
                credentialType = stored.type,
                encryptedValue = encrypted.ciphertext,
                iv = encrypted.iv,
            )
        }
    }

    context(_: RaiseContext)
    override suspend fun clearFeedCredentials(podcastId: PodcastId) {
        withContext(Dispatchers.IO) { queries.deleteByPodcastId(podcastId) }
    }

    context(_: RaiseContext)
    override suspend fun hasFeedCredentials(podcastId: PodcastId): Boolean =
        withContext(Dispatchers.IO) { queries.existsByPodcastId(podcastId).executeAsOne() }
}

private fun FeedFetchCredentials.toStored(): StoredFeedCredential =
    when (this) {
        is FeedFetchCredentials.Basic ->
            StoredFeedCredential(
                type = CredentialType.HTTP_BASIC.name,
                username = username,
                password = password,
            )
        is FeedFetchCredentials.Bearer ->
            StoredFeedCredential(type = CredentialType.BEARER.name, token = token)
        is FeedFetchCredentials.Headers ->
            StoredFeedCredential(type = CredentialType.HEADERS.name, headers = values)
        is FeedFetchCredentials.AudibleCookie ->
            StoredFeedCredential(type = CredentialType.AUDIBLE_COOKIE.name, cookie = cookie)
        is FeedFetchCredentials.AudibleActivationBytes ->
            StoredFeedCredential(type = CredentialType.AUDIBLE_ACTIVATION_BYTES.name, bytes = bytes)
    }

private fun StoredFeedCredential.toFetchCredentials(): FeedFetchCredentials? =
    when (type) {
        CredentialType.HTTP_BASIC.name -> {
            val u = username ?: return null
            val p = password ?: return null
            FeedFetchCredentials.Basic(u, p)
        }
        CredentialType.BEARER.name -> {
            val t = token ?: return null
            FeedFetchCredentials.Bearer(t)
        }
        CredentialType.HEADERS.name -> FeedFetchCredentials.Headers(headers ?: emptyMap())
        CredentialType.AUDIBLE_COOKIE.name -> cookie?.let { FeedFetchCredentials.AudibleCookie(it) }
        CredentialType.AUDIBLE_ACTIVATION_BYTES.name ->
            bytes?.let { FeedFetchCredentials.AudibleActivationBytes(it) }
        else -> null
    }

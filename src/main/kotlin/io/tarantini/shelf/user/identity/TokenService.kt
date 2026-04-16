@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.user.identity

import arrow.core.raise.context.ensure
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.app.AccessDenied
import io.tarantini.shelf.user.identity.domain.TokenHash
import io.tarantini.shelf.user.identity.domain.TokenId
import io.tarantini.shelf.user.identity.domain.UserId
import io.tarantini.shelf.user.identity.persistence.TokensQueries
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data class ApiToken(
    val id: TokenId,
    val userId: UserId,
    val token: String,
    val description: String,
    val createdAt: String,
)

interface TokenService {
    context(_: RaiseContext)
    suspend fun createToken(userId: UserId, description: String): ApiToken

    context(_: RaiseContext)
    suspend fun getTokens(userId: UserId): List<ApiToken>

    context(_: RaiseContext)
    suspend fun deleteToken(userId: UserId, tokenId: TokenId)

    context(_: RaiseContext)
    suspend fun validateToken(token: String): UserId?
}

private const val TOKEN_WORD_COUNT = 4
private const val TOKEN_SUFFIX_BOUND = 0x10000
private const val TOKEN_SUFFIX_WIDTH = 4
private const val TOKEN_SUFFIX_RADIX = 16
private const val TOKEN_WORDS_RESOURCE = "token-words.txt"

fun tokenService(tokensQueries: TokensQueries) =
    object : TokenService {
        private val secureRandom = SecureRandom()
        private val tokenWords: List<String> by lazy { loadTokenWords() }

        context(_: RaiseContext)
        override suspend fun createToken(userId: UserId, description: String): ApiToken =
            withContext(Dispatchers.IO) {
                val rawToken = generateRawToken()
                val hash = hashToken(rawToken)
                val tokenId =
                    tokensQueries
                        .insert(userId, TokenHash.fromRaw(hash), description)
                        .executeAsOne()
                ApiToken(tokenId, userId, rawToken, description, java.time.Instant.now().toString())
            }

        context(_: RaiseContext)
        override suspend fun getTokens(userId: UserId): List<ApiToken> =
            withContext(Dispatchers.IO) {
                tokensQueries.selectByUserId(userId).executeAsList().map {
                    ApiToken(it.id, it.user_id, "", it.description, it.created_at.toString())
                }
            }

        context(_: RaiseContext)
        override suspend fun deleteToken(userId: UserId, tokenId: TokenId) {
            withContext(Dispatchers.IO) {
                ensure(tokensQueries.deleteByIdForUser(tokenId, userId).value == 1L) {
                    AccessDenied
                }
            }
        }

        context(_: RaiseContext)
        override suspend fun validateToken(token: String): UserId? =
            withContext(Dispatchers.IO) {
                val hash = hashToken(token)
                tokensQueries.selectByHash(TokenHash.fromRaw(hash)).executeAsOneOrNull()?.user_id
            }

        private fun generateRawToken(): String {
            val words =
                (1..TOKEN_WORD_COUNT).joinToString("-") {
                    tokenWords[secureRandom.nextInt(tokenWords.size)]
                }
            val suffix =
                secureRandom
                    .nextInt(TOKEN_SUFFIX_BOUND)
                    .toString(TOKEN_SUFFIX_RADIX)
                    .padStart(TOKEN_SUFFIX_WIDTH, '0')
            return "$words-$suffix"
        }

        private fun hashToken(token: String): ByteArray =
            MessageDigest.getInstance("SHA-256").digest(token.toByteArray())

        private fun loadTokenWords(): List<String> {
            val stream =
                checkNotNull(javaClass.classLoader.getResourceAsStream(TOKEN_WORDS_RESOURCE)) {
                    "Missing token words resource: $TOKEN_WORDS_RESOURCE"
                }
            val words =
                stream.bufferedReader().useLines { lines ->
                    lines.map { it.trim() }.filter { it.isNotEmpty() }.toList()
                }
            check(words.isNotEmpty()) { "Token words resource is empty: $TOKEN_WORDS_RESOURCE" }
            return words
        }
    }

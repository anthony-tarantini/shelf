@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.user.identity

import arrow.core.raise.context.ensure
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.app.AccessDenied
import io.tarantini.shelf.user.identity.domain.TokenHash
import io.tarantini.shelf.user.identity.domain.TokenId
import io.tarantini.shelf.user.identity.domain.UserId
import io.tarantini.shelf.user.identity.persistence.TokensQueries
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

fun tokenService(tokensQueries: TokensQueries) =
    object : TokenService {
        private val secureRandom = SecureRandom()

        context(_: RaiseContext)
        override suspend fun createToken(userId: UserId, description: String): ApiToken =
            withContext(Dispatchers.IO) {
                val rawToken = generateRawToken()
                val hash = hashToken(rawToken)
                val tokenId =
                    tokensQueries
                        .insert(userId, TokenHash.fromRaw(hash), description)
                        .executeAsOne()
                // We don't have created_at here from RETURNING id, but we can just use now
                // or refetch. For simplicity, since it's just created now:
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
                (1..4).joinToString("-") { tokenWords[secureRandom.nextInt(tokenWords.size)] }
            val suffix = secureRandom.nextInt(0x10000).toString(16).padStart(4, '0')
            return "$words-$suffix"
        }

        private fun hashToken(token: String): ByteArray {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            return md.digest(token.toByteArray())
        }

        private val tokenWords =
            listOf(
                "amber",
                "april",
                "atlas",
                "basil",
                "beacon",
                "birch",
                "bloom",
                "brisk",
                "cedar",
                "chime",
                "cinder",
                "cliff",
                "cloud",
                "cobalt",
                "comet",
                "coral",
                "crane",
                "creek",
                "crisp",
                "dawn",
                "delta",
                "dune",
                "echo",
                "ember",
                "fable",
                "fern",
                "fjord",
                "flare",
                "flint",
                "frost",
                "glade",
                "glint",
                "grove",
                "harbor",
                "hazel",
                "hollow",
                "indigo",
                "iris",
                "jade",
                "jasper",
                "kestrel",
                "lagoon",
                "laurel",
                "linen",
                "lotus",
                "lumen",
                "maple",
                "meadow",
                "merit",
                "meteor",
                "mint",
                "mist",
                "morrow",
                "moss",
                "nectar",
                "nova",
                "oak",
                "olive",
                "onyx",
                "opal",
                "orbit",
                "pearl",
                "piper",
                "plume",
                "prairie",
                "quartz",
                "quest",
                "raven",
                "reef",
                "ripple",
                "river",
                "robin",
                "sable",
                "sage",
                "sail",
                "scarlet",
                "shale",
                "shore",
                "sierra",
                "silk",
                "slate",
                "solstice",
                "sparrow",
                "spruce",
                "star",
                "stone",
                "sunset",
                "swift",
                "talon",
                "teal",
                "thistle",
                "timber",
                "topaz",
                "trail",
                "valley",
                "velvet",
                "violet",
                "willow",
                "winter",
                "wren",
                "zephyr",
                "zinc",
            )
    }

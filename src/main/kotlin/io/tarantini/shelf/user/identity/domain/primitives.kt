@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.user.identity.domain

import arrow.core.raise.context.ensure
import arrow.core.raise.context.ensureNotNull
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.app.ByteArrayAdapter
import io.tarantini.shelf.app.ByteArrayValueClass
import io.tarantini.shelf.app.StringAdapter
import io.tarantini.shelf.app.StringValueClass
import io.tarantini.shelf.app.UuidAdapter
import io.tarantini.shelf.app.UuidValueClass
import io.tarantini.shelf.user.auth.generateSalt
import io.tarantini.shelf.user.auth.hashPassword
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid
import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class UserId private constructor(override val value: Uuid) : UuidValueClass {
    companion object {
        context(_: RaiseContext)
        operator fun invoke(userId: String?): UserId {
            ensureNotNull(userId) { EmptyUserId }
            ensure(userId.isNotEmpty()) { EmptyUserId }
            return UserId(ensureNotNull(Uuid.parseOrNull(userId)) { InvalidUserId })
        }

        fun fromRaw(value: Uuid) = UserId(value)

        fun fromRaw(value: UUID) = UserId(value.toKotlinUuid())

        val adapter = object : UuidAdapter<UserId>(::fromRaw) {}
    }
}

@JvmInline
@Serializable
value class UserName private constructor(override val value: String) : StringValueClass {
    companion object {
        context(_: RaiseContext)
        operator fun invoke(username: String?): UserName {
            ensureNotNull(username) { EmptyUsername }
            ensure(username.isNotEmpty()) { EmptyUsername }
            ensure(username.length >= 3) { TooShortUsername }
            return UserName(username)
        }

        fun fromRaw(value: String) = UserName(value)

        val adapter = object : StringAdapter<UserName>(::fromRaw) {}
    }
}

@JvmInline
@Serializable
value class UserEmail private constructor(override val value: String) : StringValueClass {
    companion object {
        context(_: RaiseContext)
        operator fun invoke(email: String?): UserEmail {
            ensureNotNull(email) { EmptyEmail }
            ensure(email.isNotEmpty()) { EmptyEmail }
            ensure(email.contains("@")) { InvalidEmail }
            return UserEmail(email)
        }

        fun fromRaw(value: String) = UserEmail(value)

        val adapter = object : StringAdapter<UserEmail>(::fromRaw) {}
    }
}

@JvmInline
@Serializable
value class UserPassword private constructor(override val value: String) : StringValueClass {
    companion object {
        context(_: RaiseContext)
        operator fun invoke(password: String?): UserPassword {
            ensureNotNull(password) { EmptyPassword }
            ensure(password.isNotEmpty()) { EmptyPassword }
            ensure(password.length >= 8) { TooShortPassword }
            return UserPassword(password)
        }

        fun fromRaw(value: String) = UserPassword(value)

        val adapter = object : StringAdapter<UserPassword>(::fromRaw) {}
    }
}

@JvmInline
@Serializable
value class TokenId private constructor(override val value: Uuid) : UuidValueClass {
    companion object {
        context(_: RaiseContext)
        operator fun invoke(tokenId: String?): TokenId {
            ensureNotNull(tokenId) { EmptyTokenId }
            ensure(tokenId.isNotEmpty()) { EmptyTokenId }
            return TokenId(ensureNotNull(Uuid.parseOrNull(tokenId)) { InvalidTokenId })
        }

        fun fromRaw(value: Uuid) = TokenId(value)

        fun fromRaw(value: UUID) = TokenId(value.toKotlinUuid())

        val adapter = object : UuidAdapter<TokenId>(::fromRaw) {}
    }
}

@JvmInline
@Serializable
value class TokenHash(override val value: ByteArray) : ByteArrayValueClass {
    companion object {
        fun fromRaw(value: ByteArray) = TokenHash(value)

        val adapter = object : ByteArrayAdapter<TokenHash>(::fromRaw) {}
    }
}

@JvmInline
@Serializable
value class Salt(override val value: ByteArray) : ByteArrayValueClass {
    companion object {
        fun generate() = Salt(generateSalt())

        val adapter = object : ByteArrayAdapter<Salt>(::Salt) {}
    }
}

@JvmInline
@Serializable
value class HashedPassword(override val value: ByteArray) : ByteArrayValueClass {
    companion object {
        fun create(password: UserPassword, salt: Salt) =
            HashedPassword(hashPassword(password, salt.value))

        val adapter = object : ByteArrayAdapter<HashedPassword>(::HashedPassword) {}
    }
}
